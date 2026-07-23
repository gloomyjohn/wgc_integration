package com.jjy.wgcbackend.service;

import com.jjy.wgcbackend.entitiy.dto.FlowFieldSimulationRequestDTO;
import com.jjy.wgcbackend.entitiy.vo.FlowFieldSimulationControlVO;

public interface IFlowFieldSimulationService {
    FlowFieldSimulationControlVO startSimulation(FlowFieldSimulationRequestDTO request);

    FlowFieldSimulationControlVO previewSimulation(FlowFieldSimulationRequestDTO request);

    FlowFieldSimulationControlVO stopSimulation();

    FlowFieldSimulationControlVO getStatus();
}
