package com.jjy.wgcbackend.mapper;

import com.jjy.wgcbackend.entitiy.dto.FlowFieldRoadSegmentDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FlowFieldRoadGraphMapper {

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
              AND mod(segment_id, #{sampleModulo}) = 0
            ORDER BY segment_id
            LIMIT #{maxSegments}
            """)
    List<FlowFieldRoadSegmentDTO> findSimulationSegments(
            @Param("sampleModulo") int sampleModulo,
            @Param("maxSegments") int maxSegments
    );
}
