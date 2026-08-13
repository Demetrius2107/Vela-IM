package com.vela.im.service.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("vela_document")
public class DocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;
    private String title;
    private String content;
    private String summary;
    private String creatorId;
    private Long categoryId;
    private String tags;
    private Long createTime;
    private Long updateTime;

    /** 文档状态：0草稿 1待审 2已发布 3驳回 */
    private Integer status;

    /** 逻辑删除：0否 1是 */
    private Integer isDeleted;

    /** 可见性：0公开 1私密 */
    private Integer visibility;

    /** 阅读数（冗余统计） */
    private Long readCount;

    /** 收藏数（冗余统计） */
    private Long favoriteCount;

    /** 检索高亮片段，非表字段 */
    @TableField(exist = false)
    private String highlight;
}
