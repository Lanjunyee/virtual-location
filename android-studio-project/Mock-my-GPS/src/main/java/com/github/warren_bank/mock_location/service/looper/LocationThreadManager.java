package com.github.warren_bank.mock_location.service.looper;

// copied from:
//   https://github.com/xiangtailiang/FakeGPS/blob/V1.1/app/src/main/java/com/github/fakegps/JoyStickManager.java

import com.github.warren_bank.mock_location.data_model.LocPoint;
import com.github.warren_bank.mock_location.data_model.SharedPrefsState;
import com.github.warren_bank.mock_location.event_hooks.IJoyStickPresenter;
import com.github.warren_bank.mock_location.event_hooks.ISharedPrefsListener;
import com.github.warren_bank.mock_location.security_model.RuntimePermissions;
import com.github.warren_bank.mock_location.service.LocationService;
import com.github.warren_bank.mock_location.ui.components.JoyStickView;

import android.content.Context;
import android.os.SystemClock;
import com.github.warren_bank.mock_location.data_model.RoutePlayback;
import java.util.List;

public class LocationThreadManager implements IJoyStickPresenter, ISharedPrefsListener {
    private static LocationThreadManager INSTANCE = new LocationThreadManager();

    private Context mContext;
    private JoyStickView mJoyStickView;
    private LocationThread mLocationThread;
    private LocPoint mCurrentLocPoint;

    private int mTimeInterval;
    private int mFixedCount;
    private int mFixedCountRemaining;
    private boolean mFixedJoystickEnabled;
    private double mFixedJoystickIncrement;
    private boolean mTripHoldDestination;

    private RoutePlayback route;
    private RoutePlayback fly;

    private boolean mIsStarted = false;
    private boolean mIsFlyMode = false;

    private LocationThreadManager() {
        mContext = null;
    }

    public void init(Context context) {
        mContext = context;
        importSharedPrefs();
    }

    public static LocationThreadManager get() {
        return INSTANCE;
    }

    public synchronized void start(LocPoint locPoint) {
        if (mContext == null) return;
        if (locPoint == null) return;

        mCurrentLocPoint = new LocPoint(locPoint);
        if ((mLocationThread == null) || !mLocationThread.isAlive()) {
            mLocationThread = new LocationThread(mContext, this, mTimeInterval);
            mLocationThread.startThread();
        }

        if (route == null && mFixedJoystickEnabled && !mIsFlyMode && RuntimePermissions.canDrawOverlays(mContext)) {
            showJoyStick();
        }

        mFixedCountRemaining = mFixedCount;
        mIsStarted = true;
    }

    public synchronized void stop() {
        route = null;
        fly = null;
        mIsFlyMode = false;
        MockLocationProvider.setMotion(0, 0);
        if (mLocationThread != null) {
            mLocationThread.stopThread();
            mLocationThread = null;
        }

        hideJoyStick();
        mJoyStickView = null;
        mIsStarted = false;

        stopService();
        mContext = null;
    }

    private void stopService() {
        if (mContext != null && LocationService.isStarted()) LocationService.doStop(mContext, true);
    }

    public synchronized boolean isStarted() {
        return mIsStarted;
    }

    public void showJoyStick() {
        if (mJoyStickView == null) {
            mJoyStickView = new JoyStickView(mContext);
            mJoyStickView.setJoyStickPresenter(this);
        }

        if (!mJoyStickView.isShowing()) {
            mJoyStickView.addToWindow();
        }
    }

    public void hideJoyStick() {
        if ((mJoyStickView != null) && mJoyStickView.isShowing()) {
            mJoyStickView.removeFromWindow();
        }
    }

    public synchronized LocPoint getCurrentLocPoint() {
        return new LocPoint(mCurrentLocPoint);
    }

    public synchronized void startRoute(List<LocPoint> points, double speed) {
        RoutePlayback next = new RoutePlayback(points, speed, SystemClock.elapsedRealtime());
        route = next;
        fly = null;
        mIsFlyMode = false;
        mTimeInterval = 1000;
        hideJoyStick();
        start(points.get(0));
    }

    public synchronized boolean hasRoute() { return route != null; }
    public synchronized boolean isRoutePaused() { return route != null && route.isPaused(); }
    public synchronized boolean isRouteFinished() { return route != null && route.isFinished(); }
    public synchronized void pauseRoute(boolean paused) {
        if (route != null) route.setPaused(paused, SystemClock.elapsedRealtime());
    }
    public synchronized String status() {
        if (!mIsStarted) return "已停止";
        if (route == null) return mIsFlyMode ? "两点移动中" : "固定位置运行中";
        return route.isFinished() ? "已到终点，保持位置" : route.isPaused() ? "已暂停，保持位置" : "路线播放中";
    }

