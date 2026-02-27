package com.jjy.wgcbackend.controller;

import com.jjy.wgcbackend.common.Result;
import com.jjy.wgcbackend.service.impl.SimulationOrchestratorServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {
    @Autowired
    private SimulationOrchestratorServiceImpl orchestratorService;

    @PostMapping("/start")
    public Result startSimulation(@RequestBody Map<String, Double> payload) {
        // 坐标数据
        double driverLat = payload.get("driverLat");
        double driverLng = payload.get("driverLng");
        double passengerLat = payload.get("passengerLat");
        double passengerLng = payload.get("passengerLng");

        orchestratorService.startDemoSimulation(driverLat, driverLng, passengerLat, passengerLng);
        return Result.success("Simulation started.");
    }
}
