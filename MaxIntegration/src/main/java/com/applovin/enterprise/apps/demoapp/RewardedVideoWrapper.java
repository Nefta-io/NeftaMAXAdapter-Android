package com.applovin.enterprise.apps.demoapp;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.ads.MaxRewardedAd;

public class RewardedVideoWrapper implements MaxRewardedAdListener, MaxAdRevenueListener {
    private final String TAG = "REWARDED_VIDEO";
    private Activity _activity;
    private Button _loadButton;
    private Button _showButton;
    MaxRewardedAd _rewardedAd;

    public RewardedVideoWrapper(Activity activity, Button loadButton, Button showButton) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;
        _loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _rewardedAd = MaxRewardedAd.getInstance("72458470d47ee781", _activity);
                _rewardedAd.setListener(RewardedVideoWrapper.this);
                _rewardedAd.setRevenueListener(RewardedVideoWrapper.this);
                _rewardedAd.loadAd();
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _rewardedAd.showAd(_activity);

                _showButton.setEnabled(false);
            }
        });

        _showButton.setEnabled(false);
    }

    @Override
    public void onUserRewarded(@NonNull MaxAd ad, @NonNull MaxReward maxReward) {
        Log.i(TAG, "onUserRewarded "+ ad.getAdUnitId());
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdLoaded "+ ad.getAdUnitId());

        _showButton.setEnabled(true);
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdDisplayed "+ ad.getAdUnitId());
    }

    @Override
    public void onAdHidden(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdHidden "+ ad.getAdUnitId());
    }

    @Override
    public void onAdClicked(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdClicked "+ ad.getAdUnitId());
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        Log.i(TAG, "onAdLoadFailed "+ adUnitId);
    }

    @Override
    public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
        Log.i(TAG, "onAdDisplayFailed "+ ad.getAdUnitId());
    }

    @Override
    public void onAdRevenuePaid(final MaxAd ad) {
        Log.i(TAG, "onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }
}