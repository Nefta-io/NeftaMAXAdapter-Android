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
    private final String DynamicAdUnitId = "a4b93fe91b278c75";
    private final String DefaultAdUnitId = "72458470d47ee781";
    private final int TimeoutInSeconds = 5;

    private MaxRewardedAd _dynamicRewarded;
    private double _dynamicAdRevenue = -1;
    private AdInsight _dynamicInsight;
    private int _consecutiveDynamicAdFails;
    private MaxRewardedAd _defaultRewarded;
    private double _defaultAdRevenue = -1;

    private MainActivity _activity;
    private Switch _loadSwitch;
    private Button _showButton;
    private Handler _handler;

    private void StartLoading() {
        if (_dynamicRewarded == null) {
            GetInsightsAndLoad(null);
        }
        if (_defaultRewarded == null) {
            LoadDefault();
        }
    }

    private void GetInsightsAndLoad(AdInsight previousInsight) {
        NeftaPlugin._instance.GetInsights(Insights.REWARDED, previousInsight, this::LoadWithInsights, TimeoutInSeconds);
    }

    private void LoadWithInsights(Insights insights) {
        _dynamicInsight = insights._rewarded;
        if (_dynamicInsight != null) {
            String bidFloorParam = String.format(Locale.ROOT, "%.10f", _dynamicInsight._floorPrice);

            Log("Loading Dynamic Rewarded with insight: "+ _dynamicInsight + " floor: " + bidFloorParam);
            _dynamicRewarded = MaxRewardedAd.getInstance(DynamicAdUnitId);
            _dynamicRewarded.setListener(RewardedWrapper.this);
            _dynamicRewarded.setRevenueListener(RewardedWrapper.this);
            _dynamicRewarded.setExtraParameter("disable_auto_retries", "true");
            _dynamicRewarded.setExtraParameter("jC7Fp", bidFloorParam);
            _dynamicRewarded.loadAd();

            NeftaMediationAdapter.OnExternalMediationRequest(_dynamicRewarded, _dynamicInsight);
        }
    }

    private void LoadDefault() {
        Log("Loading Default");
        _defaultRewarded = MaxRewardedAd.getInstance(DefaultAdUnitId);
        _defaultRewarded.setListener(RewardedWrapper.this);
        _defaultRewarded.setRevenueListener(RewardedWrapper.this);
        _defaultRewarded.loadAd();

        NeftaMediationAdapter.OnExternalMediationRequest(_defaultRewarded);
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        if (DynamicAdUnitId.equals(adUnitId)) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_dynamicRewarded, maxError);

            Log("Load failed Dynamic " + adUnitId + ": " + maxError.getMessage());

            _dynamicRewarded = null;
            _consecutiveDynamicAdFails++;
            // As per MAX recommendations, retry with exponentially higher delays up to 64s
            // In case you would like to customize fill rate / revenue please contact our customer support
            int delayInSeconds = new int[]{0, 2, 4, 8, 16, 32, 64}[Math.min(_consecutiveDynamicAdFails, 6)];
            _handler.postDelayed(() -> {
                if (_loadSwitch.isChecked()) {
                    GetInsightsAndLoad(_dynamicInsight);
                }
            }, delayInSeconds * 1000L);
        } else {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_defaultRewarded, maxError);

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
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_dynamicRewarded, ad);

            _consecutiveDynamicAdFails = 0;
            _dynamicAdRevenue = ad.getRevenue();

            Log("Loaded Dynamic " + ad.getAdUnitId() + ": " + _dynamicAdRevenue);
        } else {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_defaultRewarded, ad);

            _defaultAdRevenue = ad.getRevenue();

            Log("Loaded Default " + ad.getAdUnitId() + ": " + ad.getRevenue());
        }

        UpdateShowButton();
    }

    @Override
    public void onAdRevenuePaid(final MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationImpression(ad);

        Log("onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
    }

    @Override
    public void onAdClicked(@NonNull MaxAd ad) {
        NeftaMediationAdapter.OnExternalMediationClick(ad);

        Log( "onAdClicked "+ ad.getAdUnitId());
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
            public void onClick(View view) {
                boolean isShown = false;
                if (_dynamicAdRevenue >= 0) {
                    if (_defaultAdRevenue > _dynamicAdRevenue) {
                        isShown = TryShowDefault();
                    }
                    if (!isShown) {
                        isShown = TryShowDynamic();
                    }
                }
                if (!isShown && _defaultAdRevenue >= 0) {
                    TryShowDefault();
                }
                UpdateShowButton();
            }
        });

        _showButton.setEnabled(false);
    }

    private boolean TryShowDynamic() {
        boolean shown = false;
        if (_dynamicRewarded.isReady()) {
            _dynamicRewarded.showAd(_activity);
            shown = true;
        }
        _dynamicAdRevenue = -1;
        _dynamicRewarded = null;
        return shown;
    }

    private boolean TryShowDefault() {
        boolean shown = false;
        if (_defaultRewarded.isReady()) {
            _defaultRewarded.showAd(_activity);
            shown = true;
        }
        _defaultAdRevenue = -1;
        _defaultRewarded = null;
        return shown;
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

        // start new cycle
        if (_loadSwitch.isChecked()) {
            StartLoading();
        }
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
        _showButton.setEnabled(_dynamicAdRevenue >= 0 || _defaultAdRevenue >= 0);
    }

    void Log(String log) {
        _activity.Log("Rewarded " + log);
    }
}