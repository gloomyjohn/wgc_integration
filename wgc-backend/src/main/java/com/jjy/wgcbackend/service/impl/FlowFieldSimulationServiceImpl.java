package com.jjy.wgcbackend.service.impl;

import com.jjy.wgcbackend.entitiy.dto.FlowFieldSimulationRequestDTO;
import com.jjy.wgcbackend.entitiy.dto.FlowFieldRoadSegmentDTO;
import com.jjy.wgcbackend.entitiy.dto.NodeDTO;
import com.jjy.wgcbackend.entitiy.vo.FlowFieldSimulationControlVO;
import com.jjy.wgcbackend.entitiy.vo.FlowFieldSimulationFrameVO;
import com.jjy.wgcbackend.handler.SimulationWebSocketHandler;
import com.jjy.wgcbackend.mapper.FlowFieldRoadGraphMapper;
import com.jjy.wgcbackend.mapper.RideRequestsMapper;
import com.jjy.wgcbackend.service.IFlowFieldSimulationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Lazy
@Service
public class FlowFieldSimulationServiceImpl implements IFlowFieldSimulationService {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldSimulationServiceImpl.class);

    private static final String WEBSOCKET_PATH = "/ws/simulation";
    private static final int DEFAULT_VEHICLE_COUNT = 300;
    private static final double DEFAULT_VEHICLE_SPEED_KMH = 24.0;
    private static final double DEFAULT_OCCUPIED_SPEED_RATIO = 0.55;
    private static final double DEFAULT_TICK_SECONDS = 1.0;
    private static final int DEFAULT_TOTAL_FRAMES = 300;
    private static final int DEFAULT_FRAME_INTERVAL_MS = 120;
    private static final double DEFAULT_ATTRACTION_STRENGTH = 1.5;
    private static final double DEFAULT_FIELD_SPREAD_METERS = 100.0;
    private static final double DEFAULT_MAX_MATCH_DISTANCE_METERS = 60.0;
    private static final double DEFAULT_MATCHED_HOLD_SECONDS = 45.0;
    private static final int DEFAULT_SYNTHETIC_PASSENGER_COUNT = 18;
    private static final int DEFAULT_VECTOR_SAMPLE_STEP = 8;
    private static final int DEFAULT_PREVIEW_FRAMES = 20;
    private static final int SIMULATION_GRAPH_SAMPLE_MODULO = 20;
    private static final int SIMULATION_GRAPH_MAX_SEGMENTS = 80_000;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    @Autowired
    private FlowFieldRoadGraphMapper flowFieldRoadGraphMapper;
    @Autowired
    private RideRequestsMapper rideRequestsMapper;
    @Autowired
    private SimulationWebSocketHandler webSocketHandler;

    private final ExecutorService simulationExecutor = Executors.newSingleThreadExecutor(new SimulationThreadFactory());
    private final AtomicReference<SimulationRuntime> currentRuntime = new AtomicReference<>();
    private final Object graphInitializationMonitor = new Object();

    private List<GraphSegment> cachedSegments = Collections.emptyList();
    private Map<Long, List<GraphSegment>> adjacencyByNodeId = Collections.emptyMap();
    private Set<Long> survivableSegmentIds = Collections.emptySet();
    private List<GraphSegment> spawnableSegments = Collections.emptyList();
    private volatile boolean graphInitialized;

    @PreDestroy
    public void shutdown() {
        SimulationRuntime runtime = currentRuntime.get();
        if (runtime != null) {
            runtime.stop("STOPPED", "Application shutdown.");
        }
        simulationExecutor.shutdownNow();
    }

    @Override
    public FlowFieldSimulationControlVO startSimulation(FlowFieldSimulationRequestDTO request) {
        SimulationConfig config = normalize(request);
        ensureGraphInitialized();
        InternalSimulationState state = initializeState(config);
        String simulationId = "ffs-" + System.currentTimeMillis();
        FlowFieldSimulationFrameVO initialFrame = buildFrame(simulationId, 0, 0.0, state, config);

        SimulationRuntime previous = currentRuntime.getAndSet(null);
        if (previous != null && previous.running) {
            previous.stop("STOPPED", "Replaced by a new flow field simulation.");
        }

        SimulationRuntime runtime = new SimulationRuntime(simulationId, config, state, initialFrame);
        currentRuntime.set(runtime);

        if (config.broadcast) {
            broadcastLifecycleEvent("FLOW_FIELD_SIMULATION_STARTED", runtime, initialFrame);
            broadcastFrame(runtime, initialFrame);
        }

        Future<?> future = simulationExecutor.submit(() -> runSimulation(runtime));
        runtime.future = future;

        return buildControl(runtime, "RUNNING", "Flow field simulation started.", null);
    }

    @Override
    public FlowFieldSimulationControlVO previewSimulation(FlowFieldSimulationRequestDTO request) {
        SimulationConfig config = normalize(request);
        ensureGraphInitialized();
        int previewLimit = Math.max(1, Math.min(config.previewFrames, config.totalFrames));
        InternalSimulationState state = initializeState(config);
        String simulationId = "preview-" + System.currentTimeMillis();
        List<FlowFieldSimulationFrameVO> frames = new ArrayList<>();

        FlowFieldSimulationFrameVO initialFrame = buildFrame(simulationId, 0, 0.0, state, config);
        frames.add(initialFrame);

        Random random = new Random(config.randomSeed);
        for (int frameIndex = 1; frameIndex < previewLimit; frameIndex++) {
            advanceState(state, config, random);
            frames.add(buildFrame(simulationId, frameIndex, frameIndex * config.tickSeconds, state, config));
        }

        SimulationRuntime previewRuntime = new SimulationRuntime(simulationId, config, state, frames.get(frames.size() - 1));
        previewRuntime.running = false;
        previewRuntime.status = "PREVIEW";
        previewRuntime.message = "Preview frames generated.";
        previewRuntime.emittedFrames.set(frames.size());

        return buildControl(previewRuntime, "PREVIEW", "Preview frames generated.", frames);
    }

    @Override
    public FlowFieldSimulationControlVO stopSimulation() {
        SimulationRuntime runtime = currentRuntime.get();
        if (runtime == null) {
            return idleControl();
        }

        runtime.stop("STOPPED", "Flow field simulation stopped by request.");
        if (runtime.config.broadcast) {
            broadcastLifecycleEvent("FLOW_FIELD_SIMULATION_STOPPED", runtime, runtime.latestFrame);
        }
        return buildControl(runtime, runtime.status, runtime.message, null);
    }

    private void ensureGraphInitialized() {
        if (graphInitialized) {
            return;
        }

        synchronized (graphInitializationMonitor) {
            if (graphInitialized) {
                return;
            }

            List<FlowFieldRoadSegmentDTO> segments = flowFieldRoadGraphMapper.findSimulationSegments(
                    SIMULATION_GRAPH_SAMPLE_MODULO,
                    SIMULATION_GRAPH_MAX_SEGMENTS
            );
            cachedSegments = toGraphSegments(segments);
            adjacencyByNodeId = buildAdjacency(cachedSegments);
            survivableSegmentIds = Collections.emptySet();
            spawnableSegments = cachedSegments;
            graphInitialized = true;

            log.info(
                    "FlowFieldSimulationService graph initialized lazily. sampleModulo={}, maxSegments={}, loadedSegments={}",
                    SIMULATION_GRAPH_SAMPLE_MODULO,
                    SIMULATION_GRAPH_MAX_SEGMENTS,
                    cachedSegments.size()
            );
        }
    }

    @Override
    public FlowFieldSimulationControlVO getStatus() {
        SimulationRuntime runtime = currentRuntime.get();
        if (runtime == null) {
            return idleControl();
        }
        return buildControl(runtime, runtime.status, runtime.message, null);
    }

    private void runSimulation(SimulationRuntime runtime) {
        Random random = new Random(runtime.config.randomSeed);
        try {
            for (int frameIndex = 1; frameIndex < runtime.config.totalFrames && runtime.active.get(); frameIndex++) {
                advanceState(runtime.state, runtime.config, random);
                FlowFieldSimulationFrameVO frame = buildFrame(
                        runtime.simulationId,
                        frameIndex,
                        frameIndex * runtime.config.tickSeconds,
                        runtime.state,
                        runtime.config
                );
                runtime.latestFrame = frame;
                runtime.emittedFrames.set(frameIndex + 1);

                if (runtime.config.broadcast) {
                    broadcastFrame(runtime, frame);
                }

                if (frameIndex < runtime.config.totalFrames - 1) {
                    Thread.sleep(runtime.config.frameIntervalMs);
                }
            }

            if (runtime.active.get()) {
                runtime.running = false;
                runtime.status = "COMPLETED";
                runtime.message = "Flow field simulation completed.";
                if (runtime.config.broadcast) {
                    broadcastLifecycleEvent("FLOW_FIELD_SIMULATION_COMPLETED", runtime, runtime.latestFrame);
                }
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            if (!"STOPPED".equals(runtime.status)) {
                runtime.running = false;
                runtime.status = "STOPPED";
                runtime.message = "Flow field simulation interrupted.";
                if (runtime.config.broadcast) {
                    broadcastLifecycleEvent("FLOW_FIELD_SIMULATION_STOPPED", runtime, runtime.latestFrame);
                }
            }
        } catch (Exception exception) {
            runtime.running = false;
            runtime.status = "FAILED";
            runtime.message = "Flow field simulation failed: " + exception.getMessage();
            log.error("Flow field simulation failed. simulationId={}", runtime.simulationId, exception);
            if (runtime.config.broadcast) {
                broadcastLifecycleEvent("FLOW_FIELD_SIMULATION_FAILED", runtime, runtime.latestFrame);
            }
        }
    }

    private SimulationConfig normalize(FlowFieldSimulationRequestDTO request) {
        FlowFieldSimulationRequestDTO payload = request == null ? new FlowFieldSimulationRequestDTO() : request;
        SimulationConfig config = new SimulationConfig();
        config.vehicleCount = clampInt(payload.getVehicleCount(), 1, 10_000, DEFAULT_VEHICLE_COUNT);
        config.vehicleSpeedKmh = clampDouble(payload.getVehicleSpeedKmh(), 1.0, 180.0, DEFAULT_VEHICLE_SPEED_KMH);
        config.occupiedSpeedRatio = clampDouble(payload.getOccupiedSpeedRatio(), 0.1, 1.5, DEFAULT_OCCUPIED_SPEED_RATIO);
        config.tickSeconds = clampDouble(payload.getTickSeconds(), 0.05, 10.0, DEFAULT_TICK_SECONDS);
        config.totalFrames = clampInt(payload.getTotalFrames(), 1, 20_000, DEFAULT_TOTAL_FRAMES);
        config.frameIntervalMs = clampInt(payload.getFrameIntervalMs(), 0, 60_000, DEFAULT_FRAME_INTERVAL_MS);
        config.attractionStrength = clampDouble(payload.getAttractionStrength(), 0.01, 5.0, DEFAULT_ATTRACTION_STRENGTH);
        config.fieldSpreadMeters = clampDouble(payload.getFieldSpreadMeters(), 1.0, 5_000.0, DEFAULT_FIELD_SPREAD_METERS);
        config.maxMatchDistanceMeters = clampDouble(payload.getMaxMatchDistanceMeters(), 1.0, 2_000.0, DEFAULT_MAX_MATCH_DISTANCE_METERS);
        config.matchedHoldSeconds = clampDouble(payload.getMatchedHoldSeconds(), 0.0, 3_600.0, DEFAULT_MATCHED_HOLD_SECONDS);
        config.syntheticPassengerCount = clampInt(payload.getSyntheticPassengerCount(), 0, 5_000, DEFAULT_SYNTHETIC_PASSENGER_COUNT);
        config.useDatabasePassengers = payload.getUseDatabasePassengers() == null || payload.getUseDatabasePassengers();
        config.broadcast = payload.getBroadcast() == null || payload.getBroadcast();
        config.includeVectorField = payload.getIncludeVectorField() == null || payload.getIncludeVectorField();
        config.vectorSampleStep = clampInt(payload.getVectorSampleStep(), 1, 100, DEFAULT_VECTOR_SAMPLE_STEP);
        config.previewFrames = clampInt(payload.getPreviewFrames(), 1, config.totalFrames, Math.min(DEFAULT_PREVIEW_FRAMES, config.totalFrames));
        config.randomSeed = payload.getRandomSeed() == null ? System.currentTimeMillis() : payload.getRandomSeed();
        config.requestPassengerLocations = normalizePassengerLocations(payload.getPassengerLocations());
        return config;
    }

    private List<double[]> normalizePassengerLocations(List<double[]> passengerLocations) {
        if (passengerLocations == null || passengerLocations.isEmpty()) {
            return Collections.emptyList();
        }

        List<double[]> normalized = new ArrayList<>();
        for (double[] location : passengerLocations) {
            if (location == null || location.length < 2) {
                continue;
            }
            normalized.add(new double[]{location[0], location[1]});
        }
        return normalized;
    }

    private InternalSimulationState initializeState(SimulationConfig config) {
        Random random = new Random(config.randomSeed);
        InternalSimulationState state = new InternalSimulationState();
        state.passengers = buildPassengers(config, random);
        state.nextPassengerId = state.passengers.stream()
                .map(passenger -> passenger.passengerId)
                .max(Long::compareTo)
                .orElse(0L) + 1L;
        state.vehicles = createVehicles(config, random);
        return state;
    }

    private List<InternalPassenger> buildPassengers(SimulationConfig config, Random random) {
        List<InternalPassenger> passengers = new ArrayList<>();
        long nextPassengerId = 1L;

        if (config.useDatabasePassengers) {
            List<NodeDTO> databasePassengers = rideRequestsMapper.getActivePassengerLocations();
            for (NodeDTO node : databasePassengers) {
                if (node.getLng() == null || node.getLat() == null) {
                    continue;
                }
                passengers.add(new InternalPassenger(nextPassengerId++, node.getLng(), node.getLat(), 0.0, "database"));
            }
        }

        for (double[] location : config.requestPassengerLocations) {
            passengers.add(new InternalPassenger(nextPassengerId++, location[0], location[1], 0.0, "request"));
        }

        for (int i = 0; i < config.syntheticPassengerCount; i++) {
            InternalPassenger passenger = createSyntheticPassenger(nextPassengerId, random);
            if (passenger == null) {
                break;
            }
            passengers.add(passenger);
            nextPassengerId += 1;
        }

        return passengers;
    }

    private List<InternalVehicle> createVehicles(SimulationConfig config, Random random) {
        if (spawnableSegments.isEmpty()) {
            return Collections.emptyList();
        }

        List<InternalVehicle> vehicles = new ArrayList<>(config.vehicleCount);
        for (int i = 0; i < config.vehicleCount; i++) {
            GraphSegment segment = spawnableSegments.get(random.nextInt(spawnableSegments.size()));
            double progressRatio = 0.05 + 0.9 * random.nextDouble();
            double progressMeters = clampDouble(progressRatio * segment.lengthMeters, 0.0, segment.lengthMeters, 0.0);
            Position position = interpolate(segment, progressMeters);
            InternalVehicle vehicle = new InternalVehicle();
            vehicle.vehicleId = (long) (i + 1);
            vehicle.segment = segment;
            vehicle.progressMeters = progressMeters;
            vehicle.lng = position.lng;
            vehicle.lat = position.lat;
            LocalVector direction = normalizedDirection(segment);
            vehicle.headingX = direction.x;
            vehicle.headingY = direction.y;
            vehicle.status = "idle";
            vehicle.matchedTimerSeconds = 0.0;
            vehicle.currentSpeedMps = 0.0;
            vehicles.add(vehicle);
        }
        return vehicles;
    }

    private void advanceState(InternalSimulationState state, SimulationConfig config, Random random) {
        for (InternalPassenger passenger : state.passengers) {
            passenger.ageSeconds += config.tickSeconds;
        }

        for (InternalVehicle vehicle : state.vehicles) {
            if ("matched".equals(vehicle.status)) {
                vehicle.matchedTimerSeconds -= config.tickSeconds;
                if (vehicle.matchedTimerSeconds <= 0.0) {
                    vehicle.status = "idle";
                    vehicle.matchedTimerSeconds = 0.0;
                    vehicle.targetPassengerId = null;
                }
            }
        }

        for (InternalVehicle vehicle : state.vehicles) {
            moveVehicle(vehicle, state, config, random);
        }

        applyMatches(state, config, random);
        replenishSyntheticPassengers(state, config, random);
    }

    private void moveVehicle(
            InternalVehicle vehicle,
            InternalSimulationState state,
            SimulationConfig config,
            Random random
    ) {
        if (vehicle.segment == null) {
            respawnVehicle(vehicle, random);
            if (vehicle.segment == null) {
                return;
            }
        }

        if (!survivableSegmentIds.isEmpty() && !survivableSegmentIds.contains(vehicle.segment.segmentId)) {
            respawnVehicle(vehicle, random);
            if (vehicle.segment == null) {
                return;
            }
        }

        double baseStepMeters = kmhToMetersPerSecond(config.vehicleSpeedKmh) * config.tickSeconds;
        double stepMeters = "matched".equals(vehicle.status)
                ? baseStepMeters * config.occupiedSpeedRatio
                : baseStepMeters;

        if ("idle".equals(vehicle.status) && !state.passengers.isEmpty()) {
            LocalVector field = nearestPassengerField(
                    vehicle.lng,
                    vehicle.lat,
                    state.passengers,
                    config.attractionStrength,
                    config.fieldSpreadMeters
            );
            double boost = clampDouble(field.magnitude(), 0.18, 2.6, 1.0);
            stepMeters *= boost;
        }

        vehicle.currentSpeedMps = config.tickSeconds > 0 ? stepMeters / config.tickSeconds : 0.0;

        double remainingMeters = stepMeters;
        while (remainingMeters > 1e-9) {
            double distanceLeftOnSegment = Math.max(vehicle.segment.lengthMeters - vehicle.progressMeters, 1e-9);
            if (remainingMeters <= distanceLeftOnSegment) {
                vehicle.progressMeters += remainingMeters;
                syncVehiclePosition(vehicle);
                return;
            }

            remainingMeters -= distanceLeftOnSegment;
            GraphSegment nextSegment = chooseNextSegment(vehicle, state.passengers, config, random);
            if (nextSegment == null) {
                respawnVehicle(vehicle, random);
                return;
            }

            vehicle.segment = nextSegment;
            vehicle.progressMeters = Math.min(Math.max(nextSegment.lengthMeters * 0.02, 0.5), nextSegment.lengthMeters);
            syncVehiclePosition(vehicle);
        }
    }

    private GraphSegment chooseNextSegment(
            InternalVehicle vehicle,
            List<InternalPassenger> passengers,
            SimulationConfig config,
            Random random
    ) {
        List<GraphSegment> candidates = adjacencyByNodeId.getOrDefault(vehicle.segment.endNodeId, Collections.emptyList()).stream()
                .filter(candidate -> candidate.segmentId != null)
                .filter(candidate -> !Objects.equals(candidate.segmentId, vehicle.segment.segmentId))
                .filter(candidate -> survivableSegmentIds.isEmpty() || survivableSegmentIds.contains(candidate.segmentId))
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        if ("matched".equals(vehicle.status) || passengers.isEmpty()) {
            return weightedContinuationChoice(vehicle.segment, candidates, random);
        }

        LocalVector field = nearestPassengerField(
                vehicle.segment.endLng,
                vehicle.segment.endLat,
                passengers,
                config.attractionStrength,
                config.fieldSpreadMeters
        );

        if (field.magnitude() < 1e-9) {
            return weightedContinuationChoice(vehicle.segment, candidates, random);
        }

        LocalVector currentDirection = normalizedDirection(vehicle.segment);
        GraphSegment bestCandidate = candidates.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;

        for (GraphSegment candidate : candidates) {
            LocalVector candidateDirection = normalizedDirection(candidate);
            double forwardScore = candidateDirection.dot(field);
            double continuationScore = 0.08 * currentDirection.dot(candidateDirection);
            double score = forwardScore + continuationScore;
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }

        return bestCandidate;
    }

    private GraphSegment weightedContinuationChoice(GraphSegment currentSegment, List<GraphSegment> candidates, Random random) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        LocalVector currentDirection = normalizedDirection(currentSegment);
        double totalWeight = 0.0;
        double[] weights = new double[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            LocalVector candidateDirection = normalizedDirection(candidates.get(i));
            double cos = currentDirection.dot(candidateDirection);
            double uTurnPenalty = cos < -0.85 ? 1e-4 : 1.0;
            weights[i] = Math.exp(0.4 * (cos + 1.0)) * uTurnPenalty;
            totalWeight += weights[i];
        }

        if (totalWeight <= 0.0) {
            return candidates.get(candidates.size() - 1);
        }

        double roll = random.nextDouble() * totalWeight;
        for (int i = 0; i < candidates.size(); i++) {
            roll -= weights[i];
            if (roll <= 0.0) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private void applyMatches(InternalSimulationState state, SimulationConfig config, Random random) {
        List<InternalVehicle> idleVehicles = state.vehicles.stream()
                .filter(vehicle -> "idle".equals(vehicle.status))
                .toList();
        if (idleVehicles.isEmpty() || state.passengers.isEmpty()) {
            return;
        }

        List<InternalPassenger> shuffledPassengers = new ArrayList<>(state.passengers);
        Collections.shuffle(shuffledPassengers, random);
        Set<Long> matchedPassengerIds = new HashSet<>();
        Set<Long> matchedVehicleIds = new HashSet<>();

        for (InternalPassenger passenger : shuffledPassengers) {
            InternalVehicle bestVehicle = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (InternalVehicle vehicle : idleVehicles) {
                if (matchedVehicleIds.contains(vehicle.vehicleId)) {
                    continue;
                }
                double distanceMeters = distanceMeters(vehicle.lng, vehicle.lat, passenger.lng, passenger.lat);
                if (distanceMeters > config.maxMatchDistanceMeters) {
                    continue;
                }
                double score = 1.0 / (distanceMeters + 1e-6);
                if (score > bestScore) {
                    bestScore = score;
                    bestVehicle = vehicle;
                }
            }

            if (bestVehicle != null) {
                bestVehicle.status = "matched";
                bestVehicle.matchedTimerSeconds = config.matchedHoldSeconds;
                bestVehicle.targetPassengerId = passenger.passengerId;
                matchedVehicleIds.add(bestVehicle.vehicleId);
                matchedPassengerIds.add(passenger.passengerId);
            }
        }

        if (!matchedPassengerIds.isEmpty()) {
            state.passengers.removeIf(passenger -> matchedPassengerIds.contains(passenger.passengerId));
        }
    }

    private void replenishSyntheticPassengers(InternalSimulationState state, SimulationConfig config, Random random) {
        if (config.syntheticPassengerCount <= 0) {
            return;
        }

        int currentSyntheticCount = 0;
        for (InternalPassenger passenger : state.passengers) {
            if ("synthetic".equals(passenger.source)) {
                currentSyntheticCount += 1;
            }
        }

        while (currentSyntheticCount < config.syntheticPassengerCount) {
            InternalPassenger passenger = createSyntheticPassenger(state.nextPassengerId, random);
            if (passenger == null) {
                return;
            }
            state.passengers.add(passenger);
            state.nextPassengerId += 1;
            currentSyntheticCount += 1;
        }
    }

    private InternalPassenger createSyntheticPassenger(long passengerId, Random random) {
        if (spawnableSegments.isEmpty()) {
            return null;
        }

        GraphSegment segment = spawnableSegments.get(random.nextInt(spawnableSegments.size()));
        double progressMeters = random.nextDouble() * segment.lengthMeters;
        Position position = interpolate(segment, progressMeters);
        return new InternalPassenger(passengerId, position.lng, position.lat, 0.0, "synthetic");
    }

    private void respawnVehicle(InternalVehicle vehicle, Random random) {
        if (spawnableSegments.isEmpty()) {
            vehicle.segment = null;
            return;
        }

        GraphSegment segment = spawnableSegments.get(random.nextInt(spawnableSegments.size()));
        vehicle.segment = segment;
        vehicle.progressMeters = Math.min(Math.max(segment.lengthMeters * (0.1 + 0.8 * random.nextDouble()), 0.5), segment.lengthMeters);
        vehicle.status = "idle";
        vehicle.matchedTimerSeconds = 0.0;
        vehicle.targetPassengerId = null;
        syncVehiclePosition(vehicle);
    }

    private void syncVehiclePosition(InternalVehicle vehicle) {
        Position position = interpolate(vehicle.segment, vehicle.progressMeters);
        vehicle.lng = position.lng;
        vehicle.lat = position.lat;
        LocalVector direction = normalizedDirection(vehicle.segment);
        vehicle.headingX = direction.x;
        vehicle.headingY = direction.y;
    }

    private FlowFieldSimulationFrameVO buildFrame(
            String simulationId,
            int frameIndex,
            double simTimeSeconds,
            InternalSimulationState state,
            SimulationConfig config
    ) {
        List<FlowFieldSimulationFrameVO.VehicleSnapshot> vehicles = state.vehicles.stream()
                .map(vehicle -> new FlowFieldSimulationFrameVO.VehicleSnapshot(
                        vehicle.vehicleId,
                        vehicle.segment == null ? null : vehicle.segment.segmentId,
                        vehicle.lng,
                        vehicle.lat,
                        vehicle.headingX,
                        vehicle.headingY,
                        vehicle.segment == null || vehicle.segment.lengthMeters <= 0.0
                                ? 0.0
                                : clampDouble(vehicle.progressMeters / vehicle.segment.lengthMeters, 0.0, 1.0, 0.0),
                        vehicle.currentSpeedMps,
                        vehicle.status,
                        vehicle.targetPassengerId
                ))
                .toList();

        List<FlowFieldSimulationFrameVO.PassengerSnapshot> passengers = state.passengers.stream()
                .map(passenger -> new FlowFieldSimulationFrameVO.PassengerSnapshot(
                        passenger.passengerId,
                        passenger.lng,
                        passenger.lat,
                        passenger.ageSeconds,
                        passenger.source
                ))
                .toList();

        List<FlowFieldSimulationFrameVO.SegmentVectorSnapshot> vectors = config.includeVectorField
                ? buildVectorSnapshots(state.passengers, config)
                : Collections.emptyList();

        int idleVehicleCount = 0;
        int matchedVehicleCount = 0;
        double totalVehicleSpeed = 0.0;
        for (InternalVehicle vehicle : state.vehicles) {
            totalVehicleSpeed += vehicle.currentSpeedMps;
            if ("matched".equals(vehicle.status)) {
                matchedVehicleCount += 1;
            } else {
                idleVehicleCount += 1;
            }
        }

        double averageVectorStrength = vectors.stream()
                .map(FlowFieldSimulationFrameVO.SegmentVectorSnapshot::strength)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        FlowFieldSimulationFrameVO.SimulationStats stats = new FlowFieldSimulationFrameVO.SimulationStats(
                idleVehicleCount,
                matchedVehicleCount,
                state.passengers.size(),
                state.vehicles.isEmpty() ? 0.0 : totalVehicleSpeed / state.vehicles.size(),
                averageVectorStrength
        );

        return new FlowFieldSimulationFrameVO(
                simulationId,
                frameIndex,
                System.currentTimeMillis(),
                simTimeSeconds,
                vehicles,
                passengers,
                vectors,
                stats
        );
    }

    private List<FlowFieldSimulationFrameVO.SegmentVectorSnapshot> buildVectorSnapshots(
            List<InternalPassenger> passengers,
            SimulationConfig config
    ) {
        if (passengers.isEmpty() || spawnableSegments.isEmpty()) {
            return Collections.emptyList();
        }

        List<FlowFieldSimulationFrameVO.SegmentVectorSnapshot> snapshots = new ArrayList<>();
        int sampleStep = Math.max(1, config.vectorSampleStep);
        for (int i = 0; i < spawnableSegments.size(); i += sampleStep) {
            GraphSegment segment = spawnableSegments.get(i);
            double midLng = (segment.startLng + segment.endLng) * 0.5;
            double midLat = (segment.startLat + segment.endLat) * 0.5;
            LocalVector field = nearestPassengerField(
                    midLng,
                    midLat,
                    passengers,
                    config.attractionStrength,
                    config.fieldSpreadMeters
            );
            LocalVector direction = normalizedDirection(segment);
            double strength = direction.dot(field);
            if (strength <= 1e-9) {
                continue;
            }
            snapshots.add(new FlowFieldSimulationFrameVO.SegmentVectorSnapshot(
                    segment.segmentId,
                    segment.startLng,
                    segment.startLat,
                    segment.endLng,
                    segment.endLat,
                    direction.x * strength,
                    direction.y * strength,
                    strength
            ));
        }
        return snapshots;
    }

    private LocalVector nearestPassengerField(
            double lng,
            double lat,
            List<InternalPassenger> passengers,
            double attractionStrength,
            double spreadMeters
    ) {
        if (passengers.isEmpty()) {
            return LocalVector.ZERO;
        }

        InternalPassenger nearest = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        for (InternalPassenger passenger : passengers) {
            LocalVector delta = toLocalMeters(lng, lat, passenger.lng, passenger.lat);
            double distanceSquared = delta.x * delta.x + delta.y * delta.y;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = passenger;
            }
        }

        if (nearest == null) {
            return LocalVector.ZERO;
        }

        LocalVector delta = toLocalMeters(lng, lat, nearest.lng, nearest.lat);
        double softenedDistance = Math.sqrt(delta.x * delta.x + delta.y * delta.y + spreadMeters * spreadMeters);
        if (softenedDistance <= 1e-9) {
            return LocalVector.ZERO;
        }
        double weight = attractionStrength / softenedDistance;
        return new LocalVector(delta.x * weight, delta.y * weight);
    }

    private void broadcastFrame(SimulationRuntime runtime, FlowFieldSimulationFrameVO frame) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "FLOW_FIELD_SIMULATION_FRAME");
        payload.put("simulationId", runtime.simulationId);
        payload.put("status", runtime.status);
        payload.put("frame", frame);
        payload.put("configuredFrames", runtime.config.totalFrames);
        payload.put("emittedFrames", runtime.emittedFrames.get());
        webSocketHandler.broadcastMessage(payload);
    }

    private void broadcastLifecycleEvent(String type, SimulationRuntime runtime, FlowFieldSimulationFrameVO latestFrame) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("simulationId", runtime.simulationId);
        payload.put("status", runtime.status);
        payload.put("message", runtime.message);
        payload.put("websocketPath", WEBSOCKET_PATH);
        payload.put("configuredFrames", runtime.config.totalFrames);
        payload.put("emittedFrames", runtime.emittedFrames.get());
        payload.put("config", toEffectiveConfig(runtime.config));
        payload.put("latestFrame", latestFrame);
        webSocketHandler.broadcastMessage(payload);
    }

    private FlowFieldSimulationControlVO buildControl(
            SimulationRuntime runtime,
            String status,
            String message,
            List<FlowFieldSimulationFrameVO> previewFrames
    ) {
        return new FlowFieldSimulationControlVO(
                runtime.simulationId,
                status,
                runtime.running,
                message,
                WEBSOCKET_PATH,
                runtime.config.totalFrames,
                runtime.emittedFrames.get(),
                toEffectiveConfig(runtime.config),
                runtime.latestFrame,
                previewFrames
        );
    }

    private FlowFieldSimulationControlVO idleControl() {
        return new FlowFieldSimulationControlVO(
                null,
                "IDLE",
                false,
                "No active flow field simulation.",
                WEBSOCKET_PATH,
                0,
                0,
                null,
                null,
                null
        );
    }

    private FlowFieldSimulationControlVO.EffectiveConfig toEffectiveConfig(SimulationConfig config) {
        return new FlowFieldSimulationControlVO.EffectiveConfig(
                config.vehicleCount,
                config.vehicleSpeedKmh,
                config.occupiedSpeedRatio,
                config.tickSeconds,
                config.totalFrames,
                config.frameIntervalMs,
                config.attractionStrength,
                config.fieldSpreadMeters,
                config.maxMatchDistanceMeters,
                config.matchedHoldSeconds,
                config.syntheticPassengerCount,
                config.useDatabasePassengers,
                config.broadcast,
                config.includeVectorField,
                config.vectorSampleStep,
                config.previewFrames,
                config.randomSeed,
                config.requestPassengerLocations.size()
        );
    }

    private List<GraphSegment> toGraphSegments(List<FlowFieldRoadSegmentDTO> sourceSegments) {
        if (sourceSegments == null || sourceSegments.isEmpty()) {
            return Collections.emptyList();
        }

        List<GraphSegment> segments = new ArrayList<>();
        for (FlowFieldRoadSegmentDTO dto : sourceSegments) {
            if (dto.getSegmentId() == null
                    || dto.getStartNodeId() == null
                    || dto.getEndNodeId() == null
                    || dto.getStartLng() == null
                    || dto.getStartLat() == null
                    || dto.getEndLng() == null
                    || dto.getEndLat() == null) {
                continue;
            }

            GraphSegment segment = new GraphSegment();
            segment.segmentId = dto.getSegmentId();
            segment.startNodeId = dto.getStartNodeId();
            segment.endNodeId = dto.getEndNodeId();
            segment.startLng = dto.getStartLng();
            segment.startLat = dto.getStartLat();
            segment.endLng = dto.getEndLng();
            segment.endLat = dto.getEndLat();
            segment.lengthMeters = dto.getLengthMeters() != null && dto.getLengthMeters() > 0.0
                    ? dto.getLengthMeters()
                    : distanceMeters(dto.getStartLng(), dto.getStartLat(), dto.getEndLng(), dto.getEndLat());
            segments.add(segment);
        }
        return segments;
    }

    private Map<Long, List<GraphSegment>> buildAdjacency(List<GraphSegment> segments) {
        if (segments.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<GraphSegment>> adjacency = new HashMap<>();
        for (GraphSegment segment : segments) {
            adjacency.computeIfAbsent(segment.startNodeId, ignored -> new ArrayList<>()).add(segment);
        }
        return adjacency;
    }

    private Position interpolate(GraphSegment segment, double progressMeters) {
        if (segment == null || segment.lengthMeters <= 0.0) {
            return new Position(0.0, 0.0);
        }
        double ratio = clampDouble(progressMeters / segment.lengthMeters, 0.0, 1.0, 0.0);
        double lng = segment.startLng + (segment.endLng - segment.startLng) * ratio;
        double lat = segment.startLat + (segment.endLat - segment.startLat) * ratio;
        return new Position(lng, lat);
    }

    private LocalVector normalizedDirection(GraphSegment segment) {
        LocalVector raw = toLocalMeters(segment.startLng, segment.startLat, segment.endLng, segment.endLat);
        double length = raw.magnitude();
        if (length <= 1e-9) {
            return LocalVector.ZERO;
        }
        return new LocalVector(raw.x / length, raw.y / length);
    }

    private LocalVector toLocalMeters(double fromLng, double fromLat, double toLng, double toLat) {
        double meanLatRad = Math.toRadians((fromLat + toLat) * 0.5);
        double dx = Math.toRadians(toLng - fromLng) * EARTH_RADIUS_METERS * Math.cos(meanLatRad);
        double dy = Math.toRadians(toLat - fromLat) * EARTH_RADIUS_METERS;
        return new LocalVector(dx, dy);
    }

    private double distanceMeters(double lng1, double lat1, double lng2, double lat2) {
        return toLocalMeters(lng1, lat1, lng2, lat2).magnitude();
    }

    private double kmhToMetersPerSecond(double kmh) {
        return kmh / 3.6;
    }

    private int clampInt(Integer value, int min, int max, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    private double clampDouble(Double value, double min, double max, double defaultValue) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static final class GraphSegment {
        private Long segmentId;
        private Long startNodeId;
        private Long endNodeId;
        private double startLng;
        private double startLat;
        private double endLng;
        private double endLat;
        private double lengthMeters;
    }

    private static final class InternalVehicle {
        private Long vehicleId;
        private GraphSegment segment;
        private double progressMeters;
        private double lng;
        private double lat;
        private double headingX;
        private double headingY;
        private String status;
        private double matchedTimerSeconds;
        private double currentSpeedMps;
        private Long targetPassengerId;
    }

    private static final class InternalPassenger {
        private final Long passengerId;
        private final double lng;
        private final double lat;
        private double ageSeconds;
        private final String source;

        private InternalPassenger(Long passengerId, double lng, double lat, double ageSeconds, String source) {
            this.passengerId = passengerId;
            this.lng = lng;
            this.lat = lat;
            this.ageSeconds = ageSeconds;
            this.source = source;
        }
    }

    private static final class InternalSimulationState {
        private List<InternalVehicle> vehicles = Collections.emptyList();
        private List<InternalPassenger> passengers = Collections.emptyList();
        private long nextPassengerId;
    }

    private static final class SimulationConfig {
        private int vehicleCount;
        private double vehicleSpeedKmh;
        private double occupiedSpeedRatio;
        private double tickSeconds;
        private int totalFrames;
        private int frameIntervalMs;
        private double attractionStrength;
        private double fieldSpreadMeters;
        private double maxMatchDistanceMeters;
        private double matchedHoldSeconds;
        private int syntheticPassengerCount;
        private boolean useDatabasePassengers;
        private boolean broadcast;
        private boolean includeVectorField;
        private int vectorSampleStep;
        private int previewFrames;
        private long randomSeed;
        private List<double[]> requestPassengerLocations = Collections.emptyList();
    }

    private static final class SimulationRuntime {
        private final String simulationId;
        private final SimulationConfig config;
        private final InternalSimulationState state;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicInteger emittedFrames = new AtomicInteger(1);
        private volatile boolean running = true;
        private volatile String status = "RUNNING";
        private volatile String message = "Flow field simulation is running.";
        private volatile FlowFieldSimulationFrameVO latestFrame;
        private volatile Future<?> future;

        private SimulationRuntime(
                String simulationId,
                SimulationConfig config,
                InternalSimulationState state,
                FlowFieldSimulationFrameVO latestFrame
        ) {
            this.simulationId = simulationId;
            this.config = config;
            this.state = state;
            this.latestFrame = latestFrame;
        }

        private void stop(String status, String message) {
            active.set(false);
            running = false;
            this.status = status;
            this.message = message;
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    private record Position(double lng, double lat) {
    }

    private record LocalVector(double x, double y) {
        private static final LocalVector ZERO = new LocalVector(0.0, 0.0);

        private double magnitude() {
            return Math.hypot(x, y);
        }

        private double dot(LocalVector other) {
            return x * other.x + y * other.y;
        }
    }

    private static final class SimulationThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "flow-field-simulation");
            thread.setDaemon(true);
            return thread;
        }
    }
}
