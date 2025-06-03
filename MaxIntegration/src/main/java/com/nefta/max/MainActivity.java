package com.nefta.max;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

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

    interface IOnFullScreenAdDisplay {
        void invoke(boolean displayed);
    }

    private static boolean _isTablet;
    private static FrameLayout _bannerPlaceholder;
    private static FrameLayout _leaderPlaceholder;

    public static ViewGroup GetBannerPlaceholder() {
        return _isTablet ? _leaderPlaceholder : _bannerPlaceholder;
    }

    private BannerWrapper _bannerWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        View view = findViewById(R.id.frameLayout);
        _bannerPlaceholder = (FrameLayout) view.findViewById(R.id.bannerView);
        _leaderPlaceholder = (FrameLayout) view.findViewById(R.id.leaderView);

        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        Display display = windowManager.getDefaultDisplay();
        display.getMetrics(displayMetrics);
        Point point = new Point();
        display.getRealSize(point);
        double diagonalInInches = Math.sqrt(Math.pow((double)point.x / displayMetrics.xdpi, 2) + Math.pow((double)point.y / displayMetrics.ydpi, 2));
        _isTablet = diagonalInInches >= 6.5 && (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        _bannerPlaceholder.setVisibility(_isTablet ? View.GONE : View.VISIBLE);
        _leaderPlaceholder.setVisibility(_isTablet ? View.VISIBLE : View.GONE);

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
        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder( "IAhBswbDpMg9GhQ8NEKffzNrXQP1H4ABNFvUA7ePIz2xmarVFcy_VB8UfGnC9IPMOgpQ3p8G5hBMebJiTHv3P9" )
                .setMediationProvider( AppLovinMediationProvider.MAX )
                .build();

        AppLovinSdk sdk = AppLovinSdk.getInstance( this );

        sdk.initialize( initConfig, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration sdkConfig) {
                AppLovinSdk.getInstance(MainActivity.this).getSettings().setVerboseLogging(true);
            }
        });

        _bannerWrapper = new BannerWrapper(findViewById(R.id.showBanner), findViewById(R.id.closeBanner));
        new InterstitialWrapper(this, findViewById(R.id.loadInterstitial), findViewById(R.id.showInterstitial), this::OnFullScreenAdDisplay);
        new RewardedWrapper(this, findViewById(R.id.loadRewarded), findViewById(R.id.showRewarded), this::OnFullScreenAdDisplay);
    }

    private void OnFullScreenAdDisplay(boolean displayed) {
        _bannerWrapper.SetAutoRefresh(!displayed);
    }
}
