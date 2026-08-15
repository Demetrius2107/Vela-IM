package com.vela.im.service.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档阅读记录（同一用户同一文档每日仅一条，用于去重统计）
 */
@Data
@TableName("vela_doc_read")
public class DocReadEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;

    private Long docId;

    private String userId;

    /** yyyy-MM-dd */
    private String readDate;

    private Long createTime;
}
