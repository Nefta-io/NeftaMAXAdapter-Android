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
        Loading,
        Ready,
        Shown,
        Delay
    }

    private class Track implements MaxRewardedAdListener, MaxAdRevenueListener {
        public final String _adUnitId;
        public final boolean _isOptimized;
        public MaxRewardedAd _rewarded;
        public State _state = State.Idle;
        public AdInsight _insight;
        public double _revenue;

        public Track(String adUnit, boolean isOptimized) {
            _adUnitId = adUnit;
            _isOptimized = isOptimized;

            _rewarded = MaxRewardedAd.getInstance(_adUnitId);
            if (_isOptimized) {
                _rewarded.setExtraParameter("disable_auto_retries", "true");
            }
            _rewarded.setListener(this);
            _rewarded.setRevenueListener(this);
        }

        @Override
        public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_rewarded, maxError);

            Log("Load failed " + adUnitId + ": " + maxError.getMessage());

            OnTrackLoad(this, false);
        }

        @Override
        public void onAdLoaded(@NonNull MaxAd ad) {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_rewarded, ad);

            Log("Loaded  "+ _adUnitId +" at: "+ ad.getRevenue());

            _insight = null;
            _revenue = ad.getRevenue();
            _state = State.Ready;

            OnTrackLoad(this, true);
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
            ContinueLoading();
        }

        @Override
        public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
            Log("onAdDisplayFailed "+ ad.getAdUnitId());

            _state = State.Idle;
            ContinueLoading();
        }
    }

    private Track[] _tracks;
    private boolean _isFirstRequest = true;
    private boolean _isFirstResponseReceived = false;
    private boolean _isGlobalCooldown;

    private RewardedUi _ui;
    private Handler _handler;

    public void Init(RewardedUi ui) {
        _ui = ui;
        _handler = new Handler(Looper.getMainLooper());

        _tracks = new Track[] {
            new Track(Rewarded.AdUnitDefault, false),
            new Track(Rewarded.AdUnitOptimizedA, true),
            new Track(Rewarded.AdUnitOptimizedB, true)
        };
    }

    public void Load() {
        if (!_isFirstRequest) {
            return;
        }

        Load(_tracks[1]);

        int stopWaitingForFirstResponseAfter = NeftaPlugin._instance._state._firstResponseTimeoutRewardedInMs;
        if (_isFirstRequest && stopWaitingForFirstResponseAfter > 0) {
            _isFirstRequest = false;

            _handler.postDelayed(() -> {
                if (!_isFirstResponseReceived) {
                    _isFirstResponseReceived = true;
                    if (_tracks[2]._state == State.Idle) {
                        Load(_tracks[2]);
                    }
                }
            }, stopWaitingForFirstResponseAfter);
        }

        int backFillTrackDelayInMs = NeftaPlugin._instance._state._backfillTrackDelayInMs;
        if (backFillTrackDelayInMs == 0) {
            Load(_tracks[0]);
        } else if (backFillTrackDelayInMs > 0) {
            _handler.postDelayed(() -> {
                Load(_tracks[0]);
            }, backFillTrackDelayInMs);
        }
    }

    private void Load(Track track) {
        track._state = State.Loading;

        if (track._isOptimized) {
            NeftaMediationAdapter.GetInsights(Insights.REWARDED, track._insight, (Insights insights) -> {
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

                    Log("Loading " + track._adUnitId + " as Optimized with floor: " + bidFloor);
                    track._rewarded.loadAd();
                } else {
                    _handler.postDelayed(() -> {
                        track._state = State.Idle;
                        ContinueLoading();
                    }, (int)(NeftaPlugin.GetRetryDelayInSeconds(track._insight, track._adUnitId) * 1000));
                }
            });
        } else {
            NeftaMediationAdapter.OnExternalMediationRequest(track._rewarded);

            Log("Loading "+ track._adUnitId + " as Default");

            track._rewarded.loadAd();
        }
    }

    public void ContinueOptimized() {
        if (_tracks[0]._state == State.Idle) {
            Load(_tracks[0]);
        }

        _isGlobalCooldown = true;
        _handler.postDelayed(() -> {
            _isGlobalCooldown = false;
            ContinueLoading();
        }, NeftaPlugin._instance._state._globalTrackCooldownInMs);
    }

    private void ContinueLoading() {
        if (!_ui.IsAutoLoad) {
            return;
        }

        if (_tracks[0]._state == State.Idle) {
            Load(_tracks[0]);
        }
        if (_isGlobalCooldown) {
            return;
        }
        for (int i = 1; i < _tracks.length; i++) {
            if (_tracks[i]._state == State.Loading) {
                return;
            }
        }
        for (int i = 1; i < _tracks.length; i++) {
            if (_tracks[i]._state == State.Idle) {
                Load(_tracks[i]);
                return;
            }
        }
    }

    public void Show() {
        boolean isShown = false;
        Track readyTrack = GetReadyTrack();
        if (readyTrack != null) {
            isShown = TryShow(readyTrack);
        }
        if (!isShown) {
            ContinueLoading();
        }
        readyTrack = GetReadyTrack();
        _ui.SetAvailability(readyTrack != null);
    }

    private boolean TryShow(Track request) {
        request._revenue = -1;
        if (request._rewarded.isReady()) {
            request._state = State.Shown;
            request._rewarded.showAd(_ui.Activity);
            return true;
        }
        request._state = State.Idle;
        return false;
    }

    public void OnTrackLoad(Track track, boolean success) {
        _isFirstResponseReceived = true;
        if (track._isOptimized) {
            ContinueOptimized();
        }

        if (success) {
            _ui.SetAvailability(true);
        } else {
            track._state = State.Delay;
            int delay = (int)(NeftaPlugin.GetRetryDelayInSeconds(track._insight, track._adUnitId) * 1000);
            _handler.postDelayed(() -> {
                track._state = State.Idle;
                ContinueLoading();
            }, delay);
        }
    }

    private Track GetReadyTrack() {
        Track bestTrack = null;
        for (Track t : _tracks) {
            if (t._state == State.Ready && (bestTrack == null || t._revenue > bestTrack._revenue)) {
                bestTrack = t;
            }
        }
        return bestTrack;
    }

    private void Log(String log) {
        _ui.Log(log);
    }
}