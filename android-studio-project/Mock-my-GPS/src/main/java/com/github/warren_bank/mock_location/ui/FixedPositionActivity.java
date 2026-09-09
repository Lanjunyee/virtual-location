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

public class FixedPositionActivity extends Activity implements RuntimePermissionsListener {
    private LocPoint originalLoc;
    private android.content.SharedPreferences drafts;

    private TextView label_fixed_position;
    private TextView input_fixed_position;
    private Button   button_toggle_state;
    private Button   button_update;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fixed_position);

        originalLoc = SharedPrefs.getTripOrigin(FixedPositionActivity.this);

        label_fixed_position = (TextView) findViewById(R.id.label_fixed_position);
        input_fixed_position = (TextView) findViewById(R.id.input_fixed_position);
        button_toggle_state  = (Button)   findViewById(R.id.button_toggle_state);
        button_update        = (Button)   findViewById(R.id.button_update);

        findViewById(R.id.button_map_fixed_position).setOnClickListener(v -> {
            try {
                LocPoint point = new LocPoint(input_fixed_position.getText().toString());
                startActivityForResult(MapPickerActivity.intent(this, point.toString(), false), 201);
            } catch (Exception error) {
                android.widget.Toast.makeText(this, error.getMessage() == null ? "坐标无效" : error.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        input_fixed_position.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                label_fixed_position.setVisibility(View.GONE);

                refreshRuntimeState();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        button_toggle_state.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (LocationService.isStarted()) {
                        LocationService.doStop(FixedPositionActivity.this, true);
                        button_toggle_state.setText(R.string.label_button_start);
                        button_toggle_state.setActivated(false);
                        button_update.setVisibility(View.GONE);
                    }
                    else {
                        requestPermissions();
                    }
                }
                catch(Exception e) { android.widget.Toast.makeText(FixedPositionActivity.this, e.getMessage() == null ? "输入或操作无效" : e.getMessage(), android.widget.Toast.LENGTH_SHORT).show(); }
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
                catch(Exception e) { android.widget.Toast.makeText(FixedPositionActivity.this, e.getMessage() == null ? "输入或操作无效" : e.getMessage(), android.widget.Toast.LENGTH_SHORT).show(); }
            }
        });
        drafts = getSharedPreferences("fixed_draft", MODE_PRIVATE);
        initializeInputs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRuntimeState();
    }

    @Override protected void onPause() {
        drafts.edit().putString("position", input_fixed_position.getText().toString()).apply();
        super.onPause();
    }

    public void refreshRuntimeState() {
        boolean running = LocationService.isStarted();
        button_toggle_state.setText(running ? R.string.label_button_stop : R.string.label_button_start);
        button_toggle_state.setActivated(running);
        button_toggle_state.setEnabled(true);
        boolean changed = false;
        try { changed = !originalLoc.equals(new LocPoint(input_fixed_position.getText().toString())); }
        catch (Exception ignored) {}
        button_update.setVisibility(running && changed ? View.VISIBLE : View.GONE);
        button_update.setEnabled(running && changed);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 201 && resultCode == RESULT_OK) {
            String selected = MapPickerActivity.result(data);
            if (selected != null) {
                input_fixed_position.setText(selected.trim());
            }
        }
    }

    private void initializeInputs() {
        input_fixed_position.setText(drafts.getString("position", originalLoc.toString()));

        try {
            LocPoint point = new LocPoint(input_fixed_position.getText().toString());
            BookmarkItem bmItem = SharedPrefs.getBookmarkItem(this, point);
            if (bmItem != null) {
                label_fixed_position.setText(bmItem.title);
                label_fixed_position.setVisibility(View.VISIBLE);
            }
        } catch (Exception ignored) {}
        refreshRuntimeState();
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
        String fixed_position = input_fixed_position.getText().toString();
        LocPoint modifiedLoc  = new LocPoint(fixed_position);

        LocationService.doStart(FixedPositionActivity.this, true, modifiedLoc, null, 0);

        SharedPrefs.putTripOrigin(FixedPositionActivity.this, modifiedLoc);
        originalLoc = modifiedLoc;

        button_toggle_state.setText(R.string.label_button_stop);
        button_toggle_state.setActivated(true);
        button_update.setVisibility(View.GONE);
    }
}
