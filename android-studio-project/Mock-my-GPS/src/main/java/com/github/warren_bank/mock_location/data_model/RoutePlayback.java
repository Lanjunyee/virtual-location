package com.github.warren_bank.mock_location.data_model;

import java.util.ArrayList;
import java.util.List;

/** One route, elapsed monotonic time, no Android dependencies. */
public final class RoutePlayback {
    private final List<LocPoint> points = new ArrayList<>();
    private final double[] ends;
    private final double speed;
    private double travelled;
    private long lastTime;
    private int segment;
    private boolean paused;

    public RoutePlayback(List<LocPoint> input, double metersPerSecond, long now) {
        if (input == null || input.size() < 2 || input.size() > 10000)
            throw new IllegalArgumentException("路线需要 2～10000 个点");
        if (Double.isNaN(metersPerSecond) || Double.isInfinite(metersPerSecond) || metersPerSecond <= 0 || metersPerSecond > 100)
            throw new IllegalArgumentException("速度必须大于 0 且不超过 360 km/h");
        for (LocPoint point : input) {
            LocPoint.validate(point.getLatitude(), point.getLongitude());
            points.add(new LocPoint(point));
        }
        ends = new double[points.size() - 1];
        double total = 0;
        for (int i = 0; i < ends.length; i++) {
            total += distance(points.get(i), points.get(i + 1));
            ends[i] = total;
        }
        if (total < 0.01) throw new IllegalArgumentException("路线至少需要两个不同的位置");
        speed = metersPerSecond;
        lastTime = now;
    }

    public static double distance(LocPoint a, LocPoint b) {
        double lat1 = Math.toRadians(a.getLatitude()), lat2 = Math.toRadians(b.getLatitude());
        double dlat = lat2 - lat1, dlon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
        return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, h))));
    }

    public synchronized LocPoint position(long now) {
        if (!paused) travelled = Math.min(totalDistance(), travelled + Math.max(0, now - lastTime) * speed / 1000.0);
        lastTime = Math.max(lastTime, now);
        while (segment < ends.length - 1 && travelled >= ends[segment]) segment++;
        if (isFinished()) return new LocPoint(points.get(points.size() - 1));
        double start = segment == 0 ? 0 : ends[segment - 1];
        double length = ends[segment] - start;
        // ponytail: linear coordinate interpolation suits local routes; use geodesic interpolation for long polar segments.
        return LocPoint.interpolate(points.get(segment), points.get(segment + 1), length == 0 ? 1 : (travelled - start) / length);
    }

    public synchronized void setPaused(boolean value, long now) {
        position(now);
        paused = value;
    }
    public synchronized boolean isPaused() { return paused; }
    public synchronized boolean isFinished() { return travelled >= totalDistance(); }
    public synchronized float speed() { return paused || isFinished() ? 0 : (float) speed; }
    public double totalDistance() { return ends[ends.length - 1]; }
    public synchronized float bearing() {
        LocPoint a = points.get(segment), b = points.get(segment + 1);
        double lat1 = Math.toRadians(a.getLatitude()), lat2 = Math.toRadians(b.getLatitude());
        double lon = Math.toRadians(b.getLongitude() - a.getLongitude());
        return (float) ((Math.toDegrees(Math.atan2(Math.sin(lon) * Math.cos(lat2),
            Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon))) + 360) % 360);
    }
}
