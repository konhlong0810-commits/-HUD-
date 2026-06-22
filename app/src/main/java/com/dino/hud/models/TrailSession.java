package com.dino.hud.models;

import java.util.List;

/** 历史 Session */
public class TrailSession {
    public long id;
    public long startTime;
    public long endTime;
    public double distance;
    public List<TrailPoint> points;
}
