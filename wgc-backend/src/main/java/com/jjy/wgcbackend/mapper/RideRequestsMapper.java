package com.jjy.wgcbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjy.wgcbackend.entitiy.po.RideRequests;


/**
 * <p>
 * 统的需求入口，用于记录乘客发起的每一次乘车请求。它追踪了请求从生成到被满足或被放弃的整个生命周期，是供需匹配的核心数据源。 Mapper 接口
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface RideRequestsMapper extends BaseMapper<RideRequests> {

}
