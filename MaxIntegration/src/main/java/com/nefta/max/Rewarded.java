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

public class Rewarded extends TableLayout {
    private final String AdUnitA = "a4b93fe91b278c75";
    private final String AdUnitB = "72458470d47ee781";
    private final int TimeoutInSeconds = 5;

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
            // As per MAX recommendations, retry with exponentially higher delays up to 64s
            // In case you would like to customize fill rate / revenue please contact our customer support
            long waitTimeInMs = new int[]{0, 2, 4, 8, 16, 32, 64}[Math.min(_consecutiveAdFails, 6)] * 1000L;
            _handler.postDelayed(() -> {
                _state = State.Idle;
                RetryLoadTracks();
            }, waitTimeInMs);
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

            RetryLoadTracks();
        }
    }

    private Track _trackA;
    private Track _trackB;
    private boolean _isFirstResponseReceived = false;

    private Activity _activity;
    private Switch _loadSwitch;
    private Button _showButton;
    private TextView _status;

    private Handler _handler;

    private void LoadTracks() {
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
                track._insight = insights._rewarded;
                String bidFloor = String.format(Locale.ROOT, "%.10f", track._insight._floorPrice);

                track._rewarded.setExtraParameter("disable_auto_retries", "true");
                track._rewarded.setExtraParameter("jC7Fp", bidFloor);

                NeftaMediationAdapter.OnExternalMediationRequest(track._rewarded, track._insight);

                Log("Loading "+ track._adUnitId + " as Optimized with floor: " + bidFloor);
                track._rewarded.loadAd();
            } else {
                track.OnLoadFail();
            }
        }, TimeoutInSeconds);
    }

    private void LoadDefault(Track track) {
        track._state = State.Loading;

        Log("Loading "+ track._adUnitId + " as Default");

        track._rewarded.setExtraParameter("disable_auto_retries", "false");
        track._rewarded.setExtraParameter("jC7Fp", "");

        NeftaMediationAdapter.OnExternalMediationRequest(track._rewarded);

        track._rewarded.loadAd();
    }

    public Rewarded(Context context) {
        super(context);
        if (context instanceof Activity) {
            _activity = (Activity) context;
        }
    }

    public Rewarded(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (context instanceof Activity) {
            _activity = (Activity) context;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        _trackA = new Track(AdUnitA);
        _trackB = new Track(AdUnitB);

        _loadSwitch = findViewById(R.id.rewarded_load);;
        _showButton = findViewById(R.id.rewarded_show);;
        _status = findViewById(R.id.rewarded_status);;

        _handler = new Handler(Looper.getMainLooper());

        _loadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    LoadTracks();
                }
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
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
                UpdateShowButton();
            }
        });
        _showButton.setEnabled(false);
    }

    private boolean TryShow(Track request) {
        request._revenue = -1;
        if (request._rewarded.isReady()) {
            request._state = State.Shown;
            request._rewarded.showAd(_activity);
            return true;
        }
        request._state = State.Idle;
        RetryLoadTracks();
        return false;
    }

    public void RetryLoadTracks() {
        if (_loadSwitch.isChecked()) {
            LoadTracks();
        }
    }

    public void OnTrackLoad(boolean success) {
        if (success) {
            UpdateShowButton();
        }

        _isFirstResponseReceived = true;
        RetryLoadTracks();
    }

    private void UpdateShowButton() {
        _showButton.setEnabled(_trackA._state == State.Ready || _trackB._state == State.Ready);
    }

    void Log(String log) {
        _status.setText(log);
        Log.i("NeftaPluginMAX", "Rewarded " + log);
    }
}