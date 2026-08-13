package com.vela.im.service.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vela.im.service.knowledge.domain.entity.CategoryEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
