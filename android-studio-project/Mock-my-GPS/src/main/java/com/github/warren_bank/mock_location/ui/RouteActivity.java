package com.github.warren_bank.mock_location.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.*;
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
        setTitle("路线模拟");
        preferences = getSharedPreferences("route", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);

        if (android.os.Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setFocusableInTouchMode(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        scroll.addView(layout);
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setFitsSystemWindows(true);
        frame.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        setContentView(frame);
        TextView help = new TextView(this);
        help.setText("WGS84 坐标：每行一个「纬度,经度」，按行顺序移动。也可导入单段 GPX。国内地图坐标请先确认坐标系。\n暂停和到达终点时保持位置，停止后恢复系统定位。");
        layout.addView(help);
        button(layout, "打开开发者选项", v -> {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
            catch (Exception error) { showError(error); }
        });
        TextView label = new TextView(this); label.setText("有序途经点（WGS84）"); layout.addView(label);
        points = new EditText(this);
        points.setId(View.generateViewId()); label.setLabelFor(points.getId());
        points.setGravity(android.view.Gravity.TOP);
        points.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        points.setMinLines(4); points.setMaxLines(8);
        points.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(RouteParser.MAX_BYTES)});
        points.setHint("31.2304,121.4737\n31.2310,121.4740");
        points.setText(preferences.getString("points", ""));
        layout.addView(points);
        button(layout, "在地图中规划路线", v -> {
            try {
                String draft = points.getText().toString();
                RouteParser.draft(draft);
                startActivityForResult(MapPickerActivity.intent(this, draft, true), 102);
            } catch (Exception error) { showError(error); }
        });
        importButton = button(layout, "导入 GPX 文件", v -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 101);
            } catch (Exception error) { showError(error); }
        });
        TextView speedLabel = new TextView(this); speedLabel.setText("统一速度（km/h）"); layout.addView(speedLabel);
        speed = new EditText(this); speed.setId(View.generateViewId()); speedLabel.setLabelFor(speed.getId());
        speed.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        speed.setText(preferences.getString("speed", "8")); layout.addView(speed);
        start = button(layout, "开始路线（替换当前模拟）", v -> {
            try {
                pendingPoints = points.getText().toString();
                pendingSpeed = Double.parseDouble(speed.getText().toString()) / 3.6;
                new RoutePlayback(RouteParser.text(pendingPoints), pendingSpeed, 0);
                preferences.edit().putString("points", pendingPoints).putString("speed", speed.getText().toString()).apply();
                RuntimePermissions.requestPermissions(this, this);
            } catch (Exception error) { showError(error); }
        });
        pause = button(layout, "暂停", v -> control(true));
        resume = button(layout, "继续", v -> control(false));
        stop = button(layout, "停止并恢复真实定位", v -> {
            try { LocationService.doStop(this, true); } catch (Exception error) { showError(error); }
        });
        status = new TextView(this); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); layout.addView(status);
        layout.requestFocus();
    }
    private Button button(LinearLayout layout, String text, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(text); button.setOnClickListener(listener); layout.addView(button); return button;
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
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        preferences.edit().putString("points", points.getText().toString()).putString("speed", speed.getText().toString()).apply();
        super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacks(refresh); files.shutdownNow(); super.onDestroy(); }
}
