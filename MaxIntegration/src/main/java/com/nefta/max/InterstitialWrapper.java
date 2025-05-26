package com.nefta.max;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
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
import com.nefta.sdk.Insight;
import com.nefta.sdk.NeftaPlugin;

import java.util.HashMap;

class InterstitialWrapper implements MaxAdListener, MaxAdRevenueListener {
    private final String DefaultAdUnitId = "7267e7f4187b95b2";
    private final String TAG = "INTERSTITIAL";
    private final String AdUnitIdInsightName = "recommended_interstitial_ad_unit_id";
    private final String FloorPriceInsightName = "calculated_user_floor_price_interstitial";

    private MaxInterstitialAd _interstitialAd;
    private String _recommendedAdUnitId;
    private double _calculatedBidFloor;
    private boolean _isLoadRequested;

    private Activity _activity;
    private Button _loadButton;
    private Button _showButton;
    private final MainActivity.IOnFullScreenAdDisplay _onFullScreenAdDisplay;
    private Handler _handler;

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

        Log.i(TAG, "OnBehaviourInsights for Interstitial: "+ _recommendedAdUnitId +", calculated bid floor: "+ _calculatedBidFloor);

        if (_isLoadRequested) {
            Load();
        }
    }

    private void Load() {
        _isLoadRequested = false;

        String adUnitId = DefaultAdUnitId;
        if (_recommendedAdUnitId != null) {
            adUnitId = _recommendedAdUnitId;
        }

        Log.i(TAG, "Loading Interstitial "+ adUnitId);

        _interstitialAd = new MaxInterstitialAd(adUnitId);
        _interstitialAd.setListener(InterstitialWrapper.this);
        _interstitialAd.setRevenueListener(InterstitialWrapper.this);
        _interstitialAd.loadAd();
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Interstitial, _recommendedAdUnitId, _calculatedBidFloor, adUnitId, maxError);

        Log.i(TAG, "onAdLoadFailed "+ adUnitId);

        _loadButton.setEnabled(true);
        _showButton.setEnabled(false);

        // or automatically retry with a delay
        //_handler.postDelayed(this::GetInsightsAndLoad, 5000);
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Interstitial, _recommendedAdUnitId, _calculatedBidFloor, ad);

        Log.i(TAG, "onAdLoaded "+ ad.getAdUnitId());

        _showButton.setEnabled(true);
    }

    public InterstitialWrapper(Activity activity, Button loadButton, Button showButton, MainActivity.IOnFullScreenAdDisplay onFullScreenAdDisplay) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;
        _onFullScreenAdDisplay = onFullScreenAdDisplay;

        _handler = new Handler(Looper.getMainLooper());

        _loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GetInsightsAndLoad();

                Log.i(TAG, "Loading...");
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _interstitialAd.showAd(_activity);

                _showButton.setEnabled(false);
            }
        });

        _showButton.setEnabled(false);
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdDisplayed"+ ad.getAdUnitId());
        _onFullScreenAdDisplay.invoke(true);
    }

    @Override
    public void onAdHidden(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdHidden "+ ad.getAdUnitId());
        _onFullScreenAdDisplay.invoke(false);
    }

    @Override
    public void onAdClicked(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdClicked "+ ad.getAdUnitId());
    }

    @Override
    public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
        Log.i(TAG, "onAdDisplayFailed "+ ad.getAdUnitId());
    }

    @Override
    public void onAdRevenuePaid(final MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationImpression(ad);

        Log.i(TAG, "onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }
}
