package com.vela.im.shared.constants;

/**
 * <p>Title: ImConstants</p>
 * <p>Description: IM 系统全局常量定义，包含 TraceId、Channel 属性键、Redis/RabbitMQ 队列名、回调指令和序列 Key。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @createTime 2025-03-03
 * @updateTime 2026-07-24
 * <p>
 * Copyright © 2026 wanqiu All rights reserved
 * @since 1.0
 */
public class ImConstants {

    // ==================== TraceId ====================

    /**
     * 全链路追踪 ID 在 MDC/HTTP Header/MQ Header 中的 Key
     */
    public static class TraceId {
        public static final String TRACE_ID_KEY = "traceId";
        public static final String HTTP_HEADER_NAME = "X-Trace-Id";
        public static final String MQ_HEADER_NAME = "x-trace-id";
    }

    // ==================== Channel 属性键 ====================

    /**
     * Channel 属性 — 用户 ID
     */
    public static final String USER_ID = "userId";

    /**
     * Channel 属性 — 应用 ID
     */
    public static final String APP_ID = "appId";

    /**
     * Channel 属性 — 客户端类型
     */
    public static final String CLIENT_TYPE = "clientType";

    /**
     * Channel 属性 — 设备 IMEI
     */
    public static final String IMEI = "imei";

    /**
     * Channel 属性 — 客户端类型 + IMEI 组合键
     */
    public static final String CLIENT_IMEI = "clientImei";

    /**
     * Channel 属性 — 最后读取时间
     */
    public static final String READ_TIME = "readTime";

    // ==================== ZooKeeper 路径 ====================

    /**
     * ZooKeeper 根路径
     */
    public static final String VELA_ZK_ROOT = "/vela";

    /**
     * ZooKeeper TCP 节点路径
     */
    public static final String VELA_ZK_ROOT_TCP = "/tcp";

    /**
     * ZooKeeper WebSocket 节点路径
     */
    public static final String VELA_ZK_ROOT_WEB = "/web";

    // ==================== Redis 常量 ====================

    /**
     * <p>Title: Redis</p>
     * <p>Description: Redis Key 格式常量，定义各类缓存 Key 前缀。</p>
     */
    public static class Redis {

        /**
         * 用户签名，格式：appId:userSign:
         */
        public static final String USER_SIGN = "userSign";

        /**
         * 用户上线通知 Channel
         */
        public static final String USER_LOGIN_CHANNEL = "signal/channel/LOGIN_USER_INNER_QUEUE";

        /**
         * 用户 Session 前缀，appId + :userSession: + userId，如 10000:userSession:lld
         */
        public static final String USER_SESSION_PREFIX = ":userSession:";

        /**
         * 用户在线状态订阅前缀（正向索引），appId + :userSubscribe: + 订阅者，集合存被订阅用户
         */
        public static final String USER_SUBSCRIBE_PREFIX = ":userSubscribe:";

        /**
         * 用户在线状态订阅前缀（反向索引），appId + :userSubscribed: + 被订阅用户，集合存订阅者
         */
        public static final String USER_SUBSCRIBED_PREFIX = ":userSubscribed:";

        /**
         * 用户自定义状态前缀，appId + :userCustomStatus: + userId，值为 customStatus|customText
         */
        public static final String USER_CUSTOM_STATUS_PREFIX = ":userCustomStatus:";

        /**
         * 缓存客户端消息防重，格式：appId + :cacheMessage: + messageId
         */
        public static final String CACHE_MESSAGE = "cacheMessage";

        /**
         * 离线消息 Key 前缀
         */
        public static final String OFFLINE_MESSAGE = "offlineMessage";

        /**
         * 离线消息降级水位线 Key 前缀，格式：appId + :offlineEvicted: + userId
         * 存储值为已降级到 DB 的最大 sequence，客户端同步时若 lastSequence 低于此值，
         * 需同时查询消息历史表以补全被降级的消息。
         */
        public static final String OFFLINE_EVICTED_WATERMARK = "offlineEvicted";

        /**
         * 序列号 Key 前缀
         */
        public static final String SEQ_PREFIX = "seq";

        /**
         * 用户订阅列表，格式：appId + :subscribe: + userId，Hash 结构，field 为订阅者
         */
        public static final String SUBSCRIBE = "subscribe";

        /**
         * 用户自定义在线状态，格式：appId + :userCustomStatus: + userId
         */
        public static final String USER_CUSTOM_STATUS = "userCustomStatus";
    }

    // ==================== RabbitMQ 常量 ====================

    /**
     * <p>Title: RabbitMQ</p>
     * <p>Description: RabbitMQ 队列名常量。</p>
     */
    public static class RabbitMQ {

        /**
         * 用户服务队列（网关 → 业务）
         */
        public static final String IM_TO_USER_SERVICE = "pipelineToUserService";

        /**
         * 消息服务队列（网关 → 业务）
         */
        public static final String IM_TO_MESSAGE_SERVICE = "pipelineToMessageService";

        /**
         * 群组服务队列（网关 → 业务）
         */
        public static final String IM_TO_GROUP_SERVICE = "pipelineToGroupService";

