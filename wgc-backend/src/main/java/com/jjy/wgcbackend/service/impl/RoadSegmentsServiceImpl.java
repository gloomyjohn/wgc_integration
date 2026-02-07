package com.jjy.wgcbackend.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjy.wgcbackend.entitiy.po.RoadSegments;
import com.jjy.wgcbackend.mapper.RoadSegmentsMapper;
import com.jjy.wgcbackend.service.IRoadSegmentsService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 定义构成交通网络的有向路段（即图的边），并存储每个路段的静态和半静态属性，这些属性是 WGC 算法进行预测和路径规划的关键输入。 服务实现类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Service
public class RoadSegmentsServiceImpl extends ServiceImpl<RoadSegmentsMapper, RoadSegments> implements IRoadSegmentsService {

}
