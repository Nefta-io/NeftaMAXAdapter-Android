package com.nefta.max;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

import java.util.Locale;

public class InterstitialOptimized implements Interstitial {

    private enum State {
        Idle,
        LoadingWithInsights,
        Loading,
        Ready,
        Shown,
    }

    private class Track implements MaxAdListener, MaxAdRevenueListener {
        public final String _adUnitId;
        public MaxInterstitialAd _interstitial;
        public State _state = State.Idle;
        public AdInsight _insight;
        public double _revenue;

        public Track(String adUnit) {
            _adUnitId = adUnit;

            _interstitial = new MaxInterstitialAd(_adUnitId);
            _interstitial.setListener(this);
            _interstitial.setRevenueListener(this);
        }

        @Override
        public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_interstitial, maxError);

            Log("Load failed " + adUnitId + ": " + maxError.getMessage());

            OnLoadFail();
        }

        public void OnLoadFail() {
            RetryLoad();

            OnTrackLoad(false);
        }

        @Override
        public void onAdLoaded(@NonNull MaxAd ad) {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_interstitial, ad);

            Log("Loaded  "+ _adUnitId +" at: "+ ad.getRevenue());

            _insight = null;
            _revenue = ad.getRevenue();
            _state = State.Ready;

            OnTrackLoad(true);
        }

        public void RetryLoad() {
            _handler.postDelayed(() -> {
                _state = State.Idle;
                Load();
            }, (long)(NeftaMediationAdapter.GetRetryDelayInSeconds(_insight, _adUnitId) * 1000));
        }

        @Override
        public void onAdRevenuePaid(final MaxAd ad) {
            NeftaMediationAdapter.OnExternalMediationImpression(ad);

            Log("onAdRevenuePaid "+ ad.getAdUnitId() + ": " + ad.getRevenue());
        }

        @Override
        public void onAdDisplayed(@NonNull MaxAd ad) {
            Log("onAdDisplayed"+ ad.getAdUnitId());
        }

        @Override
        public void onAdClicked(@NonNull MaxAd ad) {
            NeftaMediationAdapter.OnExternalMediationClick(ad);

            Log("onAdClicked "+ ad.getAdUnitId());
        }

        @Override
        public void onAdHidden(@NonNull MaxAd ad) {
            Log("onAdHidden "+ ad.getAdUnitId());

            _state = State.Idle;
            Load();
        }

        @Override
        public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
            Log("onAdDisplayFailed "+ ad.getAdUnitId());

            _state = State.Idle;
            Load();
        }
    }

    private Track _trackA;
    private Track _trackB;
    private boolean _isFirstResponseReceived = false;

    private InterstitialUi _ui;
    private Handler _handler;

    public void Init(InterstitialUi ui) {
        _ui = ui;
        _handler = new Handler(Looper.getMainLooper());

        _trackA = new Track(Interstitial.AdUnitA);
        _trackB = new Track(Interstitial.AdUnitB);
    }

    public void Load() {
        if (!_ui.IsAutoLoad) {
            return;
        }

        LoadTrack(_trackA, _trackB._state);
        LoadTrack(_trackB, _trackA._state);
    }

    private void LoadTrack(Track track, State otherState) {
        if (track._state == State.Idle) {
            if (otherState == State.LoadingWithInsights || otherState == State.Shown) {
                if (_isFirstResponseReceived) {
                    LoadDefault(track);
                }
            } else {
                GetInsightsAndLoad(track);
            }
        }
    }

    private void GetInsightsAndLoad(Track track) {
        track._state = State.LoadingWithInsights;

        NeftaPlugin._instance.GetInsights(Insights.INTERSTITIAL, track._insight, (Insights insights) -> {
            Log("LoadWithInsights: " + insights);
            if (insights._interstitial != null) {
                track._insight = insights._insight;
                String bidFloor = "";
                if (track._insight._floorPrice >= 0) {
                    bidFloor = String.format(Locale.ROOT, "%.10f", track._insight._floorPrice);
                }

                track._interstitial.setExtraParameter("disable_auto_retries", "true");
                track._interstitial.setExtraParameter("jC7Fp", bidFloor);

                NeftaMediationAdapter.OnExternalMediationRequest(track._interstitial, track._insight);

                Log("Loading "+ track._adUnitId + " as Optimized with floor: " + bidFloor);
                track._interstitial.loadAd();
            } else {
                track.OnLoadFail();
            }
        });
    }

    private void LoadDefault(Track track) {
        track._state = State.Loading;

        track._interstitial.setExtraParameter("disable_auto_retries", "false");
        track._interstitial.setExtraParameter("jC7Fp", "");

        NeftaMediationAdapter.OnExternalMediationRequest(track._interstitial);

        Log("Loading "+ track._adUnitId + " as Default");
        track._interstitial.loadAd();
    }

    public void Show() {
        boolean isShown = false;
        if (_trackA._state == State.Ready) {
            if (_trackB._state == State.Ready && _trackB._revenue > _trackA._revenue) {
                isShown = TryShow(_trackB);
            }
            if (!isShown) {
                isShown = TryShow(_trackA);
            }
        }
        if (!isShown && _trackB._state == State.Ready) {
            TryShow(_trackB);
        }
        UpdateAvailability();
    }

    private boolean TryShow(Track track) {
        track._revenue = -1;
        if (track._interstitial.isReady()) {
            track._state = State.Shown;
            track._interstitial.showAd(_ui.Activity);
            return true;
        }
        track._state = State.Idle;
        Load();
        return false;
    }

    public void OnTrackLoad(boolean success) {
        if (success) {
            UpdateAvailability();
        }

        _isFirstResponseReceived = true;
        Load();
    }

    private void UpdateAvailability() {
        _ui.SetAvailability(_trackA._state == State.Ready || _trackB._state == State.Ready);
    }

    void Log(String log) {
        _ui.Log(log);
    }
}
