package com.nefta.max;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

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

import java.util.Locale;

public class RewardedWrapper implements MaxRewardedAdListener, MaxAdRevenueListener, MaxAdExpirationListener {
    private final String DefaultAdUnitId = "72458470d47ee781";
    private final String DynamicAdUnitId = "a4b93fe91b278c75";
    private final int TimeoutInSeconds = 5;

    private MaxRewardedAd _defaultRewarded;
    private MaxRewardedAd _dynamicRewarded;
    private AdInsight _dynamicAdUnitInsight;
    private int _consecutiveDynamicBidAdFails;

    private MainActivity _activity;
    private Switch _loadSwitch;
    private Button _showButton;
    private Handler _handler;

    private void StartLoading() {
        if (_dynamicRewarded == null) {
            GetInsightsAndLoad();
        }
        if (_defaultRewarded == null) {
            LoadDefault();
        }
    }

    private void GetInsightsAndLoad() {
        NeftaPlugin._instance.GetInsights(Insights.REWARDED, this::LoadWithInsights, TimeoutInSeconds);
    }

    private void LoadWithInsights(Insights insights) {
        if (insights._rewarded != null) {
            _dynamicAdUnitInsight = insights._rewarded;
            String bidFloor = String.format(Locale.ROOT, "%.10f", _dynamicAdUnitInsight._floorPrice);

            Log("Loading Dynamic with floor: " + bidFloor);
            _dynamicRewarded = MaxRewardedAd.getInstance(DynamicAdUnitId);
            _dynamicRewarded.setListener(RewardedWrapper.this);
            _dynamicRewarded.setRevenueListener(RewardedWrapper.this);
            _dynamicRewarded.setExtraParameter("disable_auto_retries", "true");
            _dynamicRewarded.setExtraParameter("jC7Fp", bidFloor);
            _dynamicRewarded.loadAd();
        }
    }

    private void LoadDefault() {
        Log("Loading Default");
        _defaultRewarded = MaxRewardedAd.getInstance(DefaultAdUnitId);
        _defaultRewarded.setListener(RewardedWrapper.this);
        _defaultRewarded.setRevenueListener(RewardedWrapper.this);
        _defaultRewarded.loadAd();
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        if (DynamicAdUnitId.equals(adUnitId)) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Rewarded, adUnitId, _dynamicAdUnitInsight, maxError);

            Log("Load failed Dynamic "+ adUnitId + ": "+ maxError.getMessage());

            _dynamicRewarded = null;
            _consecutiveDynamicBidAdFails++;
            // As per MAX recommendations, retry with exponentially higher delays up to 64s
            // In case you would like to customize fill rate / revenue please contact our customer support
            int delayInSeconds = new int[] { 0, 2, 4, 8, 16, 32, 64 } [Math.min(_consecutiveDynamicBidAdFails, 6)];
            _handler.postDelayed(() -> {
                if (_loadSwitch.isChecked()) {
                    GetInsightsAndLoad();
                }
            }, delayInSeconds * 1000L);
        } else {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(NeftaMediationAdapter.AdType.Rewarded, adUnitId, null, maxError);

            Log("Load failed Default "+ adUnitId + ": "+ maxError.getMessage());

            _defaultRewarded = null;
            if (_loadSwitch.isChecked()) {
                LoadDefault();
            }
        }
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        if (DynamicAdUnitId.equals(ad.getAdUnitId())) {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Rewarded, ad, _dynamicAdUnitInsight);

            Log("Loaded Dynamic " + ad.getAdUnitId() + ": " + ad.getRevenue());

            _consecutiveDynamicBidAdFails = 0;
        } else {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(NeftaMediationAdapter.AdType.Rewarded, ad, null);

            Log("Loaded Default " + ad.getAdUnitId() + ": " + ad.getRevenue());
        }

        UpdateShowButton();
    }

    @Override
    public void onAdRevenuePaid(@NonNull final MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationImpression(ad);

        Log("onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }

    public RewardedWrapper(MainActivity activity, Switch loadSwitch, Button showButton) {
        _activity = activity;
        _loadSwitch = loadSwitch;
        _showButton = showButton;

        _handler = new Handler(Looper.getMainLooper());

        _loadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    StartLoading();
                }
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isShown = false;
                if (_dynamicRewarded != null) {
                    if (_dynamicRewarded.isReady()) {
                        _dynamicRewarded.showAd(_activity);
                        isShown = true;
                    }
                    _dynamicRewarded = null;
                }
                if (!isShown && _defaultRewarded != null) {
                    if (_defaultRewarded.isReady()) {
                        _defaultRewarded.showAd(_activity);
                    }
                    _defaultRewarded = null;
                }

                UpdateShowButton();
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

        // start new load cycle
        if (_loadSwitch.isChecked()) {
            StartLoading();
        }
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

    private void UpdateShowButton() {
        _showButton.setEnabled(_dynamicRewarded != null && _defaultRewarded.isReady() || _defaultRewarded != null && _defaultRewarded.isReady());
    }

    private void Log(String log) {
        _activity.Log("Rewarded " + log);
    }
}