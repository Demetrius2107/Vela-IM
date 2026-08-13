package com.vela.im.service.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vela.im.service.knowledge.domain.entity.DocPermissionEntity;
import org.springframework.stereotype.Repository;

/** 文档级权限 Mapper（MyBatis-Plus） */
@Repository
public interface DocPermissionMapper extends BaseMapper<DocPermissionEntity> {
}
