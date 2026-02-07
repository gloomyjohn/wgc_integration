package com.jjy.wgcbackend.entitiy.po;

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
 * 定义构成交通网络的有向路段（即图的边），并存储每个路段的静态和半静态属性，这些属性是 WGC 算法进行预测和路径规划的关键输入。
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@TableName("road_segments")
@Schema(name = "RoadSegments对象", description = "定义构成交通网络的有向路段（即图的边），并存储每个路段的静态和半静态属性，这些属性是 WGC 算法进行预测和路径规划的关键输入。")
public class RoadSegments implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("segment_id")
    private Long segmentId;

    private Long startNodeId;

    private Long endNodeId;

    /**
     * Time in seconds for an idle driver to traverse the segment (τe).
     */
    @Schema(description = "Time in seconds for an idle driver to traverse the segment (τe).")
    private Integer travelTimeSeconds;

    private Double lengthMeters;

    private Double avgSpeedKmh;

    /**
     * Probability an idle driver at the start node chooses this segment (Q_uv).
     */
    @Schema(description = "Probability an idle driver at the start node chooses this segment (Q_uv).")
    private Double transitionProbability;

    /**
     * The base rate of passenger requests on this segment (λe).
     */
    @Schema(description = "The base rate of passenger requests on this segment (λe).")
    private Double basePassengerArrivalRate;

    private String zoneType;

    /**
     * PostGIS LineString representing the physical path of the road.
     */
    @Schema(description = "PostGIS LineString representing the physical path of the road.")
    private Object geometry;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
