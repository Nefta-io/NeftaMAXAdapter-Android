package com.nefta.max;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
        // interstitial
        "87f1b4837da231e5", // track A
        "7267e7f4187b95b2", // track B
        // rewarded
        "a4b93fe91b278c75", // track A
        "72458470d47ee781"  // track B
    };

    private boolean _isSimulator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        InitUI();
        DebugServer.Init(this, getIntent());

        NeftaPlugin.EnableLogging(true);
        NeftaMediationAdapter.InitWithAppId(getApplicationContext(), "5632029345447936", (InitConfiguration config) -> {
            Log.i("NeftaPluginMAX", "Should skip Nefta optimization: " + config._skipOptimization + " for: " + config._nuid);
        });

        AppLovinPrivacySettings.setHasUserConsent(true);
        AppLovinSdk sdk = AppLovinSdk.getInstance(this);
        sdk.getSettings().setVerboseLogging(true);
        sdk.getSettings().setExtraParameter("disable_b2b_ad_unit_ids", String.join(",", _dynamicAdUnits));
        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder(BuildConfig.MAX_KEY)
                .setMediationProvider( AppLovinMediationProvider.MAX )
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
        title.setOnClickListener(v -> ToggleUI(!_isSimulator));
        ToggleUI(BuildConfig.IS_SIMULATOR);
    }

    private void ToggleUI(boolean isSimulator) {
        _isSimulator = isSimulator;

        findViewById(R.id.interstitialSim).setVisibility(_isSimulator ? View.VISIBLE : View.GONE);
        findViewById(R.id.rewardedSim).setVisibility(_isSimulator ? View.VISIBLE : View.GONE);

        findViewById(R.id.interstitial).setVisibility(_isSimulator ? View.GONE : View.VISIBLE);
        findViewById(R.id.rewarded).setVisibility(_isSimulator ? View.GONE : View.VISIBLE);
    }
}
