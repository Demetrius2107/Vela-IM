package com.vela.im.shared.types.enums;

import com.vela.im.shared.exception.ApplicationExceptionEnum;

/**
 * 业务错误码枚举，覆盖所有业务模块的通用错误场景。
 */
public enum BusinessErrorCode implements ApplicationExceptionEnum {

    // ====== 通用 (90xxx) ======
    OK(0, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    // ====== Bot (91xxx) ======
    BOT_NOT_FOUND(91001, "Bot不存在"),
    BOT_DISABLED(91002, "Bot已禁用"),
    BOT_UNAVAILABLE(91003, "Bot不可用"),
    BOT_ALREADY_EXISTS(91004, "Bot已存在"),
    BOT_ALREADY_INSTALLED(91005, "用户已安装该Bot"),
    BOT_INSTALL_NOT_FOUND(91006, "未安装该Bot"),

    // ====== Admin (92xxx) ======
    ADMIN_NOT_FOUND(92001, "管理员不存在"),
    ADMIN_ALREADY_EXISTS(92002, "管理员已存在"),
    ADMIN_LOGIN_FAILED(92003, "管理员账号或密码错误"),
    ADMIN_DISABLED(92004, "该管理员账号已被禁用"),

    // ====== User (93xxx) ======
    USER_NOT_FOUND(93001, "用户不存在"),

    // ====== Group (94xxx) ======
    GROUP_NOT_FOUND(94001, "群组不存在"),

    // ====== Message (95xxx) ======
    MESSAGE_NOT_FOUND(95001, "消息不存在"),

    // ====== Office (96xxx) ======
    SCHEDULE_NOT_FOUND(96001, "日程不存在"),
    TODO_NOT_FOUND(96002, "待办不存在"),
    APPROVAL_NOT_FOUND(96003, "审批不存在"),
    APPROVAL_ALREADY_PROCESSED(96004, "审批已处理"),

    // ====== Document (97xxx) ======
    DOCUMENT_NOT_FOUND(97001, "文档不存在"),
    CATEGORY_NOT_FOUND(97002, "分类不存在"),
    CATEGORY_HAS_CHILDREN(97003, "分类下有子分类，无法删除"),
    CATEGORY_HAS_DOCUMENTS(97004, "分类下有文档，无法删除"),
    DOCUMENT_PERMISSION_DENIED(97005, "无权限操作该文档"),
    DOCUMENT_ALREADY_FAVORITED(97006, "文档已收藏"),
    DOCUMENT_STATUS_ILLEGAL(97007, "文档状态不允许该操作"),
    VERSION_NOT_FOUND(97008, "版本不存在"),
    APPROVAL_REASON_REQUIRED(97009, "驳回必须填写原因"),
    DOCUMENT_IS_DELETED(97010, "文档已删除"),

    // ====== Favorite (98xxx) ======
    FAVORITE_ALREADY_EXISTS(98001, "消息已收藏"),
    FAVORITE_NOT_FOUND(98002, "收藏不存在"),

    ;

    private final int code;
    private final String error;

    BusinessErrorCode(int code, String error) {
        this.code = code;
        this.error = error;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getError() {
        return error;
    }
}
