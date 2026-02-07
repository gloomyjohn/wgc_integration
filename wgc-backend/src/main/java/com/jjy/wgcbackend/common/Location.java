package com.jjy.wgcbackend.common;

public class Location {
    private Double longitude;
    private Double latitude;

    // 构造函数
    public Location() {}

    // getter 和 setter 方法
    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
}
