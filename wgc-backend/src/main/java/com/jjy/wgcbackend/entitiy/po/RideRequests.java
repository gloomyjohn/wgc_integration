package com.jjy.wgcbackend.entitiy.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjy.wgcbackend.handler.RideStatusTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 统的需求入口，用于记录乘客发起的每一次乘车请求。它追踪了请求从生成到被满足或被放弃的整个生命周期，是供需匹配的核心数据源。
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@TableName(value = "ride_requests", autoResultMap = true)
@Schema(name = "RideRequests对象", description = "统的需求入口，用于记录乘客发起的每一次乘车请求。它追踪了请求从生成到被满足或被放弃的整个生命周期，是供需匹配的核心数据源。")
public class RideRequests implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "request_id", type = IdType.AUTO)
    private Long requestId;

    private LocalDateTime requestTime;

    private Long requestSegmentId;

    private Long destinationNodeId;

    @TableField(typeHandler = RideStatusTypeHandler.class)
    private String status;

    private LocalDateTime abandonTime;

    private Long matchedDriverId;

    private LocalDateTime matchedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
