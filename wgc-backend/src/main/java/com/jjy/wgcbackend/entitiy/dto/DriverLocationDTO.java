package com.jjy.wgcbackend.entitiy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DriverLocationDTO implements Serializable {
    private Long driverId;
    private double latitude;
    private double longitude;
}
