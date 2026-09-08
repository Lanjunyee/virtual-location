package com.github.warren_bank.mock_location.service;

// Modified from warren-bank/Android-Mock-Location; GPL-2.0, see LICENSE.txt.
import com.github.warren_bank.mock_location.R;
import com.github.warren_bank.mock_location.data_model.LocPoint;
import com.github.warren_bank.mock_location.data_model.RoutePlayback;
import com.github.warren_bank.mock_location.security_model.RuntimePermissions;
import com.github.warren_bank.mock_location.service.looper.LocationThreadManager;
import com.github.warren_bank.mock_location.ui.MainActivity;
import com.github.warren_bank.mock_location.ui.RouteActivity;
import com.github.warren_bank.mock_location.util.RouteParser;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.widget.Toast;
import java.util.List;

public class LocationService extends Service {
    private static final String START = "START", STOP = "STOP", ROUTE = "ROUTE", PAUSE = "PAUSE", RESUME = "RESUME";
    private static volatile boolean running;
    private static volatile long generation;
    private static LocationThreadManager LTM = LocationThreadManager.get();
    private static volatile String lastError = "";
    private String notificationStatus = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() {
            if (running) {
                String status = LTM.status();
                if (!status.equals(notificationStatus)) {
                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, notification());
                    notificationStatus = status;
                }
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        LTM.init(this);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(getPackageName(), "模拟定位", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            String action = intent.getAction();
            if (STOP.equals(action)) { stopSession(); return START_NOT_STICKY; }
            RuntimePermissions.requireMockLocation(this);
            if (START.equals(action) || ROUTE.equals(action)) {
                // Stop before replacing the session. Failed replacement never leaves an old route running.
                generation++;
                running = false;
                LTM.stop();
                LTM.init(this);
                if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                else startForeground(1, notification());
                if (ROUTE.equals(action)) {
                    double[] coordinates = intent.getDoubleArrayExtra("coordinates");
                    if (coordinates == null || coordinates.length < 4 || coordinates.length > 20000 || coordinates.length % 2 != 0)
                        throw new IllegalArgumentException("路线数据无效");
                    List<LocPoint> points = new java.util.ArrayList<>();
                    for (int i = 0; i < coordinates.length; i += 2) points.add(new LocPoint(coordinates[i], coordinates[i + 1]));
                    LTM.startRoute(points, intent.getDoubleExtra("speed", 0));
                } else {
                    LocPoint origin = new LocPoint(intent.getDoubleExtra("ORIGIN_LAT", Double.NaN), intent.getDoubleExtra("ORIGIN_LON", Double.NaN));
                    LTM.jumpToLocation(origin);
                    if (intent.hasExtra("DESTINATION_LAT")) {
                        LTM.flyToLocation(new LocPoint(intent.getDoubleExtra("DESTINATION_LAT", Double.NaN), intent.getDoubleExtra("DESTINATION_LON", Double.NaN)), intent.getIntExtra("TRIP_DURATION", 0));
                    }
                    LTM.start(origin);
                }
                running = true;
                lastError = "";
                notificationStatus = "";
                handler.removeCallbacks(refresh);
                handler.post(refresh);
            } else if (PAUSE.equals(action) || RESUME.equals(action)) {
                LTM.pauseRoute(PAUSE.equals(action));
            } else if ("SHARED_PREFS_CHANGE".equals(action)) {
                LTM.onSharedPrefsChange((short) 0);
            } else throw new IllegalArgumentException("未知控制指令");
        } catch (Exception error) {
            lastError = message(error);
            Toast.makeText(this, lastError, Toast.LENGTH_LONG).show();
            stopSession();
        }
        return START_NOT_STICKY;
    }
    private void stopSession() {
        generation++;
        running = false;
        LTM.stop();
        handler.removeCallbacksAndMessages(null);
        stopForeground(true);
        stopSelf();
    }
    @Override public void onDestroy() {
        generation++;
        running = false;
        LTM.stop();
        handler.removeCallbacksAndMessages(null);
        stopForeground(true);
        super.onDestroy();
    }
    private Notification notification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, getPackageName()) : new Notification.Builder(this);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(this, LTM.hasRoute() ? RouteActivity.class : MainActivity.class);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, LocationService.class).setAction(STOP), flags);
        builder.setSmallIcon(R.drawable.launcher).setContentTitle("虚拟定位 · " + LTM.status())
            .setContentText("系统模拟位置 · 点击停止可恢复真实定位").setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, open, flags))
            .addAction(android.R.drawable.ic_media_pause, "停止", stop);
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }
    public static void reportFailure(Context context, Exception error) {
        long failedGeneration = generation;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (generation != failedGeneration) return;
            lastError = message(error);
            Toast.makeText(context, lastError, Toast.LENGTH_LONG).show();
            context.stopService(new Intent(context, LocationService.class));
        });
    }
    private static String message(Exception error) {
        return error.getMessage() == null ? "模拟定位失败，请检查权限和系统设置" : error.getMessage();
    }
    public static String status() { return running ? LTM.status() : lastError.isEmpty() ? "已停止" : "已停止：" + lastError; }
    public static boolean isStarted() { return running; }
    public static LocationThreadManager getLocationThreadManager() { return LTM; }

    public static Intent doStart(Context context, boolean broadcast, LocPoint origin, LocPoint destination, int duration) {
        if (origin == null) throw new IllegalArgumentException("请输入坐标");
        LocPoint.validate(origin.getLatitude(), origin.getLongitude());
        Intent intent = new Intent(context, LocationService.class).setAction(START)
            .putExtra("ORIGIN_LAT", origin.getLatitude()).putExtra("ORIGIN_LON", origin.getLongitude());
        if (destination != null) {
            LocPoint.validate(destination.getLatitude(), destination.getLongitude());
            if (duration <= 0) throw new IllegalArgumentException("移动时间必须大于 0 秒");
            intent.putExtra("DESTINATION_LAT", destination.getLatitude()).putExtra("DESTINATION_LON", destination.getLongitude()).putExtra("TRIP_DURATION", duration);
        }
        if (broadcast) launch(context, intent);
        return intent;
    }
    public static void doRoute(Context context, String points, double speed) {
        List<LocPoint> route = RouteParser.text(points);
        new RoutePlayback(route, speed, 0);
        double[] coordinates = new double[route.size() * 2];
        for (int i = 0; i < route.size(); i++) {
            coordinates[i * 2] = route.get(i).getLatitude();
            coordinates[i * 2 + 1] = route.get(i).getLongitude();
        }
        launch(context, new Intent(context, LocationService.class).setAction(ROUTE).putExtra("coordinates", coordinates).putExtra("speed", speed));
    }
    public static void pauseRoute(Context context, boolean paused) {
        if (running) context.startService(new Intent(context, LocationService.class).setAction(paused ? PAUSE : RESUME));
    }
    public static Intent doStop(Context context, boolean broadcast) {
        Intent intent = new Intent(context, LocationService.class).setAction(STOP);
        if (broadcast && running) context.startService(intent);
        return intent;
    }
    public static Intent doSharedPrefsChange(Context context, boolean broadcast) {
        Intent intent = new Intent(context, LocationService.class).setAction("SHARED_PREFS_CHANGE");
        if (broadcast && running) context.startService(intent);
        return intent;
    }
    private static void launch(Context context, Intent intent) {
        RuntimePermissions.requireMockLocation(context);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }
}
