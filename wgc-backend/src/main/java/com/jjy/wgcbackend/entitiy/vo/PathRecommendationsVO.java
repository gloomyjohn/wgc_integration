package com.jjy.wgcbackend.entitiy.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PathRecommendationsVO implements Serializable {
//    private Long recommendationId;
//
//    private Long driverId;
//
//    private Long startNodeId;
//
//    private Object recommendedPath;
//
//    private Double expectedAllocationTimeSeconds;
//
//    private String status;
//
//    private LocalDateTime recommendationTime;
//
//    private LocalDateTime createdAt;
//
//    private LocalDateTime updatedAt;
    private List<double []> bluePath;
    private double[] blueSnappedStart; // blue driver snapped start
    private List<List<double []>> redPaths;
    private List<double[]> redSnappedStarts;
}
