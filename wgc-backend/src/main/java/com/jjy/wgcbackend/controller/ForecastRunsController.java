package com.jjy.wgcbackend.controller;

import com.jjy.wgcbackend.common.Result;
import com.jjy.wgcbackend.entitiy.po.ForecastRuns;
import com.jjy.wgcbackend.service.IForecastRunsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * <p>
 * 用于记录每一次预测任务的宏观信息。 前端控制器
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Controller
@RequestMapping("/v1/forecastRuns")
public class ForecastRunsController {
    @Autowired
    private IForecastRunsService forecastRunsService;
    @PostMapping("/add")
    public Result add(ForecastRuns forecastRuns) {
        return forecastRunsService.save(forecastRuns) ? Result.success() : Result.fail();
    }

    @DeleteMapping("/delete")
    public Result delete(Integer id) {
        return forecastRunsService.removeById(id) ? Result.success() : Result.fail();
    }
    @PostMapping("/update")
    public Result update(ForecastRuns forecastRuns) {
        return forecastRunsService.updateById(forecastRuns) ? Result.success() : Result.fail();
    }
    @PostMapping("/query")
    public Result query(ForecastRuns forecastRuns) {
        return forecastRunsService.getById(forecastRuns.getRunId()) != null ? Result.success(forecastRuns) : Result.fail() ;

    }
    @RequestMapping("/list")
    public Result list() {
        return forecastRunsService.list() != null ? Result.success(forecastRunsService.list()) : Result.fail();
    }
}
