import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

import { Button } from "./components/ui/button.tsx";
import { Slider } from "./components/ui/slider.tsx";
import { Card, CardContent, CardHeader, CardTitle } from "./components/ui/card.tsx";

import manhattanGraphJson from "./data/manhattan_graph_react.json";

const WIDTH = 1200;
const HEIGHT = 1350;
const GRID_SIZE = 10;
const MARGIN = 35;
const SEGMENT_LENGTH_KM = 3.611;

const BASE_SECONDS_PER_TICK = 0.1;
const REALTIME_DT_MS = 100;
const FAST_DT_MS = 100;

const MIN_ZOOM = 0.8;
const MAX_ZOOM = 3.5;
const DEFAULT_ZOOM = 1.0;

const MIN_ATTRACTION = 0.05;
const MAX_ATTRACTION = 1.8;
const FLOW_VISUAL_REFERENCE_ATTRACTION = 0.15;

const DEFAULT_TAXI_COUNT = 100;
const MIN_TAXI_SEPARATION_PX = 80;

const STUCK_MIN_PROGRESS_ALONG_EDGE = 1e-3;
const STUCK_MIN_MOVEMENT_PX = 0.5;
const STUCK_CHECKS_THRESHOLD = 80;
const STUCK_NEARBY_POPUP_RADIUS_PX = 90;

const DEMAND_JSON_URL = "/data/passenger_arrivals_manhattan_2009-01-04_1s.json";
const DEMO_PREDICTION_MAX_STEPS = 400;
const DEMO_PATH_CAPTURE_EPS = 1e-6;

type NodeType = {
  id: string;
  x: number;
  y: number;
  lon?: number;
  lat?: number;
};

type Edge = {
  id: string;
  from: string;
  to: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  dx: number;
  dy: number;
  length: number;
};

type GraphType = {
  nodes: Record<string, NodeType>;
  edges: Edge[];
  edgeMap: Record<string, Edge>;
  adjacency: Record<string, string[]>;
  safeEdgeIds: Set<string>;
  safeEdges: Edge[];
};

type TaxiMemory = {
  prevX: number;
  prevY: number;
  prevS: number;
  prevEdgeId: string | null;
  enteredFromEdgeId: string | null;
  stuckTicks: number;
};

type Taxi = {
  id: number;
  edgeId: string;
  s: number;
  x: number;
  y: number;
  status: "idle" | "matched";
  matchedTimer: number;
  headingDx: number;
  headingDy: number;
  idleAge: number;
  memory: TaxiMemory;
};

type Passenger = {
  id: number;
  edgeId: string;
  s: number;
  x: number;
  y: number;
  age: number;
  ttl: number;
};

type DemoPathInfo = {
  predictedEdgeIds: string[];
  predictedPathPoints: Array<{ x: number; y: number }>;
  deviatedEdgeIds: string[];
};

type SimState = {
  taxis: Taxi[];
  passengers: Passenger[];
  nextPassengerId: number;
  demoPathInfo: DemoPathInfo | null;
};

type ProbPoint = {
  time: number;
  avgProb: number;
  passengerProb: number;
};

type AwtPoint = {
  time: number;
  driverAwt: number;
  passengerAwt: number;
};

type SliderBlockProps = {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (value: number) => void;
};

type LegendButtonProps = {
  color: string;
  label: string;
  active: boolean;
  onClick: () => void;
};

type CalibrationInfo = {
  metersPerPx: number;
  kmPerPx: number;
  sampleCount: number;
  usedFallback: boolean;
};

type CustomTooltipProps = {
  active?: boolean;
  payload?: Array<{
    value?: number | string;
    dataKey?: string;
    color?: string;
  }>;
  label?: number | string;
};

type DemandRecord = {
  start_timeslot: number;
  edgeId: string;
  s: number;
};

function buildGraphFromJson(data: unknown): GraphType {
  const parsed = data as {
    nodes: Record<string, NodeType>;
    edges: Edge[];
    adjacency: Record<string, string[]>;
  };

  const nodes = parsed.nodes ?? {};
  const edges = parsed.edges ?? [];
  const adjacency = parsed.adjacency ?? {};
  const edgeMap: Record<string, Edge> = Object.fromEntries(edges.map((e) => [e.id, e]));
  const safeEdgeIds = computeSurvivableEdgeIds(edges, adjacency);
  const safeEdges = edges.filter((edge) => safeEdgeIds.has(edge.id));

  return { nodes, edges, edgeMap, adjacency, safeEdgeIds, safeEdges };
}

function computeSurvivableEdgeIds(
  edges: Edge[],
  adjacency: Record<string, string[]>,
): Set<string> {
  let survivable = new Set(edges.map((e) => e.id));
  let changed = true;

  while (changed) {
    changed = false;
    const nextSurvivable = new Set<string>();

    for (const edge of edges) {
      const nextIds = (adjacency[edge.to] ?? []).filter((id) => survivable.has(id));
      if (nextIds.length > 0) nextSurvivable.add(edge.id);
    }

    if (nextSurvivable.size !== survivable.size) {
      survivable = nextSurvivable;
      changed = true;
      continue;
    }

    for (const id of survivable) {
      if (!nextSurvivable.has(id)) {
        survivable = nextSurvivable;
        changed = true;
        break;
      }
    }
  }

  return survivable;
}

function nearestSafeEdge(x: number, y: number, graph: GraphType): Edge | null {
  if (graph.safeEdges.length === 0) return null;

  let best: Edge | null = null;
  let bestD = Infinity;

  for (const edge of graph.safeEdges) {
    const mx = 0.5 * (edge.x1 + edge.x2);
    const my = 0.5 * (edge.y1 + edge.y2);
    const d = (mx - x) * (mx - x) + (my - y) * (my - y);
    if (d < bestD) {
      bestD = d;
      best = edge;
    }
  }

  return best;
}

function nearbySafeEdge(
  x: number,
  y: number,
  graph: GraphType,
  maxRadiusPx: number,
  rng: () => number,
): Edge | null {
  const candidates = graph.safeEdges.filter((edge) => {
    const mx = 0.5 * (edge.x1 + edge.x2);
    const my = 0.5 * (edge.y1 + edge.y2);
    return Math.hypot(mx - x, my - y) <= maxRadiusPx;
  });

  if (candidates.length === 0) return nearestSafeEdge(x, y, graph);

  const weights = candidates.map((edge) => {
    const mx = 0.5 * (edge.x1 + edge.x2);
    const my = 0.5 * (edge.y1 + edge.y2);
    const d = Math.hypot(mx - x, my - y);
    return 1 / (d + 5);
  });

  return weightedChoice(candidates, weights, rng);
}

function getGridSpacingPx() {
  return (WIDTH - 2 * MARGIN) / (GRID_SIZE - 1);
}

function secondsToMinutes(seconds: number) {
  return seconds / 60;
}

function formatMinutesFromSeconds(seconds: number) {
  const v = secondsToMinutes(seconds);
  const s = v.toFixed(1);
  return `${s.endsWith(".0") ? s.slice(0, -2) : s} min`;
}

function formatSimTime(simSeconds: number) {
  const mins = simSeconds / 60;
  if (mins < 60) return `${mins.toFixed(0)}m`;
  const h = Math.floor(mins / 60);
  const m = Math.round(mins % 60);
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}

function formatProbTick(value: number) {
  return value.toFixed(1);
}

