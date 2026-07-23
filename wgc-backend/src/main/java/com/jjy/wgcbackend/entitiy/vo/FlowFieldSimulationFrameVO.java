package com.jjy.wgcbackend.entitiy.vo;

import java.io.Serializable;
import java.util.List;

public record FlowFieldSimulationFrameVO(
        String simulationId,
        Integer frameIndex,
        Long generatedAt,
        Double simTimeSeconds,
        List<VehicleSnapshot> vehicles,
        List<PassengerSnapshot> passengers,
        List<SegmentVectorSnapshot> vectors,
        SimulationStats stats
) implements Serializable {

    public record VehicleSnapshot(
            Long vehicleId,
            Long segmentId,
            Double lng,
            Double lat,
            Double headingX,
            Double headingY,
            Double progressRatio,
            Double speedMetersPerSecond,
            String status,
            Long targetPassengerId
    ) implements Serializable {
    }

    public record PassengerSnapshot(
            Long passengerId,
            Double lng,
            Double lat,
            Double ageSeconds,
            String source
    ) implements Serializable {
    }

    public record SegmentVectorSnapshot(
            Long segmentId,
            Double startLng,
            Double startLat,
            Double endLng,
            Double endLat,
            Double vectorX,
            Double vectorY,
            Double strength
    ) implements Serializable {
    }

    public record SimulationStats(
            Integer idleVehicleCount,
            Integer matchedVehicleCount,
            Integer activePassengerCount,
            Double averageVehicleSpeedMetersPerSecond,
            Double averageVectorStrength
    ) implements Serializable {
    }
}
