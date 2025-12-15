package com.nefta.max;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinPrivacySettings;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.nefta.sdk.InitConfiguration;
import com.nefta.sdk.NeftaPlugin;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private final static String preferences = "preferences";
    private final static String trackingKey = "tracking";

    private String[] _dynamicAdUnits = new String[] {
        // interstitial
        "87f1b4837da231e5", // track A
        "7267e7f4187b95b2", // track B
        // rewarded
        "a4b93fe91b278c75", // track A
        "72458470d47ee781"  // track B
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        DebugServer.Init(this, getIntent());

        NeftaPlugin.EnableLogging(true);
        NeftaPlugin.Init(getApplicationContext(), "5643649824063488").OnReady = (InitConfiguration config) -> {
            Log.i("NeftaPluginMAX", "Should bypass Nefta optimization? " + config._skipOptimization);
        };
    }

    public void SetTracking() {
        SharedPreferences sharedPreferences = getSharedPreferences(preferences, Context.MODE_PRIVATE);
        if (sharedPreferences.contains(trackingKey)) {
            boolean isTrackingAllowed = sharedPreferences.getBoolean(trackingKey, false);
            AppLovinPrivacySettings.setHasUserConsent(isTrackingAllowed, this);

            InitMax();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            DialogInterface.OnClickListener trackingHandler = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    boolean isAllowed = which == -1;
                    AppLovinPrivacySettings.setHasUserConsent(isAllowed, MainActivity.this);

                    SharedPreferences sharedPreferences = getSharedPreferences(preferences, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(trackingKey, isAllowed);
                    editor.apply();

                    InitMax();
                }
            };
            builder.setTitle("Advertising id access")
                    .setMessage("Is tracking allowed")
                    .setPositiveButton("Yes", trackingHandler)
                    .setNegativeButton("No", trackingHandler);

            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void InitMax() {
        AppLovinSdk sdk = AppLovinSdk.getInstance(this);
        sdk.getSettings().setVerboseLogging(true);
        sdk.getSettings().setExtraParameter("disable_b2b_ad_unit_ids", String.join(",", _dynamicAdUnits));
        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder(BuildConfig.MAX_KEY)
                .setMediationProvider( AppLovinMediationProvider.MAX )
                .build();
        sdk.initialize( initConfig, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration sdkConfig) {
            }
        });
    }
}
