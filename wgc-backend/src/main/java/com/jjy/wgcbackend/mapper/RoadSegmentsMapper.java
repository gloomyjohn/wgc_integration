package com.jjy.wgcbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjy.wgcbackend.entitiy.dto.NavigationGraphSegmentDTO;
import com.jjy.wgcbackend.entitiy.dto.RoadSegmentNavDTO;
import com.jjy.wgcbackend.entitiy.po.RoadSegments;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;


/**
 * <p>
 * 定义构成交通网络的有向路段（即图的边），并存储每个路段的静态和半静态属性，这些属性是 WGC 算法进行预测和路径规划的关键输入。 Mapper 接口
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface RoadSegmentsMapper extends BaseMapper<RoadSegments> {

    @Select("""
            SELECT
                segment_id AS segment_id,
                ST_X(ST_StartPoint(geometry)) AS start_lng,
                ST_Y(ST_StartPoint(geometry)) AS start_lat,
                ST_X(ST_EndPoint(geometry)) AS end_lng,
                ST_Y(ST_EndPoint(geometry)) AS end_lat
            FROM road_segments
            WHERE geometry IS NOT NULL
            """)
    List<Map<String, Object>> getAllSegmentVectors();

    @Select("""
            SELECT
                segment_id AS segmentId,
                start_node_id AS startNodeId,
                end_node_id AS endNodeId,
                length_meters AS lengthMeters,
                ST_X(ST_StartPoint(geometry)) AS startLng,
                ST_Y(ST_StartPoint(geometry)) AS startLat,
                ST_X(ST_EndPoint(geometry)) AS endLng,
                ST_Y(ST_EndPoint(geometry)) AS endLat
            FROM road_segments
            WHERE geometry IS NOT NULL
            ORDER BY geometry <-> ST_SetSRID(ST_MakePoint(#{lng}, #{lat}), 4326)
            LIMIT 1
            """)
    RoadSegmentNavDTO findNearestSegment(@Param("lng") double lng, @Param("lat") double lat);

    @Select("""
            SELECT
                segment_id AS segmentId,
                start_node_id AS startNodeId,
                end_node_id AS endNodeId,
                length_meters AS lengthMeters,
                ST_X(ST_StartPoint(geometry)) AS startLng,
                ST_Y(ST_StartPoint(geometry)) AS startLat,
                ST_X(ST_EndPoint(geometry)) AS endLng,
                ST_Y(ST_EndPoint(geometry)) AS endLat
            FROM road_segments
            WHERE geometry IS NOT NULL
              AND start_node_id = #{nodeId}
            """)
    List<RoadSegmentNavDTO> findOutgoingSegments(@Param("nodeId") Long nodeId);

    @Select("""
            SELECT
                segment_id AS segmentId,
                start_node_id AS startNodeId,
                end_node_id AS endNodeId,
                length_meters AS lengthMeters,
                ST_X(ST_StartPoint(geometry)) AS startLng,
                ST_Y(ST_StartPoint(geometry)) AS startLat,
                ST_X(ST_EndPoint(geometry)) AS endLng,
                ST_Y(ST_EndPoint(geometry)) AS endLat
            FROM road_segments
            WHERE geometry IS NOT NULL
            """)
    List<NavigationGraphSegmentDTO> findAllSegmentsForNavigation();
}
