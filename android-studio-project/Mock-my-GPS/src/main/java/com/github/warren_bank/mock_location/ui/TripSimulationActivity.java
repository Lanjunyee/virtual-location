package com.github.warren_bank.mock_location.ui;

import com.github.warren_bank.mock_location.R;
import com.github.warren_bank.mock_location.data_model.BookmarkItem;
import com.github.warren_bank.mock_location.data_model.LocPoint;
import com.github.warren_bank.mock_location.data_model.SharedPrefs;
import com.github.warren_bank.mock_location.service.LocationService;
import com.github.warren_bank.mock_location.ui.interfaces.RuntimePermissionsListener;
import com.github.warren_bank.mock_location.ui.interfaces.RuntimePermissionsRequester;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class TripSimulationActivity extends Activity implements RuntimePermissionsListener {
    private LocPoint originalLocOrigin;
    private LocPoint originalLocDestination;
    private int originalTripDuration;
    private String pendingMapOrigin;
    private String pendingMapDestination;

    private TextView label_trip_origin;
    private TextView input_trip_origin;
    private TextView label_trip_destination;
    private TextView input_trip_destination;
    private TextView input_trip_duration;
    private Button   button_toggle_state;
    private Button   button_update;

    private short diff_fields = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_simulation);

        originalLocOrigin      = SharedPrefs.getTripOrigin(TripSimulationActivity.this);
        originalLocDestination = SharedPrefs.getTripDestination(TripSimulationActivity.this);
        originalTripDuration   = SharedPrefs.getTripDuration(TripSimulationActivity.this);

        label_trip_origin      = (TextView) findViewById(R.id.label_trip_origin);
        input_trip_origin      = (TextView) findViewById(R.id.input_trip_origin);
        label_trip_destination = (TextView) findViewById(R.id.label_trip_destination);
        input_trip_destination = (TextView) findViewById(R.id.input_trip_destination);
        input_trip_duration    = (TextView) findViewById(R.id.input_trip_duration);
        button_toggle_state    = (Button)   findViewById(R.id.button_toggle_state);
        button_update          = (Button)   findViewById(R.id.button_update);

        findViewById(R.id.button_map_trip_origin).setOnClickListener(v -> openMap(input_trip_origin, 201));
        findViewById(R.id.button_map_trip_destination).setOnClickListener(v -> openMap(input_trip_destination, 202));

        input_trip_origin.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                label_trip_origin.setVisibility(View.GONE);

                if (!LocationService.isStarted()) return;

                try {
                    String trip_origin = s.toString();
                    LocPoint modifiedLocOrigin = new LocPoint(trip_origin);
                    short mask = (1 << 0);  // 0x0001

                    if (originalLocOrigin.equals(modifiedLocOrigin)) {
                        // flip bit[0] to 0
                        diff_fields &= ~mask;
                    }
                    else {
                        // flip bit[0] to 1
                        diff_fields |= mask;
                    }
                    checkDiff();
                }
                catch(Exception e) {}
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        input_trip_destination.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                label_trip_destination.setVisibility(View.GONE);

                if (!LocationService.isStarted()) return;

                try {
                    String trip_destination = s.toString();
                    LocPoint modifiedLocDestination = new LocPoint(trip_destination);
                    short mask = (1 << 1);  // 0x0002

                    if (originalLocDestination.equals(modifiedLocDestination)) {
                        // flip bit[1] to 0
                        diff_fields &= ~mask;
                    }
                    else {
                        // flip bit[1] to 1
                        diff_fields |= mask;
                    }
                    checkDiff();
                }
                catch(Exception e) {}
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        input_trip_duration.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                if (!LocationService.isStarted()) return;

                try {
                    String trip_duration = s.toString();
                    int modifiedTripDuration = Integer.parseInt(trip_duration, 10);
                    short mask = (1 << 2);  // 0x0004

                    if (originalTripDuration != modifiedTripDuration) {
                        // flip bit[2] to 0
                        diff_fields &= ~mask;
                    }
                    else {
                        // flip bit[2] to 1
                        diff_fields |= mask;
                    }
                    checkDiff();
                }
                catch(Exception e) {}
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        button_toggle_state.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (LocationService.isStarted()) {
                        LocationService.doStop(TripSimulationActivity.this, true);
                        button_toggle_state.setText(R.string.label_button_start);
                        button_update.setVisibility(View.GONE);
                    }
                    else {
                        requestPermissions();
                    }
                }
                catch(Exception e) { android.widget.Toast.makeText(TripSimulationActivity.this, e.getMessage() == null ? "输入或操作无效" : e.getMessage(), android.widget.Toast.LENGTH_SHORT).show(); }
            }
        });

        button_update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (LocationService.isStarted()) {
                        requestPermissions();
                    }
                    else {
                        button_update.setVisibility(View.GONE);
                    }
                }
                catch(Exception e) { android.widget.Toast.makeText(TripSimulationActivity.this, e.getMessage() == null ? "输入或操作无效" : e.getMessage(), android.widget.Toast.LENGTH_SHORT).show(); }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reset();
        if (pendingMapOrigin != null) {
            input_trip_origin.setText(pendingMapOrigin);
            pendingMapOrigin = null;
        }
        if (pendingMapDestination != null) {
            input_trip_destination.setText(pendingMapDestination);
            pendingMapDestination = null;
        }
    }

    private void openMap(TextView input, int requestCode) {
        try {
            LocPoint point = new LocPoint(input.getText().toString());
            startActivityForResult(MapPickerActivity.intent(this, point.toString(), false), requestCode);
        } catch (Exception error) {
            android.widget.Toast.makeText(this, error.getMessage() == null ? "坐标无效" : error.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == 201 || requestCode == 202) && resultCode == RESULT_OK) {
            String selected = MapPickerActivity.result(data);
            if (selected != null) {
                if (requestCode == 201) pendingMapOrigin = selected.trim();
                else pendingMapDestination = selected.trim();
                (requestCode == 201 ? input_trip_origin : input_trip_destination).setText(selected.trim());
            }
        }
    }

    private void reset() {
        input_trip_origin.setText(originalLocOrigin.toString());
        input_trip_destination.setText(originalLocDestination.toString());
        input_trip_duration.setText(Integer.toString(originalTripDuration, 10));

        BookmarkItem bmItem;
        bmItem = SharedPrefs.getBookmarkItem(TripSimulationActivity.this, originalLocOrigin);
        if (bmItem != null) {
            label_trip_origin.setText(bmItem.title);
            label_trip_origin.setVisibility(View.VISIBLE);
        }
        bmItem = SharedPrefs.getBookmarkItem(TripSimulationActivity.this, originalLocDestination);
        if (bmItem != null) {
            label_trip_destination.setText(bmItem.title);
            label_trip_destination.setVisibility(View.VISIBLE);
        }

        button_toggle_state.setText(LocationService.isStarted() ? R.string.label_button_stop : R.string.label_button_start);

        button_update.setVisibility(View.GONE);
    }

    private void checkDiff() {
        if (diff_fields == 0) {
            button_update.setVisibility(View.GONE);
        }
        else {
            button_update.setVisibility(View.VISIBLE);
        }
    }

    // =============================================================================================
    // interface invocation: RuntimePermissionsRequester
    // =============================================================================================

    private void requestPermissions() {
        RuntimePermissionsRequester requester = (RuntimePermissionsRequester) getParent();
        requester.requestRuntimePermissions();
    }

    // =============================================================================================
    // interface implementation: RuntimePermissionsListener
    // =============================================================================================

    public void doStart() {
        String trip_origin              = input_trip_origin.getText().toString();
        LocPoint modifiedLocOrigin      = new LocPoint(trip_origin);

        String trip_destination         = input_trip_destination.getText().toString();
        LocPoint modifiedLocDestination = new LocPoint(trip_destination);

        String trip_duration            = input_trip_duration.getText().toString();
        int modifiedTripDuration        = Integer.parseInt(trip_duration, 10);

        LocationService.doStart(TripSimulationActivity.this, true, modifiedLocOrigin, modifiedLocDestination, modifiedTripDuration);

        short mask;

        mask = (1 << 0);
        if ((diff_fields & mask) == mask) {
            SharedPrefs.putTripOrigin(TripSimulationActivity.this, modifiedLocOrigin);
        }

        mask = (1 << 1);
        if ((diff_fields & mask) == mask) {
            SharedPrefs.putTripDestination(TripSimulationActivity.this, modifiedLocDestination);
        }

        mask = (1 << 2);
        if ((diff_fields & mask) == mask) {
            SharedPrefs.putTripDuration(TripSimulationActivity.this, modifiedTripDuration);
        }

        originalLocOrigin      = modifiedLocOrigin;
        originalLocDestination = modifiedLocDestination;
        originalTripDuration   = modifiedTripDuration;
        diff_fields = 0;

        button_toggle_state.setText(R.string.label_button_stop);
        button_update.setVisibility(View.GONE);
    }
}
