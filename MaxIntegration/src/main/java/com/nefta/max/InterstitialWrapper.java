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
import com.nefta.sdk.Insight;
import com.nefta.sdk.NeftaPlugin;

import java.util.HashMap;

class InterstitialWrapper implements MaxAdListener, MaxAdRevenueListener {
    private final String DefaultAdUnitId = "7267e7f4187b95b2";
    private final String TAG = "INTERSTITIAL";
    private final String AdUnitIdInsightName = "recommended_interstitial_ad_unit_id";
    private final String FloorPriceInsightName = "calculated_user_floor_price_interstitial";

    private String _recommendedAdUnitId;
    private double _calculatedBidFloor;

    private Activity _activity;
    private Button _loadButton;
    private Button _showButton;
    private final MainActivity.IOnFullScreenAdDisplay _onFullScreenAdDisplay;

    MaxInterstitialAd _defaultInterstitial;
    MaxAd _defaultAd;
    MaxInterstitialAd _recommendedInterstitial;
    MaxAd _recommendedAd;

    private void Load() {
        Log.i(TAG, "Load default: "+ _defaultAd +" recommended: "+ _recommendedAd);

        if (_defaultAd == null) {
            _defaultInterstitial = new MaxInterstitialAd(DefaultAdUnitId, _activity);
            _defaultInterstitial.setListener(InterstitialWrapper.this);
            _defaultInterstitial.setRevenueListener(InterstitialWrapper.this);
            _defaultInterstitial.loadAd();
        }

        if (_recommendedAd == null) {
            NeftaPlugin._instance.GetBehaviourInsight(new String[] { AdUnitIdInsightName, FloorPriceInsightName }, this::OnBehaviourInsight);
        }
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

        if (_recommendedAdUnitId != null && _recommendedAdUnitId != DefaultAdUnitId) {
            _recommendedInterstitial = new MaxInterstitialAd(DefaultAdUnitId, _activity);
            _recommendedInterstitial.setListener(InterstitialWrapper.this);
            _recommendedInterstitial.setRevenueListener(InterstitialWrapper.this);
            _recommendedInterstitial.loadAd();
        }
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        if (adUnitId == _recommendedAdUnitId) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Interstitial, _recommendedAdUnitId, _calculatedBidFloor, adUnitId, maxError);

            _recommendedInterstitial = null;
            _recommendedAdUnitId = null;
            _calculatedBidFloor = 0;
        } else {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Interstitial, null, 0, adUnitId, maxError);

            _defaultInterstitial = null;
        }

        // or automatically retry
        //if (_recommendedInterstitial == null && _defaultInterstitial == null) {
        //    Load();
        //}

        Log.i(TAG, "onAdLoadFailed "+ adUnitId);
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        if (ad.getAdUnitId() == _recommendedAdUnitId) {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Interstitial, _recommendedAdUnitId, _calculatedBidFloor, ad);

            _recommendedAd = ad;
        } else {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Interstitial, null, 0, ad);

            _defaultAd = ad;
        }

        Log.i(TAG, "onAdLoaded "+ ad.getAdUnitId());

        _showButton.setEnabled(true);
    }

    public InterstitialWrapper(Activity activity, Button loadButton, Button showButton, MainActivity.IOnFullScreenAdDisplay onFullScreenAdDisplay) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;
        _onFullScreenAdDisplay = onFullScreenAdDisplay;

        _loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Load();
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Show default: "+ _defaultAd + " recommended: "+ _recommendedAd);

                if (_recommendedAd != null) {
                    if (_defaultAd != null && _defaultAd.getRevenue() > _recommendedAd.getRevenue()) {
                        _defaultInterstitial.showAd(_activity);
                        _defaultInterstitial = null;
                        _defaultAd = null;
                    } else {
                        _recommendedInterstitial.showAd(_activity);
                        _recommendedInterstitial = null;
                        _recommendedAd = null;
                    }
                } else if (_defaultAd != null) {
                    _defaultInterstitial.showAd(_activity);
                    _defaultInterstitial = null;
                    _defaultAd = null;
                }

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
