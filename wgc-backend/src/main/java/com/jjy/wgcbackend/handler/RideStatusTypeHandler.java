package com.jjy.wgcbackend.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 专门处理 PostgreSQL 中 ride_status 类型的 TypeHandler
 */
public class RideStatusTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        // 创建 PostgreSQL 的专属对象
        PGobject pgObject = new PGobject();
        // 这里的类型名字必须和数据库里定义的枚举类型名字一模一样
        pgObject.setType("ride_status"); 
        pgObject.setValue(parameter);
        // 将包装好的对象放进 SQL 语句中
        ps.setObject(i, pgObject);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}