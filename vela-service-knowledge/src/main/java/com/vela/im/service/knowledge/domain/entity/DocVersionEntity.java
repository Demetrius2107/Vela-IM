package com.vela.im.service.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档版本快照
 */
@Data
@TableName("vela_doc_version")
public class DocVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;

    private Long docId;

    /** 版本号，从 1 递增 */
    private Integer versionNo;

    private String title;

    private String content;

    private String summary;

    private String editorId;

    private Long createTime;
}
