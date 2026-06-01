package com.jjy.wgcbackend.service.impl;

import com.jjy.wgcbackend.entitiy.dto.FlowFieldRouteResultDTO;
import com.jjy.wgcbackend.entitiy.dto.NavigationGraphSegmentDTO;
import com.jjy.wgcbackend.entitiy.dto.NodeDTO;
import com.jjy.wgcbackend.entitiy.dto.RoadSegmentNavDTO;
import com.jjy.wgcbackend.entitiy.vo.FlowFieldVO;
import com.jjy.wgcbackend.mapper.RideRequestsMapper;
import com.jjy.wgcbackend.mapper.RoadSegmentsMapper;
import com.jjy.wgcbackend.service.IFlowFieldService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

@Slf4j
@Service
public class FlowFieldServiceImpl implements IFlowFieldService {

    @Autowired
    private RideRequestsMapper rideRequestsMapper;
    @Autowired
    private RoadSegmentsMapper roadSegmentsMapper;

    // 将路网缓存在内存中，避免每次计算都查库
    private List<Map<String, Object>> cachedSegments = Collections.emptyList();
    private Map<Long, List<RoadSegmentNavDTO>> cachedOutgoingSegments = Collections.emptyMap();

    // 系统参数
    private static final double ATTRACTION_STRENGTH = 1.5;
    private static final double ROUTE_FIELD_SPREAD_DEG = 0.0015;
    private static final double ROUTE_ARRIVAL_THRESHOLD_METERS = 60.0;
    private static final int ROUTE_MAX_EXPANSIONS = 5000;
    private static final double ROUTE_FLOW_REWARD_FACTOR = 0.35;
    private static final double FIELD_SPREAD_DEG = 0.01; // 约1公里转换度数，根据 EPSG:4326 调整

    @PostConstruct
    public void init() {
        cachedSegments = roadSegmentsMapper.getAllSegmentVectors();
        cachedOutgoingSegments = buildOutgoingSegmentCache(roadSegmentsMapper.findAllSegmentsForNavigation());
        log.info("Road segments cached for Flow Field calculation. Count: {}", cachedSegments.size());
    }

    @Override
    public FlowFieldVO calculateCurrentFlowField() {
        // 1. 获取所有未匹配的乘客
        List<NodeDTO> passengers = rideRequestsMapper.getActivePassengerLocations();
        List<FlowFieldVO.SegmentVector> vectors = new ArrayList<>();

        if (passengers.isEmpty()) {
            return new FlowFieldVO(System.currentTimeMillis(), vectors);
        }

        // 2. 遍历所有路段计算引力
        for (Map<String, Object> segment : cachedSegments) {
            Long segmentId = asLong(segment.get("segment_id"));
            double startLng = asDouble(segment.get("start_lng"));
            double startLat = asDouble(segment.get("start_lat"));
            double endLng = asDouble(segment.get("end_lng"));
            double endLat = asDouble(segment.get("end_lat"));

            // 路段中点
            double midLng = (startLng + endLng) / 2.0;
            double midLat = (startLat + endLat) / 2.0;

            // 路段方向向量
            double segDx = endLng - startLng;
            double segDy = endLat - startLat;

            // 计算该中点受到的叠加引力场
            double fieldVx = 0;
            double fieldVy = 0;

            for (NodeDTO p : passengers) {
                double dx = p.getLng() - midLng;
                double dy = p.getLat() - midLat;
                double distanceSq = dx * dx + dy * dy + FIELD_SPREAD_DEG * FIELD_SPREAD_DEG;

                // 引力与距离平方成反比
                double weight = ATTRACTION_STRENGTH / Math.sqrt(distanceSq);
                fieldVx += weight * dx;
                fieldVy += weight * dy;
            }

            // 3. 将全局引力投影到该路段的前进方向上 (点乘)
            double dotProduct = fieldVx * segDx + fieldVy * segDy;

            // 只有当拉力方向与行驶方向基本一致时，该路段才具备引导价值
            if (dotProduct > 1e-6) {
                vectors.add(new FlowFieldVO.SegmentVector(
                        segmentId,
                        dotProduct * segDx,
                        dotProduct * segDy,
                        dotProduct // 绝对强度
                ));
            }
        }

        return new FlowFieldVO(System.currentTimeMillis(), vectors);
    }

