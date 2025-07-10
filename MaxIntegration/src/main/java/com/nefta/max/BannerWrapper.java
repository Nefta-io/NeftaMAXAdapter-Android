package com.nefta.max;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxAdView;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

class BannerWrapper implements MaxAdViewAdListener, MaxAdRevenueListener {
    private final String DefaultAdUnitId = "f655876e93d11263";
    private final int TimeoutInSeconds = 5;

    private MaxAdView _adView;
    private AdInsight _usedInsight;
    private int _consecutiveAdFails;

    private Button _loadAndShowButton;
    private Button _closeButton;
    private Handler _handler;
    private MainActivity _activity;

    private void GetInsightsAndLoad() {
        NeftaPlugin._instance.GetInsights(Insights.BANNER, this::OnInsights, TimeoutInSeconds);
    }

    private void OnInsights(Insights insights) {
        String selectedAdUnitId = DefaultAdUnitId;
        _usedInsight = insights._banner;
        if (_usedInsight != null && _usedInsight._adUnit != null) {
            selectedAdUnitId = _usedInsight._adUnit;
        }

        Log("Loading "+ selectedAdUnitId +" insights: "+ _usedInsight);
        _adView = new MaxAdView(selectedAdUnitId);
        _adView.setListener(BannerWrapper.this);
        _adView.setRevenueListener(BannerWrapper.this);
        _adView.setExtraParameter("disable_auto_retries", "true");
        _activity.GetBannerPlaceholder().addView(_adView.getRootView());
        _adView.loadAd();
    }

    @Override
    public void onAdLoadFailed(@NonNull final String adUnitId, @NonNull final MaxError maxError) {
        NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Banner, adUnitId, _usedInsight, maxError);

        Log("onAdLoadFailed "+ adUnitId);

        _consecutiveAdFails++;
        // As per MAX recommendations, retry with exponentially higher delays up to 64s
        // In case you would like to customize fill rate / revenue please contact our customer support
        int delayInSeconds = new int[] { 0, 2, 4, 8, 16, 32, 64 } [Math.min(_consecutiveAdFails, 6)];

        _handler.postDelayed(this::GetInsightsAndLoad, delayInSeconds * 1000L);
    }

    @Override
    public void onAdLoaded(final MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Banner, ad, _usedInsight);

        Log("onAdLoaded "+ ad);

        _consecutiveAdFails = 0;

        _closeButton.setEnabled(true);
    }

    public BannerWrapper(MainActivity activity, Button loadAndShowButton, Button closeButton) {
        _activity = activity;
        _loadAndShowButton = loadAndShowButton;
        _closeButton = closeButton;

        _handler = new Handler(Looper.getMainLooper());

        _loadAndShowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log("GetInsightsAndLoad...");
                GetInsightsAndLoad();
                loadAndShowButton.setEnabled(false);
            }
        });
        _closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _adView.stopAutoRefresh();
                ViewGroup parent = (ViewGroup) _adView.getParent();
                if (parent != null) {
                    parent.removeView(_adView);
                }
                _adView.destroy();
                _adView = null;

                _loadAndShowButton.setEnabled(true);
                _closeButton.setEnabled(false);
            }
        });

        _closeButton.setEnabled(false);
    }

    public void SetAutoRefresh(boolean refresh) {
        if (_adView != null) {
            if (refresh) {
                _adView.stopAutoRefresh();
            } else {
                _adView.stopAutoRefresh();
            }
        }
    }

    @Override
    public void onAdDisplayFailed(@NonNull final MaxAd ad, @NonNull final MaxError error) {
        Log("onAdDisplayFailed "+ ad.getAdUnitId());

        _loadAndShowButton.setEnabled(true);
        _closeButton.setEnabled(false);
    }

    @Override
    public void onAdClicked(@NonNull final MaxAd ad) {
        Log("onAdClicked "+ ad.getAdUnitId());
    }

    @Override
    public void onAdExpanded(@NonNull final MaxAd ad) {
        Log("onAdExpanded "+ ad.getAdUnitId());
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log("onAdDisplayed "+ ad.getAdUnitId());
    }

    @Override
    public void onAdHidden(@NonNull MaxAd ad) {
        Log("onAdHidden "+ ad.getAdUnitId());

        _loadAndShowButton.setEnabled(true);
        _closeButton.setEnabled(false);
    }

    @Override
    public void onAdCollapsed(@NonNull final MaxAd ad) {
        Log("onAdCollapsed "+ ad.getAdUnitId());
    }

    @Override
    public void onAdRevenuePaid(final MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationImpression(ad);

        Log("onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }

    void Log(String log) {
        _activity.Log("Banner " + log);
    }
}
