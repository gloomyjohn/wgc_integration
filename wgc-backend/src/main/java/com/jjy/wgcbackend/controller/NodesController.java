package com.jjy.wgcbackend.controller;

import com.jjy.wgcbackend.common.Result;
import com.jjy.wgcbackend.entitiy.po.Nodes;
import com.jjy.wgcbackend.service.INodesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * <p>
 * 论文在第二章开篇就将路网建模为有向图 G(V, E)，其中 V 是节点的集合 。在数据库中，集合里的每一个元素（节点）都需要一个唯一的ID来区分和引用。 前端控制器
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Controller
@RequestMapping("/v1/nodes")
public class NodesController {
    @Autowired
    private INodesService nodesService;
    @PostMapping("/add")
    public Result add(Nodes nodes) {
        return nodesService.save(nodes) ? Result.success() : Result.fail();
    }
    @DeleteMapping("/delete")
    public Result delete(Integer id) {
        return nodesService.removeById(id) ? Result.success() : Result.fail();
    }

    @PostMapping("/update")
    public Result update(Nodes nodes) {
        return nodesService.updateById(nodes) ? Result.success() : Result.fail();
    }
    @PostMapping("/query")
    public Result query(Nodes nodes) {
        return nodesService.getById(nodes.getNodeId()) != null ? Result.success(nodesService.getById(nodes.getNodeId())) : Result.fail();
    }
    @GetMapping("/list")
    public Result list() {
        return nodesService.list() != null ? Result.success(nodesService.list()) : Result.fail();
    }
}
