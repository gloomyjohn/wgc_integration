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
 * 用于存储系统通过复杂的供需预测计算后，为每一位空闲司机生成的、以最快接到乘客为目标的个性化巡航路径。
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@TableName("path_recommendations")
@Schema(name = "PathRecommendations对象", description = "用于存储系统通过复杂的供需预测计算后，为每一位空闲司机生成的、以最快接到乘客为目标的个性化巡航路径。")
public class PathRecommendations implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "recommendation_id", type = IdType.AUTO)
    private Long recommendationId;

    private Long driverId;

    private Long startNodeId;

    private Object recommendedPath;

    private Double expectedAllocationTimeSeconds;

    private String status;

    private LocalDateTime recommendationTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
