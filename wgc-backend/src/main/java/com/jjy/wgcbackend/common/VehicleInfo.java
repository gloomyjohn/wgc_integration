package com.jjy.wgcbackend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "车辆信息")
public class VehicleInfo {
    private String vehicleId;
    private String vehicleType;
}
