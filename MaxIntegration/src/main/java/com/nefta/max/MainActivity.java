package com.nefta.max;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinPrivacySettings;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.nefta.sdk.InitConfiguration;
import com.nefta.sdk.NeftaPlugin;
import com.nefta.debug.DebugServer;

import androidx.appcompat.app.AppCompatActivity;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private String[] _dynamicAdUnits = new String[] {
        Interstitial.AdUnitA, Interstitial.AdUnitA,
        Rewarded.AdUnitA, Rewarded.AdUnitB
    };

    private CheckBox _consentCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        InitUI();
        DebugServer.Init(this, getIntent());

    }

    private void InitializeNefta() {
        NeftaPlugin.EnableLogging(true);
        NeftaMediationAdapter.InitWithAppId(getApplicationContext(), "5632029345447936", (InitConfiguration config) -> {
            Log.i("NeftaPluginMAX", "Nefta initialized nuid: " + config._nuid);
        });
    }

    private void InitializeMAX(boolean isOptimized) {
        AppLovinPrivacySettings.setHasUserConsent(true);
        AppLovinSdk sdk = AppLovinSdk.getInstance(this);
        sdk.getSettings().setVerboseLogging(true);

        NeftaPlugin.SetInterstitialLogic(isOptimized);
        NeftaPlugin.SetRewardedLogic(isOptimized);
        if (isOptimized) {
            sdk.getSettings().setExtraParameter("disable_b2b_ad_unit_ids", String.join(",", _dynamicAdUnits));
        }

        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder(BuildConfig.MAX_KEY)
                .setMediationProvider( AppLovinMediationProvider.MAX)
                .setTestDeviceAdvertisingIds(Arrays.asList("97ec28e2-e65a-4fac-b11e-3975391f7cb7", "dca773a6-3445-4776-b361-4d950a0e212f"))
                .build();
        sdk.initialize( initConfig, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration sdkConfig) {
            }
        });

        InterstitialUi interstitialUi = findViewById(R.id.interstitial);
        RewardedUi rewardedUi = findViewById(R.id.rewarded);
        if (isOptimized) {
            interstitialUi.Init(new InterstitialOptimized());
            rewardedUi.Init(new RewardedOptimized());
        } else {
            interstitialUi.Init(new InterstitialDefault());
            rewardedUi.Init(new RewardedDefault());
        }
    }

    private void InitUI() {
        TextView title = findViewById(R.id.title);
        title.setText("MAX Integration "+ AppLovinSdk.VERSION);

        _consentCheckBox = findViewById(R.id.hasConsent);
        _consentCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NeftaPlugin.SetHasUserConsent(false);
                _consentCheckBox.setEnabled(false);
            }
        });

        findViewById(R.id.control).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.groupView).setVisibility(View.GONE);

                InitializeNefta();
                InitializeMAX(false);
            }
        });
        findViewById(R.id.optimized).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.groupView).setVisibility(View.GONE);

                InitializeNefta();
                InitializeMAX(true);
            }
        });
        findViewById(R.id.simulator).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InitializeNefta();

                findViewById(R.id.groupView).setVisibility(View.GONE);

                NeftaPlugin.SetInterstitialLogic(true);
                findViewById(R.id.interstitialSim).setVisibility(View.VISIBLE);
                NeftaPlugin.SetRewardedLogic(true);
                findViewById(R.id.rewardedSim).setVisibility(View.VISIBLE);
            }
        });
    }
}
