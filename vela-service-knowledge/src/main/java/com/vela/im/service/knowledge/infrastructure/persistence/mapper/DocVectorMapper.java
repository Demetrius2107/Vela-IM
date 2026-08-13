package com.vela.im.service.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vela.im.service.knowledge.domain.entity.DocVectorEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface DocVectorMapper extends BaseMapper<DocVectorEntity> {
}