    @Override
    public FlowFieldRouteResultDTO buildRouteToPassenger(double[] driverLocation, double[] passengerLocation) {
        if (!isValidLocation(driverLocation) || !isValidLocation(passengerLocation)) {
            return null;
        }

        RoadSegmentNavDTO startSegment = roadSegmentsMapper.findNearestSegment(driverLocation[0], driverLocation[1]);
        RoadSegmentNavDTO destinationSegment = roadSegmentsMapper.findNearestSegment(passengerLocation[0], passengerLocation[1]);
        if (startSegment == null || destinationSegment == null) {
            return null;
        }

        List<RoadSegmentNavDTO> routeSegments = findRouteSegments(startSegment, destinationSegment, passengerLocation);
        if (routeSegments.isEmpty()) {
            routeSegments = List.of(startSegment);
        }

        List<Long> routeSegmentIds = new ArrayList<>();
        List<double[]> pathPoints = new ArrayList<>();
        pathPoints.add(new double[]{driverLocation[0], driverLocation[1]});
        for (RoadSegmentNavDTO segment : routeSegments) {
            routeSegmentIds.add(segment.getSegmentId());
            appendPoint(pathPoints, new double[]{segment.getStartLng(), segment.getStartLat()});
            appendPoint(pathPoints, new double[]{segment.getEndLng(), segment.getEndLat()});
        }
        appendPoint(pathPoints, new double[]{passengerLocation[0], passengerLocation[1]});

        return new FlowFieldRouteResultDTO(
                startSegment.getSegmentId(),
                destinationSegment.getSegmentId(),
                new double[]{routeSegments.get(0).getStartLng(), routeSegments.get(0).getStartLat()},
                new double[]{passengerLocation[0], passengerLocation[1]},
                dedupeConsecutiveSegmentIds(routeSegmentIds),
                dedupeConsecutivePoints(pathPoints)
        );
    }

    private List<RoadSegmentNavDTO> findRouteSegments(
            RoadSegmentNavDTO startSegment,
            RoadSegmentNavDTO destinationSegment,
            double[] passengerLocation
    ) {
        Map<Long, List<RoadSegmentNavDTO>> outgoingCache = new HashMap<>();
        Map<Long, Double> bestCostBySegment = new HashMap<>();
        Map<Long, Long> previousSegmentBySegment = new HashMap<>();
        Map<Long, RoadSegmentNavDTO> segmentById = new HashMap<>();
        PriorityQueue<RouteState> frontier = new PriorityQueue<>((a, b) -> Double.compare(a.priority(), b.priority()));

        segmentById.put(startSegment.getSegmentId(), startSegment);
        bestCostBySegment.put(startSegment.getSegmentId(), 0.0);
        frontier.add(new RouteState(startSegment, 0.0, heuristicMeters(startSegment, passengerLocation)));

        int expansions = 0;
        while (!frontier.isEmpty() && expansions < ROUTE_MAX_EXPANSIONS) {
            RouteState state = frontier.poll();
            RoadSegmentNavDTO current = state.segment();
            double bestKnown = bestCostBySegment.getOrDefault(current.getSegmentId(), Double.POSITIVE_INFINITY);
            if (state.costSoFar() > bestKnown + 1e-9) {
                continue;
            }

            if (isGoalSegment(current, destinationSegment, passengerLocation)) {
                return reconstructRoute(current.getSegmentId(), previousSegmentBySegment, segmentById);
            }

            expansions += 1;
            List<RoadSegmentNavDTO> outgoing = outgoingCache.computeIfAbsent(
                    current.getEndNodeId(),
                    nodeId -> cachedOutgoingSegments.getOrDefault(nodeId, Collections.emptyList())
            );

            for (RoadSegmentNavDTO candidate : outgoing) {
                if (candidate.getSegmentId() == null || candidate.getSegmentId().equals(current.getSegmentId())) {
                    continue;
                }

                segmentById.putIfAbsent(candidate.getSegmentId(), candidate);
                double edgeCost = traversalCost(current, candidate, passengerLocation);
                double nextCost = state.costSoFar() + edgeCost;
                double previousBest = bestCostBySegment.getOrDefault(candidate.getSegmentId(), Double.POSITIVE_INFINITY);
                if (nextCost + 1e-9 >= previousBest) {
                    continue;
                }

                bestCostBySegment.put(candidate.getSegmentId(), nextCost);
                previousSegmentBySegment.put(candidate.getSegmentId(), current.getSegmentId());
                frontier.add(new RouteState(
                        candidate,
                        nextCost,
                        nextCost + heuristicMeters(candidate, passengerLocation)
                ));
            }
        }

        return Collections.emptyList();
    }

