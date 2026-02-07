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
 * 用于存储 WGC 模型和模拟仿真中使用的各种全局参数。将这些参数存入数据库，可以使得系统行为的调整无需修改代码，极大地提高了灵活性和可维护性。
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@TableName("system_parameters")
@Schema(name = "SystemParameters对象", description = "用于存储 WGC 模型和模拟仿真中使用的各种全局参数。将这些参数存入数据库，可以使得系统行为的调整无需修改代码，极大地提高了灵活性和可维护性。")
public class SystemParameters implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("parameter_key")
    private String parameterKey;

    private String parameterValue;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
