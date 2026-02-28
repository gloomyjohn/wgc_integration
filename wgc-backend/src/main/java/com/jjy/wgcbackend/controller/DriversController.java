package com.jjy.wgcbackend.controller;

import com.jjy.wgcbackend.common.Result;
import com.jjy.wgcbackend.entitiy.dto.DriverDto;
import com.jjy.wgcbackend.entitiy.dto.DriverLocationDTO;
import com.jjy.wgcbackend.entitiy.dto.NodeDTO;
import com.jjy.wgcbackend.entitiy.po.Drivers;
import com.jjy.wgcbackend.mapper.NodesMapper;
import com.jjy.wgcbackend.service.IDriversService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 这张表用于存储系统中每一个运营司机的核心静态信息。在WGC模型中，司机是接受路径推荐和执行运输任务的核心实体。 前端控制器
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@RestController
@RequestMapping("/v1/drivers")
@CrossOrigin(origins = "*")
public class DriversController {

    private static final Logger log = LoggerFactory.getLogger(DriversController.class);
    @Autowired
    private IDriversService driversService;
    @Autowired
    private NodesMapper nodesMapper;

    // driver增删查改
    @PostMapping("/add")
    public Result add(Drivers drivers) {
        if (drivers.getDriverId() == null)
            return Result.fail();
        return driversService.save(drivers) ? Result.success() : Result.fail();
    }
    @DeleteMapping("/delete")
    public Result delete(Integer id) {
        return driversService.removeById(id) ? Result.success() : Result.fail();
    }
    @PostMapping("/update")
    public Result update(@RequestBody Drivers drivers) {

        System.out.println(drivers);
        try {
            // 若table中没有数据
            if (driversService.getById(drivers.getDriverId()) == null)
                return Result.fail("No such driver./n");
            boolean result = driversService.updateById(drivers);
            return result ? Result.success("Update Success") : Result.fail("update faild.\n");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("更新异常：" + e.getMessage());
        }
    }


    @PostMapping("/query")
    public Result query(Drivers drivers) {
        DriverDto driverDto = new DriverDto(drivers.getDriverId(), drivers.getCurrentStatus(), drivers.getOnboardedAt(), drivers.getCreatedAt(), drivers.getUpdatedAt());
        return driversService.getById(driverDto.getDriverId()) != null ? Result.success(driverDto) : Result.fail();
    }

    @GetMapping("/list")
    public Result list() {
        return driversService.list() != null ? Result.success(driversService.list()) : Result.fail();
    }

    @PostMapping("/location/update")
    public Result updateLocation(@RequestBody DriverLocationDTO driverLocationDTO){
        return driversService.updateLocation(driverLocationDTO) ? Result.success("location update success") : Result.fail("location update failed");
    }

    // 请求乘客
    @PostMapping("/requestPassenger")
    public Result requestPassenger(@RequestBody DriverLocationDTO driverLocationDTO){
        // todo
        // 在司机位置周围随机生成一个乘客坐标
        // double[] passengerLocation = new double[]{driverLocationDTO.getLatitude() + Math.random() * 0.01, driverLocationDTO.getLongitude() + Math.random() * 0.01};
        double driverLat = driverLocationDTO.getLatitude();
        double driverLng = driverLocationDTO.getLongitude();
// 核心逻辑：在司机周边 2000 米（2公里）的真实路网范围内，随机抽取 5 个真实的坐标点
        List<NodeDTO> passengerLocations = nodesMapper.getRandomNearbyNodes(driverLng, driverLat, 2000.0, 5);
        if(passengerLocations.isEmpty()){
            return Result.fail("司机所在区域2公里内无可用位置");
        }
        log.info(passengerLocations.toString());
        return Result.success(passengerLocations);

//        return driversService.requestPassenger(driverLocationDTO) ? Result.success("request passenger success") : Result.fail("request passenger failed");
    }

    // 生成竞争司机坐标
    @PostMapping("/requestRivalDrivers")
    public Result requestRivalDriver(@RequestBody DriverLocationDTO driverLocationDTO){
        log.info("接收到主司机坐标，准备在真实路网上生成竞争车: {}", driverLocationDTO);

        double driverLng = driverLocationDTO.getLongitude();
        double driverLat = driverLocationDTO.getLatitude();

        // 核心逻辑：在主司机周围 3000 米（3公里）的真实路网范围内，随机抽取 3 个真实坐标点
        // (竞争车可以比乘客稍微分散一点，所以这里用了 3000 米，你可以根据演示效果自由调整)
        List<NodeDTO> rivalNodes = nodesMapper.getRandomNearbyNodes(driverLng, driverLat, 3000.0, 3);

        if (rivalNodes.isEmpty()) {
            return Result.fail("该司机所在区域 3 公里内没有找到可用的路网节点");
        }

        // 将查询到的真实节点转换为前端原本期望的 JSON 格式
        List<Map<String, Object>> rivalDrivers = new ArrayList<>();
        for (int i = 0; i < rivalNodes.size(); i++) {
            NodeDTO node = rivalNodes.get(i);
            Map<String, Object> driver = new HashMap<>();

            driver.put("id", i + 1); // 保持和原来一样的 id (1, 2, 3)
            driver.put("latitude", node.getLat());   // 填入真实的纬度
            driver.put("longitude", node.getLng());  // 填入真实的经度
            driver.put("nodeId", node.getNodeId());  // 附加真实的节点 ID，前端以后也许用得上

            rivalDrivers.add(driver);
        }

        return Result.success(rivalDrivers);
    }


}
