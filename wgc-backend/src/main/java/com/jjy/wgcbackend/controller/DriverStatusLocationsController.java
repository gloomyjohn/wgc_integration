package com.jjy.wgcbackend.controller;

import com.jjy.wgcbackend.common.Result;
import com.jjy.wgcbackend.entitiy.po.DriverStatusLocations;
import com.jjy.wgcbackend.service.IDriverStatusLocationsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 用于追踪每一位司机的瞬时状态和精确位置。论文中的数学模型（如 De(t), Pu(t)）描述了司机在路网中的宏观分布，而这张表则是这些宏观变量在数据库中的微观实现。 前端控制器
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@RestController
@RequestMapping("/v1/driverStatusLocations")
@CrossOrigin(origins = "*")
public class DriverStatusLocationsController {
    @Autowired
    private IDriverStatusLocationsService driverStatusLocationsService;

    // driverStatusLocations增删查改
    @PostMapping("/add")
    public Result add(DriverStatusLocations driverStatusLocations) {

        return driverStatusLocationsService.save(driverStatusLocations) ? Result.success() : Result.fail();
    }

    @DeleteMapping("/delete")
    public Result delete(Integer id) {

        return driverStatusLocationsService.removeById(id) ? Result.success() : Result.fail();
    }

    @PostMapping("/update")
    public Result update(DriverStatusLocations driverStatusLocations) {

        return driverStatusLocationsService.updateById(driverStatusLocations) ? Result.success() : Result.fail();
    }

    @PostMapping("/query")
    public Result query(DriverStatusLocations driverStatusLocations) {

        return driverStatusLocationsService.getById(driverStatusLocations.getDriverId()) != null ? Result.success(driverStatusLocations) : Result.fail();
    }

    @GetMapping("/list")
    public Result list() {

        return driverStatusLocationsService.list() != null ? Result.success(driverStatusLocationsService.list()) : Result.fail();
    }

}
