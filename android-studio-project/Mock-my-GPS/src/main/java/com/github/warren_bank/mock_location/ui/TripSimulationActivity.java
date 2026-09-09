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
    private android.content.SharedPreferences drafts;

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
                catch(Exception e) { checkDiff(); }
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        input_trip_destination.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                label_trip_destination.setVisibility(View.GONE);


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
                catch(Exception e) { checkDiff(); }
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        input_trip_duration.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {

                try {
                    String trip_duration = s.toString();
                    int modifiedTripDuration = Integer.parseInt(trip_duration, 10);
                    short mask = (1 << 2);  // 0x0004

                    if (originalTripDuration == modifiedTripDuration) {
                        // flip bit[2] to 0
                        diff_fields &= ~mask;
                    }
                    else {
                        // flip bit[2] to 1
                        diff_fields |= mask;
                    }
                    checkDiff();
                }
                catch(Exception e) { checkDiff(); }
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
                        button_toggle_state.setActivated(false);
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
        drafts = getSharedPreferences("trip_draft", MODE_PRIVATE);
        initializeInputs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRuntimeState();
    }

    @Override protected void onPause() {
        drafts.edit().putString("origin", input_trip_origin.getText().toString())
            .putString("destination", input_trip_destination.getText().toString())
            .putString("duration", input_trip_duration.getText().toString()).apply();
        super.onPause();
    }

    public void refreshRuntimeState() {
        boolean running = LocationService.isStarted();
        button_toggle_state.setText(running ? R.string.label_button_stop : R.string.label_button_start);
        button_toggle_state.setActivated(running);
        button_toggle_state.setEnabled(true);
        checkDiff();
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
                (requestCode == 201 ? input_trip_origin : input_trip_destination).setText(selected.trim());
            }
        }
    }

    private void initializeInputs() {
        input_trip_origin.setText(drafts.getString("origin", originalLocOrigin.toString()));
        input_trip_destination.setText(drafts.getString("destination", originalLocDestination.toString()));
        input_trip_duration.setText(drafts.getString("duration", Integer.toString(originalTripDuration)));
        showBookmark(label_trip_origin, input_trip_origin);
        showBookmark(label_trip_destination, input_trip_destination);
        refreshRuntimeState();
    }

    private void showBookmark(TextView label, TextView input) {
        try {
            BookmarkItem item = SharedPrefs.getBookmarkItem(this, new LocPoint(input.getText().toString()));
            if (item != null) {
                label.setText(item.title);
                label.setVisibility(View.VISIBLE);
            }
        } catch (Exception ignored) {}
    }

    private void checkDiff() {
        // Re-evaluate the complete draft, including invalid/incomplete edits.
        boolean valid = false;
        try {
            LocPoint origin = new LocPoint(input_trip_origin.getText().toString());
            LocPoint destination = new LocPoint(input_trip_destination.getText().toString());
            int duration = Integer.parseInt(input_trip_duration.getText().toString());
            diff_fields = (short) ((!originalLocOrigin.equals(origin) ? 1 : 0)
                | (!originalLocDestination.equals(destination) ? 2 : 0)
                | (originalTripDuration != duration ? 4 : 0));
            valid = duration > 0;
        } catch (Exception ignored) {}
        boolean show = LocationService.isStarted() && valid && diff_fields != 0;
        button_update.setVisibility(show ? View.VISIBLE : View.GONE);
        button_update.setEnabled(show);
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

        SharedPrefs.putTripOrigin(this, modifiedLocOrigin);
        SharedPrefs.putTripDestination(this, modifiedLocDestination);
        SharedPrefs.putTripDuration(this, modifiedTripDuration);

        originalLocOrigin      = modifiedLocOrigin;
        originalLocDestination = modifiedLocDestination;
        originalTripDuration   = modifiedTripDuration;
        diff_fields = 0;

        button_toggle_state.setText(R.string.label_button_stop);
        button_toggle_state.setActivated(true);
        button_update.setVisibility(View.GONE);
    }
}
