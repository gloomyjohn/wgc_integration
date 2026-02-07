package com.jjy.wgcbackend.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.jjy.wgcbackend.entitiy.dto.DriverLocationDTO;
import com.jjy.wgcbackend.entitiy.po.Drivers;

/**
 * <p>
 * 这张表用于存储系统中每一个运营司机的核心静态信息。在WGC模型中，司机是接受路径推荐和执行运输任务的核心实体。 服务类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface IDriversService extends IService<Drivers> {

    boolean updateLocation(DriverLocationDTO driverLocationDTO);
}
