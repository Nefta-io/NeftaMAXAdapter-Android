package com.nefta.max;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class InterstitialUi extends TableLayout {

    private Button _showButton;
    private TextView _status;

    private Interstitial _logic;

    public Activity Activity;
    public boolean IsAutoLoad;

    public InterstitialUi(Context context) {
        super(context);
        if (context instanceof Activity) {
            Activity = (Activity) context;
        }
    }

    public InterstitialUi(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (context instanceof Activity) {
            Activity = (Activity) context;
        }
    }

    public void Init(Interstitial logic) {
        _logic = logic;
        _logic.Init(this);

        setVisibility(VISIBLE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Switch loadSwitch = findViewById(R.id.interstitial_load);
        _showButton = findViewById(R.id.interstitial_show);
        _showButton.setEnabled(false);
        _status = findViewById(R.id.interstitial_status);

        loadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                IsAutoLoad = isChecked;
                if (IsAutoLoad) {
                    _logic.Load();
                }
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                _logic.Show();
            }
        });
    }

    public void SetAvailability(boolean isAvailable) {
        _showButton.setEnabled(isAvailable);
    }

    void Log(String log) {
        _status.setText(log);
        Log.i("InterstitialDefault", "Interstitial " + log);
    }
}