    private FieldVector nearestPassengerFieldForRoute(
            double lng,
            double lat,
            List<NodeDTO> passengers,
            double attraction,
            double spreadDeg
    ) {
        if (passengers == null || passengers.isEmpty()) {
            return new FieldVector(0.0, 0.0);
        }

        NodeDTO nearest = passengers.get(0);
        double bestR2 = Double.MAX_VALUE;
        for (NodeDTO passenger : passengers) {
            if (passenger.getLng() == null || passenger.getLat() == null) {
                continue;
            }
            double dx = passenger.getLng() - lng;
            double dy = passenger.getLat() - lat;
            double r2 = dx * dx + dy * dy;
            if (r2 < bestR2) {
                bestR2 = r2;
                nearest = passenger;
            }
        }

        if (nearest.getLng() == null || nearest.getLat() == null) {
            return new FieldVector(0.0, 0.0);
        }

        double dx = nearest.getLng() - lng;
        double dy = nearest.getLat() - lat;
        double r2 = dx * dx + dy * dy + spreadDeg * spreadDeg;
        double weight = attraction / Math.sqrt(r2);
        return new FieldVector(weight * dx, weight * dy);
    }

    private double traversalCost(
            RoadSegmentNavDTO current,
            RoadSegmentNavDTO candidate,
            double[] passengerLocation
    ) {
        double baseCost = candidate.getLengthMeters() != null && candidate.getLengthMeters() > 0
                ? candidate.getLengthMeters()
                : distanceMeters(
                candidate.getStartLng(),
                candidate.getStartLat(),
                candidate.getEndLng(),
                candidate.getEndLat()
        );

        FieldVector field = nearestPassengerFieldForRoute(
                current.getEndLng(),
                current.getEndLat(),
                List.of(toNode(passengerLocation)),
                ATTRACTION_STRENGTH,
                ROUTE_FIELD_SPREAD_DEG
        );

        double[] candidateDir = normalizedDirection(candidate);
        double fieldMagnitude = Math.hypot(field.vx(), field.vy());
        double alignment = 0.0;
        if (fieldMagnitude > 1e-12) {
            alignment = (candidateDir[0] * field.vx() + candidateDir[1] * field.vy()) / fieldMagnitude;
        }

        double rewardMultiplier = 1.0 - ROUTE_FLOW_REWARD_FACTOR * Math.max(0.0, clamp(alignment, -1.0, 1.0));
        return Math.max(1.0, baseCost * rewardMultiplier);
    }

    private double heuristicMeters(RoadSegmentNavDTO segment, double[] passengerLocation) {
        return distanceMeters(
                segment.getEndLng(),
                segment.getEndLat(),
                passengerLocation[0],
                passengerLocation[1]
        );
    }

    private boolean isGoalSegment(
            RoadSegmentNavDTO current,
            RoadSegmentNavDTO destinationSegment,
            double[] passengerLocation
    ) {
        return isNearPassenger(current, passengerLocation)
                || current.getSegmentId().equals(destinationSegment.getSegmentId())
                || sameNode(current.getEndNodeId(), destinationSegment.getStartNodeId())
                || sameNode(current.getEndNodeId(), destinationSegment.getEndNodeId());
    }

