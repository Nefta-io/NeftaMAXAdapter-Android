package com.applovin.enterprise.apps.demoapp;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxAdView;

class BannerWrapper implements MaxAdViewAdListener, MaxAdRevenueListener {
    private final String TAG = "BANNER";
    private Activity _activity;
    private ViewGroup _bannerGroup;
    private Button _loadAndShowButton;
    private Button _closeButton;
    private MaxAdView _adView;

    public BannerWrapper(Activity activity, ViewGroup bannerGroup, Button loadAndShowButton, Button closeButton) {
        _activity = activity;
        _bannerGroup = bannerGroup;
        _loadAndShowButton = loadAndShowButton;
        _closeButton = closeButton;
        _loadAndShowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoadAndShow();
            }
        });
        _closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Close();
            }
        });

        _closeButton.setEnabled(false);
    }
    private void LoadAndShow() {
        _adView = new MaxAdView("4c659b0149dbfbae", _activity);
        _adView.setListener(this);
        _adView.setRevenueListener(this);
        _bannerGroup.addView(_adView.getRootView());
        _adView.loadAd();

        _loadAndShowButton.setEnabled(false);
    }

    private void Close() {
        _adView.destroy();
        _adView = null;

        _loadAndShowButton.setEnabled(true);
        _closeButton.setEnabled(false);
    }

    @Override
    public void onAdLoaded(final MaxAd ad) {
        Log.i(TAG, "onAdLoaded "+ ad);
        _closeButton.setEnabled(true);
    }

    @Override
    public void onAdLoadFailed(@NonNull final String adUnitId, @NonNull final MaxError error) {
        Log.i(TAG, "onAdLoadFailed "+ adUnitId);

        _loadAndShowButton.setEnabled(true);
        _closeButton.setEnabled(false);
    }

    @Override
    public void onAdDisplayFailed(@NonNull final MaxAd ad, @NonNull final MaxError error) {
        Log.i(TAG, "onAdDisplayFailed "+ ad.getAdUnitId());

        _loadAndShowButton.setEnabled(true);
        _closeButton.setEnabled(false);
    }

    @Override
    public void onAdClicked(@NonNull final MaxAd ad) {
        Log.i(TAG, "onAdClicked "+ ad.getAdUnitId());
    }

    @Override
    public void onAdExpanded(@NonNull final MaxAd ad) {
        Log.i(TAG, "onAdExpanded "+ ad.getAdUnitId());
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdDisplayed "+ ad.getAdUnitId());
    }

    @Override
    public void onAdHidden(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdHidden "+ ad.getAdUnitId());

        _loadAndShowButton.setEnabled(true);
        _closeButton.setEnabled(false);
    }

    @Override
    public void onAdCollapsed(@NonNull final MaxAd ad) {
        Log.i(TAG, "onAdCollapsed "+ ad.getAdUnitId());
    }

    @Override
    public void onAdRevenuePaid(final MaxAd ad) {
        Log.i(TAG, "onAdRevenuePaid"+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }
}
