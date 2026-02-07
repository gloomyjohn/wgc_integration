package com.jjy.wgcbackend.entitiy.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 核心的业务流水表，用于记录一次完整的出行服务。它在司机与乘客请求成功匹配后被创建，详细记载了从乘客被接取到送达目的地的全过程信息。
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@Schema(name = "Trips对象", description = "核心的业务流水表，用于记录一次完整的出行服务。它在司机与乘客请求成功匹配后被创建，详细记载了从乘客被接取到送达目的地的全过程信息。")
public class Trips implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "trip_id", type = IdType.AUTO)
    private Long tripId;

    private Long requestId;

    private Long driverId;

    private String status;

    private Long originSegmentId;

    private Long destinationNodeId;

    private LocalDateTime startTime;

    private LocalDateTime estimatedEndTime;

    private LocalDateTime actualEndTime;

    private Object pathTaken;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
