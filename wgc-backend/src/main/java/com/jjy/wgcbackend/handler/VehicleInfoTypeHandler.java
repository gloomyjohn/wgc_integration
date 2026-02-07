package com.jjy.wgcbackend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjy.wgcbackend.common.VehicleInfo;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class VehicleInfoTypeHandler extends BaseTypeHandler<VehicleInfo> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, VehicleInfo parameter, JdbcType jdbcType) throws SQLException {
        try {
            String json = objectMapper.writeValueAsString(parameter);
            ps.setString(i, json);
        } catch (Exception e) {
            throw new SQLException("Error converting VehicleInfo to JSON", e);
        }
    }

    @Override
    public VehicleInfo getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, VehicleInfo.class);
        } catch (Exception e) {
            throw new SQLException("Error converting JSON to VehicleInfo", e);
        }
    }

    @Override
    public VehicleInfo getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, VehicleInfo.class);
        } catch (Exception e) {
            throw new SQLException("Error converting JSON to VehicleInfo", e);
        }
    }

    @Override
    public VehicleInfo getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, VehicleInfo.class);
        } catch (Exception e) {
            throw new SQLException("Error converting JSON to VehicleInfo", e);
        }
    }



}
