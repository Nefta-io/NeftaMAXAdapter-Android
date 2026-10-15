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
    private final String[] _dynamicAdUnits = new String[] {
        Interstitial.AdUnitOptimizedA, Interstitial.AdUnitOptimizedA,
        Rewarded.AdUnitOptimizedA, Rewarded.AdUnitOptimizedB
    };

    private CheckBox _consentCheckBox;
    private InterstitialUi _interstitialUi;
    private RewardedUi _rewardedUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        InitUI();
        DebugServer.Init(this, getIntent());
    }

    private void InitializeNefta(NeftaPlugin.OptimizationType optimizationType) {
        NeftaPlugin.EnableLogging(true);
        NeftaPlugin.SetHasUserConsent(_consentCheckBox.isChecked());
        NeftaMediationAdapter.InitWithAppId(getApplicationContext(), "5632029345447936", optimizationType, (InitConfiguration config) -> {
            Log.i("NeftaPluginMAX", "Nefta initialized nuid: " + config._nuid + " should optimize: "+ config._isSessionOptimized);

            boolean isSimulator = ((CheckBox)findViewById(R.id.isSimulator)).isChecked();
            if (isSimulator) {
                ((InterstitialSim) findViewById(R.id.interstitialSim)).Init(config._isSessionOptimized);
                ((RewardedSim) findViewById(R.id.rewardedSim)).Init(config._isSessionOptimized);
            } else if (config._isSessionOptimized) {
                _interstitialUi.Init(new InterstitialOptimized());
                _rewardedUi.Init(new RewardedOptimized());
            } else {
                _interstitialUi.Init(new InterstitialDefault());
                _rewardedUi.Init(new RewardedDefault());
            }
        });
    }

    private void InitializeMAX() {
        Log.i("NeftaPluginMAX", "Initializing MAX");

        AppLovinPrivacySettings.setHasUserConsent(_consentCheckBox.isChecked());
        AppLovinSdk sdk = AppLovinSdk.getInstance(this);
        sdk.getSettings().setVerboseLogging(true);

        sdk.getSettings().setExtraParameter("disable_b2b_ad_unit_ids", String.join(",", _dynamicAdUnits));

        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder(BuildConfig.MAX_KEY)
                .setMediationProvider( AppLovinMediationProvider.MAX)
                .setTestDeviceAdvertisingIds(Arrays.asList("97ec28e2-e65a-4fac-b11e-3975391f7cb7", "dca773a6-3445-4776-b361-4d950a0e212f"))
                .build();
        sdk.initialize( initConfig, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration sdkConfig) {

            }
        });
    }

    private void InitUI() {
        TextView title = findViewById(R.id.title);
        title.setText("MAX Integration "+ AppLovinSdk.VERSION);

        _consentCheckBox = findViewById(R.id.hasConsent);
        _consentCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NeftaPlugin.SetHasUserConsent(_consentCheckBox.isChecked());
            }
        });

        findViewById(R.id.unknown).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Initialize(NeftaPlugin.OptimizationType.Unknown);
            }
        });
        findViewById(R.id.optimized).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Initialize(NeftaPlugin.OptimizationType.Optimized);
            }
        });
        findViewById(R.id.notOptimized).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Initialize(NeftaPlugin.OptimizationType.NotOptimized);
            }
        });

        _interstitialUi = findViewById(R.id.interstitial);
        _rewardedUi = findViewById(R.id.rewarded);
    }

    private void Initialize(NeftaPlugin.OptimizationType optimizationType) {
        findViewById(R.id.groupView).setVisibility(View.GONE);

        InitializeNefta(optimizationType);

        boolean isSimulator = ((CheckBox)findViewById(R.id.isSimulator)).isChecked();
        if (!isSimulator) {
            InitializeMAX();
        }
    }
}
