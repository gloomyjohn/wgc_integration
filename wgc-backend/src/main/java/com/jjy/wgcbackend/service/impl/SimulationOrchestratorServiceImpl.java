package com.jjy.wgcbackend.service.impl;

import com.jjy.wgcbackend.config.RestTemplateConfig;
import com.jjy.wgcbackend.entitiy.po.PathRecommendations;
import com.jjy.wgcbackend.entitiy.po.RideRequests;
import com.jjy.wgcbackend.entitiy.vo.PathRecommendationsVO;
import com.jjy.wgcbackend.handler.SimulationWebSocketHandler;
import com.jjy.wgcbackend.service.IPathRecommendationsService;
import com.jjy.wgcbackend.service.IRideRequestsService;
import com.jjy.wgcbackend.service.ISimulationOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class SimulationOrchestratorServiceImpl implements ISimulationOrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(SimulationOrchestratorServiceImpl.class);
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private IRideRequestsService rideRequestsService;
    @Autowired
    private IPathRecommendationsService pathRecommendationsService;
    @Autowired
    private SimulationWebSocketHandler webSocketHandler;

    // python 引擎地址
    private final String PYTHON_ENGINE = "http://python-engine:8000/match";
    @Override
    public void startDemoSimulation(double driverLat, double driverLng, double passengerLat, double passengerLng) {
        log.info("Start demo simulation, Request received.");

        //
        RideRequests rideRequests = new RideRequests();
        rideRequests.setRequestTime(LocalDateTime.now());
        rideRequests.setStatus("WATTING");
        rideRequestsService.save(rideRequests);
        log.info("√ 订单入库成功，订单号：{}", rideRequests.getRequestId());

        // python engine
        // parameters
        Map<String, Object> pythonRequest = new HashMap<>();

        Map<String, double[]> drivers = new HashMap<>();
        drivers.put("driver1", new double[]{driverLat, driverLng});
        pythonRequest.put("drivers", drivers);

        Map<String , double[]> passengers = new HashMap<>();
        passengers.put("passenger1", new double[]{passengerLat});
        pythonRequest.put("passengers", passengers);

        pythonRequest.put("k", 20);

        try {
            log.info("Requesting MATCH engine...");
            Map<String, Object> pythonResponse = restTemplate.postForObject(PYTHON_ENGINE, pythonRequest, Map.class);
            log.info("Match engine: {}", pythonResponse);

            // 匹配成功，解析结果
            Map<String, String> matches = (Map<String, String>) pythonResponse.get("matches");
            String matchedPassengers = matches.get("driver_1");

            if (matchedPassengers != null) {
                // update status
                rideRequests.setStatus("MATCHED");
                rideRequests.setMatchedDriverId(1L);// 假定司机id为1
                rideRequests.setMatchedAt(LocalDateTime.now());
                rideRequestsService.save(rideRequests);

                // 生成路径规划
                double[] dLoc = new double[]{driverLat, driverLng};
                double[] pLoc = new double[]{passengerLat, passengerLng};
                PathRecommendationsVO pathVO = pathRecommendationsService.getRecommendedPath(dLoc, pLoc);

                // websocket 推送
                Map<String, Object> wsMessage = new HashMap<>();
                wsMessage.put("type", "SIMULATION_START");
                wsMessage.put("status", "SUCCESS");
                wsMessage.put("message", "Driver is ready, simulation start!");
                wsMessage.put("paths", pathVO);

                webSocketHandler.broadcastMessage(wsMessage);
                log.info("Simulation start! Path sent!");
            } else{
                log.warn("WRONG! Can't find proper passenger.");
                rideRequests.setStatus("FAILED");
                rideRequestsService.updateById(rideRequests);
            }
        }
        catch (Exception e) {
            log.error("Python Engine Wrong, " + e.getMessage());
        }
    }
}
