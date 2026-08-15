package com.vela.im.service.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vela.im.service.knowledge.domain.entity.DocVersionEntity;
import org.springframework.stereotype.Repository;

/** 文档版本 Mapper（MyBatis-Plus） */
@Repository
public interface DocVersionMapper extends BaseMapper<DocVersionEntity> {
}
