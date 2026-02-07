package com.jjy.wgcbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjy.wgcbackend.entitiy.po.RoadSegments;

/**
 * <p>
 * 定义构成交通网络的有向路段（即图的边），并存储每个路段的静态和半静态属性，这些属性是 WGC 算法进行预测和路径规划的关键输入。 服务类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface IRoadSegmentsService extends IService<RoadSegments> {

}
