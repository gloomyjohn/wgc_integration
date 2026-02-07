package com.jjy.wgcbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjy.wgcbackend.entitiy.po.ForecastSnapshots;
import com.jjy.wgcbackend.mapper.ForecastSnapshotsMapper;
import com.jjy.wgcbackend.service.IForecastSnapshotsService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 存储了每一次预测任务所产生的详细、密集的时间序列数据。 服务实现类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Service
public class ForecastSnapshotsServiceImpl extends ServiceImpl<ForecastSnapshotsMapper, ForecastSnapshots> implements IForecastSnapshotsService {

}
