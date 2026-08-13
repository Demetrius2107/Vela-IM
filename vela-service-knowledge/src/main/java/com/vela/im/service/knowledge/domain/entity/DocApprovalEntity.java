package com.vela.im.service.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档发布审批记录（单级审批：一人通过即发布）
 */
@Data
@TableName("vela_doc_approval")
public class DocApprovalEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;

    private Long docId;

    private String approverId;

    /** approve / reject */
    private String action;

    /** 驳回原因 */
    private String reason;

    private Long createTime;
}
