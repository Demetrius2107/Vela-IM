#!/usr/bin/env python3
"""
Vela IM - Phase 2 TCP 协议连通性测试脚本
=========================================
测试链路: TCP 连接 → 登录 → 发 P2P 消息 → 收 ACK

协议格式:
  客户端→服务端: [4B command][4B version][4B clientType][4B messageType]
                 [4B appId][4B imeiLength][imei][4B bodyLength][JSON body]
  服务端→客户端: [4B command][4B bodyLength][JSON body]

命令码:
  LOGIN = 9000 (0x2328)
  LOGIN_ACK = 9001 (0x2329)
  MSG_P2P = 1103 (0x44F)
  MSG_ACK = 1046 (0x416)
  PING = 9999 (0x270F)

使用方式:
    python phase2_tcp_test.py [--host HOST] [--port PORT]
"""

import struct
import json
import time
import socket
import argparse
import sys
import traceback
from datetime import datetime

# ==================== 配置 ====================

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 9000
APP_ID = 10000

USER_A_ID = "test_conn_a"
USER_B_ID = "test_conn_b"

# 命令码
CMD_LOGIN = 0x2328       # 9000
CMD_LOGIN_ACK = 0x2329   # 9001
CMD_LOGOUT = 0x232B      # 9003
CMD_PING = 0x270F        # 9999
CMD_MSG_P2P = 0x44F      # 1103
CMD_MSG_ACK = 0x416      # 1046


class Color:
    GREEN = "\033[92m"
    RED = "\033[91m"
    YELLOW = "\033[93m"
    CYAN = "\033[96m"
    BOLD = "\033[1m"
    END = "\033[0m"


if sys.platform == "win32":
    import os
    os.system("")


def log(msg, color=""):
    ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
    print(f"{color}[{ts}] {msg}{Color.END}")


def log_send(cmd_code, body_str):
    log(f"  >>> SEND command=0x{cmd_code:04X} body={body_str}", Color.CYAN)


def log_recv(cmd_code, payload):
    body_str = json.dumps(payload, ensure_ascii=False) if isinstance(payload, dict) else str(payload)
    log(f"  <<< RECV command=0x{cmd_code:04X} body={body_str[:200]}", Color.YELLOW)


# ==================== TCP 客户端 ====================