        /**
         * 好友服务队列（网关 → 业务）
         */
        public static final String IM_TO_FRIENDSHIP_SERVICE = "pipelineToFriendshipService";

        /**
         * 消息回传队列（业务 → 网关）
         */
        public static final String MESSAGE_SERVICE_TO_IM = "messageServiceToPipeline";

        /**
         * 群组回传队列（业务 → 网关）
         */
        public static final String GROUP_SERVICE_TO_IM = "GroupServiceToPipeline";

        /**
         * 好友回传队列（业务 → 网关）
         */
        public static final String FRIENDSHIP_TO_IM = "friendShipToPipeline";

        /**
         * 单聊消息存储队列
         */
        public static final String STORE_P2P_MESSAGE = "storeP2PMessage";

        /**
         * 群聊消息存储队列
         */
        public static final String STORE_GROUP_MESSAGE = "storeGroupMessage";
    }

    // ==================== 回调指令 ====================

    /**
     * <p>Title: CallbackCommand</p>
     * <p>Description: 回调事件指令常量，定义各类业务事件的回调命令字。</p>
     */
    public static class CallbackCommand {

        /**
         * 用户资料修改后回调
         */
        public static final String MODIFY_USER_AFTER = "user.modify.after";

        /**
         * 创建群组后回调
         */
        public static final String CREATE_GROUP_AFTER = "group.create.after";

        /**
         * 更新群组后回调
         */
        public static final String UPDATE_GROUP_AFTER = "group.update.after";

        /**
         * 解散群组后回调
         */
        public static final String DESTROY_GROUP_AFTER = "group.destroy.after";

        /**
         * 转让群组后回调
         */
        public static final String TRANSFER_GROUP_AFTER = "group.transfer.after";

        /**
         * 添加群成员前回调
         */
        public static final String GROUP_MEMBER_ADD_BEFORE = "group.member.add.before";

        /**
         * 添加群成员后回调
         */
        public static final String GROUP_MEMBER_ADD_AFTER = "group.member.add.after";

        /**
         * 删除群成员后回调
         */
        public static final String GROUP_MEMBER_DELETE_AFTER = "group.member.delete.after";

        /**
         * 添加好友前回调
         */
        public static final String ADD_FRIEND_BEFORE = "friend.add.before";

        /**
         * 添加好友后回调
         */
        public static final String ADD_FRIEND_AFTER = "friend.add.after";

        /**
         * 更新好友前回调
         */
        public static final String UPDATE_FRIEND_BEFORE = "friend.update.before";

        /**
         * 更新好友后回调
         */
        public static final String UPDATE_FRIEND_AFTER = "friend.update.after";

        /**
         * 删除好友后回调
         */
        public static final String DELETE_FRIEND_AFTER = "friend.delete.after";

        /**
         * 添加黑名单后回调
         */
        public static final String ADD_BLACK_AFTER = "black.add.after";

        /**
         * 删除黑名单回调
         */
        public static final String DELETE_BLACK = "black.delete";

        /**
         * 发送消息后回调
         */
        public static final String SEND_MESSAGE_AFTER = "message.send.after";

        /**
         * 发送消息前回调
         */
        public static final String SEND_MESSAGE_BEFORE = "message.send.before";
    }

    // ==================== 序列号 Key ====================

    /**
     * <p>Title: Seq</p>
     * <p>Description: Redis 序列号 Key 常量，用于生成各领域的单调递增序列。</p>
     */
    public static class Sequence {

        /**
         * 单聊消息序列号 Key
         */
        public static final String MESSAGE = "messageSequence";

        /**
         * 群聊消息序列号 Key
         */
        public static final String GROUP_MESSAGE = "groupMessageSequence";

        /**
         * 好友关系序列号 Key
         */
        public static final String FRIENDSHIP = "friendshipSequence";

        /**
         * 好友请求序列号 Key
         */
        public static final String FRIENDSHIP_REQUEST = "friendshipRequestSequence";

        /**
         * 好友分组序列号 Key
         */
        public static final String FRIENDSHIP_GROUP = "friendshipGroupSequence";

        /**
         * 群组序列号 Key
         */
        public static final String GROUP = "groupSequence";

        /**
         * 会话序列号 Key
         */
        public static final String CONVERSATION = "conversationSequence";
    }

    // ==================== Bot 常量 ====================

    public static class Bot {
        /** Bot 消息速率限制间隔（纳秒） */
        public static final long RATE_LIMIT_INTERVAL = 500_000_000L;

        /** Webhook 响应超时（秒） */
        public static final int WEBHOOK_TIMEOUT_SECONDS = 10;

        /** Bot 回复最大重试次数 */
        public static final int MAX_REPLY_RETRIES = 3;
    }

    // ==================== 通知常量 ====================

    public static class Notify {
        /** 通知前缀 */
        public static final String PREFIX = "notify";

        /** 消息通知类别 */
        public static final String TYPE_NEW_MESSAGE = "new_message";
        public static final String TYPE_CALL_INCOMING = "call_incoming";
    }
}
