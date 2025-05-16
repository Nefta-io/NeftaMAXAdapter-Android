package com.nefta.max;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import com.nefta.sdk.Insight;
import com.nefta.sdk.NeftaPlugin;

import java.util.HashMap;

class BannerWrapper implements MaxAdViewAdListener, MaxAdRevenueListener {
    private final String DefaultAdUnitId = "f655876e93d11263";
    private final String TAG = "BANNER";
    private final String AdUnitIdInsightName = "recommended_banner_ad_unit_id";
    private final String FloorPriceInsightName = "calculated_user_floor_price_banner";

    private String _recommendedAdUnitId;
    private double _calculatedBidFloor;
    private boolean _isLoadRequested;

    private Activity _activity;
    private ViewGroup _bannerGroup;
    private Button _loadAndShowButton;
    private Button _closeButton;
    private Handler _handler;

    private MaxAdView _adView;

    private void GetInsightsAndLoad() {
        _isLoadRequested = true;

        NeftaPlugin._instance.GetBehaviourInsight(new String[] { AdUnitIdInsightName, FloorPriceInsightName }, this::OnBehaviourInsight);

        _handler.postDelayed(() -> {
            if (_isLoadRequested) {
                _recommendedAdUnitId = null;
                _calculatedBidFloor = 0;
                Load();
            }
        }, 5000);
    }

    private void OnBehaviourInsight(HashMap<String, Insight> insights) {
        _recommendedAdUnitId = null;
        _calculatedBidFloor = 0;
        if (insights.containsKey(AdUnitIdInsightName)) {
            _recommendedAdUnitId = insights.get(AdUnitIdInsightName)._string;
        }
        if (insights.containsKey(FloorPriceInsightName)) {
            _calculatedBidFloor = insights.get(FloorPriceInsightName)._float;
        }

        Log.i(TAG, "OnBehaviourInsights for Banner: "+ _recommendedAdUnitId +", calculated bid floor: "+ _calculatedBidFloor);

        if (_isLoadRequested) {
            Load();
        }
    }

    private void Load() {
        _isLoadRequested = false;

        String adUnitId = DefaultAdUnitId;
        if (_recommendedAdUnitId != null && !_recommendedAdUnitId.isEmpty()) {
            adUnitId = _recommendedAdUnitId;
        }

        Log.i(TAG, "Loading Banner "+ adUnitId);

        _adView = new MaxAdView(adUnitId, _activity);
        _adView.setListener(BannerWrapper.this);
        _adView.setRevenueListener(BannerWrapper.this);
        _bannerGroup.addView(_adView.getRootView());
        _adView.loadAd();

        _loadAndShowButton.setEnabled(false);
    }

    @Override
    public void onAdLoadFailed(@NonNull final String adUnitId, @NonNull final MaxError error) {
        NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Banner, _recommendedAdUnitId, _calculatedBidFloor, adUnitId, error);

        Log.i(TAG, "onAdLoadFailed "+ adUnitId);

        _loadAndShowButton.setEnabled(true);
        _closeButton.setEnabled(false);
    }

    @Override
    public void onAdLoaded(final MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Banner, _recommendedAdUnitId, _calculatedBidFloor, ad);

        Log.i(TAG, "onAdLoaded "+ ad);

        _closeButton.setEnabled(true);
    }

    public BannerWrapper(Activity activity, ViewGroup bannerGroup, Button loadAndShowButton, Button closeButton) {
        _activity = activity;
        _bannerGroup = bannerGroup;
        _loadAndShowButton = loadAndShowButton;
        _closeButton = closeButton;

        _handler = new Handler(Looper.getMainLooper());

        _loadAndShowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GetInsightsAndLoad();

                Log.i(TAG, "Loading...");
            }
        });
        _closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _adView.stopAutoRefresh();
                _bannerGroup.removeView(_adView);
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
        NeftaMediationAdapter.OnExternalMediationImpression(ad);

        Log.i(TAG, "onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }
}
