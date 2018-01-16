package com.example.nutri_000.testinggauge;

public class Status {
    public boolean scanning = false;
    public boolean stimming = false;
    public boolean fireflyCharFound = false;
    public boolean hipCalibrate, ankleCalibrate, kneeCalibrate = false;
    public int hipCalibrateCounter, kneeCalibrateCounter, ankleCalibrateCounter = 0;
    public float averageHip, averageAnkle, averageKnee = 0;
}
