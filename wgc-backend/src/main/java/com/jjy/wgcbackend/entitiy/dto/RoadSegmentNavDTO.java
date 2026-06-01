package com.jjy.wgcbackend.entitiy.dto;

import lombok.Data;

@Data
public class RoadSegmentNavDTO {
    private Long segmentId;
    private Long startNodeId;
    private Long endNodeId;
    private Double lengthMeters;
    private Double startLng;
    private Double startLat;
    private Double endLng;
    private Double endLat;
}
