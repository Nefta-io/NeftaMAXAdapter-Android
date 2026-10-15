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
        Loading,
        Ready,
        Shown,
        Delay
    }

    private class Track implements MaxAdListener, MaxAdRevenueListener {
        public final String _adUnitId;
        public final boolean _isOptimized;
        public MaxInterstitialAd _interstitial;
        public State _state = State.Idle;
        public AdInsight _insight;
        public double _revenue;

        public Track(String adUnit, boolean isOptimized) {
            _adUnitId = adUnit;
            _isOptimized = isOptimized;

            _interstitial = new MaxInterstitialAd(_adUnitId);
            if (_isOptimized) {
                _interstitial.setExtraParameter("disable_auto_retries", "true");
            }
            _interstitial.setListener(this);
            _interstitial.setRevenueListener(this);
        }

        @Override
        public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_interstitial, maxError);

            Log("Load failed " + adUnitId + ": " + maxError.getMessage());

            OnTrackLoad(this, false);
        }

        @Override
        public void onAdLoaded(@NonNull MaxAd ad) {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_interstitial, ad);

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
    private boolean _isFirstOptimizedResponseReceived = false;
    private int _isGlobalCooldown;

    private InterstitialUi _ui;
    private Handler _handler;

    public void Init(InterstitialUi ui) {
        _ui = ui;
        _handler = new Handler(Looper.getMainLooper());

        _tracks = new Track[] {
            new Track(Interstitial.AdUnitDefault, false),
            new Track(Interstitial.AdUnitOptimizedA, true),
            new Track(Interstitial.AdUnitOptimizedB, true)
        };
    }

    public void Load() {
        if (_isFirstRequest) {
            Load(_tracks[1]);

            int stopWaitingForFirstResponseAfter = NeftaPlugin._instance._state._firstResponseTimeoutInterstitialInMs;
            if (stopWaitingForFirstResponseAfter > 0) {
                _handler.postDelayed(() -> {
                    if (!_isFirstOptimizedResponseReceived) {
                        _isFirstOptimizedResponseReceived = true;
                        if (_tracks.length > 2 && _tracks[2]._state == State.Idle) {
                            Load(_tracks[2]);
                        }
                        if (NeftaPlugin._instance._state._backfillTrackDelayInMs < 0 && _tracks[0]._state == State.Idle) {
                            Load(_tracks[0]);
                        }
                    }
                }, stopWaitingForFirstResponseAfter);
            }

            int backFillTrackDelayInMs = NeftaPlugin._instance._state._backfillTrackDelayInMs;
            if (backFillTrackDelayInMs == 0) {
                Load(_tracks[0]);
            } else if (backFillTrackDelayInMs > 0) {
                _tracks[0]._state = State.Delay;
                _handler.postDelayed(() -> Load(_tracks[0]), backFillTrackDelayInMs);
            }

            _isFirstRequest = false;
        } else {
            ContinueLoading();
        }
    }

    private void Load(Track track) {
        track._state = State.Loading;

        if (track._isOptimized) {
            NeftaMediationAdapter.GetInsights(Insights.INTERSTITIAL, track._insight, (Insights insights) -> {
                Log("LoadWithInsights: " + insights);
                if (insights._interstitial != null) {
                    track._insight = insights._insight;
                    String bidFloor = "";
                    if (track._insight._floorPrice >= 0) {
                        bidFloor = String.format(Locale.ROOT, "%.10f", track._insight._floorPrice);
                    }

                    track._interstitial.setExtraParameter("jC7Fp", bidFloor);

                    NeftaMediationAdapter.OnExternalMediationRequest(track._interstitial, track._insight);

                    Log("Loading "+ track._adUnitId + " as Optimized with floor: " + bidFloor);
                    track._interstitial.loadAd();
                } else {
                    _handler.postDelayed(() -> {
                        track._state = State.Idle;
                        ContinueLoading();
                    }, (int)(NeftaPlugin.GetRetryDelayInSeconds(track._insight, track._adUnitId) * 1000));
                }
            });
        } else {
            NeftaMediationAdapter.OnExternalMediationRequest(track._interstitial);

            Log("Loading "+ track._adUnitId + " as Default");
            track._interstitial.loadAd();
        }
    }

    public void ContinueOptimized() {
        _isFirstOptimizedResponseReceived = true;
        if (_tracks[0]._state == State.Idle) {
            Load(_tracks[0]);
        }

        _isGlobalCooldown++;
        _handler.postDelayed(() -> {
            _isGlobalCooldown--;
            if (_isGlobalCooldown <= 0) {
                _isGlobalCooldown = 0;
                ContinueLoading();
            }
        }, NeftaPlugin._instance._state._globalTrackCooldownInMs);
    }

    private void ContinueLoading() {
        if (!_ui.IsAutoLoad) {
            return;
        }

        if (_tracks[0]._state == State.Idle) {
            Load(_tracks[0]);
        }
        if (_isGlobalCooldown > 0) {
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

    private boolean TryShow(Track track) {
        track._revenue = -1;
        if (track._interstitial.isReady()) {
            track._state = State.Shown;
            track._interstitial.showAd(_ui.Activity);
            return true;
        }
        track._state = State.Idle;
        return false;
    }

    public void OnTrackLoad(Track track, boolean success) {
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

        if (track._isOptimized) {
            ContinueOptimized();
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

    void Log(String log) {
        _ui.Log(log);
    }
}
