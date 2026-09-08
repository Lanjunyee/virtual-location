package com.github.warren_bank.mock_location.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.github.warren_bank.mock_location.BuildConfig;
import com.github.warren_bank.mock_location.data_model.LocPoint;
import com.github.warren_bank.mock_location.data_model.RoutePlayback;
import com.github.warren_bank.mock_location.data_model.SharedPrefs;
import com.github.warren_bank.mock_location.util.Gcj02;
import com.github.warren_bank.mock_location.util.RouteParser;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

public class MapPickerActivity extends Activity implements MapEventsReceiver {
    private static final int REQUEST_LOCATION = 301;
    private static final String EXTRA_POINTS = "map_picker_points";
    private static final String EXTRA_MULTIPLE = "map_picker_multiple";
    private static final String STATE_POINTS = "selected_points";
    private static final String STATE_LAT = "map_lat";
    private static final String STATE_LON = "map_lon";
    private static final String STATE_ZOOM = "map_zoom";
    private static final OnlineTileSourceBase CHINA_TILES = new OnlineTileSourceBase(
        "Amap", 3, 20, 256, ".png",
        new String[]{
            "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&",
            "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&",
            "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&",
            "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&"
        }, "© 高德地图"
    ) {
        @Override public String getTileURLString(long index) {
            return getBaseUrl() + "x=" + MapTileIndex.getX(index)
                + "&y=" + MapTileIndex.getY(index) + "&z=" + MapTileIndex.getZoom(index);
        }
    };

