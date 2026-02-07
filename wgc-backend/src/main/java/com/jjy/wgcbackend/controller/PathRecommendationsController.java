package com.jjy.wgcbackend.controller;

import com.jjy.wgcbackend.common.Result;
import com.jjy.wgcbackend.entitiy.dto.PathRecommendationsDTO;
import com.jjy.wgcbackend.entitiy.po.PathRecommendations;
import com.jjy.wgcbackend.entitiy.vo.PathRecommendationsVO;
import com.jjy.wgcbackend.service.IPathRecommendationsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * <p>
 * 用于存储系统通过复杂的供需预测计算后，为每一位空闲司机生成的、以最快接到乘客为目标的个性化巡航路径。 前端控制器
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Slf4j
@RestController
@RequestMapping("/v1/pathRecommendations")
@CrossOrigin(origins = "*")
public class PathRecommendationsController {
    @Autowired
    private IPathRecommendationsService pathRecommendationsService;

    @RequestMapping("/add")
    public Result add(PathRecommendations pathRecommendations) {
        return pathRecommendationsService.save(pathRecommendations) ? Result.success() : Result.fail();
    }

    @RequestMapping("/delete")
    public Result delete(Integer id) {
        return pathRecommendationsService.removeById(id) ? Result.success() : Result.fail();
    }

    @RequestMapping("/update")
    public Result update(PathRecommendations pathRecommendations) {
        return pathRecommendationsService.updateById(pathRecommendations) ? Result.success() : Result.fail();
    }



    @RequestMapping("/list")
    public Result list() {
        return pathRecommendationsService.list() != null ? Result.success(pathRecommendationsService.list()) : Result.fail();
    }

    // 接受包括司机id、当前位置的坐标、目的地的坐标
    @PostMapping("/getRec")
    public Result getRec(@RequestBody PathRecommendationsDTO pathRecommendationsDTO) {
        // 使用log.info替代System.out.println，方便测试
        log.info("===================== Received path request: =========================");
        log.info("Driver:" + Arrays.toString(pathRecommendationsDTO.getDriverLocation()));
        log.info("Passenger:" + Arrays.toString(pathRecommendationsDTO.getPassengerLocation()));

        // todo
        double[] driverLocation = pathRecommendationsDTO.getDriverLocation();
        double[] passengerLocation = pathRecommendationsDTO.getPassengerLocation();
        if (driverLocation == null || passengerLocation == null)
            return Result.fail("Position Parameters are null.");
//        Object recommendedPath = pathRecommendationsService.getRecommendedPath(driverLocation, passengerLocation);
//        return recommendedPath != null ? Result.success(recommendedPath): Result.fail();
        // 返回一个VO格式的 数据
        PathRecommendationsVO pathRecommendationsVO = pathRecommendationsService.getRecommendedPath(driverLocation, passengerLocation);
        return pathRecommendationsVO != null ? Result.success(pathRecommendationsVO): Result.fail("Path calculation failed.");
    }
}
