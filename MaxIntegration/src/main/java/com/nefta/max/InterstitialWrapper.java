package com.nefta.max;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdExpirationListener;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdRequestListener;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxAdReviewListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

class InterstitialWrapper implements MaxAdListener, MaxAdRevenueListener, MaxAdRequestListener, MaxAdReviewListener, MaxAdExpirationListener {
    private final String DefaultAdUnitId = "7267e7f4187b95b2";
    private final int TimeoutInSeconds = 5;

    private MaxInterstitialAd _interstitial;
    private AdInsight _usedInsight;
    private int _consecutiveAdFails;

    private MainActivity _activity;
    private Button _loadButton;
    private Button _showButton;
    private Handler _handler;

    private void GetInsightsAndLoad() {
        NeftaPlugin._instance.GetInsights(Insights.INTERSTITIAL, this::Load, TimeoutInSeconds);
    }

    private void Load(Insights insights) {
        String selectedAdUnitId = DefaultAdUnitId;
        _usedInsight = insights._interstitial;
        if (_usedInsight != null && _usedInsight._adUnit != null) {
            selectedAdUnitId = _usedInsight._adUnit;
        }

        Log("Loading "+ selectedAdUnitId +" insights: "+ _usedInsight);
        _interstitial = new MaxInterstitialAd(selectedAdUnitId);
        _interstitial.setListener(InterstitialWrapper.this);
        _interstitial.setRevenueListener(InterstitialWrapper.this);
        _interstitial.setExpirationListener(InterstitialWrapper.this);
        _interstitial.setExtraParameter("disable_auto_retries", "true");
        _interstitial.loadAd();
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Interstitial, adUnitId, _usedInsight, maxError);

        Log("onAdLoadFailed "+ adUnitId + ": "+ maxError.getMessage());

        _consecutiveAdFails++;
        // As per MAX recommendations, retry with exponentially higher delays up to 64s
        // In case you would like to customize fill rate / revenue please contact our customer support
        int delayInSeconds = new int[] { 0, 2, 4, 8, 16, 32, 64 } [Math.min(_consecutiveAdFails, 6)];

        _handler.postDelayed(this::GetInsightsAndLoad, delayInSeconds * 1000L);
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Interstitial, ad, _usedInsight);

        Log("onAdLoaded "+ ad.getAdUnitId() +": "+ ad.getRevenue());

        _consecutiveAdFails = 0;
        _showButton.setEnabled(true);
    }

    @Override
    public void onAdRevenuePaid(final MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationImpression(ad);

        Log("onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }

    public InterstitialWrapper(MainActivity activity, Button loadButton, Button showButton) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;

        _handler = new Handler(Looper.getMainLooper());

        _loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log("GetInsightsAndLoad...");
                GetInsightsAndLoad();
                _loadButton.setEnabled(false);
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
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log("onAdDisplayed"+ ad.getAdUnitId());
        _activity.OnFullScreenAdDisplay(true);
    }

    @Override
    public void onAdHidden(@NonNull MaxAd ad) {
        Log("onAdHidden "+ ad.getAdUnitId());
        _activity.OnFullScreenAdDisplay(false);
        _loadButton.setEnabled(true);
    }

    @Override
    public void onAdClicked(@NonNull MaxAd ad) {
        Log("onAdClicked "+ ad.getAdUnitId());
    }

    @Override
    public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
        Log("onAdDisplayFailed "+ ad.getAdUnitId());
    }

    @Override
    public void onAdRequestStarted(@NonNull String var1) {
        Log("onAdRequestStarted "+ var1);
    }

    @Override
    public void onCreativeIdGenerated(@NonNull String var1, @NonNull MaxAd var2) {
        Log("onCreativeIdGenerated "+ var1 + ": "+ var2);
    }

    @Override
    public void onExpiredAdReloaded(@NonNull MaxAd var1, @NonNull MaxAd var2) {
        Log("onExpiredAdReloaded "+ var1 + ": "+ var2);
    }

    void Log(String log) {
        _activity.Log("Interstitial " + log);
    }
}
