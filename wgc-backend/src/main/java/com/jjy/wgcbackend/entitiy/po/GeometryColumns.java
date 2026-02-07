package com.jjy.wgcbackend.entitiy.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author baomidou
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@TableName("geometry_columns")
@Schema(name = "GeometryColumns对象", description = "")
public class GeometryColumns implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fTableCatalog;

    private String fTableSchema;

    private String fTableName;

    private String fGeometryColumn;

    private Integer coordDimension;

    private Integer srid;

    private String type;
}
