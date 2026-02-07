package com.jjy.wgcbackend.entitiy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverDto {
    private Long driverId;

    private String currentStatus;

    private OffsetDateTime onboardedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
