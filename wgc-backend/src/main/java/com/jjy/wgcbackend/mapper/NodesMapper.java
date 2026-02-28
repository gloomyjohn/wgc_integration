package com.jjy.wgcbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjy.wgcbackend.common.Result;
import com.jjy.wgcbackend.entitiy.dto.NodeDTO;
import com.jjy.wgcbackend.entitiy.po.Nodes;
import org.apache.ibatis.annotations.Param;

import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 论文在第二章开篇就将路网建模为有向图 G(V, E)，其中 V 是节点的集合 。在数据库中，集合里的每一个元素（节点）都需要一个唯一的ID来区分和引用。 Mapper 接口
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface NodesMapper extends BaseMapper<Nodes> {


    /**
     * 在指定坐标的特定半径获取N个路口点
     */
    @Select("SELECT node_id AS nodeId, ST_X(coordinates) AS lng, ST_Y(coordinates) AS lat " +
            "FROM nodes " +
            "WHERE ST_DWithin(" +
            "   coordinates::geography, " +
            "   ST_SetSRID(ST_MakePoint(#{lng}, #{lat}), 4326)::geography, " +
            "   #{radiusMeters}" +
            ") " +
            "ORDER BY RANDOM() " +
            "LIMIT #{limit}")
    List<NodeDTO> getRandomNearbyNodes(@Param("lng") double lng,
                                               @Param("lat") double lat,
                                               @Param("radiusMeters") double radiusMeters,
                                               @Param("limit") int limit);



}
