package com.vela.im.service.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vela.im.service.knowledge.domain.entity.DocReadEntity;
import org.springframework.stereotype.Repository;

/** 文档阅读记录 Mapper（MyBatis-Plus） */
@Repository
public interface DocReadMapper extends BaseMapper<DocReadEntity> {
}