    private List<RoadSegmentNavDTO> reconstructRoute(
            Long goalSegmentId,
            Map<Long, Long> previousSegmentBySegment,
            Map<Long, RoadSegmentNavDTO> segmentById
    ) {
        List<RoadSegmentNavDTO> reversed = new ArrayList<>();
        Long cursor = goalSegmentId;
        Set<Long> guard = new HashSet<>();
        while (cursor != null && guard.add(cursor)) {
            RoadSegmentNavDTO segment = segmentById.get(cursor);
            if (segment == null) {
                break;
            }
            reversed.add(segment);
            cursor = previousSegmentBySegment.get(cursor);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private Map<Long, List<RoadSegmentNavDTO>> buildOutgoingSegmentCache(List<NavigationGraphSegmentDTO> segments) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<RoadSegmentNavDTO>> grouped = new HashMap<>();
        for (NavigationGraphSegmentDTO segment : segments) {
            if (segment.getStartNodeId() == null || segment.getSegmentId() == null) {
                continue;
            }

            RoadSegmentNavDTO dto = new RoadSegmentNavDTO();
            dto.setSegmentId(segment.getSegmentId());
            dto.setStartNodeId(segment.getStartNodeId());
            dto.setEndNodeId(segment.getEndNodeId());
            dto.setLengthMeters(segment.getLengthMeters());
            dto.setStartLng(segment.getStartLng());
            dto.setStartLat(segment.getStartLat());
            dto.setEndLng(segment.getEndLng());
            dto.setEndLat(segment.getEndLat());

            grouped.computeIfAbsent(segment.getStartNodeId(), ignored -> new ArrayList<>()).add(dto);
        }
        return grouped;
    }

    private boolean isNearPassenger(RoadSegmentNavDTO segment, double[] passengerLocation) {
        return distanceMeters(
                segment.getEndLng(),
                segment.getEndLat(),
                passengerLocation[0],
                passengerLocation[1]
        ) <= ROUTE_ARRIVAL_THRESHOLD_METERS;
    }

    private double[] normalizedDirection(RoadSegmentNavDTO segment) {
        double dx = segment.getEndLng() - segment.getStartLng();
        double dy = segment.getEndLat() - segment.getStartLat();
        double length = Math.hypot(dx, dy);
        if (length < 1e-12) {
            return new double[]{0.0, 0.0};
        }
        return new double[]{dx / length, dy / length};
    }

    private double directionalAlignment(double[] currentDir, double[] candidateDir) {
        return currentDir[0] * candidateDir[0] + currentDir[1] * candidateDir[1];
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private NodeDTO toNode(double[] location) {
        NodeDTO node = new NodeDTO();
        node.setLng(location[0]);
        node.setLat(location[1]);
        return node;
    }

    private boolean isValidLocation(double[] location) {
        return location != null && location.length >= 2;
    }

    private boolean sameNode(Long left, Long right) {
        return left != null && left.equals(right);
    }

    private List<Long> dedupeConsecutiveSegmentIds(List<Long> segmentIds) {
        List<Long> deduped = new ArrayList<>();
        for (Long segmentId : segmentIds) {
            if (segmentId == null) {
                continue;
            }
            if (deduped.isEmpty() || !deduped.get(deduped.size() - 1).equals(segmentId)) {
                deduped.add(segmentId);
            }
        }
        return deduped;
    }

    private List<double[]> dedupeConsecutivePoints(List<double[]> points) {
        List<double[]> deduped = new ArrayList<>();
        for (double[] point : points) {
            if (point == null || point.length < 2) {
                continue;
            }
            if (!deduped.isEmpty()) {
                double[] last = deduped.get(deduped.size() - 1);
                if (Math.abs(last[0] - point[0]) < 1e-12 && Math.abs(last[1] - point[1]) < 1e-12) {
                    continue;
                }
            }
            deduped.add(point);
        }
        return deduped;
    }

    private void appendPoint(List<double[]> points, double[] point) {
        if (point == null || point.length < 2) {
            return;
        }
        if (points.isEmpty()) {
            points.add(point);
            return;
        }
        double[] last = points.get(points.size() - 1);
        if (Math.abs(last[0] - point[0]) < 1e-12 && Math.abs(last[1] - point[1]) < 1e-12) {
            return;
        }
        points.add(point);
    }

    private double distanceMeters(double lng1, double lat1, double lng2, double lat2) {
        double x = Math.toRadians(lng2 - lng1) * Math.cos(Math.toRadians((lat1 + lat2) / 2.0));
        double y = Math.toRadians(lat2 - lat1);
        return 6_371_000.0 * Math.hypot(x, y);
    }

    private double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private record FieldVector(double vx, double vy) {
    }

    private record RouteState(RoadSegmentNavDTO segment, double costSoFar, double priority) {
    }
}
