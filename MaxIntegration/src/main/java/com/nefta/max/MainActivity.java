package com.nefta.max;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinPrivacySettings;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.nefta.sdk.NeftaPlugin;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
public class MainActivity extends AppCompatActivity {

    private final static String preferences = "preferences";
    private final static String trackingKey = "tracking";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        NeftaPlugin.EnableLogging(true);
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            String override = intent.getStringExtra("override");
            if (override != null && override.length() > 2) {
                NeftaPlugin.SetOverride(override);
            }
        }

        NeftaPlugin.Init(this, "5643649824063488");

        SetTracking();
    }

    private void SetTracking() {
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
        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder( "IAhBswbDpMg9GhQ8NEKffzNrXQP1H4ABNFvUA7ePIz2xmarVFcy_VB8UfGnC9IPMOgpQ3p8G5hBMebJiTHv3P9", this )
                .setMediationProvider( AppLovinMediationProvider.MAX )
                .build();

        AppLovinSdk sdk = AppLovinSdk.getInstance( this );
        sdk.initialize( initConfig, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration sdkConfig) {
                AppLovinSdk.getInstance(MainActivity.this).getSettings().setVerboseLogging(true);
            }
        });

        new BannerWrapper(this, findViewById(R.id.bannerView), findViewById(R.id.showBanner), findViewById(R.id.closeBanner));
        new InterstitialWrapper(this, findViewById(R.id.loadInterstitial), findViewById(R.id.showInterstitial));
        new RewardedWrapper(this, findViewById(R.id.loadRewardedVideo), findViewById(R.id.showRewardedVideo));
    }
}
