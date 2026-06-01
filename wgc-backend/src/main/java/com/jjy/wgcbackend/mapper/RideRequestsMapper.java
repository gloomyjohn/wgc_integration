package com.jjy.wgcbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjy.wgcbackend.entitiy.dto.NodeDTO;
import com.jjy.wgcbackend.entitiy.po.RideRequests;
import org.apache.ibatis.annotations.Select;

import java.util.List;


/**
 * <p>
 * 统的需求入口，用于记录乘客发起的每一次乘车请求。它追踪了请求从生成到被满足或被放弃的整个生命周期，是供需匹配的核心数据源。 Mapper 接口
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
public interface RideRequestsMapper extends BaseMapper<RideRequests> {

    @Select("""
            SELECT
                rr.request_id AS nodeId,
                ST_X(ST_LineInterpolatePoint(rs.geometry, 0.5)) AS lng,
                ST_Y(ST_LineInterpolatePoint(rs.geometry, 0.5)) AS lat
            FROM ride_requests rr
            JOIN road_segments rs ON rr.request_segment_id = rs.segment_id
            WHERE rr.matched_driver_id IS NULL
              AND rr.matched_at IS NULL
              AND rr.abandon_time IS NULL
              AND rs.geometry IS NOT NULL
            """)
    List<NodeDTO> getActivePassengerLocations();
}
