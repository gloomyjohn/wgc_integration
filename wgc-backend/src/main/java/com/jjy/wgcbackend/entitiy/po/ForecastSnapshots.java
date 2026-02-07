package com.jjy.wgcbackend.entitiy.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 存储了每一次预测任务所产生的详细、密集的时间序列数据。
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@TableName("forecast_snapshots")
@Schema(name = "ForecastSnapshots对象", description = "存储了每一次预测任务所产生的详细、密集的时间序列数据。")
public class ForecastSnapshots implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "snapshot_id", type = IdType.AUTO)
    private Long snapshotId;

    private Long runId;

    private LocalDateTime forecastTimestamp;

    private String locationType;

    private Long locationId;

    private Double predictedPassengerCount;

    private Double predictedIdleDriverCount;
}
