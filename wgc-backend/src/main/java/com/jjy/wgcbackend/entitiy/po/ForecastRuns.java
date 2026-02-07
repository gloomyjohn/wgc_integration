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
 * 用于记录每一次预测任务的宏观信息。
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@TableName("forecast_runs")
@Schema(  name = "ForecastRuns对象", description = "用于记录每一次预测任务的宏观信息。")
public class ForecastRuns implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "run_id", type = IdType.AUTO)
    private Long runId;

    private LocalDateTime runStartTime;

    private Integer forecastHorizonSeconds;

    private Integer timeStepSeconds;

    private String status;

    private LocalDateTime createdAt;
}
