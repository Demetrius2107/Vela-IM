package com.vela.im.service.common.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vela.im.service.common.entity.ImGroupMessageHistoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface ImGroupMessageHistoryMapper extends BaseMapper<ImGroupMessageHistoryEntity> {


}
