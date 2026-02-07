package com.jjy.wgcbackend.controller;

import com.jjy.wgcbackend.common.Result;
import com.jjy.wgcbackend.entitiy.po.RideRequests;
import com.jjy.wgcbackend.entitiy.vo.RideRequestVO;
import com.jjy.wgcbackend.service.IRideRequestsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 统的需求入口，用于记录乘客发起的每一次乘车请求。它追踪了请求从生成到被满足或被放弃的整个生命周期，是供需匹配的核心数据源。 前端控制器
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@RestController
@RequestMapping("/v1/rideRequests")
public class RideRequestsController {
    @Autowired
    private IRideRequestsService rideRequestsService;

    @PostMapping("/add")
    public Result add(RideRequests rideRequests) {
        return rideRequestsService.save(rideRequests) ? Result.success() : Result.fail();
    }

    @PostMapping("/delete")
    public Result delete(Integer id) {
        return rideRequestsService.removeById(id) ? Result.success() : Result.fail();
    }

    @PostMapping("/update")
    public Result update(RideRequests rideRequests) {
        return rideRequestsService.updateById(rideRequests) ? Result.success() : Result.fail();
    }

    @PostMapping("/query")
    public Result query(RideRequests rideRequests) {
        return rideRequestsService.getById(rideRequests.getRequestId()) != null ? Result.success(rideRequestsService.getById(rideRequests.getRequestId())) : Result.fail();
    }

    @PostMapping("/list")
    public Result list() {
        return rideRequestsService.list() != null ? Result.success(rideRequestsService.list()) : Result.fail();
    }

    @GetMapping("/getOne")
    public Result getOne(@RequestBody RideRequestVO rideRequests) {
        System.out.println("Received : \n" +
                "Driver:" + rideRequests.getDriverId()
         + "\nLocation: " + rideRequests.getCurrentLocation());
        List<RideRequests> list = rideRequestsService.list();
        if (list.isEmpty()) {
            return Result.success("No Requests now.");
        }
        return rideRequestsService.list() != null ? Result.success(list.get(0)) : Result.fail();
    }
}