function formatAwtTick(value: number) {
  return `${value.toFixed(1)}`;
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function mulberry32(a: number) {
  return function () {
    let t = (a += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function dist(a: { x: number; y: number }, b: { x: number; y: number }) {
  const dx = a.x - b.x;
  const dy = a.y - b.y;
  return Math.hypot(dx, dy);
}

function weightedChoice<T>(items: T[], weights: number[], rng: () => number): T {
  const total = weights.reduce((acc, v) => acc + v, 0);
  if (items.length === 0) throw new Error("weightedChoice called with empty items");
  if (total <= 0) return items[items.length - 1];

  let r = rng() * total;
  for (let i = 0; i < items.length; i++) {
    r -= weights[i];
    if (r <= 0) return items[i];
  }

  return items[items.length - 1];
}

function pointOnEdge(edge: Edge, s: number) {
  return {
    x: edge.x1 + edge.dx * s,
    y: edge.y1 + edge.dy * s,
  };
}

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number) {
  const R = 6371;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);

  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;

  return 2 * R * Math.asin(Math.sqrt(a));
}

function median(values: number[]) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0
    ? 0.5 * (sorted[mid - 1] + sorted[mid])
    : sorted[mid];
}

function calibrateMetersPerPx(graph: GraphType): CalibrationInfo {
  const samples: number[] = [];

  for (const edge of graph.edges) {
    const fromNode = graph.nodes[edge.from];
    const toNode = graph.nodes[edge.to];

    if (
      !fromNode ||
      !toNode ||
      !Number.isFinite(fromNode.lat) ||
      !Number.isFinite(fromNode.lon) ||
      !Number.isFinite(toNode.lat) ||
      !Number.isFinite(toNode.lon) ||
      edge.length <= 0
    ) {
      continue;
    }

    const km = haversineKm(fromNode.lat!, fromNode.lon!, toNode.lat!, toNode.lon!);
    if (km > 0) samples.push((km * 1000) / edge.length);
  }

  if (samples.length === 0) {
    const fallbackMetersPerPx = (SEGMENT_LENGTH_KM * 1000) / getGridSpacingPx();
    return {
      metersPerPx: fallbackMetersPerPx,
      kmPerPx: fallbackMetersPerPx / 1000,
      sampleCount: 0,
      usedFallback: true,
    };
  }

  const metersPerPx = median(samples);
  return {
    metersPerPx,
    kmPerPx: metersPerPx / 1000,
    sampleCount: samples.length,
    usedFallback: false,
  };
}

function nearestPassengerField(
  x: number,
  y: number,
  passengers: Passenger[],
  attraction: number,
  spreadPx: number,
) {
  if (!passengers.length) return { vx: 0, vy: 0 };

  let nearest = passengers[0];
  let bestR2 = Infinity;

  for (const p of passengers) {
    const dx = p.x - x;
    const dy = p.y - y;
    const r2 = dx * dx + dy * dy;
    if (r2 < bestR2) {
      bestR2 = r2;
      nearest = p;
    }
  }

  const dx = nearest.x - x;
  const dy = nearest.y - y;
  const r2 = dx * dx + dy * dy + spreadPx * spreadPx;
  const weight = attraction / Math.sqrt(r2);

  return {
    vx: weight * dx,
    vy: weight * dy,
  };
}

function projectVectorOntoEdgeForward(edge: Edge, vx: number, vy: number) {
  const dot = vx * edge.dx + vy * edge.dy;
  return {
    px: dot * edge.dx,
    py: dot * edge.dy,
    strength: Math.max(0, dot),
  };
}

function directionalAlignment(edge: Edge, candidate: Edge) {
  return edge.dx * candidate.dx + edge.dy * candidate.dy;
}

function chooseNextEdgeFieldAligned(
  edge: Edge,
  graph: GraphType,
  passengers: Passenger[],
  attraction: number,
  spreadPx: number,
  rng: () => number,
): Edge | null {
  const candidates = (graph.adjacency[edge.to] ?? [])
    .map((id) => graph.edgeMap[id])
    .filter((candidate): candidate is Edge => !!candidate && graph.safeEdgeIds.has(candidate.id))
    .filter((candidate) => candidate.id !== edge.id);

  if (candidates.length === 0) return null;

  const junctionX = edge.x2;
  const junctionY = edge.y2;
  const field = nearestPassengerField(junctionX, junctionY, passengers, attraction, spreadPx);

  if (Math.hypot(field.vx, field.vy) < 1e-9) {
    const weights = candidates.map((candidate) => {
      const cos = directionalAlignment(edge, candidate);
      const uTurnPenalty = cos < -0.85 ? 1e-4 : 1.0;
      return Math.exp(0.5 * (cos + 1)) * uTurnPenalty;
    });
    return weightedChoice(candidates, weights, rng);
  }

  let best = candidates[0];
  let bestScore = -Infinity;

  for (const candidate of candidates) {
    const forwardScore = candidate.dx * field.vx + candidate.dy * field.vy;
    const continuationScore = 0.08 * directionalAlignment(edge, candidate);
    const score = forwardScore + continuationScore;

    if (score > bestScore) {
      bestScore = score;
      best = candidate;
    }
  }

  return best;
}

function moveTaxiToEdgeStart(
  taxi: Taxi,
  nextEdge: Edge,
  enteredFromEdgeId: string | null,
) {
  const entryEps = Math.min(1e-3, 0.05 * nextEdge.length);
  taxi.memory.enteredFromEdgeId = enteredFromEdgeId;
  taxi.edgeId = nextEdge.id;
  taxi.s = entryEps;
  const p = pointOnEdge(nextEdge, taxi.s);
  taxi.x = p.x;
  taxi.y = p.y;
  taxi.headingDx = nextEdge.dx;
  taxi.headingDy = nextEdge.dy;
}

function respawnTaxiNearby(
  taxi: Taxi,
  graph: GraphType,
  rng: () => number,
  radiusPx: number,
) {
  const nearbyEdge = nearbySafeEdge(taxi.x, taxi.y, graph, radiusPx, rng);
  if (!nearbyEdge) return;

  const s = clamp(
    (0.2 + 0.6 * rng()) * nearbyEdge.length,
    0.05 * nearbyEdge.length,
    0.95 * nearbyEdge.length,
  );
  const p = pointOnEdge(nearbyEdge, s);

  taxi.edgeId = nearbyEdge.id;
  taxi.s = s;
  taxi.x = p.x;
  taxi.y = p.y;
  taxi.headingDx = nearbyEdge.dx;
  taxi.headingDy = nearbyEdge.dy;
  taxi.status = "idle";
  taxi.matchedTimer = 0;
  taxi.idleAge = 0;
  taxi.memory.prevX = p.x;
  taxi.memory.prevY = p.y;
  taxi.memory.prevS = s;
  taxi.memory.prevEdgeId = nearbyEdge.id;
  taxi.memory.enteredFromEdgeId = null;
  taxi.memory.stuckTicks = 0;
}

function updateStuckStatusAndRespawnIfNeeded(
  taxi: Taxi,
  graph: GraphType,
  rng: () => number,
  hasActiveDemand: boolean,
) {
  if (!hasActiveDemand || taxi.status !== "idle") {
    taxi.memory.stuckTicks = 0;
    return;
  }

  const sameEdge = taxi.edgeId === taxi.memory.prevEdgeId;
  const progressDelta = Math.abs(taxi.s - taxi.memory.prevS);
  const movementDelta = Math.hypot(taxi.x - taxi.memory.prevX, taxi.y - taxi.memory.prevY);

  if (!sameEdge) {
    taxi.memory.stuckTicks = 0;
    return;
  }

  if (
    progressDelta < STUCK_MIN_PROGRESS_ALONG_EDGE &&
    movementDelta < STUCK_MIN_MOVEMENT_PX
  ) {
    taxi.memory.stuckTicks += 1;
  } else {
    taxi.memory.stuckTicks = 0;
  }

  if (taxi.memory.stuckTicks >= STUCK_CHECKS_THRESHOLD) {
    respawnTaxiNearby(taxi, graph, rng, STUCK_NEARBY_POPUP_RADIUS_PX);
  }
}

function cloneTaxi(taxi: Taxi): Taxi {
  return {
    ...taxi,
    memory: { ...taxi.memory },
  };
}

function dedupeConsecutiveEdgeIds(edgeIds: string[]) {
  const out: string[] = [];
  for (const id of edgeIds) {
    if (out.length === 0 || out[out.length - 1] !== id) out.push(id);
  }
  return out;
}

function buildPathPolylineFromEdgeIds(
  edgeIds: string[],
  graph: GraphType,
  start: { x: number; y: number },
  end: { x: number; y: number },
) {
  const points: Array<{ x: number; y: number }> = [{ x: start.x, y: start.y }];

  for (const edgeId of edgeIds) {
    const edge = graph.edgeMap[edgeId];
    if (!edge) continue;
    points.push({ x: edge.x2, y: edge.y2 });
  }

  points.push({ x: end.x, y: end.y });
  return points;
}

function predictDemoPath(
  taxi: Taxi,
  passenger: Passenger,
  graph: GraphType,
  attraction: number,
  spreadPx: number,
  maxMatchDist: number,
) {
  const simTaxi = cloneTaxi(taxi);
  const passengers = [passenger];
  const edgeIds: string[] = [];

  for (let step = 0; step < DEMO_PREDICTION_MAX_STEPS; step++) {
    if (dist(simTaxi, passenger) <= maxMatchDist) break;

    const currentEdge = graph.edgeMap[simTaxi.edgeId];
    if (!currentEdge) break;

    if (edgeIds.length === 0 || edgeIds[edgeIds.length - 1] !== currentEdge.id) {
      edgeIds.push(currentEdge.id);
    }

    const nextEdge = chooseNextEdgeFieldAligned(
      currentEdge,
      graph,
      passengers,
      attraction,
      spreadPx,
      () => 0,
    );

    if (!nextEdge) break;

    moveTaxiToEdgeStart(simTaxi, nextEdge, currentEdge.id);

    if (dist(simTaxi, passenger) <= maxMatchDist) {
      if (edgeIds[edgeIds.length - 1] !== nextEdge.id) edgeIds.push(nextEdge.id);
      break;
    }
  }

  const deduped = dedupeConsecutiveEdgeIds(edgeIds);
  const points = buildPathPolylineFromEdgeIds(
    deduped,
    graph,
    { x: taxi.x, y: taxi.y },
    { x: passenger.x, y: passenger.y },
  );

  return {
    predictedEdgeIds: deduped,
    predictedPathPoints: points,
    deviatedEdgeIds: [] as string[],
  };
}

function updateDemoDeviationInfo(
  info: DemoPathInfo | null,
  actualTraversedEdgeId: string | null,
  predictedProgressRef: React.MutableRefObject<number>,
) {
  if (!info || !actualTraversedEdgeId) return info;

  const predicted = info.predictedEdgeIds;
  const k = predictedProgressRef.current;

  if (k < predicted.length && actualTraversedEdgeId === predicted[k]) {
    predictedProgressRef.current = k + 1;
    return info;
  }

  if (!predicted.includes(actualTraversedEdgeId) && !info.deviatedEdgeIds.includes(actualTraversedEdgeId)) {
    return {
      ...info,
      deviatedEdgeIds: [...info.deviatedEdgeIds, actualTraversedEdgeId],
    };
  }

  if (
    predicted.includes(actualTraversedEdgeId) &&
    k < predicted.length &&
    actualTraversedEdgeId !== predicted[k] &&
    !info.deviatedEdgeIds.includes(actualTraversedEdgeId)
  ) {
    return {
      ...info,
      deviatedEdgeIds: [...info.deviatedEdgeIds, actualTraversedEdgeId],
    };
  }

  return info;
}

function moveTaxiFieldBased(
  taxi: Taxi,
  graph: GraphType,
  baseSpeed: number,
  rng: () => number,
  passengers: Passenger[],
  attraction: number,
  spreadPx: number,
  flowFieldEnabled: boolean,
): string | null {
  let traversedNewEdgeId: string | null = null;
  let edge = graph.edgeMap[taxi.edgeId];
  if (!edge) return traversedNewEdgeId;

  if (!graph.safeEdgeIds.has(edge.id)) {
    const fallback = nearestSafeEdge(taxi.x, taxi.y, graph);
    if (!fallback) return traversedNewEdgeId;
    moveTaxiToEdgeStart(taxi, fallback, null);
    traversedNewEdgeId = fallback.id;
    edge = fallback;
  }

  if (!flowFieldEnabled) return traversedNewEdgeId;

  if (taxi.status === "matched") {
    let remaining = baseSpeed * 0.55;

    while (remaining > 0) {
      const left = Math.max(edge.length - taxi.s, 1e-9);

      if (remaining <= left) {
        taxi.s += remaining;
        const p = pointOnEdge(edge, taxi.s);
        taxi.x = p.x;
        taxi.y = p.y;
        taxi.headingDx = edge.dx;
        taxi.headingDy = edge.dy;
        remaining = 0;
      } else {
        remaining -= left;

        const nextCandidates = (graph.adjacency[edge.to] ?? [])
          .map((id) => graph.edgeMap[id])
          .filter(
            (candidate): candidate is Edge =>
              !!candidate &&
              graph.safeEdgeIds.has(candidate.id) &&
              candidate.id !== edge.id,
          );

        if (nextCandidates.length === 0) return traversedNewEdgeId;

        const weights = nextCandidates.map((candidate) => {
          const cos = directionalAlignment(edge, candidate);
          const uTurnPenalty = cos < -0.85 ? 1e-4 : 1.0;
          return Math.exp(0.4 * (cos + 1)) * uTurnPenalty;
        });

        const nextEdge = weightedChoice(nextCandidates, weights, rng);
        moveTaxiToEdgeStart(taxi, nextEdge, edge.id);
        traversedNewEdgeId = nextEdge.id;
        edge = nextEdge;
      }
    }

    return traversedNewEdgeId;
  }

  if (passengers.length === 0) return traversedNewEdgeId;

  const field = nearestPassengerField(taxi.x, taxi.y, passengers, attraction, spreadPx);
  const fieldMag = Math.hypot(field.vx, field.vy);
  const pullBoost = clamp(fieldMag, 0.18, 2.6);

  let remaining = baseSpeed * pullBoost;

  while (remaining > 0) {
    const left = Math.max(edge.length - taxi.s, 1e-9);

    if (remaining <= left) {
      taxi.s += remaining;
      const p = pointOnEdge(edge, taxi.s);
      taxi.x = p.x;
      taxi.y = p.y;
      taxi.headingDx = edge.dx;
      taxi.headingDy = edge.dy;
      remaining = 0;
    } else {
      remaining -= left;

      const nextEdge = chooseNextEdgeFieldAligned(
        edge,
        graph,
        passengers,
        attraction,
        spreadPx,
        rng,
      );

      if (!nextEdge) return traversedNewEdgeId;

      moveTaxiToEdgeStart(taxi, nextEdge, edge.id);
      traversedNewEdgeId = nextEdge.id;
      edge = nextEdge;
    }
  }

  return traversedNewEdgeId;
}

function probabilityAwareMatch(
  taxis: Taxi[],
  passengers: Passenger[],
  maxDist: number,
  rng: () => number,
) {
  const idleTaxis = taxis.filter((t) => t.status === "idle");
  const matches: Array<{ taxiId: number; passengerId: number }> = [];

  if (idleTaxis.length === 0 || passengers.length === 0) return matches;

  const unmatchedTaxiMap = new Map<number, Taxi>(idleTaxis.map((t) => [t.id, t]));
  const shuffledPassengers = [...passengers].sort(() => rng() - 0.5);

  for (const passenger of shuffledPassengers) {
    let bestTaxi: Taxi | null = null;
    let bestScore = -1;

    for (const taxi of unmatchedTaxiMap.values()) {
      const d = dist(taxi, passenger);
      if (d > maxDist) continue;

      const score = 1 / (d + 1e-6);
      if (score > bestScore) {
        bestScore = score;
        bestTaxi = taxi;
      }
    }

    if (bestTaxi) {
      matches.push({ taxiId: bestTaxi.id, passengerId: passenger.id });
      unmatchedTaxiMap.delete(bestTaxi.id);
    }
  }

  return matches;
}

function createPassengerFromRecord(
  graph: GraphType,
  id: number,
  ttl: number,
  record: DemandRecord,
): Passenger | null {
  const edge = graph.edgeMap[record.edgeId];
  if (!edge) return null;

  const s = clamp(record.s, 0, edge.length);
  const p = pointOnEdge(edge, s);

  return {
    id,
    edgeId: edge.id,
    s,
    x: p.x,
    y: p.y,
    age: 0,
    ttl,
  };
}

function createRandomTaxi(graph: GraphType, id: number, rng: () => number): Taxi {
  const edgePool = graph.safeEdges.length > 0 ? graph.safeEdges : graph.edges;
  if (edgePool.length === 0) throw new Error("No edges found in graph");

  const edge = edgePool[Math.floor(rng() * edgePool.length)];
  const s = clamp((0.1 + 0.8 * rng()) * edge.length, 0.02 * edge.length, 0.98 * edge.length);
  const p = pointOnEdge(edge, s);

  return {
    id,
    edgeId: edge.id,
    s,
    x: p.x,
    y: p.y,
    status: "idle",
    matchedTimer: 0,
    headingDx: edge.dx,
    headingDy: edge.dy,
    idleAge: 0,
    memory: {
      prevX: p.x,
      prevY: p.y,
      prevS: s,
      prevEdgeId: edge.id,
      enteredFromEdgeId: null,
      stuckTicks: 0,
    },
  };
}

function createCompetingTaxis(
  graph: GraphType,
  rng: () => number,
  count: number = DEFAULT_TAXI_COUNT,
): Taxi[] {
  const taxis: Taxi[] = [];
  let nextId = 0;
  let attempts = 0;
  const maxAttempts = count * 200;

  while (taxis.length < count && attempts < maxAttempts) {
    const candidate = createRandomTaxi(graph, nextId, rng);
    const tooClose = taxis.some((taxi) => dist(taxi, candidate) < MIN_TAXI_SEPARATION_PX);

    if (!tooClose) {
      taxis.push(candidate);
      nextId += 1;
    }

    attempts += 1;
  }

  while (taxis.length < count) {
    taxis.push(createRandomTaxi(graph, nextId, rng));
    nextId += 1;
  }

  return taxis;
}

function buildFlowFieldOverlay(
  graph: GraphType,
  passengers: Passenger[],
  attraction: number,
  spreadPx: number,
  mapScale: number,
) {
  if (passengers.length === 0) return null;

  const edgesToDraw = (graph.safeEdges.length > 0 ? graph.safeEdges : graph.edges).filter(
    (_, idx) => idx % 5 === 0,
  );

  const visualScale = FLOW_VISUAL_REFERENCE_ATTRACTION / Math.max(attraction, 1e-9);

  return (
    <g opacity={0.95}>
      {edgesToDraw.map((edge) => {
        const sampleS = 0.5 * edge.length;
        const p = pointOnEdge(edge, sampleS);
        const field = nearestPassengerField(p.x, p.y, passengers, attraction, spreadPx);
        const magnitude = Math.hypot(field.vx, field.vy);

        if (magnitude < 1e-6) return null;

        const projected = projectVectorOntoEdgeForward(edge, field.vx, field.vy);
        if (projected.strength <= 1e-6) return null;

        const norm = Math.max(1e-9, Math.hypot(projected.px, projected.py));
        const ux = projected.px / norm;
        const uy = projected.py / norm;

        const visualStrength = projected.strength * visualScale;
        const intensity = Math.tanh(1.5 * visualStrength);

        const arrowLen = (8 + 26 * intensity) / mapScale;
        const strokeW = (0.8 + 1.7 * intensity) / mapScale;
        const headSize = (3.5 + 5.0 * intensity) / mapScale;
        const alpha = 0.18 + 0.72 * intensity;

        const x1 = p.x - 0.5 * arrowLen * ux;
        const y1 = p.y - 0.5 * arrowLen * uy;
        const x2 = p.x + 0.5 * arrowLen * ux;
        const y2 = p.y + 0.5 * arrowLen * uy;

        const hx1 = x2 - headSize * ux + 0.7 * headSize * uy;
        const hy1 = y2 - headSize * uy - 0.7 * headSize * ux;

        const hx2 = x2 - headSize * ux - 0.7 * headSize * uy;
        const hy2 = y2 - headSize * uy + 0.7 * headSize * ux;

        return (
          <g key={`flow-${edge.id}`} opacity={alpha}>
            <line
              x1={x1}
              y1={y1}
              x2={x2}
              y2={y2}
              stroke="#22d3ee"
              strokeWidth={strokeW}
              strokeLinecap="round"
            />
            <line
              x1={x2}
              y1={y2}
              x2={hx1}
              y2={hy1}
              stroke="#22d3ee"
              strokeWidth={strokeW}
              strokeLinecap="round"
            />
            <line
              x1={x2}
              y1={y2}
              x2={hx2}
              y2={hy2}
              stroke="#22d3ee"
              strokeWidth={strokeW}
              strokeLinecap="round"
            />
          </g>
        );
      })}
    </g>
  );
}

function SliderBlock({ label, value, min, max, step, onChange }: SliderBlockProps) {
  return (
    <div className="space-y-2">
      <div className="text-sm text-neutral-300">{label}</div>
      <Slider
        value={[value]}
        min={min}
        max={max}
        step={step}
        onValueChange={(vals) => onChange(vals[0])}
        className="py-1"
      />
    </div>
  );
}

function LegendButton({ color, label, active, onClick }: LegendButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "flex w-44 items-center justify-center gap-2 rounded-xl border px-3 py-2.5",
        "whitespace-nowrap font-medium shadow-md backdrop-blur-sm transition-all",
        active
          ? "border-zinc-500 bg-zinc-700 text-white"
          : "border-zinc-800 bg-zinc-900/70 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200",
      ].join(" ")}
    >
      <span className="h-3 w-3 rounded-full" style={{ backgroundColor: color }} />
      <span>{label}</span>
    </button>
  );
}

