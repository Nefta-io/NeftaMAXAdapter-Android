package com.nefta.max;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxRewardedAd;

public class RewardedDefault implements MaxRewardedAdListener, MaxAdRevenueListener, Rewarded {

    private RewardedUi _ui;
    private MaxRewardedAd _rewarded;
    private Handler _handler;
    private int _consecutiveAdFails;

    public void Init(RewardedUi ui) {
        _ui = ui;
        _handler = new Handler(Looper.getMainLooper());

        _rewarded = MaxRewardedAd.getInstance(Rewarded.AdUnitA);
        _rewarded.setListener(this);
        _rewarded.setRevenueListener(this);
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        NeftaMediationAdapter.OnExternalMediationRequestFailed(_rewarded, maxError);

        Log("Load failed " + adUnitId + ": " + maxError.getMessage());

        _consecutiveAdFails++;
        long waitTimeInMs = new int[]{0, 2, 4, 8, 16, 32, 64}[Math.min(_consecutiveAdFails, 6)] * 1000L;
        _handler.postDelayed(() -> {
            if (_ui.IsAutoLoad) {
                Load();
            }
        }, waitTimeInMs);
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationRequestLoaded(_rewarded, ad);

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
    public void onUserRewarded(@NonNull MaxAd ad, @NonNull MaxReward maxReward) {
        Log("onUserRewarded "+ ad.getAdUnitId());
    }

    @Override
    public void onAdHidden(@NonNull MaxAd ad) {
        Log("onAdHidden "+ ad.getAdUnitId());

        if (_ui.IsAutoLoad) {
            Load();
        }
    }

    @Override
    public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
        Log("onAdDisplayFailed "+ ad.getAdUnitId());

        if (_ui.IsAutoLoad) {
            Load();
        }
    }

    public void Load() {
        NeftaMediationAdapter.OnExternalMediationRequest(_rewarded);
        _rewarded.loadAd();
    }

    public void Show() {
        if (_rewarded.isReady()) {
            _rewarded.showAd(_ui.Activity);
        } else if (_ui.IsAutoLoad) {
            Load();
        }

        _ui.SetAvailability(false);
    }

    void Log(String log) {
        _ui.Log(log);
    }
}
