package com.jjy.wgcbackend.entitiy.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * 用于追踪每一位司机的瞬时状态和精确位置。论文中的数学模型（如 De(t), Pu(t)）描述了司机在路网中的宏观分布，而这张表则是这些宏观变量在数据库中的微观实现。
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@TableName("driver_status_locations")
@Schema( description = "用于追踪每一位司机的瞬时状态和精确位置。论文中的数学模型（如 De(t), Pu(t)）描述了司机在路网中的宏观分布，而这张表则是这些宏观变量在数据库中的微观实现。")
public class DriverStatusLocations implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("driver_id")
    private Long driverId;

    private String status;

    private String locationType;

    private Long locationNodeId;

    private Long locationSegmentId;

    private Double progressOnSegment;

    private OffsetDateTime lastUpdatedAt;

    private double latitude;
    private double longitude;

    private double target_latitude;
    private double target_longitude;
}