    private final ArrayList<LocPoint> points = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MapView map;
    private TextView status;
    private boolean multiple;
    private LocationManager locationManager;
    private Location staleLocation;
    private final Runnable locationTimeout = () -> showLocation(staleLocation);
    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (isReal(location)) showLocation(location);
        }
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
        @SuppressWarnings("deprecation") @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    static Intent intent(Activity activity, String points, boolean multiple) {
        return new Intent(activity, MapPickerActivity.class)
            .putExtra(EXTRA_POINTS, points)
            .putExtra(EXTRA_MULTIPLE, multiple);
    }

    static String result(Intent data) {
        return data == null ? null : data.getStringExtra(EXTRA_POINTS);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("地图选择");
        if (android.os.Build.VERSION.SDK_INT >= 23)
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        multiple = getIntent().getBooleanExtra(EXTRA_MULTIPLE, false);
        String initial = state == null ? getIntent().getStringExtra(EXTRA_POINTS) : state.getString(STATE_POINTS, "");
        try {
            points.addAll(RouteParser.draft(initial == null ? "" : initial));
            if (!multiple && points.size() > 1) throw new IllegalArgumentException("单点选择只能包含一个位置");
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage() == null ? "坐标无效" : error.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        File base = new File(getCacheDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(base);
        Configuration.getInstance().setOsmdroidTileCache(new File(base, "tiles"));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView help = new TextView(this);
        help.setText(multiple
            ? "轻触地图依次添加 WGS84 途经点；路线按直线连接。"
            : "轻触地图选择一个 WGS84 位置。");
        root.addView(help);

        status = new TextView(this);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);

        map = new MapView(this);
        map.setContentDescription("地图，轻触选择位置");
        map.setTileSource(CHINA_TILES);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(true);
        root.addView(map, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(button("撤销", v -> undo()), new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(button("清空", v -> { points.clear(); refresh(); }), new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(button("确认", v -> confirm()), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls);
        setContentView(root);

        refresh();
        if (state != null && state.containsKey(STATE_LAT)) {
            map.getController().setCenter(new GeoPoint(state.getDouble(STATE_LAT), state.getDouble(STATE_LON)));
            map.getController().setZoom(state.getDouble(STATE_ZOOM));
        } else {
            map.post(points.isEmpty() || !multiple ? this::locate : this::fitPoints);
        }
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        return button;
    }

    private void undo() {
        if (!points.isEmpty()) points.remove(points.size() - 1);
        refresh();
    }

    private void refresh() {
        if (map == null) return;
        ArrayList<GeoPoint> geo = geoPoints();
        map.getOverlays().clear();
        if (geo.size() > 1) {
            Polyline line = new Polyline(map);
            line.setPoints(geo);
            line.getOutlinePaint().setColor(Color.rgb(33, 150, 243));
            line.getOutlinePaint().setStrokeWidth(8);
            map.getOverlays().add(line);
        }
        for (int i = 0; i < geo.size(); i++) {
            Marker marker = new Marker(map);
            marker.setPosition(geo.get(i));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setTextIcon(Integer.toString(i + 1));
            marker.setTitle(i == 0 ? "起点" : "第 " + (i + 1) + " 个点");
            map.getOverlays().add(marker);
        }
        CopyrightOverlay copyright = new CopyrightOverlay(this);
        copyright.setCopyrightNotice("© 高德地图");
        copyright.setAlignBottom(true);
        copyright.setAlignRight(true);
        map.getOverlays().add(copyright);
        map.getOverlays().add(new MapEventsOverlay(this));
        setStatus("");
        map.invalidate();
    }

    private void setStatus(String suffix) {
        status.setText("已选择 " + points.size() + " 个点" + (points.isEmpty() ? "" : "；可缩放后继续点选") + suffix);
    }

    private ArrayList<GeoPoint> geoPoints() {
        ArrayList<GeoPoint> result = new ArrayList<>(points.size());
        for (LocPoint point : points) result.add(toMapPoint(point));
        return result;
    }

    private GeoPoint toMapPoint(LocPoint point) {
        LocPoint mapped = Gcj02.fromWgs84(point);
        return new GeoPoint(mapped.getLatitude(), mapped.getLongitude());
    }

    private void fitPoints() {
        ArrayList<GeoPoint> geo = geoPoints();
        if (geo.isEmpty()) geo.add(toMapPoint(SharedPrefs.getTripOrigin(this)));
        if (geo.size() == 1) {
            map.getController().setZoom(16.0);
            map.getController().setCenter(geo.get(0));
        } else {
            int padding = (int) (48 * getResources().getDisplayMetrics().density);
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(geo), false, padding);
        }
    }

    private void locate() {
        if (android.os.Build.VERSION.SDK_INT >= 23
            && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (isReal(candidate) && (staleLocation == null || candidate.getTime() > staleLocation.getTime())) staleLocation = candidate;
            }
            if (staleLocation != null && System.currentTimeMillis() - staleLocation.getTime() < 120000) {
                showLocation(staleLocation);
                return;
            }
            boolean requested = false;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 0, 0, locationListener, Looper.getMainLooper());
                    requested = true;
                }
            }
            if (requested) {
                status.setText("正在获取当前真实位置…");
                handler.postDelayed(locationTimeout, 8000);
            } else {
                showLocation(staleLocation);
            }
        } catch (Exception ignored) {
            showLocation(staleLocation);
        }
    }

    private boolean isReal(Location location) {
        return location != null && (android.os.Build.VERSION.SDK_INT < 18 || !location.isFromMockProvider());
    }

    private void showLocation(Location location) {
        stopLocating();
        if (location == null) {
            if (multiple && points.isEmpty()) { points.add(SharedPrefs.getTripOrigin(this)); refresh(); }
            fitPoints();
            setStatus("；未获取到真实位置");
            return;
        }
        LocPoint current = new LocPoint(location.getLatitude(), location.getLongitude());
        if (!multiple || points.isEmpty()) { points.clear(); points.add(current); refresh(); }
        map.getController().setZoom(16.0);
        map.getController().setCenter(toMapPoint(current));
        setStatus("；已定位当前真实位置");
    }

    @SuppressLint("MissingPermission")
    private void stopLocating() {
        handler.removeCallbacks(locationTimeout);
        if (locationManager != null) {
            try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION) {
            if (results.length > 0 && (results[0] == PackageManager.PERMISSION_GRANTED
                || results.length > 1 && results[1] == PackageManager.PERMISSION_GRANTED)) locate();
            else showLocation(null);
        }
    }

    private void confirm() {
        try {
            String text = RouteParser.format(points);
            if (multiple) {
                List<LocPoint> route = RouteParser.text(text);
                new RoutePlayback(route, 1, 0);
            } else if (points.size() != 1) {
                throw new IllegalArgumentException("请在地图中选择一个位置");
            }
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_POINTS, text));
            finish();
        } catch (Exception error) {
            new AlertDialog.Builder(this)
                .setTitle("无法确认")
                .setMessage(error.getMessage() == null ? "请检查选择的位置" : error.getMessage())
                .setPositiveButton("知道了", null)
                .show();
        }
    }

    @Override public boolean singleTapConfirmedHelper(GeoPoint point) {
        if (multiple) {
            if (points.size() >= 10000) {
                Toast.makeText(this, "路线最多 10000 个点", Toast.LENGTH_SHORT).show();
                return true;
            }
        } else {
            points.clear();
        }
        points.add(Gcj02.toWgs84(point.getLatitude(), point.getLongitude()));
        refresh();
        return true;
    }

    @Override public boolean longPressHelper(GeoPoint point) { return false; }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(STATE_POINTS, RouteParser.format(points));
        if (map != null) {
            IGeoPoint center = map.getMapCenter();
            state.putDouble(STATE_LAT, center.getLatitude());
            state.putDouble(STATE_LON, center.getLongitude());
            state.putDouble(STATE_ZOOM, map.getZoomLevelDouble());
        }
    }

    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override protected void onPause() { if (map != null) map.onPause(); super.onPause(); }
    @Override protected void onDestroy() { stopLocating(); if (map != null) map.onDetach(); super.onDestroy(); }
}
