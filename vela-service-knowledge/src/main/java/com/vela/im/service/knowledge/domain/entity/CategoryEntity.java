package com.vela.im.service.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库分类（目录树节点）
 */
@Data
@TableName("vela_category")
public class CategoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;

    /** 父分类ID，0 表示根节点 */
    private Long parentId;

    private String name;

    /** 同级排序，越小越靠前 */
    private Integer sort;

    private Long createTime;

    private Long updateTime;

    /** 树形查询用子节点，非表字段 */
    @TableField(exist = false)
    private List<CategoryEntity> children = new ArrayList<>();
}
