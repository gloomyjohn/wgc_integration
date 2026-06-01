package com.jjy.wgcbackend.entitiy.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowFieldVO implements Serializable {
    private long timestamp;
    private List<SegmentVector> Vectors;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SegmentVector {
        private Long segmentId;      // 对应的路段ID
        private double vectorX;      // 投影后的向量X
        private double vectorY;      // 投影后的向量Y
        private double strength;     // 该路段受到的需求拉力强度
    }
}