class TcpTestClient:
    """Vela IM TCP 协议测试客户端"""

    def __init__(self, host, port, user_id, app_id=APP_ID):
        self.host = host
        self.port = port
        self.user_id = user_id
        self.app_id = app_id
        self.sock = None
        self.imei = f"test-{user_id}-001"
        self.connected = False

    def connect(self):
        """建立 TCP 连接"""
        log(f"Connecting to {self.host}:{self.port}...")
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(10)
        try:
            self.sock.connect((self.host, self.port))
            self.connected = True
            self.sock.settimeout(5)
            return True
        except Exception as e:
            log(f"  [FAIL] Connection failed: {e}", Color.RED)
            return False

    def close(self):
        """关闭连接"""
        if self.sock:
            try:
                self.sock.close()
            except:
                pass
            self.connected = False

    def send_packet(self, command, body_dict):
        """发送二进制协议包"""
        body_json = json.dumps(body_dict, ensure_ascii=False)
        body_bytes = body_json.encode("utf-8")
        imei_bytes = self.imei.encode("utf-8")

        header = struct.pack(">7i",
            command,      # command
            1,            # version
            3,            # clientType (3=Windows)
            0,            # messageType (0=JSON)
            self.app_id,  # appId
            len(imei_bytes),  # imeiLength
            len(body_bytes),  # bodyLength
        )
        packet = header + imei_bytes + body_bytes
        self.sock.sendall(packet)

    def recv_packet(self):
        """接收服务端响应包 [4B command][4B bodyLength][JSON body]"""
        # 读取 8 字节包头
        header_bytes = self._recv_exact(8)
        if not header_bytes:
            return None, None
        command, body_len = struct.unpack(">2i", header_bytes)

        # 读取 body
        body_bytes = self._recv_exact(body_len) if body_len > 0 else b""
        if body_bytes is None and body_len > 0:
            return command, None

        body_str = body_bytes.decode("utf-8") if body_bytes else ""
        try:
            body_obj = json.loads(body_str)
        except:
            body_obj = body_str
        return command, body_obj

    def _recv_exact(self, n):
        """精确读取 n 字节"""
        data = b""
        while len(data) < n:
            try:
                chunk = self.sock.recv(n - len(data))
                if not chunk:
                    return None
                data += chunk
            except socket.timeout:
                return data if data else None
        return data

    # ==================== 业务操作 ====================

    def login(self):
        """登录"""
        log_send(CMD_LOGIN, f'{{"userId": "{self.user_id}"}}')
        body = {"userId": self.user_id}
        self.send_packet(CMD_LOGIN, body)
        # 先尝试非阻塞读取，诊断服务端是否有任何响应
        self.sock.settimeout(3)
        try:
            raw = self.sock.recv(256)
            if raw:
                log(f"  [DEBUG] Received {len(raw)} raw bytes: {raw.hex()}", Color.YELLOW)
            else:
                log(f"  [DEBUG] recv returned empty (connection closed by server)", Color.YELLOW)
            self.sock.settimeout(5)
            return None, {"error": "debug"}
        except socket.timeout:
            log(f"  [DEBUG] No bytes received within 3s (server not responding)", Color.YELLOW)
            self.sock.settimeout(5)
            return None, {"error": "timeout_no_bytes"}

    def send_message(self, to_id, content):
        """发送 P2P 消息"""
        msg_id = f"tcp_msg_{int(time.time()*1000)}"
        body = {
            "messageId": msg_id,
            "fromId": self.user_id,
            "toId": to_id,
            "messageRandom": 12345,
            "messageTime": int(time.time()),
            "messageBody": content,
        }
        log_send(CMD_MSG_P2P, json.dumps(body, ensure_ascii=False)[:100])
        self.send_packet(CMD_MSG_P2P, body)
        cmd, resp = self.recv_packet()
        if cmd == CMD_MSG_ACK:
            log_recv(CMD_MSG_ACK, resp)
            return True, resp
        elif cmd is not None:
            log_recv(cmd, resp or {})
            return False, resp
        else:
            log("  [FAIL] No ACK received", Color.RED)
            return False, {"error": "timeout"}


# ==================== 测试主流程 ====================

