package com.jjy.wgcbackend.entitiy.dto;

import lombok.Data;

@Data
public class NodeDTO {
    private Long nodeId;   // 真实路口的 ID
    private Double lng;    // 吸附后的真实经度
    private Double lat;    // 吸附后的真实纬度
}
