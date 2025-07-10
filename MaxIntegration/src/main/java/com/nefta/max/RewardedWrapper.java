package com.nefta.max;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdExpirationListener;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

public class RewardedWrapper implements MaxRewardedAdListener, MaxAdRevenueListener, MaxAdExpirationListener {
    private final String DefaultAdUnitId = "72458470d47ee781";
    private final int TimeoutInSeconds = 5;

    private MaxRewardedAd _rewardedAd;
    private AdInsight _usedInsight;
    private int _consecutiveAdFails;

    private MainActivity _activity;
    private Button _loadButton;
    private Button _showButton;
    private Handler _handler;

    private void GetInsightsAndLoad() {
        NeftaPlugin._instance.GetInsights(Insights.REWARDED, this::Load, TimeoutInSeconds);
    }

    private void Load(Insights insights) {
        String selectedAdUnitId = DefaultAdUnitId;
        _usedInsight = insights._rewarded;
        if (_usedInsight != null && _usedInsight._adUnit != null) {
            selectedAdUnitId = _usedInsight._adUnit;
        }

        Log("Loading "+ selectedAdUnitId +" insights: "+ _usedInsight);
        _rewardedAd = MaxRewardedAd.getInstance(selectedAdUnitId);
        _rewardedAd.setListener(RewardedWrapper.this);
        _rewardedAd.setRevenueListener(RewardedWrapper.this);
        _rewardedAd.setExpirationListener(RewardedWrapper.this);
        _rewardedAd.setExtraParameter("disable_auto_retries", "true");
        _rewardedAd.loadAd();
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Rewarded, adUnitId, _usedInsight, maxError);

        Log("onAdLoadFailed "+ adUnitId + ": "+ maxError.getMessage());

        _consecutiveAdFails++;
        // As per MAX recommendations, retry with exponentially higher delays up to 64s
        // In case you would like to customize fill rate / revenue please contact our customer support
        int delayInSeconds = new int[] { 0, 2, 4, 8, 16, 32, 64 } [Math.min(_consecutiveAdFails, 6)];

        _handler.postDelayed(this::GetInsightsAndLoad, delayInSeconds * 1000L);
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Rewarded, ad, _usedInsight);

        Log("onAdLoaded "+ ad.getAdUnitId() +": "+ ad.getRevenue());

        _consecutiveAdFails = 0;
        _showButton.setEnabled(true);
    }

    @Override
    public void onAdRevenuePaid(final MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationImpression(ad);

        Log("onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }

    public RewardedWrapper(MainActivity activity, Button loadButton, Button showButton) {
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
            public void onClick(View view) {
                _rewardedAd.showAd(_activity);

                _showButton.setEnabled(false);
            }
        });

        _showButton.setEnabled(false);
    }

    @Override
    public void onUserRewarded(@NonNull MaxAd ad, @NonNull MaxReward maxReward) {
        Log("onUserRewarded "+ ad.getAdUnitId());
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log("onAdDisplayed "+ ad.getAdUnitId());
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
        Log( "onAdClicked "+ ad.getAdUnitId());
    }

    @Override
    public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
        Log("onAdDisplayFailed "+ ad.getAdUnitId());
    }

    @Override
    public void onExpiredAdReloaded(@NonNull MaxAd var1, @NonNull MaxAd var2) {
        Log("onExpiredAdReloaded "+ var1 + ": "+ var2);
    }

    void Log(String log) {
        _activity.Log("Rewarded " + log);
    }
}