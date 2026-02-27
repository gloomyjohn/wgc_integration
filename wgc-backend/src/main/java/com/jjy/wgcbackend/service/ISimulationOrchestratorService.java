package com.jjy.wgcbackend.service;
/**
 * 模拟器总控调度服务接口
 */
public interface ISimulationOrchestratorService {
    /**
     * 启动演示模拟器流程
     *
     * @param driverLat    主司机初始纬度
     * @param driverLng    主司机初始经度
     * @param passengerLat 乘客纬度
     * @param passengerLng 乘客经度
     */
    void startDemoSimulation(double driverLat, double driverLng, double passengerLat, double passengerLng);
}
