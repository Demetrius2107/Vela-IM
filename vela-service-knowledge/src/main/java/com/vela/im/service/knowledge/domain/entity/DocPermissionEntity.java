package com.vela.im.service.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档级权限（RBAC）。
 * docId=0 表示应用级角色（role=admin 即应用管理员）。
 * role 取值：admin / owner / editor / reader。
 */
@Data
@TableName("vela_doc_permission")
public class DocPermissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;

    /** 文档ID；0 表示应用级权限 */
    private Long docId;

    private String userId;

    /** admin / owner / editor / reader */
    private String role;

    private Long createTime;
}
