package com.github.warren_bank.mock_location;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.accessibilityservice.AccessibilityServiceInfo;
import com.github.warren_bank.mock_location.ui.*;
import org.osmdroid.util.GeoPoint;
import java.io.File;
import java.io.FileOutputStream;

/** Optional UI regression: am instrument -w -e ui true .../DeviceCheck. Dedicated emulator only. */
final class UiCheck {
    private final Instrumentation test;
    private final StringBuilder log = new StringBuilder();
    private UiCheck(Instrumentation test) { this.test = test; }
    static void run(Instrumentation test) {
        UiCheck check = new UiCheck(test);
        Bundle result = new Bundle();
        try {
            check.run();
            result.putString("stream", check.log.toString());
            test.finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", check.log + "FAIL: " + android.util.Log.getStackTraceString(error));
            test.finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        log.append("PASS: ").append(label).append('\n');
    }
    private Activity open(Class<?> page) {
        return test.startActivitySync(new Intent(test.getTargetContext(), page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private AccessibilityNodeInfo node(String id) throws Exception {
        for (int i = 0; i < 30; i++) {
            AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(test.getTargetContext().getPackageName() + ":id/" + id);
                if (!nodes.isEmpty()) return nodes.get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("View not found: " + id);
    }
    private void input(String id, String text) throws Exception {
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        check(node(id).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "set " + id);
        test.waitForIdleSync();
    }
    private void click(String id) throws Exception {
        check(node(id).performAction(AccessibilityNodeInfo.ACTION_CLICK), "click " + id);
        test.waitForIdleSync();
        Thread.sleep(200);
    }
    private void snapshot(String name) throws Exception {
        test.waitForIdleSync();
        Thread.sleep(250); // Allow the next rendered frame to include accessibility text updates.
        android.graphics.Bitmap image = test.getUiAutomation().takeScreenshot();
        if (image == null) throw new AssertionError("Screenshot unavailable");
        try (FileOutputStream output = new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir("ui-check"), name + ".png"))) {
            check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output), "capture " + name);
        } finally { image.recycle(); }
    }
    private void run() throws Exception {
        AccessibilityServiceInfo info = test.getUiAutomation().getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        test.getUiAutomation().setServiceInfo(info);
        Activity bookmarks = open(BookmarksActivity.class);
        test.invokeMenuActionSync(bookmarks, R.id.menu_add_bookmarkitem, 0);
        String title = "UI-check-long-bookmark-name-0123456789-0123456789";
        input("input_bookmark_title", title);
        input("input_bookmark_location", "31.230400123456,121.473700123456");
        snapshot("13-bookmark-edit");
        click("button_save");
        snapshot("14-bookmark-list");
        check(node("listview").getChildCount() > 0, "bookmark created with long coordinates");
        AccessibilityNodeInfo row = node("listview").getChild(0);
        check(title.contentEquals(row.getText()), "long bookmark title retained");
        row.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        input("input_bookmark_title", "UI-check-edited"); click("button_save");
        row = node("listview").getChild(0);
        check("UI-check-edited".contentEquals(row.getText()), "bookmark edit retained");
        row.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK); click("button_delete");
        check(node("bookmarks_empty").isVisibleToUser(), "delete restores empty state");

        Activity settings = open(PreferencesActivity.class);
        input("input_time_interval", "1500");
        test.runOnMainSync(() -> settings.findViewById(R.id.button_save).performClick());
        Activity restored = open(PreferencesActivity.class);
        check("1500".contentEquals(node("input_time_interval").getText()), "settings saved and restored");
        input("input_time_interval", "1000");
        test.runOnMainSync(() -> restored.findViewById(R.id.button_save).performClick());

        Activity route = open(RouteActivity.class);
        File gpx = new File(test.getTargetContext().getCacheDir(), "ui-check.gpx");
        try (FileOutputStream file = new FileOutputStream(gpx)) {
            file.write("<gpx><rte><rtept lat='31.23' lon='121.47'/><rtept lat='31.24' lon='121.48'/></rte></gpx>".getBytes("UTF-8"));
        }
        IntentFilter picker = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE); picker.addDataType("*/*");
        Instrumentation.ActivityMonitor importMonitor = test.addMonitor(picker,
            new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent().setData(Uri.fromFile(gpx))), true);
        click("route_import");
        for (int i = 0; i < 30 && !node("route_points").getText().toString().contains("31.24"); i++) Thread.sleep(100);
        check(node("route_points").getText().toString().contains("31.24"), "GPX result imported into route editor");
        test.removeMonitor(importMonitor);
        Instrumentation.ActivityMonitor mapMonitor = test.addMonitor(MapPickerActivity.class.getName(), null, false);
        click("route_map");
        MapPickerActivity map = (MapPickerActivity) test.waitForMonitorWithTimeout(mapMonitor, 5000);
        test.removeMonitor(mapMonitor); check(map != null, "route opens map");
        test.runOnMainSync(() -> {
            map.findViewById(R.id.map_clear).performClick();
            map.singleTapConfirmedHelper(new GeoPoint(31.23, 121.47));
            map.singleTapConfirmedHelper(new GeoPoint(31.24, 121.48));
            map.findViewById(R.id.map_undo).performClick();
        });
        String status = node("map_status").getText().toString();
        test.runOnMainSync(() -> map.onRequestPermissionsResult(301,
            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
            new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED}));
        Thread.sleep(8500);
        check(status.equals(node("map_status").getText().toString()), "manual map selection survives late permission result and timeout");
        test.runOnMainSync(() -> {
            map.singleTapConfirmedHelper(new GeoPoint(31.24, 121.48));
            map.findViewById(R.id.map_confirm).performClick();
        });
        test.waitForIdleSync();
        check(node("route_points").getText().toString().trim().split("\\n").length == 2, "map confirmation returns two WGS84 waypoints");
        gpx.delete();
    }
}
