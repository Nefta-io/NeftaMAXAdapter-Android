package com.nefta.max;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxInterstitialAd;

class InterstitialWrapper implements MaxAdListener, MaxAdRevenueListener {
    private final String TAG = "INTERSTITIAL";
    private Activity _activity;
    private Button _loadButton;
    private Button _showButton;
    MaxInterstitialAd _interstitial;

    public InterstitialWrapper(Activity activity, Button loadButton, Button showButton) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;

        _loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _interstitial = new MaxInterstitialAd("7267e7f4187b95b2", _activity);
                _interstitial.setListener(InterstitialWrapper.this);
                _interstitial.setRevenueListener(InterstitialWrapper.this);
                _interstitial.loadAd();
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _interstitial.showAd(_activity);

                _showButton.setEnabled(false);
            }
        });

        _showButton.setEnabled(false);
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdLoaded "+ ad.getAdUnitId());

        _showButton.setEnabled(true);
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdDisplayed"+ ad.getAdUnitId());
        NeftaMediationAdapter.OnExternalAdShown(ad);
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
