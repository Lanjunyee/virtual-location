package com.github.warren_bank.mock_location;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import com.github.warren_bank.mock_location.data_model.LocPoint;
import com.github.warren_bank.mock_location.data_model.SharedPrefs;
import com.github.warren_bank.mock_location.service.LocationService;
import com.github.warren_bank.mock_location.ui.AospMainActivity;
import com.github.warren_bank.mock_location.ui.MainActivity;
import com.github.warren_bank.mock_location.ui.RouteActivity;
import com.github.warren_bank.mock_location.util.RouteParser;
import java.io.ByteArrayInputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Small device smoke check, no third-party test framework. Run only on a dedicated test device. */
public class DeviceCheck extends Instrumentation {
    private final LinkedBlockingQueue<Location> locations = new LinkedBlockingQueue<>();
    private final StringBuilder log = new StringBuilder();
    private Context context;
    private boolean uiOnly;
    private LocationManager manager;
    private Activity activity;
    private HandlerThread listenerThread;
    private int originalTimeInterval, originalFixedCount;
    private boolean originalTripHoldDestination, preferencesSaved;
    private final LocationListener listener = new LocationListener() {
        public void onLocationChanged(Location location) { locations.offer(location); }
        public void onProviderEnabled(String provider) {}
        public void onProviderDisabled(String provider) {}
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); uiOnly = arguments != null && "true".equals(arguments.getString("ui")); start(); }
    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        log.append("PASS: ").append(label).append('\n');
        Bundle progress = new Bundle(); progress.putString("stream", label + "\n"); sendStatus(0, progress);
    }
    private Location fresh() throws Exception {
        locations.clear();
        Location result = locations.poll(6, TimeUnit.SECONDS);
        if (result == null) throw new AssertionError("no GPS update: " + LocationService.status());
        return result;
    }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(command);
             java.io.InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[1024]; while (input.read(buffer) != -1) {}
        }
    }
    private void onMain(Runnable runnable) { runOnMainSync(runnable); }
    @Override public void onStart() {
        if (uiOnly) { UiCheck.run(this); return; }
        Bundle result = new Bundle();
        try {
            context = getTargetContext();
            originalTimeInterval = SharedPrefs.getTimeInterval(context);
            originalFixedCount = SharedPrefs.getFixedCount(context);
            originalTripHoldDestination = SharedPrefs.getTripHoldDestination(context);
            preferencesSaved = true;
            SharedPrefs.putTimeInterval(context, 1000);
            SharedPrefs.putFixedCount(context, 0);
            SharedPrefs.putTripHoldDestination(context, true);
            activity = startActivitySync(new Intent(context, RouteActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            onMain(() -> {
                android.widget.EditText points = activity.findViewById(R.id.route_points);
                android.widget.EditText speed = activity.findViewById(R.id.route_speed);
                points.setText("1,2\n3,4"); points.setSelection(2);
                speed.setText("12.5");
            });
            ActivityMonitor recreated = addMonitor(RouteActivity.class.getName(), null, false);
            onMain(() -> activity.recreate());
            activity = waitForMonitorWithTimeout(recreated, 5000);
            removeMonitor(recreated);
            check(activity != null, "route page recreated");
            waitForIdleSync();
            onMain(() -> {
                android.widget.EditText points = activity.findViewById(R.id.route_points);
                android.widget.EditText speed = activity.findViewById(R.id.route_speed);
                check(points.getText().toString().equals("1,2\n3,4") && points.getSelectionStart() == 2
                    && speed.getText().toString().equals("12.5"), "route draft and cursor survive recreation");
            });
            manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            listenerThread = new HandlerThread("location-check"); listenerThread.start();
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, listener, listenerThread.getLooper());
            onMain(() -> LocationService.doStart(context, true, new LocPoint(31.2304,121.4737), null, 0));
            Thread.sleep(1500);
            Location fixed = fresh();
            check(Math.abs(fixed.getLatitude()-31.2304)<1e-6 && Math.abs(fixed.getLongitude()-121.4737)<1e-6, "fixed coordinate delivered");
            check(fixed.isFromMockProvider(), "system mock flag retained");
            check(fixed.hasSpeed() && fixed.getSpeed()==0, "stationary speed is zero");
            LocPoint flyOrigin = new LocPoint(31.2304,121.4737), flyDestination = new LocPoint(31.2304,121.4747);
            onMain(() -> LocationService.doStart(context, true, flyOrigin, flyDestination, 6));
            Thread.sleep(1500);
            Location flying = fresh();
            float flySpeed = (float) (com.github.warren_bank.mock_location.data_model.RoutePlayback.distance(flyOrigin, flyDestination) / 6);
            check(flying.getLongitude()>flyOrigin.getLongitude() && flying.getLongitude()<flyDestination.getLongitude()
                && Math.abs(flying.getSpeed()-flySpeed)<.01 && Math.abs(flying.getBearing()-90)<1,
                "two-point trip follows elapsed time with speed and bearing");
            Thread.sleep(5000);
            Location flyEnd = fresh();
            check(Math.abs(flyEnd.getLongitude()-flyDestination.getLongitude())<1e-6 && flyEnd.getSpeed()==0,
                "two-point trip reaches endpoint on monotonic schedule");
            check(LocationService.isStarted(), "two-point trip keeps destination when configured");
            SharedPrefs.putTripHoldDestination(context, false);
            onMain(() -> LocationService.doStart(context, true, flyOrigin, flyDestination, 2));
            Thread.sleep(3500);
            check(!LocationService.isStarted(), "two-point trip stops at destination when configured");
            SharedPrefs.putTripHoldDestination(context, true);
            onMain(() -> LocationService.doRoute(context, "31.2304,121.4737\n31.2304,121.4757\n31.2314,121.4757", 10));
            Thread.sleep(1500);
            Location moving = fresh();
            check(moving.getLongitude()>121.4737 && Math.abs(moving.getSpeed()-10)<.01, "route advances with configured speed");
            onMain(() -> check(activity.findViewById(R.id.route_pause).isEnabled()
                && !activity.findViewById(R.id.route_resume).isEnabled(), "route UI enables only pause while playing"));
            onMain(() -> LocationService.pauseRoute(context,true));
            Thread.sleep(1200);
            onMain(() -> check(!activity.findViewById(R.id.route_pause).isEnabled()
                && activity.findViewById(R.id.route_resume).isEnabled(), "route UI enables only resume while paused"));
            Location paused = fresh(); Thread.sleep(2200); Location pausedAgain = fresh();
            check(paused.distanceTo(pausedAgain)<.01 && pausedAgain.getSpeed()==0, "pause holds coordinate");
            onMain(() -> LocationService.pauseRoute(context,false));
            Thread.sleep(1200);
            onMain(() -> check(activity.findViewById(R.id.route_pause).isEnabled()
                && !activity.findViewById(R.id.route_resume).isEnabled(), "route UI returns to playing controls after resume"));
            Location resumed = fresh();
            check(resumed.distanceTo(pausedAgain)>1, "resume continues route");
            onMain(() -> context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            Location background1 = fresh(); Location background2 = fresh();
            check(background2.distanceTo(background1)>1 && LocationService.isStarted(), "foreground service continues while app is backgrounded");
            activity = startActivitySync(new Intent(context, RouteActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            onMain(() -> LocationService.doStop(context,true)); Thread.sleep(1200);
            check(!LocationService.isStarted() && !LocationService.getLocationThreadManager().isStarted(), "stop releases running session");
            check(!fresh().isFromMockProvider(), "real GPS resumes after stop");
            activity = startActivitySync(new Intent(context, RouteActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            onMain(() -> LocationService.doRoute(context,"31.2304,121.4737\n31.2304,121.47371", 10));
            Thread.sleep(1800); Location end = fresh();
            check(Math.abs(end.getLongitude()-121.47371)<1e-6 && end.getSpeed()==0, "endpoint holds with zero speed");
            check(LocationService.status().contains("终点"), "endpoint status displayed");
            onMain(() -> check(!activity.findViewById(R.id.route_pause).isEnabled()
                && !activity.findViewById(R.id.route_resume).isEnabled(), "route UI disables pause and resume at endpoint"));
            onMain(() -> context.stopService(new Intent(context, LocationService.class))); Thread.sleep(1200);
            check(!LocationService.isStarted(), "service destruction cleans session");
            String xml = "<gpx><trk><trkseg><trkpt lat='1' lon='2'/><trkpt lat='2' lon='3'/></trkseg></trk></gpx>";
            check(RouteParser.gpx(new ByteArrayInputStream(xml.getBytes("UTF-8"))).size()==2, "native GPX parser accepts route");
            boolean rejected = false;
            try { RouteParser.gpx(new ByteArrayInputStream(("<!DOCTYPE gpx [<!ENTITY x 'unsafe'>]>"+xml).getBytes("UTF-8"))); }
            catch (Exception expected) { rejected=true; }
            check(rejected, "native GPX parser rejects DTD");
            AospMainActivity main = (AospMainActivity) startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            onMain(() -> LocationService.doStart(context,true,new LocPoint(1,2),null,0));
            Thread.sleep(1200); check(fresh().isFromMockProvider(), "restart fixed session");
            Thread.sleep(1000);
            onMain(() -> {
                android.widget.TextView toggle = main.getCurrentActivity().findViewById(R.id.button_toggle_state);
                check(toggle.isActivated() && context.getString(R.string.label_button_stop).contentEquals(toggle.getText()),
                    "home button shows active stop state");
            });
            shell("appops set " + context.getPackageName() + " android:mock_location ignore");
            Thread.sleep(2500);
            check(!LocationService.isStarted() && LocationService.status().contains("已停止"), "revoked mock authorization stops service");
            onMain(() -> {
                android.widget.TextView toggle = main.getCurrentActivity().findViewById(R.id.button_toggle_state);
                check(!toggle.isActivated() && toggle.isEnabled()
                    && context.getString(R.string.label_button_start).contentEquals(toggle.getText()),
                    "home button restores enabled start state after external stop");
            });
            NotificationManager notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 23) check(notifications.getActiveNotifications().length == 0, "stopped service removes foreground notification");
            shell("appops set " + context.getPackageName() + " android:mock_location allow");
            result.putString("stream", log.toString());
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", log + "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (context != null) onMain(() -> context.stopService(new Intent(context,LocationService.class)));
            if (preferencesSaved) {
                SharedPrefs.putTimeInterval(context, originalTimeInterval);
                SharedPrefs.putFixedCount(context, originalFixedCount);
                SharedPrefs.putTripHoldDestination(context, originalTripHoldDestination);
            }
            if (manager != null) manager.removeUpdates(listener);
            if (listenerThread != null) listenerThread.quitSafely();
        }
    }
}
