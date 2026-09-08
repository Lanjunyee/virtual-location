package com.github.warren_bank.mock_location.util;

import com.github.warren_bank.mock_location.data_model.LocPoint;

/** Keeps the app's WGS84 data aligned with mainland-China GCJ-02 map tiles. */
public final class Gcj02 {
    private static final double A = 6378245.0;
    private static final double EE = 0.006693421622965943;

    private Gcj02() {}

    public static LocPoint fromWgs84(LocPoint point) {
        double lat = point.getLatitude(), lon = point.getLongitude();
        if (outsideChina(lat, lon)) return new LocPoint(point);
        double dLat = transformLat(lon - 105, lat - 35);
        double dLon = transformLon(lon - 105, lat - 35);
        double radLat = Math.toRadians(lat);
        double magic = 1 - EE * Math.sin(radLat) * Math.sin(radLat);
        double sqrtMagic = Math.sqrt(magic);
        dLat = dLat * 180 / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLon = dLon * 180 / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new LocPoint(lat + dLat, lon + dLon);
    }

    public static LocPoint toWgs84(double lat, double lon) {
        LocPoint result = new LocPoint(lat, lon);
        if (outsideChina(lat, lon)) return result;
        for (int i = 0; i < 4; i++) {
            LocPoint mapped = fromWgs84(result);
            result = new LocPoint(
                result.getLatitude() - mapped.getLatitude() + lat,
                result.getLongitude() - mapped.getLongitude() + lon
            );
        }
        return result;
    }

    private static boolean outsideChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double x, double y) {
        return -100 + 2 * x + 3 * y + .2 * y * y + .1 * x * y + .2 * Math.sqrt(Math.abs(x))
            + (20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2 / 3
            + (20 * Math.sin(y * Math.PI) + 40 * Math.sin(y / 3 * Math.PI)) * 2 / 3
            + (160 * Math.sin(y / 12 * Math.PI) + 320 * Math.sin(y * Math.PI / 30)) * 2 / 3;
    }

    private static double transformLon(double x, double y) {
        return 300 + x + 2 * y + .1 * x * x + .1 * x * y + .1 * Math.sqrt(Math.abs(x))
            + (20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2 / 3
            + (20 * Math.sin(x * Math.PI) + 40 * Math.sin(x / 3 * Math.PI)) * 2 / 3
            + (150 * Math.sin(x / 12 * Math.PI) + 300 * Math.sin(x / 30 * Math.PI)) * 2 / 3;
    }
}
