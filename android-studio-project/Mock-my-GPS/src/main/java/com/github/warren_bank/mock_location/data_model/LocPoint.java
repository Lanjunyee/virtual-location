package com.github.warren_bank.mock_location.data_model;

// copied from:
//   https://github.com/xiangtailiang/FakeGPS/blob/V1.1/app/src/main/java/com/github/fakegps/model/LocPoint.java

public class LocPoint {
    private double mLatitude;
    private double mLongitude;

    public LocPoint(LocPoint locPoint) {
        mLatitude = locPoint.getLatitude();
        mLongitude = locPoint.getLongitude();
    }

    public LocPoint(double latitude, double longitude) {
        validate(latitude, 0);
        mLatitude = latitude;
        validate(0, longitude);
        mLongitude = longitude;
    }

    public LocPoint(String text) throws NumberFormatException {
        String[] parts = text.split(",", -1);
        if (parts.length == 2) {
            mLatitude  = Double.parseDouble(parts[0].trim());
            mLongitude = Double.parseDouble(parts[1].trim());
            validate(mLatitude, mLongitude);
        }
        else {
            throw new NumberFormatException("expected: latitude,longitude");
        }
    }

    public static void validate(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isInfinite(lat) || Math.abs(lat) > 90 ||
            Double.isNaN(lon) || Double.isInfinite(lon) || Math.abs(lon) > 180)
            throw new NumberFormatException("WGS84 坐标无效：纬度 -90～90，经度 -180～180");
    }

    // Shared linear interpolation, including routes across the date line.
    public static LocPoint interpolate(LocPoint a, LocPoint b, double factor) {
        double delta = ((b.mLongitude - a.mLongitude + 540) % 360) - 180;
        double lon = ((a.mLongitude + factor * delta + 540) % 360) - 180;
        return new LocPoint(a.mLatitude + factor * (b.mLatitude - a.mLatitude), lon);
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public void setLatitude(double latitude) {
        validate(latitude, 0);
        mLatitude = latitude;
    }

    public void setLongitude(double longitude) {
        validate(0, longitude);
        mLongitude = longitude;
    }

    @Override
    public String toString() {
        return String.format(
            "%1$s, %2$s",
            mLatitude,
            mLongitude
        );
    }

    public boolean equals(LocPoint locPoint) {
        double threshold = 1e-4;
        return equals(locPoint, threshold);
    }

    public boolean equals(LocPoint locPoint, double threshold) {
        double that_lat = locPoint.getLatitude();
        double that_lon = locPoint.getLongitude();

        return (Math.abs(that_lat - mLatitude) < threshold) && (Math.abs(that_lon - mLongitude) < threshold); // todo: fix that longitude difference doesn't work if straddling opposite sides of the 180th meridian (ex: 179.99999 and -179.99999)
    }

}
