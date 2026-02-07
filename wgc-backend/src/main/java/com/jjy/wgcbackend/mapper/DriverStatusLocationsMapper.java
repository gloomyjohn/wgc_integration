package com.jjy.wgcbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjy.wgcbackend.entitiy.po.DriverStatusLocations;


/**
 * <p>
 * 用于追踪每一位司机的瞬时状态和精确位置。论文中的数学模型（如 De(t), Pu(t)）描述了司机在路网中的宏观分布，而这张表则是这些宏观变量在数据库中的微观实现。 Mapper 接口
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface DriverStatusLocationsMapper extends BaseMapper<DriverStatusLocations> {

}
