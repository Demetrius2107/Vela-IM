package com.vela.im.service.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档收藏
 */
@Data
@TableName("vela_doc_favorite")
public class DocFavoriteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;

    private String userId;

    private Long docId;

    private Long createTime;
}
