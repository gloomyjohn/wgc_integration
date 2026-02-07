package com.jjy.wgcbackend.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjy.wgcbackend.entitiy.po.ForecastRuns;
import com.jjy.wgcbackend.mapper.ForecastRunsMapper;
import com.jjy.wgcbackend.service.IForecastRunsService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用于记录每一次预测任务的宏观信息。 服务实现类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Service
public class ForecastRunsServiceImpl extends ServiceImpl<ForecastRunsMapper, ForecastRuns> implements IForecastRunsService {

}
