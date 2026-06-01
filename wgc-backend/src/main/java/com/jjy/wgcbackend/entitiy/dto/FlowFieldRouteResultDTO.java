package com.jjy.wgcbackend.entitiy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowFieldRouteResultDTO {
    private Long startSegmentId;
    private Long destinationSegmentId;
    private double[] snappedDriverStart;
    private double[] snappedPassengerTarget;
    private List<Long> segmentIds;
    private List<double[]> pathPoints;
}
