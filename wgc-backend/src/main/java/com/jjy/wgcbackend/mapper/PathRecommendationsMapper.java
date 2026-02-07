package com.jjy.wgcbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjy.wgcbackend.entitiy.po.PathRecommendations;

/**
 * <p>
 * 用于存储系统通过复杂的供需预测计算后，为每一位空闲司机生成的、以最快接到乘客为目标的个性化巡航路径。 Mapper 接口
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface PathRecommendationsMapper extends BaseMapper<PathRecommendations> {

}
