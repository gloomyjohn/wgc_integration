package com.jjy.wgcbackend.entitiy.dto;

import java.util.List;

public class FlowFieldSimulationRequestDTO {
    private Integer vehicleCount;
    private Double vehicleSpeedKmh;
    private Double occupiedSpeedRatio;
    private Double tickSeconds;
    private Integer totalFrames;
    private Integer frameIntervalMs;
    private Double attractionStrength;
    private Double fieldSpreadMeters;
    private Double maxMatchDistanceMeters;
    private Double matchedHoldSeconds;
    private Integer syntheticPassengerCount;
    private Boolean useDatabasePassengers;
    private Boolean broadcast;
    private Boolean includeVectorField;
    private Integer vectorSampleStep;
    private Integer previewFrames;
    private Long randomSeed;
    private List<double[]> passengerLocations;

    public Integer getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(Integer vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public Double getVehicleSpeedKmh() {
        return vehicleSpeedKmh;
    }

    public void setVehicleSpeedKmh(Double vehicleSpeedKmh) {
        this.vehicleSpeedKmh = vehicleSpeedKmh;
    }

    public Double getOccupiedSpeedRatio() {
        return occupiedSpeedRatio;
    }

    public void setOccupiedSpeedRatio(Double occupiedSpeedRatio) {
        this.occupiedSpeedRatio = occupiedSpeedRatio;
    }

    public Double getTickSeconds() {
        return tickSeconds;
    }

    public void setTickSeconds(Double tickSeconds) {
        this.tickSeconds = tickSeconds;
    }

    public Integer getTotalFrames() {
        return totalFrames;
    }

    public void setTotalFrames(Integer totalFrames) {
        this.totalFrames = totalFrames;
    }

    public Integer getFrameIntervalMs() {
        return frameIntervalMs;
    }

    public void setFrameIntervalMs(Integer frameIntervalMs) {
        this.frameIntervalMs = frameIntervalMs;
    }

    public Double getAttractionStrength() {
        return attractionStrength;
    }

    public void setAttractionStrength(Double attractionStrength) {
        this.attractionStrength = attractionStrength;
    }

    public Double getFieldSpreadMeters() {
        return fieldSpreadMeters;
    }

    public void setFieldSpreadMeters(Double fieldSpreadMeters) {
        this.fieldSpreadMeters = fieldSpreadMeters;
    }

    public Double getMaxMatchDistanceMeters() {
        return maxMatchDistanceMeters;
    }

    public void setMaxMatchDistanceMeters(Double maxMatchDistanceMeters) {
        this.maxMatchDistanceMeters = maxMatchDistanceMeters;
    }

    public Double getMatchedHoldSeconds() {
        return matchedHoldSeconds;
    }

    public void setMatchedHoldSeconds(Double matchedHoldSeconds) {
        this.matchedHoldSeconds = matchedHoldSeconds;
    }

    public Integer getSyntheticPassengerCount() {
        return syntheticPassengerCount;
    }

    public void setSyntheticPassengerCount(Integer syntheticPassengerCount) {
        this.syntheticPassengerCount = syntheticPassengerCount;
    }

    public Boolean getUseDatabasePassengers() {
        return useDatabasePassengers;
    }

    public void setUseDatabasePassengers(Boolean useDatabasePassengers) {
        this.useDatabasePassengers = useDatabasePassengers;
    }

    public Boolean getBroadcast() {
        return broadcast;
    }

    public void setBroadcast(Boolean broadcast) {
        this.broadcast = broadcast;
    }

    public Boolean getIncludeVectorField() {
        return includeVectorField;
    }

    public void setIncludeVectorField(Boolean includeVectorField) {
        this.includeVectorField = includeVectorField;
    }

    public Integer getVectorSampleStep() {
        return vectorSampleStep;
    }

    public void setVectorSampleStep(Integer vectorSampleStep) {
        this.vectorSampleStep = vectorSampleStep;
    }

    public Integer getPreviewFrames() {
        return previewFrames;
    }

    public void setPreviewFrames(Integer previewFrames) {
        this.previewFrames = previewFrames;
    }

    public Long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(Long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public List<double[]> getPassengerLocations() {
        return passengerLocations;
    }

    public void setPassengerLocations(List<double[]> passengerLocations) {
        this.passengerLocations = passengerLocations;
    }
}