    public synchronized LocPoint getUpdateLocPoint() {
        if (route != null) {
            mCurrentLocPoint = route.position(SystemClock.elapsedRealtime());
            MockLocationProvider.setMotion(route.speed(), route.bearing());
            return new LocPoint(mCurrentLocPoint);
        }
        if (mIsFlyMode) {
            mCurrentLocPoint = fly.position(SystemClock.elapsedRealtime());
            MockLocationProvider.setMotion(fly.speed(), fly.bearing());
            if (fly.isFinished()) {
                fly = null;
                mIsFlyMode = false;
                mFixedCountRemaining = mTripHoldDestination ? mFixedCount : -1;
            }
            return new LocPoint(mCurrentLocPoint);
        }
        MockLocationProvider.setMotion(0, 0);
        if (mFixedCountRemaining != 0) {
            if (mFixedCountRemaining < 0) {
                return null;
            }
            if (mFixedCountRemaining == 1) {
                mFixedCountRemaining = -1;
            }
            else {
                mFixedCountRemaining--;
            }
        }
        return new LocPoint(mCurrentLocPoint);
    }

    public synchronized boolean shouldContinue() {
        if (route != null) return mIsStarted;
        boolean is_done = !mIsFlyMode && (mFixedCountRemaining < 0);

        if (is_done) {
            stop();
        }

        return !is_done;
    }

    public synchronized void jumpToLocation(LocPoint location) {
        LocPoint.validate(location.getLatitude(), location.getLongitude());
        route = null;
        fly = null;
        mIsFlyMode = false;
        mCurrentLocPoint = new LocPoint(location);
    }

    public synchronized void flyToLocation(LocPoint location, int trip_duration_seconds) {
        LocPoint.validate(location.getLatitude(), location.getLongitude());
        if (trip_duration_seconds <= 0) throw new IllegalArgumentException("移动时间必须大于 0");
        route = null;
        if (mIsStarted && mFixedJoystickEnabled) {
            hideJoyStick();
        }

        fly = RoutePlayback.forDuration(mCurrentLocPoint, location, trip_duration_seconds * 1000L, SystemClock.elapsedRealtime());
        mIsFlyMode = true;
    }

    public synchronized boolean isFlyMode() {
        return mIsFlyMode;
    }

    public synchronized void stopFlyMode() {
        fly = null;
        mIsFlyMode = false;
    }

    public void setMoveStep(double moveStep) {
        mFixedJoystickIncrement = moveStep;
    }

    public double getMoveStep() {
        return mFixedJoystickIncrement;
    }

    @Override
    public synchronized void onArrowUpClick() {
        mCurrentLocPoint.setLatitude(mCurrentLocPoint.getLatitude() + mFixedJoystickIncrement);
    }

    @Override
    public synchronized void onArrowDownClick() {
        mCurrentLocPoint.setLatitude(mCurrentLocPoint.getLatitude() - mFixedJoystickIncrement);
    }

    @Override
    public synchronized void onArrowLeftClick() {
        mCurrentLocPoint.setLongitude(mCurrentLocPoint.getLongitude() - mFixedJoystickIncrement);
    }

    @Override
    public synchronized void onArrowRightClick() {
        mCurrentLocPoint.setLongitude(mCurrentLocPoint.getLongitude() + mFixedJoystickIncrement);
    }

    // =================================
    // integrate with Shared Preferences
    // =================================

    @Override
    public synchronized void onSharedPrefsChange(short diff_fields) {
        if (route == null) importSharedPrefs();
    }

    private void importSharedPrefs() {
        if (mContext == null) return;

        SharedPrefsState prefsState = new SharedPrefsState(mContext, true);

        if (mFixedCount != prefsState.fixed_count) {
            mFixedCount = prefsState.fixed_count;

            if (mIsStarted && !mIsFlyMode) {
                mFixedCountRemaining = mFixedCount;
            }
        }

        if (mTimeInterval != prefsState.time_interval) {
            mTimeInterval = prefsState.time_interval;

            if ((mLocationThread != null) && mLocationThread.isAlive()) {
                mLocationThread.updateTimeInterval(mTimeInterval);
            }
        }

        if (mFixedJoystickEnabled != prefsState.fixed_joystick_enabled) {
            mFixedJoystickEnabled = prefsState.fixed_joystick_enabled;

            if (mIsStarted && !mIsFlyMode) {
                if (mFixedJoystickEnabled)
                    showJoyStick();
                else
                    hideJoyStick();
            }
        }

        mFixedJoystickIncrement = prefsState.fixed_joystick_increment;
        mTripHoldDestination    = prefsState.trip_hold_destination;
    }

}
