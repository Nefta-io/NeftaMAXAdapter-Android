package com.nefta.max;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxInterstitialAd;

public class InterstitialDefault implements MaxAdListener, MaxAdRevenueListener, Interstitial {

    private InterstitialUi _ui;
    private MaxInterstitialAd _interstitial;
    private Handler _handler;
    private int _consecutiveAdFails;

    public void Init(InterstitialUi ui) {
        _ui = ui;
        _handler = new Handler(Looper.getMainLooper());

        _interstitial = new MaxInterstitialAd(Interstitial.AdUnitA);
        _interstitial.setListener(this);
        _interstitial.setRevenueListener(this);
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        NeftaMediationAdapter.OnExternalMediationRequestFailed(_interstitial, maxError);

        Log("Load failed " + adUnitId + ": " + maxError.getMessage());

        _consecutiveAdFails++;
        long waitTimeInMs = new int[]{0, 2, 4, 8, 16, 32, 64}[Math.min(_consecutiveAdFails, 6)] * 1000L;
        _handler.postDelayed(() -> {
            Load();
        }, waitTimeInMs);
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationRequestLoaded(_interstitial, ad);

        Log("Loaded  "+ ad +" at: "+ ad.getRevenue());

        _consecutiveAdFails = 0;

        _ui.SetAvailability(true);
    }

    @Override
    public void onAdRevenuePaid(final MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationImpression(ad);

        Log("onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log("onAdDisplayed"+ ad.getAdUnitId());
    }

    @Override
    public void onAdClicked(@NonNull MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationClick(ad);

        Log("onAdClicked "+ ad.getAdUnitId());
    }

    @Override
    public void onAdHidden(@NonNull MaxAd ad) {
        Log("onAdHidden "+ ad.getAdUnitId());

        Load();
    }

    @Override
    public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
        Log("onAdDisplayFailed "+ ad.getAdUnitId());

        Load();
    }

    public void Load() {
        if (!_ui.IsAutoLoad) {
            return;
        }

        NeftaMediationAdapter.OnExternalMediationRequest(_interstitial);
        _interstitial.loadAd();
    }

    public void Show() {
        if (_interstitial.isReady()) {
            _interstitial.showAd(_ui.Activity);
        } else {
            Load();
        }

        _ui.SetAvailability(false);
    }

    void Log(String log) {
        _ui.Log(log);
    }
}
