package com.jjy.wgcbackend.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjy.wgcbackend.entitiy.po.Nodes;
import com.jjy.wgcbackend.mapper.NodesMapper;
import com.jjy.wgcbackend.service.INodesService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 论文在第二章开篇就将路网建模为有向图 G(V, E)，其中 V 是节点的集合 。在数据库中，集合里的每一个元素（节点）都需要一个唯一的ID来区分和引用。 服务实现类
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Service
public class NodesServiceImpl extends ServiceImpl<NodesMapper, Nodes> implements INodesService {

}
