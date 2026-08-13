package com.vela.im.service.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vela.im.service.knowledge.domain.entity.DocFavoriteEntity;
import org.springframework.stereotype.Repository;

/** 文档收藏 Mapper（MyBatis-Plus） */
@Repository
public interface DocFavoriteMapper extends BaseMapper<DocFavoriteEntity> {
}
