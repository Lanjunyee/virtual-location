package com.github.warren_bank.mock_location.service.looper;

import com.huawei.hms.api.ConnectionResult;
import com.huawei.hms.api.HuaweiApiAvailability;
import com.huawei.hms.location.FusedLocationProviderClient;
import com.huawei.hms.location.LocationServices;
import com.github.warren_bank.mock_location.service.LocationService;
import android.content.Context;

public class HmsMockLocationProviderManager {
    private static volatile FusedLocationProviderClient client;
    private static volatile boolean ready;
    private static Context context;

    protected static void startMockingLocation(Context appContext) {
        if (HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(appContext) != ConnectionResult.SUCCESS)
            throw new IllegalStateException("HMS Core 不可用，请安装 AOSP 版或更新 HMS Core");
        stopMockingLocation();
        context = appContext.getApplicationContext();
        FusedLocationProviderClient session = LocationServices.getFusedLocationProviderClient(context);
        client = session;
        session.setMockMode(true).addOnSuccessListener(value -> {
            if (client == session) ready = true;
        }).addOnFailureListener(error -> {
            if (client == session) LocationService.reportFailure(context, error);
        });
    }
    protected static void exec(double lat, double lon) {
        FusedLocationProviderClient session = client;
        if (session != null && ready) {
            session.setMockLocation(MockLocationProvider.getLocation(lat, lon)).addOnFailureListener(error -> {
                if (client == session) LocationService.reportFailure(context, error);
            });
        }
    }
    protected static void stopMockingLocation() {
        FusedLocationProviderClient session = client;
        client = null;
        ready = false;
        if (session != null) session.setMockMode(false).addOnFailureListener(error ->
            android.util.Log.e("MockLocation", "HMS mock mode cleanup failed", error));
    }
}
