package com.applovin.enterprise.apps.demoapp;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.nefta.sdk.NeftaPlugin;


import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    private BannerWrapper _bannerWrapper;
    private InterstitialWrapper _interstitialWrapper;
    private RewardedVideoWrapper _rewardedVideoWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        NeftaPlugin.Init(this, "5643649824063488");

        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder( "IAhBswbDpMg9GhQ8NEKffzNrXQP1H4ABNFvUA7ePIz2xmarVFcy_VB8UfGnC9IPMOgpQ3p8G5hBMebJiTHv3P9", this )
            .setMediationProvider( AppLovinMediationProvider.MAX )
            .build();
        AppLovinSdk.getInstance( this ).initialize( initConfig, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration sdkConfig) {

            }
        });

        _bannerWrapper = new BannerWrapper(this, findViewById(R.id.bannerView), findViewById(R.id.showBanner), findViewById(R.id.closeBanner));
        _interstitialWrapper = new InterstitialWrapper(this, findViewById(R.id.showInterstitial));
        _rewardedVideoWrapper = new RewardedVideoWrapper(this, findViewById(R.id.showRewardedVideo));
    }
}