def run_phase2(host, port):
    test_time = datetime.now().strftime("%H:%M:%S")
    total = 0
    passed = 0

    print(f"\n{Color.BOLD}Vela IM - Phase 2 TCP 协议连通性测试{Color.END}")
    print(f"  Host:    {host}:{port}")
    print(f"  User A:  {USER_A_ID}")
    print(f"  User B:  {USER_B_ID}")
    print(f"  Time:    {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    # ---------- Step 1: 用户 A 连接 TCP ----------
    print(f"\n{'='*60}")
    log(f"Step 1: 用户 A 连接 TCP 网关", Color.BOLD + Color.CYAN)
    print(f"{'='*60}")
    total += 1
    client_a = TcpTestClient(host, port, USER_A_ID)
    if client_a.connect():
        log(f"  [PASS] 用户 A TCP 连接成功", Color.GREEN)
        passed += 1
    else:
        log(f"  [FAIL] 用户 A TCP 连接失败", Color.RED)
        return False

    # ---------- Step 2: 用户 A 登录 ----------
    print(f"\n{'='*60}")
    log(f"Step 2: 用户 A 登录", Color.BOLD + Color.CYAN)
    print(f"{'='*60}")
    total += 1
    ok, resp = client_a.login()
    if ok:
        log(f"  [PASS] 用户 A 登录成功", Color.GREEN)
        passed += 1
    else:
        log(f"  [FAIL] 用户 A 登录失败: {resp}", Color.RED)
        client_a.close()
        # 不直接返回，继续尝试后续步骤

    # ---------- Step 3: 发送 P2P 消息 ----------
    print(f"\n{'='*60}")
    log(f"Step 3: 发送 P2P 消息 (A -> B)", Color.BOLD + Color.CYAN)
    print(f"{'='*60}")
    total += 1
    msg_content = f"Phase2 TCP test at {test_time}"
    ok, resp = client_a.send_message(USER_B_ID, msg_content)
    if ok:
        log(f"  [PASS] P2P 消息发送成功，已收到 ACK", Color.GREEN)
        passed += 1
    else:
        log(f"  [WARN] P2P 消息发送异常: {resp}", Color.YELLOW)

    # ---------- Step 4: 用户 B 连接并登录 ----------
    print(f"\n{'='*60}")
    log(f"Step 4: 用户 B 连接并登录", Color.BOLD + Color.CYAN)
    print(f"{'='*60}")
    total += 1
    client_b = TcpTestClient(host, port, USER_B_ID)
    if client_b.connect():
        ok, resp = client_b.login()
        if ok:
            log(f"  [PASS] 用户 B 连接并登录成功", Color.GREEN)
            passed += 1
        else:
            log(f"  [FAIL] 用户 B 登录失败: {resp}", Color.RED)
    else:
        log(f"  [FAIL] 用户 B TCP 连接失败", Color.RED)

    # ---------- Step 5: B 向 A 发送消息 ----------
    print(f"\n{'='*60}")
    log(f"Step 5: 发送 P2P 消息 (B -> A)", Color.BOLD + Color.CYAN)
    print(f"{'='*60}")
    total += 1
    msg_content = f"Phase2 reverse test at {test_time}"
    ok, resp = client_b.send_message(USER_A_ID, msg_content)
    if ok:
        log(f"  [PASS] 反向 P2P 消息发送成功，已收到 ACK", Color.GREEN)
        passed += 1
    else:
        log(f"  [WARN] 反向 P2P 消息发送异常: {resp}", Color.YELLOW)

    # 清理
    client_a.close()
    client_b.close()

    # ---------- 汇总 ----------
    print(f"\n{'='*60}")
    print(f"{Color.BOLD}Phase 2 测试结果汇总{Color.END}")
    print(f"{'='*60}")
    print(f"  通过: {Color.GREEN}{passed}{Color.END} / {total}")
    rate = passed / total * 100 if total > 0 else 0
    color = Color.GREEN if passed == total else (Color.YELLOW if passed >= total * 0.6 else Color.RED)
    print(f"  通过率: {color}{rate:.0f}%{Color.END}")

    if passed == total:
        print(f"\n  {Color.GREEN}{Color.BOLD}ALL PASSED - TCP 协议链路完整可用{Color.END}")
    elif passed >= total * 0.6:
        print(f"\n  {Color.YELLOW}部分通过 - 核心链路基本可用{Color.END}")
    else:
        print(f"\n  {Color.RED}大量失败 - TCP 链路存在阻断问题{Color.END}")

    return passed >= 3  # 至少 3/5 通过才算基本可用


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Vela IM Phase 2 TCP 协议连通性测试")
    parser.add_argument("--host", default=DEFAULT_HOST, help=f"TCP 网关地址 (默认: {DEFAULT_HOST})")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"TCP 端口 (默认: {DEFAULT_PORT})")
    args = parser.parse_args()

    try:
        success = run_phase2(args.host, args.port)
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print(f"\n{Color.YELLOW}测试被中断{Color.END}")
        sys.exit(130)
    except Exception as e:
        log(f"测试异常: {e}", Color.RED)
        traceback.print_exc()
        sys.exit(1)
