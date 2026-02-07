package com.jjy.wgcbackend.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjy.wgcbackend.entitiy.po.RideRequests;
import com.jjy.wgcbackend.mapper.RideRequestsMapper;
import com.jjy.wgcbackend.service.IRideRequestsService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 统的需求入口，用于记录乘客发起的每一次乘车请求。它追踪了请求从生成到被满足或被放弃的整个生命周期，是供需匹配的核心数据源。 服务实现类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Service
public class RideRequestsServiceImpl extends ServiceImpl<RideRequestsMapper, RideRequests> implements IRideRequestsService {

}
