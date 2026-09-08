package com.github.warren_bank.mock_location.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import com.github.warren_bank.mock_location.R;
import com.github.warren_bank.mock_location.data_model.LocPoint;
import com.github.warren_bank.mock_location.data_model.RoutePlayback;
import com.github.warren_bank.mock_location.security_model.RuntimePermissions;
import com.github.warren_bank.mock_location.service.LocationService;
import com.github.warren_bank.mock_location.util.RouteParser;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RouteActivity extends Activity implements RuntimePermissions.RuntimePermissionsListener {
    private EditText points, speed;
    private TextView status;
    private Button importButton, start, pause, resume, stop;
    private SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private String pendingPoints;
    private double pendingSpeed;
    private boolean importing;
    private final Runnable refresh = new Runnable() {
        public void run() {
            status.setText(LocationService.status());
            boolean route = LocationService.isStarted() && LocationService.getLocationThreadManager().hasRoute();
            pause.setEnabled(route); resume.setEnabled(route);
            stop.setEnabled(LocationService.isStarted());
            handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.ui_route_title);
        if (state != null) {
            pendingPoints = state.getString("pending_points");
            pendingSpeed = state.getDouble("pending_speed");
        }
        preferences = getSharedPreferences("route", MODE_PRIVATE);
        setContentView(R.layout.activity_route);
        points = findViewById(R.id.route_points);
        speed = findViewById(R.id.route_speed);
        status = findViewById(R.id.route_status);
        importButton = findViewById(R.id.route_import);
        start = findViewById(R.id.route_start);
        pause = findViewById(R.id.route_pause);
        resume = findViewById(R.id.route_resume);
        stop = findViewById(R.id.route_stop);
        findViewById(R.id.route_developer).setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
            catch (Exception error) { showError(error); }
        });
        points.setText(preferences.getString("points", ""));
        findViewById(R.id.route_map).setOnClickListener(v -> {
            try {
                String draft = points.getText().toString();
                RouteParser.draft(draft);
                startActivityForResult(MapPickerActivity.intent(this, draft, true), 102);
            } catch (Exception error) { showError(error); }
        });
        importButton.setOnClickListener(v -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 101);
            } catch (Exception error) { showError(error); }
        });
        speed.setText(preferences.getString("speed", "8"));
        start.setOnClickListener(v -> {
            try {
                pendingPoints = points.getText().toString();
                pendingSpeed = Double.parseDouble(speed.getText().toString()) / 3.6;
                new RoutePlayback(RouteParser.text(pendingPoints), pendingSpeed, 0);
                preferences.edit().putString("points", pendingPoints).putString("speed", speed.getText().toString()).apply();
                RuntimePermissions.requestPermissions(this, this);
            } catch (Exception error) { showError(error); }
        });
        pause.setOnClickListener(v -> control(true));
        resume.setOnClickListener(v -> control(false));
        stop.setOnClickListener(v -> {
            try { LocationService.doStop(this, true); } catch (Exception error) { showError(error); }
        });
    }
    private void control(boolean paused) {
        try { LocationService.pauseRoute(this, paused); } catch (Exception error) { showError(error); }
    }
    @Override public void onPermissionsGranted() {
        try { LocationService.doRoute(this, pendingPoints, pendingSpeed); } catch (Exception error) { showError(error); }
    }
    @Override public void onPermissionsDenied(String[] permissions) { showError(new IllegalStateException("请授予精确定位权限后再开始")); }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        RuntimePermissions.onRequestPermissionsResult(this, this, code, permissions, results);
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == 102) {
            if (result == RESULT_OK) {
                String selected = MapPickerActivity.result(data);
                if (selected != null) points.setText(selected);
            }
            return;
        }
        if (code != 101) { RuntimePermissions.onActivityResult(this, this, code, result, data); return; }
        if (result != RESULT_OK || data == null || data.getData() == null || importing) return;
        importing = true;
        importButton.setEnabled(false); start.setEnabled(false);
        files.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                List<LocPoint> parsed = RouteParser.gpx(input);
                String text = RouteParser.format(parsed);
                handler.post(() -> {
                    if (!isDestroyed()) {
                        points.setText(text);
                        preferences.edit().putString("points", text).apply();
                        Toast.makeText(this, "已导入 " + parsed.size() + " 个点", Toast.LENGTH_SHORT).show();
                    }
                    finishImport();
                });
            } catch (Exception error) {
                handler.post(() -> { if (!isDestroyed()) showError(error); finishImport(); });
            }
        });
    }
    private void finishImport() {
        importing = false;
        if (!isDestroyed()) { importButton.setEnabled(true); start.setEnabled(true); }
    }
    private void showError(Exception error) {
        new AlertDialog.Builder(this).setTitle("未能执行").setMessage(error.getMessage() == null ? "请检查输入和系统权限" : error.getMessage()).setPositiveButton("知道了", null).show();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("pending_points", pendingPoints);
        state.putDouble("pending_speed", pendingSpeed);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        preferences.edit().putString("points", points.getText().toString()).putString("speed", speed.getText().toString()).apply();
        super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacks(refresh); files.shutdownNow(); super.onDestroy(); }
}
