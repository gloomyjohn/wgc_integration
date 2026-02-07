package com.jjy.wgcbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjy.wgcbackend.entitiy.po.SystemParameters;


/**
 * <p>
 * 用于存储 WGC 模型和模拟仿真中使用的各种全局参数。将这些参数存入数据库，可以使得系统行为的调整无需修改代码，极大地提高了灵活性和可维护性。 Mapper 接口
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface SystemParametersMapper extends BaseMapper<SystemParameters> {

}
