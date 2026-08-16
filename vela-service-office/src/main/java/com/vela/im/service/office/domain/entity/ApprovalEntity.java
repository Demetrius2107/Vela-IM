package com.vela.im.service.office.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("vela_approval")
public class ApprovalEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;
    private String applicantId;   // 申请人
    private String title;         // 审批标题
    private String content;       // 审批内容
    private String type;          // 审批类型: leave/expense/purchase/other

    /** 0-待审批 1-已通过 2-已拒绝 3-已撤回 */
    private Integer status;

    private String approverId;    // 当前层审批人
    private String comment;       // 审批意见

    /** 审批链（逗号分隔的审批人 ID 列表，按层级顺序），如 "u001,u002" */
    private String approverChain;

    /** 当前审批层级（从 1 开始） */
    private Integer currentLevel;

    /** 审批链总层级 */
    private Integer maxLevel;

    private Long createTime;
    private Long updateTime;
}