function ChartTooltipShell({
  title,
  rows,
}: {
  title: string;
  rows: Array<{ label: string; value: string; color: string }>;
}) {
  return (
    <div className="rounded-xl border border-zinc-700 bg-zinc-950/95 px-3 py-2 shadow-2xl">
      <div className="mb-2 text-xs font-semibold text-zinc-400">{title}</div>
      <div className="space-y-1.5">
        {rows.map((row) => (
          <div key={row.label} className="flex items-center justify-between gap-4 text-xs">
            <div className="flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: row.color }} />
              <span className="text-zinc-300">{row.label}</span>
            </div>
            <span className="font-semibold text-zinc-100">{row.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function AllocationTooltip({ active, payload, label }: CustomTooltipProps) {
  if (!active || !payload || payload.length === 0) return null;

  const rows = payload.map((item) => {
    const value = typeof item.value === "number" ? item.value : Number(item.value ?? 0);
    const key = item.dataKey;
    return {
      label: key === "avgProb" ? "Supply AP" : "Demand AP",
      value: value.toFixed(4),
      color: item.color ?? "#ffffff",
    };
  });

  return <ChartTooltipShell title={`Time: ${formatSimTime(Number(label ?? 0))}`} rows={rows} />;
}

function AwtTooltip({ active, payload, label }: CustomTooltipProps) {
  if (!active || !payload || payload.length === 0) return null;

  const rows = payload.map((item) => {
    const value = typeof item.value === "number" ? item.value : Number(item.value ?? 0);
    const key = item.dataKey;
    return {
      label: key === "driverAwt" ? "Supply AWT" : "Demand AWT",
      value: `${value.toFixed(2)} min`,
      color: item.color ?? "#ffffff",
    };
  });

  return <ChartTooltipShell title={`Time: ${formatSimTime(Number(label ?? 0))}`} rows={rows} />;
}

function createRandomDemoPassenger(
  graph: GraphType,
  id: number,
  ttl: number,
  rng: () => number,
  bounds: { minX: number; maxX: number; minY: number; maxY: number },
): Passenger {
  const edgePool = graph.safeEdges.length > 0 ? graph.safeEdges : graph.edges;

  const targetX =
    bounds.minX + (0.28 + 0.22 * rng()) * (bounds.maxX - bounds.minX);

  const targetY =
    bounds.maxY - (0.05 + 0.12 * rng()) * (bounds.maxY - bounds.minY);

  const candidateEdges = edgePool.filter((edge) => {
    const mx = 0.5 * (edge.x1 + edge.x2);
    const my = 0.5 * (edge.y1 + edge.y2);
    return Math.abs(mx - targetX) <= 140 && Math.abs(my - targetY) <= 140;
  });

  const usableEdges = candidateEdges.length > 0 ? candidateEdges : edgePool;

  let bestEdge = usableEdges[0];
  let bestScore = Infinity;

  for (const edge of usableEdges) {
    const mx = 0.5 * (edge.x1 + edge.x2);
    const my = 0.5 * (edge.y1 + edge.y2);
    const dx = mx - targetX;
    const dy = my - targetY;
    const score = dx * dx + dy * dy;

    if (score < bestScore) {
      bestScore = score;
      bestEdge = edge;
    }
  }

  const s = clamp((0.2 + 0.6 * rng()) * bestEdge.length, 0, bestEdge.length);
  const p = pointOnEdge(bestEdge, s);

  return {
    id,
    edgeId: bestEdge.id,
    s,
    x: p.x,
    y: p.y,
    age: 0,
    ttl,
  };
}

export default function RealtimeTaxiSimulatorProbabilityAware() {
  const graph = useMemo<GraphType>(() => buildGraphFromJson(manhattanGraphJson), []);
  const calibration = useMemo(() => calibrateMetersPerPx(graph), [graph]);
  const metersPerPx = calibration.metersPerPx;
  const kmPerPx = calibration.kmPerPx;

  const roadPathD = useMemo(() => {
    return graph.edges
      .map(
        (edge) =>
          `M ${edge.x1.toFixed(2)} ${edge.y1.toFixed(2)} L ${edge.x2.toFixed(2)} ${edge.y2.toFixed(
            2,
          )}`,
      )
      .join(" ");
  }, [graph]);

  const bounds = useMemo(() => {
    const xs = graph.edges.flatMap((e) => [e.x1, e.x2]);
    const ys = graph.edges.flatMap((e) => [e.y1, e.y2]);

    return {
      minX: Math.min(...xs),
      maxX: Math.max(...xs),
      minY: Math.min(...ys),
      maxY: Math.max(...ys),
    };
  }, [graph]);

  const baseMapScale = useMemo(() => {
    const w = Math.max(bounds.maxX - bounds.minX, 1);
    const h = Math.max(bounds.maxY - bounds.minY, 1);
    return Math.min(WIDTH / w, HEIGHT / h) * 1.22;
  }, [bounds]);

  const mapCenterX = (bounds.minX + bounds.maxX) / 2;
  const mapCenterY = (bounds.minY + bounds.maxY) / 2;

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const simTimeRef = useRef(0);
  const rngRef = useRef(mulberry32(45));
  const didMountTaxiCountRef = useRef(false);
  const stopAfterTickRef = useRef(false);
  const demoPredictedProgressRef = useRef(0);

  const demandScheduleRef = useRef<Map<number, DemandRecord[]>>(new Map());
  const demandLoadedRef = useRef(false);
  const [demandLoaded, setDemandLoaded] = useState(false);

  const [isDragging, setIsDragging] = useState(false);
  const dragRef = useRef<{
    startClientX: number;
    startClientY: number;
    startOffsetX: number;
    startOffsetY: number;
  } | null>(null);

  const pinchRef = useRef<{
    distance: number;
    zoom: number;
  } | null>(null);

  const panXRef = useRef(0);
  const panYRef = useRef(0);
  const zoomRef = useRef(DEFAULT_ZOOM);

  const [panX, setPanX] = useState(0);
  const [panY, setPanY] = useState(0);

  const [showParameters, setShowParameters] = useState(false);
  const [running, setRunning] = useState(true);
  const [taxiCount, setTaxiCount] = useState(DEFAULT_TAXI_COUNT);
  const [taxiSpeedKmh, setTaxiSpeedKmh] = useState(20);
  const [zoom, setZoom] = useState(DEFAULT_ZOOM);
  const [maxMatchDist, setMaxMatchDist] = useState<number>(500 / metersPerPx);
  const [matchedStay, setMatchedStay] = useState(20 * 60);
  const [passengerTTL, setPassengerTTL] = useState(10 * 60);
  const [showIdleTaxis, setShowIdleTaxis] = useState(true);
  const [showOccupiedTaxis, setShowOccupiedTaxis] = useState(true);
  const [showPassengers, setShowPassengers] = useState(true);
  const [showCalibrationCircle, setShowCalibrationCircle] = useState(false);

  const [flowFieldEnabled, setFlowFieldEnabled] = useState(true);
  const [showVectorField, setShowVectorField] = useState(true);
  const [demoMode, setDemoMode] = useState(false);

  const [attractionStrength, setAttractionStrength] = useState(MAX_ATTRACTION);
  const [fieldSpreadMeters, setFieldSpreadMeters] = useState(100);

  const [speedMultiplier, setSpeedMultiplier] = useState(1);

  const secondsPerTick = BASE_SECONDS_PER_TICK * speedMultiplier;
  const currentDtMs = speedMultiplier === 1 ? REALTIME_DT_MS : FAST_DT_MS;

  const [probHistory, setProbHistory] = useState<ProbPoint[]>([
    { time: 0, avgProb: 0, passengerProb: 0 },
  ]);

  const [awtHistory, setAwtHistory] = useState<AwtPoint[]>([
    { time: 0, driverAwt: 0, passengerAwt: 0 },
  ]);

  const awtStatsRef = useRef({
    driverWaitSum: 0,
    driverWaitCount: 0,
    passengerWaitSum: 0,
    passengerWaitCount: 0,
  });

  function createInitialNormalState(rng: () => number): SimState {
    return {
      taxis: createCompetingTaxis(graph, rng, DEFAULT_TAXI_COUNT),
      passengers: [],
      nextPassengerId: 0,
      demoPathInfo: null,
    };
  }

  function createInitialDemoState(rng: () => number): SimState {
    const taxi = createRandomTaxi(graph, 0, rng);
    const passenger = createRandomDemoPassenger(graph, 0, passengerTTL, rng, bounds);
    const demoPathInfo = predictDemoPath(
      taxi,
      passenger,
      graph,
      attractionStrength,
      fieldSpreadMeters / metersPerPx,
      maxMatchDist,
    );
    demoPredictedProgressRef.current = 0;

    return {
      taxis: [taxi],
      passengers: [passenger],
      nextPassengerId: 1,
      demoPathInfo,
    };
  }

  const [sim, setSim] = useState<SimState>(() => {
    const rng = mulberry32(45);
    return createInitialNormalState(rng);
  });

  useEffect(() => {
    let cancelled = false;

    async function loadDemand() {
      try {
        const res = await fetch(DEMAND_JSON_URL);
        if (!res.ok) {
          throw new Error(`Failed to load demand JSON: ${res.status}`);
        }

        const rows = (await res.json()) as DemandRecord[];
        const grouped = new Map<number, DemandRecord[]>();

        for (const row of rows) {
          if (!grouped.has(row.start_timeslot)) grouped.set(row.start_timeslot, []);
          grouped.get(row.start_timeslot)!.push(row);
        }

        if (!cancelled) {
          demandScheduleRef.current = grouped;
          demandLoadedRef.current = true;
          setDemandLoaded(true);
        }
      } catch (err) {
        console.error(err);
      }
    }

    loadDemand();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    panXRef.current = panX;
  }, [panX]);

  useEffect(() => {
    panYRef.current = panY;
  }, [panY]);

  useEffect(() => {
    zoomRef.current = zoom;
  }, [zoom]);

  const mapScale = baseMapScale * zoom;
  const spreadPx = fieldSpreadMeters / metersPerPx;

  const vectorFieldOverlay =
    flowFieldEnabled && showVectorField && sim.passengers.length > 0
      ? buildFlowFieldOverlay(graph, sim.passengers, attractionStrength, spreadPx, mapScale)
      : null;

  const idleCount = sim.taxis.filter((t) => t.status === "idle").length;
  const occupiedCount = sim.taxis.filter((t) => t.status === "matched").length;
  const passengerCount = sim.passengers.length;

  const displayedDriverAP =
    probHistory.length > 0 ? probHistory[probHistory.length - 1].avgProb : 0;
  const displayedPassengerAP =
    probHistory.length > 0 ? probHistory[probHistory.length - 1].passengerProb : 0;

  function kmhToPxPerStep(kmh: number, secondsThisStep: number) {
    return (kmh * (secondsThisStep / 3600)) / Math.max(kmPerPx, 1e-12);
  }

  function formatMetersFromPxCalibrated(px: number) {
    return `${Math.round(px * metersPerPx)} m`;
  }

  function formatKmh(kmh: number) {
    return `${Math.round(kmh)} km/h`;
  }

  function applyZoomFromCenter(nextZoomRaw: number) {
    const nextZoom = clamp(nextZoomRaw, MIN_ZOOM, MAX_ZOOM);
    if (Math.abs(nextZoom - zoomRef.current) < 1e-6) return;
    zoomRef.current = nextZoom;
    setZoom(nextZoom);
  }

  function resetSimulation(nextDemoMode: boolean = demoMode) {
    simTimeRef.current = 0;
    stopAfterTickRef.current = false;
    rngRef.current = mulberry32(45 + Math.floor(Math.random() * 100000));
    demoPredictedProgressRef.current = 0;

    setSim(
      nextDemoMode
        ? createInitialDemoState(rngRef.current)
        : {
            taxis: createCompetingTaxis(graph, rngRef.current, taxiCount),
            passengers: [],
            nextPassengerId: 0,
            demoPathInfo: null,
          },
    );

    awtStatsRef.current = {
      driverWaitSum: 0,
      driverWaitCount: 0,
      passengerWaitSum: 0,
      passengerWaitCount: 0,
    };

    setProbHistory([{ time: 0, avgProb: 0, passengerProb: 0 }]);
    setAwtHistory([{ time: 0, driverAwt: 0, passengerAwt: 0 }]);
    setPanX(0);
    setPanY(0);
    setZoom(DEFAULT_ZOOM);
    setShowCalibrationCircle(false);
    setSpeedMultiplier(nextDemoMode ? 100 : 1);
    setRunning(true);
    panXRef.current = 0;
    panYRef.current = 0;
    zoomRef.current = DEFAULT_ZOOM;
  }

  function enableDemoMode() {
    setDemoMode(true);
    setSpeedMultiplier(100);
    resetSimulation(true);
  }

  function disableDemoMode() {
    setDemoMode(false);
    resetSimulation(false);
  }

  function resetView() {
    setZoom(DEFAULT_ZOOM);
    setPanX(0);
    setPanY(0);
    zoomRef.current = DEFAULT_ZOOM;
    panXRef.current = 0;
    panYRef.current = 0;
  }

  function applyMatches(
    taxis: Taxi[],
    passengers: Passenger[],
    rng: () => number,
    matchedStayValue: number,
  ) {
    const matches = probabilityAwareMatch(taxis, passengers, maxMatchDist, rng);

    const matchedPassengerIds = new Set(matches.map((m) => m.passengerId));
    const matchedTaxiIds = new Set(matches.map((m) => m.taxiId));

    for (const taxi of taxis) {
      if (matchedTaxiIds.has(taxi.id) && taxi.status === "idle") {
        awtStatsRef.current.driverWaitSum += taxi.idleAge;
        awtStatsRef.current.driverWaitCount += 1;
        taxi.status = "matched";
        taxi.matchedTimer = matchedStayValue;
        taxi.idleAge = 0;
        taxi.memory.stuckTicks = 0;
      }
    }

    for (const passenger of passengers) {
      if (matchedPassengerIds.has(passenger.id)) {
        awtStatsRef.current.passengerWaitSum += passenger.age;
        awtStatsRef.current.passengerWaitCount += 1;
      }
    }

    return {
      remainingPassengers: passengers.filter((p) => !matchedPassengerIds.has(p.id)),
      matchedTaxiCount: matchedTaxiIds.size,
      matchedPassengerCount: matchedPassengerIds.size,
    };
  }

  useEffect(() => {
    if (!showCalibrationCircle) return;
    const timer = setTimeout(() => setShowCalibrationCircle(false), 5000);
    return () => clearTimeout(timer);
  }, [showCalibrationCircle]);

  useEffect(() => {
    if (!didMountTaxiCountRef.current) {
      didMountTaxiCountRef.current = true;
      return;
    }
    if (!demoMode) resetSimulation(false);
  }, [taxiCount]);

  useEffect(() => {
    if ((!running || !demandLoaded) && !demoMode) {
      if (intervalRef.current) clearInterval(intervalRef.current);
      intervalRef.current = null;
      return;
    }

    if (!running && demoMode) {
      if (intervalRef.current) clearInterval(intervalRef.current);
      intervalRef.current = null;
      return;
    }

    intervalRef.current = setInterval(() => {
      const rng = rngRef.current;
      const speedPxPerStep = kmhToPxPerStep(taxiSpeedKmh, secondsPerTick);
      stopAfterTickRef.current = false;

      setSim((prev) => {
        const taxis = prev.taxis.map((taxi) => ({
          ...taxi,
          memory: { ...taxi.memory },
        }));

        let passengers = prev.passengers.map((passenger) => ({
          ...passenger,
          age: passenger.age + secondsPerTick,
        }));

        let demoPathInfo = prev.demoPathInfo
          ? {
              ...prev.demoPathInfo,
              predictedEdgeIds: [...prev.demoPathInfo.predictedEdgeIds],
              predictedPathPoints: [...prev.demoPathInfo.predictedPathPoints],
              deviatedEdgeIds: [...prev.demoPathInfo.deviatedEdgeIds],
            }
          : null;

        if (!demoMode) {
          passengers = passengers.filter((passenger) => passenger.age < passenger.ttl);
        }

        let nextPassengerId = prev.nextPassengerId;
        const prevTime = simTimeRef.current;
        const nextTime = prevTime + secondsPerTick;

        if (!demoMode) {
          const startSlot = Math.floor(prevTime) + 1;
          const endSlot = Math.floor(nextTime);

          for (let slot = startSlot; slot <= endSlot; slot++) {
            const arrivals = demandScheduleRef.current.get(slot) ?? [];
            for (const record of arrivals) {
              const passenger = createPassengerFromRecord(graph, nextPassengerId, passengerTTL, record);
              if (passenger) {
                passengers.push(passenger);
                nextPassengerId += 1;
              }
            }
          }
        }

        for (const taxi of taxis) {
          if (taxi.status === "matched") {
            taxi.matchedTimer -= secondsPerTick;
            if (taxi.matchedTimer <= 0) {
              taxi.status = "idle";
              taxi.matchedTimer = 0;
              taxi.idleAge = 0;
              taxi.memory.stuckTicks = 0;
            }
          } else {
            taxi.idleAge += secondsPerTick;
          }
        }

        const idleBeforeMatch = taxis.filter((t) => t.status === "idle").length;
        const passengersBeforeMatch = passengers.length;
        const hasActiveDemand = passengers.length > 0;

        for (const taxi of taxis) {
          const traversedNewEdgeId = moveTaxiFieldBased(
            taxi,
            graph,
            speedPxPerStep,
            rng,
            passengers,
            attractionStrength,
            spreadPx,
            flowFieldEnabled,
          );

          if (demoMode && taxi.status === "idle" && traversedNewEdgeId) {
            demoPathInfo = updateDemoDeviationInfo(
              demoPathInfo,
              traversedNewEdgeId,
              demoPredictedProgressRef,
            );
          }

          updateStuckStatusAndRespawnIfNeeded(taxi, graph, rng, hasActiveDemand);

          if (demoMode && taxi.status === "idle") {
            const movedToUnexpectedEdge =
              taxi.edgeId !== taxi.memory.prevEdgeId &&
              Math.abs(taxi.s - (taxi.memory.prevS ?? 0)) > DEMO_PATH_CAPTURE_EPS;

            if (movedToUnexpectedEdge && taxi.edgeId) {
              demoPathInfo = updateDemoDeviationInfo(
                demoPathInfo,
                taxi.edgeId,
                demoPredictedProgressRef,
              );
            }
          }

          taxi.memory.prevX = taxi.x;
          taxi.memory.prevY = taxi.y;
          taxi.memory.prevS = taxi.s;
          taxi.memory.prevEdgeId = taxi.edgeId;
        }

        const matchPass = applyMatches(taxis, passengers, rng, matchedStay);
        const matchedTaxiCount = matchPass.matchedTaxiCount;
        const matchedPassengerCount = matchPass.matchedPassengerCount;

        passengers = matchPass.remainingPassengers;

        if (demoMode && matchedPassengerCount > 0) {
          passengers = [];
          stopAfterTickRef.current = true;
        }

        const driverAP = idleBeforeMatch > 0 ? matchedTaxiCount / idleBeforeMatch : 0;
        const passengerAP =
          passengersBeforeMatch > 0 ? matchedPassengerCount / passengersBeforeMatch : 0;

        simTimeRef.current = nextTime;

        const driverAwt =
          awtStatsRef.current.driverWaitCount > 0
            ? secondsToMinutes(
                awtStatsRef.current.driverWaitSum / awtStatsRef.current.driverWaitCount,
              )
            : 0;

        const passengerAwt =
          awtStatsRef.current.passengerWaitCount > 0
            ? secondsToMinutes(
                awtStatsRef.current.passengerWaitSum / awtStatsRef.current.passengerWaitCount,
              )
            : 0;

        setProbHistory((prevHistory) =>
          [
            ...prevHistory,
            {
              time: simTimeRef.current,
              avgProb: Number(driverAP.toFixed(4)),
              passengerProb: Number(passengerAP.toFixed(4)),
            },
          ].slice(-200),
        );

        setAwtHistory((prevHistory) =>
          [
            ...prevHistory,
            {
              time: simTimeRef.current,
              driverAwt: Number(driverAwt.toFixed(3)),
              passengerAwt: Number(passengerAwt.toFixed(3)),
            },
          ].slice(-200),
        );

        return { taxis, passengers, nextPassengerId, demoPathInfo };
      });

      if (stopAfterTickRef.current) setRunning(false);
    }, currentDtMs);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      intervalRef.current = null;
    };
  }, [
    running,
    demandLoaded,
    demoMode,
    graph,
    matchedStay,
    taxiSpeedKmh,
    secondsPerTick,
    attractionStrength,
    spreadPx,
    flowFieldEnabled,
    maxMatchDist,
    kmPerPx,
    passengerTTL,
    currentDtMs,
  ]);

  useEffect(() => {
    function onMouseMove(e: MouseEvent) {
      if (!dragRef.current) return;

      const dx = e.clientX - dragRef.current.startClientX;
      const dy = e.clientY - dragRef.current.startClientY;

      const nextPanX = dragRef.current.startOffsetX + dx;
      const nextPanY = dragRef.current.startOffsetY + dy;

      panXRef.current = nextPanX;
      panYRef.current = nextPanY;
      setPanX(nextPanX);
      setPanY(nextPanY);
    }

    function onMouseUp() {
      dragRef.current = null;
      setIsDragging(false);
    }

    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);

    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
    };
  }, []);

  function handleMouseDown(e: React.MouseEvent<HTMLDivElement>) {
    dragRef.current = {
      startClientX: e.clientX,
      startClientY: e.clientY,
      startOffsetX: panXRef.current,
      startOffsetY: panYRef.current,
    };
    setIsDragging(true);
  }

  function handleWheel(e: React.WheelEvent<HTMLDivElement>) {
    e.preventDefault();
    e.stopPropagation();
    const factor = Math.exp(-e.deltaY * 0.0015);
    applyZoomFromCenter(zoomRef.current * factor);
  }

  function getTouchDistance(t1: React.Touch, t2: React.Touch) {
    return Math.hypot(t2.clientX - t1.clientX, t2.clientY - t1.clientY);
  }

  function handleTouchStart(e: React.TouchEvent<HTMLDivElement>) {
    if (e.touches.length === 1) {
      const t = e.touches[0];
      dragRef.current = {
        startClientX: t.clientX,
        startClientY: t.clientY,
        startOffsetX: panXRef.current,
        startOffsetY: panYRef.current,
      };
      setIsDragging(true);
      pinchRef.current = null;
      return;
    }

    if (e.touches.length === 2) {
      const t1 = e.touches[0];
      const t2 = e.touches[1];
      dragRef.current = null;
      setIsDragging(false);
      pinchRef.current = {
        distance: getTouchDistance(t1, t2),
        zoom: zoomRef.current,
      };
    }
  }

  function handleTouchMove(e: React.TouchEvent<HTMLDivElement>) {
    if (e.touches.length === 1 && dragRef.current) {
      const t = e.touches[0];
      const dx = t.clientX - dragRef.current.startClientX;
      const dy = t.clientY - dragRef.current.startClientY;

      const nextPanX = dragRef.current.startOffsetX + dx;
      const nextPanY = dragRef.current.startOffsetY + dy;

      panXRef.current = nextPanX;
      panYRef.current = nextPanY;
      setPanX(nextPanX);
      setPanY(nextPanY);
      return;
    }

    if (e.touches.length === 2 && pinchRef.current) {
      e.preventDefault();

      const t1 = e.touches[0];
      const t2 = e.touches[1];
      const distance = getTouchDistance(t1, t2);
      const ratio = distance / Math.max(1, pinchRef.current.distance);
      applyZoomFromCenter(pinchRef.current.zoom * ratio);
    }
  }

  function handleTouchEnd() {
    if (dragRef.current && !pinchRef.current) setIsDragging(false);
    dragRef.current = null;
    pinchRef.current = null;
  }

  const demoPredictedPolyline =
    demoMode && sim.demoPathInfo && sim.demoPathInfo.predictedPathPoints.length >= 2
      ? sim.demoPathInfo.predictedPathPoints
          .map((p, idx) => `${idx === 0 ? "M" : "L"} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`)
          .join(" ")
      : "";

  const demoDeviatedEdges =
    demoMode && sim.demoPathInfo
      ? sim.demoPathInfo.deviatedEdgeIds
          .map((edgeId) => graph.edgeMap[edgeId])
          .filter((edge): edge is Edge => !!edge)
      : [];

  return (
    <div className="min-h-screen w-full bg-black p-4 text-neutral-200 md:p-6">
      <div className="mx-auto grid max-w-[2200px] gap-4 lg:grid-cols-[420px_1fr]">
        <div className="space-y-4">
          <Card className="border-neutral-800 bg-neutral-900/95 shadow-[0_0_0_1px_rgba(255,255,255,0.02)]">
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <CardTitle className="text-xl font-semibold tracking-tight text-zinc-100">
                  Allocation Probability (AP)
                </CardTitle>
              </div>
            </CardHeader>

            <CardContent>
              <div className="rounded-2xl border border-zinc-800 bg-gradient-to-b from-zinc-950 to-zinc-900 p-2">
                <div className="mb-3 flex flex-wrap items-center gap-3">
                  <div className="flex flex-wrap gap-2">
                    <div className="rounded-full border border-amber-400/30 bg-amber-400/10 px-3 py-1 text-xs font-medium text-amber-300">
                      Supply AP: {displayedDriverAP.toFixed(4)}
                    </div>
                    <div className="rounded-full border border-sky-400/30 bg-sky-400/10 px-3 py-1 text-xs font-medium text-sky-300">
                      Demand AP: {displayedPassengerAP.toFixed(4)}
                    </div>
                    {demoMode && (
                      <div className="rounded-full border border-rose-300/30 bg-rose-400/10 px-3 py-1 text-xs font-medium text-rose-300">
                        Demo mode
                      </div>
                    )}
                  </div>
                </div>

                <div className="h-44 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={probHistory} margin={{ top: 8, right: 16, left: 4, bottom: 4 }}>
                      <CartesianGrid stroke="#27272a" strokeDasharray="4 4" vertical={false} />
                      <XAxis
                        dataKey="time"
                        stroke="#71717a"
                        tickLine={false}
                        axisLine={{ stroke: "#3f3f46" }}
                        tickFormatter={formatSimTime}
                        fontSize={12}
                        dy={6}
                      />
                      <YAxis
                        yAxisId="right"
                        orientation="right"
                        domain={[0, 1]}
                        stroke="#71717a"
                        tickLine={false}
                        axisLine={false}
                        tickFormatter={formatProbTick}
                        fontSize={12}
                        dx={6}
                      />
                      <Tooltip
                        cursor={{ stroke: "#52525b", strokeDasharray: "3 3" }}
                        content={<AllocationTooltip />}
                      />
                      <Line
                        yAxisId="right"
                        type="monotone"
                        dataKey="avgProb"
                        stroke="#fbbf24"
                        strokeWidth={3}
                        dot={false}
                        activeDot={{ r: 4, strokeWidth: 0 }}
                        isAnimationActive={false}
                      />
                      <Line
                        yAxisId="right"
                        type="monotone"
                        dataKey="passengerProb"
                        stroke="#38bdf8"
                        strokeWidth={3}
                        dot={false}
                        activeDot={{ r: 4, strokeWidth: 0 }}
                        isAnimationActive={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card className="border-neutral-800 bg-neutral-900/95 shadow-[0_0_0_1px_rgba(255,255,255,0.02)]">
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <CardTitle className="text-xl font-semibold tracking-tight text-zinc-100">
                  Average Waiting Time (AWT)
                </CardTitle>
              </div>
            </CardHeader>

            <CardContent>
              <div className="rounded-2xl border border-zinc-800 bg-gradient-to-b from-zinc-950 to-zinc-900 p-2">
                <div className="mb-3 flex flex-wrap items-center gap-3">
                  <div className="flex flex-wrap gap-2">
                    <div className="rounded-full border border-amber-400/30 bg-amber-400/10 px-3 py-1 text-xs font-medium text-amber-300">
                      Supply AWT:{" "}
                      {awtHistory.length > 0
                        ? awtHistory[awtHistory.length - 1].driverAwt.toFixed(2)
                        : "0.00"}{" "}
                      min
                    </div>
                    <div className="rounded-full border border-sky-400/30 bg-sky-400/10 px-3 py-1 text-xs font-medium text-sky-300">
                      Demand AWT:{" "}
                      {awtHistory.length > 0
                        ? awtHistory[awtHistory.length - 1].passengerAwt.toFixed(2)
                        : "0.00"}{" "}
                      min
                    </div>
                  </div>
                </div>

                <div className="h-44 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={awtHistory} margin={{ top: 8, right: 16, left: 4, bottom: 4 }}>
                      <CartesianGrid stroke="#27272a" strokeDasharray="4 4" vertical={false} />
                      <XAxis
                        dataKey="time"
                        stroke="#71717a"
                        tickLine={false}
                        axisLine={{ stroke: "#3f3f46" }}
                        tickFormatter={formatSimTime}
                        fontSize={12}
                        dy={6}
                      />
                      <YAxis
                        yAxisId="right"
                        orientation="right"
                        stroke="#71717a"
                        tickLine={false}
                        axisLine={false}
                        tickFormatter={formatAwtTick}
                        fontSize={12}
                        dx={6}
                      />
                      <Tooltip
                        cursor={{ stroke: "#52525b", strokeDasharray: "3 3" }}
                        content={<AwtTooltip />}
                      />
                      <Line
                        yAxisId="right"
                        type="monotone"
                        dataKey="driverAwt"
                        stroke="#fbbf24"
                        strokeWidth={3}
                        dot={false}
                        activeDot={{ r: 4, strokeWidth: 0 }}
                        isAnimationActive={false}
                      />
                      <Line
                        yAxisId="right"
                        type="monotone"
                        dataKey="passengerAwt"
                        stroke="#38bdf8"
                        strokeWidth={3}
                        dot={false}
                        activeDot={{ r: 4, strokeWidth: 0 }}
                        isAnimationActive={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card className="border-neutral-800 bg-neutral-900">
            <CardHeader className="flex items-center">
              <div className="flex flex-wrap items-center gap-3">
                <Button
                  size="sm"
                  onClick={() => resetSimulation()}
                  className="rounded-lg border border-zinc-500 bg-zinc-700 px-3 py-2 text-lg text-white hover:bg-zinc-600"
                  title="Reset simulation"
                >
                  ↺
                </Button>

                <Button
                  size="sm"
                  onClick={() => setRunning((r) => !r)}
                  className="rounded-lg border border-zinc-500 bg-zinc-700 px-3 py-2 text-lg text-white hover:bg-zinc-600"
                  title={running ? "Pause" : "Resume"}
                >
                  {running ? "❚❚" : "▶"}
                </Button>

                <Button
                  size="sm"
                  onClick={resetView}
                  className="rounded-md border border-sky-300 bg-sky-400 px-4 py-2 text-black hover:bg-sky-300"
                >
                  Reset view
                </Button>

                <Button
                  size="sm"
                  onClick={() => setShowParameters((v) => !v)}
                  className="rounded-md border border-amber-300 bg-amber-400 px-4 py-2 text-black hover:bg-amber-300"
                >
                  Parameters
                </Button>

                {demoMode && (
                  <Button
                    size="sm"
                    onClick={disableDemoMode}
                    className="rounded-md border border-zinc-500 bg-zinc-700 px-4 py-2 text-white hover:bg-zinc-600"
                  >
                    Exit Demo
                  </Button>
                )}
              </div>
            </CardHeader>

            {showParameters && (
              <CardContent className="space-y-4">
                {!demoMode && (
                  <SliderBlock
                    label={`Number of taxis: ${taxiCount}`}
                    value={taxiCount}
                    min={100}
                    max={1000}
                    step={100}
                    onChange={(v) => setTaxiCount(Math.round(v / 100) * 100)}
                  />
                )}

                <SliderBlock
                  label={`Taxi speed: ${formatKmh(taxiSpeedKmh)} (0–50 km/h)`}
                  value={taxiSpeedKmh}
                  min={0}
                  max={50}
                  step={1}
                  onChange={setTaxiSpeedKmh}
                />

                <SliderBlock
                  label={`Match radius: ${formatMetersFromPxCalibrated(maxMatchDist)} (100–4000 m)`}
                  value={maxMatchDist}
                  min={100 / metersPerPx}
                  max={4000 / metersPerPx}
                  step={100 / metersPerPx}
                  onChange={setMaxMatchDist}
                />

                {!demoMode && (
                  <>
                    <SliderBlock
                      label={`Occupied trip time: ${formatMinutesFromSeconds(matchedStay)} (10–40 min)`}
                      value={matchedStay}
                      min={10 * 60}
                      max={40 * 60}
                      step={10 * 60}
                      onChange={setMatchedStay}
                    />

                    <SliderBlock
                      label={`Passenger patience: ${formatMinutesFromSeconds(passengerTTL)} (1–20 min)`}
                      value={passengerTTL}
                      min={1 * 60}
                      max={20 * 60}
                      step={1 * 60}
                      onChange={setPassengerTTL}
                    />
                  </>
                )}

                <SliderBlock
                  label={`Demand attraction: ${attractionStrength.toFixed(2)}`}
                  value={attractionStrength}
                  min={MIN_ATTRACTION}
                  max={MAX_ATTRACTION}
                  step={0.05}
                  onChange={setAttractionStrength}
                />

                <SliderBlock
                  label={`Field spread: ${fieldSpreadMeters.toFixed(0)} m`}
                  value={fieldSpreadMeters}
                  min={100}
                  max={4000}
                  step={50}
                  onChange={setFieldSpreadMeters}
                />
              </CardContent>
            )}
          </Card>
        </div>

        <Card className="overflow-hidden border-neutral-800 bg-neutral-900">
          <CardHeader>
            <div className="mx-auto flex w-[96%] flex-col gap-4">
              <div className="flex flex-wrap items-center justify-center gap-3 text-sm text-zinc-300">
                <LegendButton
                  color="#fbbf24"
                  label={`Idle taxi (${idleCount})`}
                  active={showIdleTaxis}
                  onClick={() => setShowIdleTaxis((v) => !v)}
                />
                <LegendButton
                  color="#22c55e"
                  label={`Occupied taxi (${occupiedCount})`}
                  active={showOccupiedTaxis}
                  onClick={() => setShowOccupiedTaxis((v) => !v)}
                />
                <LegendButton
                  color="#38bdf8"
                  label={`Passenger (${passengerCount})`}
                  active={showPassengers}
                  onClick={() => setShowPassengers((v) => !v)}
                />
              </div>
            </div>
          </CardHeader>

          <CardContent>
            <div
              ref={mapContainerRef}
              className={[
                "relative overflow-hidden rounded-2xl border border-zinc-800 bg-black",
                isDragging ? "cursor-grabbing" : "cursor-grab",
              ].join(" ")}
              onMouseDown={handleMouseDown}
              onWheel={handleWheel}
              onTouchStart={handleTouchStart}
              onTouchMove={handleTouchMove}
              onTouchEnd={handleTouchEnd}
              onTouchCancel={handleTouchEnd}
              style={{ touchAction: "none" }}
            >
              <div className="absolute left-3 top-3 z-20 flex items-center gap-1 rounded-xl border border-zinc-700 bg-zinc-950/90 p-1 shadow-xl backdrop-blur-sm">
                {[1, 10, 20, 50, 100].map((value) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => setSpeedMultiplier(value)}
                    className={`rounded-lg px-3 py-1.5 text-xs font-medium transition ${
                      speedMultiplier === value
                        ? "bg-cyan-400 text-black"
                        : "bg-zinc-800 text-zinc-200 hover:bg-zinc-700"
                    }`}
                  >
                    {value === 1 ? "Real-time" : `${value}x`}
                  </button>
                ))}
              </div>

              <div className="absolute right-3 top-3 z-20 flex items-center gap-1 rounded-xl border border-zinc-700 bg-zinc-950/90 p-1 shadow-xl backdrop-blur-sm">
                <button
                  type="button"
                  onClick={() => setFlowFieldEnabled((v) => !v)}
                  className={`rounded-lg px-3 py-1.5 text-xs font-medium transition ${
                    flowFieldEnabled
                      ? "bg-cyan-400 text-black"
                      : "bg-zinc-800 text-zinc-200 hover:bg-zinc-700"
                  }`}
                >
                  {flowFieldEnabled ? "Guidance ON" : "Guidance OFF"}
                </button>

                <button
                  type="button"
                  onClick={() => setShowVectorField((v) => !v)}
                  className={`rounded-lg px-3 py-1.5 text-xs font-medium transition ${
                    showVectorField
                      ? "bg-cyan-400 text-black"
                      : "bg-zinc-800 text-zinc-200 hover:bg-zinc-700"
                  }`}
                >
                  {showVectorField ? "Vectors shown" : "Vectors hidden"}
                </button>

                <button
                  type="button"
                  onClick={enableDemoMode}
                  className="rounded-lg border border-rose-300 bg-rose-300 px-3 py-1.5 text-xs font-medium text-black transition hover:bg-rose-200"
                >
                  Demo
                </button>
              </div>

              {!demandLoaded && !demoMode && (
                <div className="absolute inset-x-0 top-16 z-20 mx-auto w-fit rounded-lg border border-amber-400/40 bg-amber-400/10 px-3 py-1.5 text-xs text-amber-200">
                  Loading demand file...
                </div>
              )}

              {demoMode && !running && sim.passengers.length === 0 && (
                <div className="absolute inset-x-0 top-16 z-20 mx-auto w-fit rounded-lg border border-rose-400/40 bg-rose-400/10 px-3 py-1.5 text-xs text-rose-200">
                  Demo completed. Press reset for a new taxi and passenger.
                </div>
              )}

              <svg
                viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
                className="block w-full select-none"
                style={{ height: "1200px" }}
              >
                <rect x="0" y="0" width={WIDTH} height={HEIGHT} fill="black" />

                <g
                  transform={`translate(${WIDTH / 2 + panX}, ${HEIGHT / 2 + panY}) scale(${mapScale}) translate(${-mapCenterX}, ${-mapCenterY})`}
                >
                  <path
                    d={roadPathD}
                    stroke="#3f3f46"
                    strokeWidth={0.7 / mapScale}
                    opacity="0.8"
                    fill="none"
                    strokeLinecap="round"
                  />

                  {demoMode && sim.demoPathInfo && demoPredictedPolyline && (
                    <path
                      d={demoPredictedPolyline}
                      fill="none"
                      stroke="#fda4af"
                      strokeWidth={1.8 / mapScale}
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      opacity={0.95}
                    />
                  )}

                  {demoMode &&
                    demoDeviatedEdges.map((edge) => (
                      <line
                        key={`deviation-${edge.id}`}
                        x1={edge.x1}
                        y1={edge.y1}
                        x2={edge.x2}
                        y2={edge.y2}
                        stroke="#22c55e"
                        strokeWidth={1.8 / mapScale}
                        strokeLinecap="round"
                        opacity={0.95}
                      />
                    ))}

                  {vectorFieldOverlay}

                  {showPassengers &&
                    sim.passengers.map((passenger) => (
                      <g key={passenger.id}>
                        <circle
                          cx={passenger.x}
                          cy={passenger.y}
                          r={maxMatchDist}
                          fill="#38bdf8"
                          opacity={0.18}
                          stroke="#38bdf8"
                          strokeWidth={0.6 / mapScale}
                        />
                        <circle
                          cx={passenger.x}
                          cy={passenger.y}
                          r={3.5 / mapScale}
                          fill="#38bdf8"
                          stroke="white"
                          strokeWidth={1.5 / mapScale}
                          opacity={1}
                        />
                      </g>
                    ))}

                  {sim.taxis.map((taxi) => {
                    const isIdle = taxi.status === "idle";
                    if ((isIdle && !showIdleTaxis) || (!isIdle && !showOccupiedTaxis)) return null;

                    return (
                      <circle
                        key={taxi.id}
                        cx={taxi.x}
                        cy={taxi.y}
                        r={3.5 / mapScale}
                        fill={isIdle ? "#fbbf24" : "#22c55e"}
                        stroke="black"
                        strokeWidth={0.7 / mapScale}
                      />
                    );
                  })}
                </g>
              </svg>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}