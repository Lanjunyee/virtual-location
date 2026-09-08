package com.github.warren_bank.mock_location.service.looper;

// copied from:
//   https://github.com/xiangtailiang/FakeGPS/blob/V1.1/app/src/main/java/com/github/fakegps/LocationThread.java

import com.github.warren_bank.mock_location.data_model.LocPoint;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

public class LocationThread extends HandlerThread {
    private Context mContext;
    private LocationThreadManager mLocationThreadManager;
    private int mTimeInterval;
    private Handler mHandler;
    private volatile boolean stopped;

    public LocationThread(Context context, LocationThreadManager locationThreadManager, int timeInterval) {
        super("LocationThread", Process.THREAD_PRIORITY_MORE_FAVORABLE);

        mContext = context;
        mLocationThreadManager = locationThreadManager;
        mTimeInterval = timeInterval;
    }

    @Override
    public synchronized void start() {
        super.start();

        mHandler = new Handler(getLooper());
        mHandler.post(mUpdateLocation);
    }

    public void startThread() {
        MockLocationProviderManager.startMockingLocation(mContext);

        start();
    }

    public void stopThread() {
        stopped = true;
        MockLocationProviderManager.stopMockingLocation();

        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
        try {
            quit();
            interrupt();
        }
        catch (Exception e) {}

    }

    public void updateTimeInterval(int timeInterval) {
        mTimeInterval = timeInterval;
    }

    Runnable mUpdateLocation = new Runnable() {
        @Override
        public void run() {
            synchronized (mLocationThreadManager) {
                if (stopped) return;
                try {
                    com.github.warren_bank.mock_location.security_model.RuntimePermissions.requireMockLocation(mContext);
                    LocPoint point = mLocationThreadManager.getUpdateLocPoint();
                    if (point != null) MockLocationProviderManager.exec(point.getLatitude(), point.getLongitude());
                    if (!stopped && mLocationThreadManager.shouldContinue()) mHandler.postDelayed(this, mTimeInterval);
                } catch (Exception error) {
                    stopped = true;
                    com.github.warren_bank.mock_location.service.LocationService.reportFailure(mContext, error);
                }
            }
        }
    };

    public Handler getHandler() {
        return mHandler;
    }
}
