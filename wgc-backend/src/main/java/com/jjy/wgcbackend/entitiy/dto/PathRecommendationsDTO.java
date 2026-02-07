package com.jjy.wgcbackend.entitiy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PathRecommendationsDTO {
    // 包括driver id 和 currentLocation
//    private Long driverId;
//    private String currentLocation;
//    private String destination;
    private double[] driverLocation ;
    private double[] passengerLocation;
}
