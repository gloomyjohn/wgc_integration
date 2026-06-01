package com.jjy.wgcbackend.service;

import com.jjy.wgcbackend.entitiy.dto.FlowFieldRouteResultDTO;
import com.jjy.wgcbackend.entitiy.vo.FlowFieldVO;

public interface IFlowFieldService {
    FlowFieldVO calculateCurrentFlowField();

    FlowFieldRouteResultDTO buildRouteToPassenger(double[] driverLocation, double[] passengerLocation);
}
