package com.vela.im.service.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vela.im.service.knowledge.domain.entity.DocApprovalEntity;
import org.springframework.stereotype.Repository;

/** 文档审批记录 Mapper（MyBatis-Plus） */
@Repository
public interface DocApprovalMapper extends BaseMapper<DocApprovalEntity> {
}
