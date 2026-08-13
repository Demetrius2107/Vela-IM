package com.vela.im.service.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档向量分块（P3）：正文按 chunk 切分后逐块入库，vector 存 JSON 数组
 */
@Data
@TableName("vela_doc_vector")
public class DocVectorEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;

    private Long docId;

    /** 块序号，从 0 递增 */
    private Integer chunkNo;

    private String content;

    /** 向量 JSON 数组（如 [0.1,0.2,...]），维度由 EmbeddingProvider 决定 */
    private String vector;

    private Long createTime;
}
