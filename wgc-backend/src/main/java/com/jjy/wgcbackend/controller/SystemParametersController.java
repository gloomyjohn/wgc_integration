package com.jjy.wgcbackend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * <p>
 * 用于存储 WGC 模型和模拟仿真中使用的各种全局参数。将这些参数存入数据库，可以使得系统行为的调整无需修改代码，极大地提高了灵活性和可维护性。 前端控制器
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Controller
@RequestMapping("/v1/systemParameters")
public class SystemParametersController {

}
