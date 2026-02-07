package com.jjy.wgcbackend.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjy.wgcbackend.entitiy.po.Trips;
import com.jjy.wgcbackend.mapper.TripsMapper;
import com.jjy.wgcbackend.service.ITripsService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 核心的业务流水表，用于记录一次完整的出行服务。它在司机与乘客请求成功匹配后被创建，详细记载了从乘客被接取到送达目的地的全过程信息。 服务实现类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Service
public class TripsServiceImpl extends ServiceImpl<TripsMapper, Trips> implements ITripsService {

}
