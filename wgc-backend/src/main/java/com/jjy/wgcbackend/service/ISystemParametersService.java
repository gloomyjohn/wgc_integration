package com.jjy.wgcbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjy.wgcbackend.entitiy.po.SystemParameters;

/**
 * <p>
 * 用于存储 WGC 模型和模拟仿真中使用的各种全局参数。将这些参数存入数据库，可以使得系统行为的调整无需修改代码，极大地提高了灵活性和可维护性。 服务类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface ISystemParametersService extends IService<SystemParameters> {

}
