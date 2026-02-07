package com.jjy.wgcbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjy.wgcbackend.entitiy.po.Nodes;

/**
 * <p>
 * 论文在第二章开篇就将路网建模为有向图 G(V, E)，其中 V 是节点的集合 。在数据库中，集合里的每一个元素（节点）都需要一个唯一的ID来区分和引用。 服务类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface INodesService extends IService<Nodes> {

}
