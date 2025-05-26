package com.nefta.max;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.nefta.sdk.Insight;
import com.nefta.sdk.NeftaPlugin;

import java.util.HashMap;

public class RewardedWrapper implements MaxRewardedAdListener, MaxAdRevenueListener {
    private final String DefaultAdUnitId = "72458470d47ee781";
    private final String TAG = "REWARDED_VIDEO";

    private final String AdUnitIdInsightName = "recommended_rewarded_ad_unit_id";
    private final String FloorPriceInsightName = "calculated_user_floor_price_rewarded";

    MaxRewardedAd _rewardedAd;
    private String _recommendedAdUnitId;
    private double _calculatedBidFloor;
    private boolean _isLoadRequested;
    private int _consecutiveAdFails;


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

        Log.i(TAG, "OnBehaviourInsights for Rewarded: "+ _recommendedAdUnitId +", calculated bid floor: "+ _calculatedBidFloor);

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

        _rewardedAd = MaxRewardedAd.getInstance(adUnitId);
        _rewardedAd.setListener(RewardedWrapper.this);
        _rewardedAd.setRevenueListener(RewardedWrapper.this);
        _rewardedAd.loadAd();
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Rewarded, _recommendedAdUnitId, _calculatedBidFloor, adUnitId, maxError);

        Log.i(TAG, "onAdLoadFailed "+ adUnitId);

        _loadButton.setEnabled(true);
        _showButton.setEnabled(false);

        _consecutiveAdFails++;
        // As per MAX recommendations, retry with exponentially higher delays up to 64s
        // In case you would like to customize fill rate / revenue please contact our customer support
        _handler.postDelayed(this::GetInsightsAndLoad, new int[] { 0, 2, 4, 8, 32, 64 } [Math.min(_consecutiveAdFails, 5)] * 1000L);
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Rewarded, _recommendedAdUnitId, _calculatedBidFloor, ad);

        Log.i(TAG, "onAdLoaded "+ ad.getAdUnitId());

        _consecutiveAdFails = 0;
        _showButton.setEnabled(true);
    }

    public RewardedWrapper(Activity activity, Button loadButton, Button showButton, MainActivity.IOnFullScreenAdDisplay onFullScreenAdDisplay) {
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
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log.i(TAG, "onAdDisplayed "+ ad.getAdUnitId());
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