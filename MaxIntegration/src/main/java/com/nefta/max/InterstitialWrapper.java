package com.nefta.max;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

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

import java.util.Locale;

class InterstitialWrapper implements MaxAdListener, MaxAdRevenueListener, MaxAdRequestListener, MaxAdReviewListener, MaxAdExpirationListener {
    private final String DynamicAdUnitId = "87f1b4837da231e5";
    private final String DefaultAdUnitId = "7267e7f4187b95b2";
    private final int TimeoutInSeconds = 5;

    private MaxInterstitialAd _dynamicInterstitial;
    private double _dynamicAdRevenue = -1;
    private AdInsight _dynamicInsight;
    private int _consecutiveDynamicAdFails;
    private MaxInterstitialAd _defaultInterstitial;
    private double _defaultAdRevenue = -1;

    private MainActivity _activity;
    private Switch _loadSwitch;
    private Button _showButton;
    private TextView _status;
    private Handler _handler;

    private void StartLoading() {
        if (_dynamicInterstitial == null) {
            GetInsightsAndLoad(null);
        }
        if (_defaultInterstitial == null) {
            LoadDefault();
        }
    }

    private void GetInsightsAndLoad(AdInsight previousInsight) {
        NeftaPlugin._instance.GetInsights(Insights.INTERSTITIAL, previousInsight, this::LoadWithInsights, TimeoutInSeconds);
    }

    private void LoadWithInsights(Insights insights) {
        _dynamicInsight = insights._interstitial;
        if (_dynamicInsight != null) {
            String bidFloorParam = String.format(Locale.ROOT, "%.10f", _dynamicInsight._floorPrice);

            Log("Loading Dynamic Interstitial with insight: " + _dynamicInsight + " floor: " + bidFloorParam);
            _dynamicInterstitial = new MaxInterstitialAd(DynamicAdUnitId);
            _dynamicInterstitial.setListener(InterstitialWrapper.this);
            _dynamicInterstitial.setRevenueListener(InterstitialWrapper.this);
            _dynamicInterstitial.setExpirationListener(InterstitialWrapper.this);
            _dynamicInterstitial.setExtraParameter("disable_auto_retries", "true");
            _dynamicInterstitial.setExtraParameter("jC7Fp", bidFloorParam);
            _dynamicInterstitial.loadAd();

            NeftaMediationAdapter.OnExternalMediationRequest(_dynamicInterstitial, _dynamicInsight);
        }
    }

    private void LoadDefault() {
        Log("Loading Default");
        _defaultInterstitial = new MaxInterstitialAd(DefaultAdUnitId);
        _defaultInterstitial.setListener(InterstitialWrapper.this);
        _defaultInterstitial.setRevenueListener(InterstitialWrapper.this);
        _defaultInterstitial.loadAd();

        NeftaMediationAdapter.OnExternalMediationRequest(_defaultInterstitial);
    }

    @Override
    public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
        if (DynamicAdUnitId.equals(adUnitId)) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_dynamicInterstitial, maxError);

            Log("Load failed Dynamic " + adUnitId + ": " + maxError.getMessage());

            _dynamicInterstitial = null;
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
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_defaultInterstitial, maxError);

            Log("Load failed Default "+ adUnitId + ": "+ maxError.getMessage());

            _defaultInterstitial = null;
            if (_loadSwitch.isChecked()) {
                LoadDefault();
            }
        }
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd ad) {
        if (DynamicAdUnitId.equals(ad.getAdUnitId())) {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_dynamicInterstitial, ad);

            _consecutiveDynamicAdFails = 0;
            _dynamicAdRevenue = ad.getRevenue();

            Log("Loaded Dynamic "+ ad.getAdUnitId() +": "+ ad.getRevenue());
        } else {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_defaultInterstitial, ad);

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

        Log("onAdClicked "+ ad.getAdUnitId());
    }

    public InterstitialWrapper(MainActivity activity, Switch loadSwitch, Button showButton, TextView status) {
        _activity = activity;
        _loadSwitch = loadSwitch;
        _showButton = showButton;
        _status = status;

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
        if (_dynamicInterstitial.isReady()) {
            _dynamicInterstitial.showAd(_activity);
            shown = true;
        }
        _dynamicAdRevenue = -1;
        _dynamicInterstitial = null;
        return shown;
    }

    private boolean TryShowDefault() {
        boolean shown = false;
        if (_defaultInterstitial.isReady()) {
            _defaultInterstitial.showAd(_activity);
            shown = true;
        }
        _defaultAdRevenue = -1;
        _defaultInterstitial = null;
        return shown;
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd ad) {
        Log("onAdDisplayed"+ ad.getAdUnitId());
    }

    @Override
    public void onAdHidden(@NonNull MaxAd ad) {
        Log("onAdHidden "+ ad.getAdUnitId());

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

    private void UpdateShowButton() {
        _showButton.setEnabled(_dynamicAdRevenue >= 0 || _defaultAdRevenue >= 0);
    }

    void Log(String log) {
        _status.setText(log);
        Log.i("NeftaPluginMAX", "Interstitial " + log);
    }
}
