package com.nefta.max;

import static com.applovin.mediation.adapter.MaxAdapterError.ERROR_CODE_INTERNAL_ERROR;
import static com.applovin.mediation.adapter.MaxAdapterError.ERROR_CODE_NO_FILL;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxAdWaterfallInfo;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxNetworkResponseInfo;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.nefta.networkconfig.Callback;
import com.nefta.networkconfig.NetworkConfig;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

import java.util.Locale;

public class RewardedSim extends TableLayout {
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
        public SimRewarded _rewarded;
        public State _state = State.Idle;
        public AdInsight _insight;
        public double _revenue;
        public int _consecutiveAdFails;

        public Track(String adUnit) {
            _adUnitId = adUnit;

            _rewarded = new SimRewarded(_adUnitId);
            _rewarded.setListener(this);
            _rewarded.setRevenueListener(this);
        }

        @Override
        public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_rewarded._instance, maxError);

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
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_rewarded._instance, ad);

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
        public void onAdRevenuePaid(@NonNull final MaxAd ad) {
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

    private TextView _aStatus;
    private Button _aFill2;
    private Button _aFill1;
    private Button _aNoFill;
    private Button _aOther;

    private TextView _bStatus;
    private Button _bFill2;
    private Button _bFill1;
    private Button _bNoFill;
    private Button _bOther;

    private Handler _handler;

    private void LoadTracks() {
        LoadTrack(_trackA, _trackB._state);
        LoadTrack(_trackB, _trackA._state);
    }

    private void LoadTrack(Track track, State otherState) {
        if (track._state == State.Idle) {
            if (otherState == State.LoadingWithInsights) {
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

                NeftaMediationAdapter.OnExternalMediationRequest(track._rewarded._instance, track._insight);

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

        NeftaMediationAdapter.OnExternalMediationRequest(track._rewarded._instance);

        track._rewarded.loadAd();
    }

    public RewardedSim(Context context) {
        super(context);
        if (context instanceof Activity) {
            _activity = (Activity) context;
        }
    }

    public RewardedSim(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (context instanceof Activity) {
            _activity = (Activity) context;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        _loadSwitch = findViewById(R.id.rewardedSim_load);
        _showButton = findViewById(R.id.rewardedSim_show);
        _status = findViewById(R.id.rewardedSim_status);

        _handler = new Handler(Looper.getMainLooper());

        String adUnitA = "Rewarded Track A";
        _trackA = new Track(adUnitA);
        String adUnitB = "Rewarded Track B";
        _trackB = new Track(adUnitB);

        _loadSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                LoadTracks();
            }
        });
        _showButton.setOnClickListener(view -> {
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
        });
        _showButton.setEnabled(false);

        _aStatus = findViewById(R.id.rewardedSim_statusA);
        _aFill2 = findViewById(R.id.rewardedSim_fill2A);
        _aFill2.setOnClickListener(v -> SimOnAdLoadedEvent(_trackA, true));
        _aFill1 = findViewById(R.id.rewardedSim_fill1A);
        _aFill1.setOnClickListener(v -> SimOnAdLoadedEvent(_trackA, false));
        _aNoFill = findViewById(R.id.rewardedSim_noFillA);
        _aNoFill.setOnClickListener(v -> SimOnAdFailedEvent(_trackA, 2));
        _aOther = findViewById(R.id.rewardedSim_OtherA);
        _aOther.setOnClickListener(v -> SimOnAdFailedEvent(_trackA, 0));
        ToggleTrackA(false, true);

        _bStatus = findViewById(R.id.rewardedSim_statusB);
        _bFill2 = findViewById(R.id.rewardedSim_fill2B);
        _bFill2.setOnClickListener(v -> SimOnAdLoadedEvent(_trackB, true));
        _bFill1 = findViewById(R.id.rewardedSim_fill1B);
        _bFill1.setOnClickListener(v -> SimOnAdLoadedEvent(_trackB, false));
        _bNoFill = findViewById(R.id.rewardedSim_noFillB);
        _bNoFill.setOnClickListener(v -> SimOnAdFailedEvent(_trackB, 2));
        _bOther = findViewById(R.id.rewardedSim_OtherB);
        _bOther.setOnClickListener(v -> SimOnAdFailedEvent(_trackB, 0));
        ToggleTrackB(false, true);
    }

    private boolean TryShow(Track track) {
        track._revenue = -1;
        if (track._rewarded.isReady()) {
            track._state = State.Shown;
            track._rewarded.showAd(_activity);
            return true;
        }
        track._state = State.Idle;
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
        Log.i("NeftaPluginMAX Sim", "Rewarded " + log);
    }

    private class SimRewarded {
        public final String _adUnitId;
        public MaxAd _ad;
        public double _floor = -1;
        public MaxRewardedAdListener _listener;
        public MaxAdRevenueListener _revenueListener;
        public MaxRewardedAd _instance;

        public SimRewarded(String adUnitId) {
            _adUnitId = adUnitId;
            _instance = MaxRewardedAd.getInstance(adUnitId);
        }

        public void setListener(MaxRewardedAdListener listener) {
            _listener = listener;
        }

        public void setRevenueListener(MaxAdRevenueListener listener) {
            _revenueListener = listener;
        }

        public void setExtraParameter(String key, String value) {
            if ("jC7Fp".equals(key)) {
                if (value == null || value.isEmpty()) {
                    _floor = -1;
                } else {
                    _floor = Double.parseDouble(value);
                }
            }
        }

        public void loadAd() {
            String status = _adUnitId + " loading " + (_floor >= 0 ? " as Optimized" : "as Default");

            if (_trackA._adUnitId.equals(_adUnitId)) {
                ToggleTrackA(true, true);
                _aStatus.setText(status);
            } else {
                ToggleTrackB(true, true);
                _bStatus.setText(status);
            }
        }

        public void showAd(Activity activity) {
            _revenueListener.onAdRevenuePaid(_ad);

            NetworkConfig.Open("Rewarded", activity,
                    new Callback() {
                        @Override
                        public void onShow() {
                            _listener.onAdDisplayed(_ad);
                        }

                        @Override
                        public void onClick() {
                            _listener.onAdClicked(_ad);
                        }

                        @Override
                        public void onReward() {
                            _listener.onUserRewarded(_ad, new MaxReward() {
                                @Override
                                public String getLabel() {
                                    return "simulated reward";
                                }

                                @Override
                                public int getAmount() {
                                    return 1;
                                }
                            });
                        }

                        @Override
                        public void onClose() {
                            _listener.onAdHidden(_ad);
                            _ad = null;
                        }
                    });

            if (_trackA._adUnitId.equals(_adUnitId)) {
                _aStatus.setText("Showing A");
            } else {
                _bStatus.setText("Showing B");
            }
        }

        public void SimLoad(MaxAd ad) {
            _ad = ad;
            _listener.onAdLoaded(_ad);
        }

        public void SimFailLoad(MaxError error) {
            _listener.onAdLoadFailed(_adUnitId,  error);
        }

        public boolean isReady() {
            return _ad != null;
        }

        public String getAdUnitId() {
            return _adUnitId;
        }
    }

    private void ToggleTrackA(boolean on, boolean refresh) {
        _aFill2.setEnabled(on);
        _aFill1.setEnabled(on);
        _aNoFill.setEnabled(on);
        _aOther.setEnabled(on);

        if (refresh) {
            _aFill2.setBackgroundResource(R.drawable.button);
            _aFill1.setBackgroundResource(R.drawable.button);
            _aNoFill.setBackgroundResource(R.drawable.button);
            _aOther.setBackgroundResource(R.drawable.button);
        }

        _aFill2.refreshDrawableState();
        _aFill1.refreshDrawableState();
        _aNoFill.refreshDrawableState();
        _aOther.refreshDrawableState();
    }

    private void ToggleTrackB(boolean on, boolean refresh) {
        _bFill2.setEnabled(on);
        _bFill1.setEnabled(on);
        _bNoFill.setEnabled(on);
        _bOther.setEnabled(on);

        if (refresh) {
            _bFill2.setBackgroundResource(R.drawable.button);
            _bFill1.setBackgroundResource(R.drawable.button);
            _bNoFill.setBackgroundResource(R.drawable.button);
            _bOther.setBackgroundResource(R.drawable.button);
        }

        _bFill2.refreshDrawableState();
        _bFill1.refreshDrawableState();
        _bNoFill.refreshDrawableState();
        _bOther.refreshDrawableState();
    }

    private void SimOnAdLoadedEvent(Track track, boolean isHigh) {
        double revenue = isHigh ? 0.002 : 0.001;
        if (track._rewarded._ad != null) {
            track._rewarded._ad = null;

            if (track == _trackA) {
                if (isHigh) {
                    _aFill2.setBackgroundResource(R.drawable.button);
                    _aFill2.setEnabled(false);
                } else {
                    _aFill1.setBackgroundResource(R.drawable.button);
                    _aFill1.setEnabled(false);
                }
            } else {
                if (isHigh) {
                    _bFill2.setBackgroundResource(R.drawable.button);
                    _bFill2.setEnabled(false);
                } else {
                    _bFill1.setBackgroundResource(R.drawable.button);
                    _bFill1.setEnabled(false);
                }
            }
            return;
        }
        MaxAd ad = new SMaxAd(track._adUnitId, MaxAdFormat.REWARDED, revenue,
            new MaxNetworkResponseInfo.AdLoadState[] { MaxNetworkResponseInfo.AdLoadState.AD_LOADED, MaxNetworkResponseInfo.AdLoadState.AD_LOAD_NOT_ATTEMPTED });

        if (track == _trackA) {
            ToggleTrackA(false, false);
            if (isHigh) {
                _aFill2.setBackgroundResource(R.drawable.button_fill);
                _aFill2.setEnabled(true);
            } else {
                _aFill1.setBackgroundResource(R.drawable.button_fill);
                _aFill1.setEnabled(true);
            }
            _aStatus.setText(track._adUnitId + " loaded " + revenue);
        } else {
            ToggleTrackB(false, false);
            if (isHigh) {
                _bFill2.setBackgroundResource(R.drawable.button_fill);
                _bFill2.setEnabled(true);
            } else {
                _bFill1.setBackgroundResource(R.drawable.button_fill);
                _bFill1.setEnabled(true);
            }
            _bStatus.setText(track._adUnitId + " loaded " + revenue);
        }

        track._rewarded.SimLoad(ad);
    }

    private void SimOnAdFailedEvent(Track track, int status) {
        if (track == _trackA) {
            if (status == 2) {
                _aNoFill.setBackgroundResource(R.drawable.button_no);
            } else {
                _aOther.setBackgroundResource(R.drawable.button_no);
            }
            ToggleTrackA(false, false);
        } else {
            if (status == 2) {
                _bNoFill.setBackgroundResource(R.drawable.button_no);
            } else {
                _bOther.setBackgroundResource(R.drawable.button_no);
            }
            ToggleTrackB(false, false);
        }

        MaxError error = new MaxError() {
            @Override
            public int getCode() {
                return status == 2 ? ERROR_CODE_NO_FILL : ERROR_CODE_INTERNAL_ERROR;
            }

            @Override
            public String getMessage() {
                return null;
            }

            @Override
            public int getMediatedNetworkErrorCode() {
                return 0;
            }

            @Override
            public String getMediatedNetworkErrorMessage() {
                return null;
            }

            @Override
            public MaxAdWaterfallInfo getWaterfall() {
                return SMaxAd.GetWaterfall(new MaxNetworkResponseInfo.AdLoadState[] {
                        MaxNetworkResponseInfo.AdLoadState.FAILED_TO_LOAD,
                        MaxNetworkResponseInfo.AdLoadState.FAILED_TO_LOAD
                });
            }

            @Override
            public long getRequestLatencyMillis() {
                return 0;
            }

            @Override
            public String getAdLoadFailureInfo() {
                return null;
            }
        };
        track._rewarded.SimFailLoad(error);
    }
}
