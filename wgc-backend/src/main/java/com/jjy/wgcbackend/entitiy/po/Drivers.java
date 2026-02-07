package com.jjy.wgcbackend.entitiy.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * 这张表用于存储系统中每一个运营司机的核心静态信息。在WGC模型中，司机是接受路径推荐和执行运输任务的核心实体。
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@Schema( description = "这张表用于存储系统中每一个运营司机的核心静态信息。在WGC模型中，司机是接受路径推荐和执行运输任务的核心实体。")
public class Drivers implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "driver_id", type = IdType.AUTO)
    private Long driverId;

    private String currentStatus;

    private OffsetDateTime onboardedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;


}
