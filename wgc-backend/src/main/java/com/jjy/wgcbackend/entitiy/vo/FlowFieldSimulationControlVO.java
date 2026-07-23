package com.jjy.wgcbackend.entitiy.vo;

import java.io.Serializable;
import java.util.List;

public record FlowFieldSimulationControlVO(
        String simulationId,
        String status,
        Boolean running,
        String message,
        String websocketPath,
        Integer configuredFrames,
        Integer emittedFrames,
        EffectiveConfig effectiveConfig,
        FlowFieldSimulationFrameVO latestFrame,
        List<FlowFieldSimulationFrameVO> previewFrames
) implements Serializable {

    public record EffectiveConfig(
            Integer vehicleCount,
            Double vehicleSpeedKmh,
            Double occupiedSpeedRatio,
            Double tickSeconds,
            Integer totalFrames,
            Integer frameIntervalMs,
            Double attractionStrength,
            Double fieldSpreadMeters,
            Double maxMatchDistanceMeters,
            Double matchedHoldSeconds,
            Integer syntheticPassengerCount,
            Boolean useDatabasePassengers,
            Boolean broadcast,
            Boolean includeVectorField,
            Integer vectorSampleStep,
            Integer previewFrames,
            Long randomSeed,
            Integer requestPassengerCount
    ) implements Serializable {
    }
}
