package com.nefta.max;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
        public int _consecutiveAdFails;

        public Track(String adUnit) {
            _adUnitId = adUnit;

            Reset();
        }

        public void Reset() {
            if (_rewarded != null) {
                _rewarded.destroy();
            }

            _rewarded = MaxRewardedAd.getInstance(_adUnitId);
            _rewarded.setListener(this);
            _rewarded.setRevenueListener(this);

            _state = State.Idle;
            _insight = null;
            _revenue = 0;
        }

        @Override
        public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_rewarded, maxError);

            Log("Load failed " + adUnitId + ": " + maxError.getMessage());

            OnLoadFail();
        }

        public void OnLoadFail() {
            _consecutiveAdFails++;
            RetryLoad();

            OnTrackLoad(false);
        }

        @Override
        public void onAdLoaded(@NonNull MaxAd ad) {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_rewarded, ad);

            Log("Loaded  "+ _adUnitId +" at: "+ ad.getRevenue());

            _insight = null;
            _consecutiveAdFails = 0;
            _revenue = ad.getRevenue();
            _state = State.Ready;

            OnTrackLoad(true);
        }

        public void RetryLoad() {
            _handler.postDelayed(() -> {
                _state = State.Idle;
                RetryLoadTracks();
            }, (long)(NeftaMediationAdapter.GetRetryDelayInSeconds(_insight) * 1000));
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

            Log( "onAdClicked "+ ad.getAdUnitId());
        }

        @Override
        public void onUserRewarded(@NonNull MaxAd ad, @NonNull MaxReward maxReward) {
            Log("onUserRewarded "+ ad.getAdUnitId());
        }

        @Override
        public void onAdHidden(@NonNull MaxAd ad) {
            Log("onAdHidden "+ ad.getAdUnitId());

            _state = State.Idle;

            RetryLoadTracks();
        }

        @Override
        public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
            Log("onAdDisplayFailed "+ ad.getAdUnitId());

            _state = State.Idle;
            RetryLoadTracks();
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
        NeftaMediationAdapter.AddNewSessionCallback(() -> {
            Log("Rewarded on new session");
            _trackA.Reset();
            _trackB.Reset();

            UpdateAvailability();
            _isFirstResponseReceived = false;
            RetryLoadTracks();
        });
    }

    public void Load() {
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
        RetryLoadTracks();
        return false;
    }

    public void RetryLoadTracks() {
        if (_ui.IsAutoLoad) {
            Load();
        }
    }

    public void OnTrackLoad(boolean success) {
        if (success) {
            UpdateAvailability();
        }

        _isFirstResponseReceived = true;
        RetryLoadTracks();
    }

    private void UpdateAvailability() {
        _ui.SetAvailability(_trackA._state == State.Ready || _trackB._state == State.Ready);
    }

    void Log(String log) {
        _ui.Log(log);
    }
}