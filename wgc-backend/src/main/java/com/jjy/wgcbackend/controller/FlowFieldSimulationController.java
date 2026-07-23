package com.jjy.wgcbackend.controller;

import com.jjy.wgcbackend.common.Result;
import com.jjy.wgcbackend.entitiy.dto.FlowFieldSimulationRequestDTO;
import com.jjy.wgcbackend.service.IFlowFieldSimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/flowFieldSimulation")
@CrossOrigin(origins = "*")
public class FlowFieldSimulationController {

    @Autowired
    @Lazy
    private IFlowFieldSimulationService flowFieldSimulationService;

    @PostMapping("/start")
    public Result start(@RequestBody(required = false) FlowFieldSimulationRequestDTO request) {
        return Result.success(flowFieldSimulationService.startSimulation(request));
    }

    @PostMapping("/preview")
    public Result preview(@RequestBody(required = false) FlowFieldSimulationRequestDTO request) {
        return Result.success(flowFieldSimulationService.previewSimulation(request));
    }

    @PostMapping("/stop")
    public Result stop() {
        return Result.success(flowFieldSimulationService.stopSimulation());
    }

    @GetMapping("/status")
    public Result status() {
        return Result.success(flowFieldSimulationService.getStatus());
    }
}
