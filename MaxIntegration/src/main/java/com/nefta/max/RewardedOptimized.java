package com.nefta.max;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
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

public class RewardedOptimized implements Rewarded {

    private enum State {
        Idle,
        LoadingWithInsights,
        Loading,
        Ready,
        Shown
    }

    private class Track implements MaxRewardedAdListener, MaxAdRevenueListener {
        public final String _adUnitId;
        public MaxRewardedAd _rewarded;
        public State _state = State.Idle;
        public AdInsight _insight;
        public double _revenue;

        public Track(String adUnit) {
            _adUnitId = adUnit;

            _rewarded = MaxRewardedAd.getInstance(_adUnitId);
            _rewarded.setListener(this);
            _rewarded.setRevenueListener(this);
        }

        @Override
        public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_rewarded, maxError);

            Log("Load failed " + adUnitId + ": " + maxError.getMessage());

            OnLoadFail();
        }

        public void OnLoadFail() {
            RetryLoad();

            OnTrackLoad(false);
        }

        @Override
        public void onAdLoaded(@NonNull MaxAd ad) {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_rewarded, ad);

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
            Log("onAdDisplayed "+ ad.getAdUnitId());
        }

        @Override
        public void onAdClicked(@NonNull MaxAd ad) {
            NeftaMediationAdapter.OnExternalMediationClick(ad);

            Log("onAdClicked "+ ad.getAdUnitId());
        }

        @Override
        public void onUserRewarded(@NonNull MaxAd ad, @NonNull MaxReward maxReward) {
            Log("onUserRewarded "+ ad.getAdUnitId());
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

    private RewardedUi _ui;
    private Handler _handler;

    public void Init(RewardedUi ui) {
        _ui = ui;
        _handler = new Handler(Looper.getMainLooper());

        _trackA = new Track(Rewarded.AdUnitA);
        _trackB = new Track(Rewarded.AdUnitB);
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

        NeftaPlugin._instance.GetInsights(Insights.REWARDED, track._insight, (Insights insights) -> {
            Log("LoadWithInsights: " + insights);
            if (insights._rewarded != null) {
                track._insight = insights._insight;
                String bidFloor = "";
                if (track._insight._floorPrice >= 0) {
                    bidFloor = String.format(Locale.ROOT, "%.10f", track._insight._floorPrice);
                }

                track._rewarded.setExtraParameter("disable_auto_retries", "true");
                track._rewarded.setExtraParameter("jC7Fp", bidFloor);

                NeftaMediationAdapter.OnExternalMediationRequest(track._rewarded, track._insight);

                Log("Loading "+ track._adUnitId + " as Optimized with floor: " + bidFloor);
                track._rewarded.loadAd();
            } else {
                track.OnLoadFail();
            }
        });
    }

    private void LoadDefault(Track track) {
        track._state = State.Loading;

        Log("Loading "+ track._adUnitId + " as Default");

        track._rewarded.setExtraParameter("disable_auto_retries", "false");
        track._rewarded.setExtraParameter("jC7Fp", "");

        NeftaMediationAdapter.OnExternalMediationRequest(track._rewarded);

        track._rewarded.loadAd();
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

    private boolean TryShow(Track request) {
        request._revenue = -1;
        if (request._rewarded.isReady()) {
            request._state = State.Shown;
            request._rewarded.showAd(_ui.Activity);
            return true;
        }
        request._state = State.Idle;
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

    private void Log(String log) {
        _ui.Log(log);
    }
}