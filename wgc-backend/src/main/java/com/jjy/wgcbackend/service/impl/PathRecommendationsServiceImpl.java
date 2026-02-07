package com.jjy.wgcbackend.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjy.wgcbackend.entitiy.po.PathRecommendations;
import com.jjy.wgcbackend.entitiy.vo.PathRecommendationsVO;
import com.jjy.wgcbackend.mapper.PathRecommendationsMapper;
import com.jjy.wgcbackend.service.IPathRecommendationsService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 用于存储系统通过复杂的供需预测计算后，为每一位空闲司机生成的、以最快接到乘客为目标的个性化巡航路径。 服务实现类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Service

public class PathRecommendationsServiceImpl extends ServiceImpl<PathRecommendationsMapper, PathRecommendations> implements IPathRecommendationsService {

//
//    @Override
//    public List getRecommendedPath(Long driverId, String currentLocation, String destination) {
//        // 接受包括司机id、当前位置的坐标、目的地的坐标，调用python算法获取推荐路径，返回recid、期待时间、path（坐标节点数组）
//        // 暂时先用静态数据data替代python算法
//        List<Object> data = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
//        return data;
//    }

    @Override
    @Cacheable(
            value = "recommended_paths",
            key = "#driverLocation[0] + ',' + #driverLocation[1] + '-' + #passengerLocation[0] + ',' + #passengerLocation[1]",
            unless = "#result == null"
    )
    public PathRecommendationsVO getRecommendedPath(double[] driverLocation, double[] passengerLocation) {
//        PathRecommendationsVO pathRecommendationsVO = new PathRecommendationsVO();
//        List<double []> bluePath = Arrays.asList(new double[][]{{1,2},{3,4},{5,6},{7,8},{9,10}});
//        pathRecommendationsVO.setBluePath(bluePath);
//        pathRecommendationsVO.setBlueSnappedStart(new double[]{1,2});
//        List<List<double []> > redPaths = Arrays.asList(new List[]{Collections.singletonList(new double[][]{{1, 2}, {3, 4}, {5, 6}, {7, 8}, {9, 10}})});
//        pathRecommendationsVO.setRedPaths(redPaths);
//        pathRecommendationsVO.setRedSnappedStarts(List.of(new double[]{1, 2}));
//        return pathRecommendationsVO;
        System.out.println("⚡️ 缓存未命中，正在计算路径...");
        PathRecommendationsVO vo = new PathRecommendationsVO();

        // 1. 设置蓝色路径
        List<double[]> bluePath = List.of(
                new double[]{-122.402, 37.79},
                new double[]{-122.401, 37.791}
        );
        vo.setBluePath(bluePath);
        vo.setBlueSnappedStart(bluePath.get(0));

        // 2. 设置红色路径（多个竞争对手）
        List<List<double[]>> redPaths = new ArrayList<>();
        redPaths.add(List.of(new double[]{-122.41, 37.8}, new double[]{-122.40, 37.79}));

        vo.setRedPaths(redPaths);
        vo.setRedSnappedStarts(List.of(new double[]{-122.41, 37.8}));

        return vo;

    }
}
