package com.jjy.wgcbackend.entitiy.vo;

import com.jjy.wgcbackend.common.Location;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RideRequestVO {
    private long driverId;
    private Location currentLocation;
}
