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
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxAdWaterfallInfo;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxNetworkResponseInfo;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.nefta.networkconfig.Callback;
import com.nefta.networkconfig.NetworkConfig;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

import java.util.Locale;

public class InterstitialSim extends TableLayout {
    private final int TimeoutInSeconds = 5;

    private enum State {
        Idle,
        LoadingWithInsights,
        Loading,
        Ready,
        Shown
    }

    private class Track implements MaxAdListener, MaxAdRevenueListener {
        public final String _adUnitId;
        public SimInterstitial _interstitial;
        public State _state = State.Idle;
        public AdInsight _insight;
        public double _revenue;
        public int _consecutiveAdFails;

        public Track(String adUnit) {
            _adUnitId = adUnit;

            _interstitial = new SimInterstitial(_adUnitId);
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
            _consecutiveAdFails++;
            RetryLoad();

            OnTrackLoad(false);
        }

        @Override
        public void onAdLoaded(@NonNull MaxAd ad) {
            NeftaMediationAdapter.OnExternalMediationRequestLoaded(_interstitial, ad);

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
        Load(_trackA, _trackB._state);
        Load(_trackB, _trackA._state);
    }

    private void Load(Track track, State otherState) {
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

        NeftaPlugin._instance.GetInsights(Insights.INTERSTITIAL, track._insight, (Insights insights) -> {
            Log("LoadWithInsights: " + insights);
            if (insights._interstitial != null) {
                track._insight = insights._interstitial;
                String bidFloor = String.format(Locale.ROOT, "%.10f", track._insight._floorPrice);

                track._interstitial.setExtraParameter("disable_auto_retries", "true");
                track._interstitial.setExtraParameter("jC7Fp", bidFloor);

                NeftaMediationAdapter.OnExternalMediationRequest(track._interstitial, track._insight);

                Log("Loading "+ track._adUnitId + " as Optimized with floor: " + bidFloor);
                track._interstitial.loadAd();
            } else {
                track.OnLoadFail();
            }
        }, TimeoutInSeconds);
    }

    private void LoadDefault(Track track) {
        track._state = State.Loading;

        Log("Loading "+ track._adUnitId + " as Default");

        track._interstitial.setExtraParameter("disable_auto_retries", "false");
        track._interstitial.setExtraParameter("jC7Fp", "");

        NeftaMediationAdapter.OnExternalMediationRequest(track._interstitial);

        track._interstitial.loadAd();
    }

    public InterstitialSim(Context context) {
        super(context);
        if (context instanceof Activity) {
            _activity = (Activity) context;
        }
    }

    public InterstitialSim(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (context instanceof Activity) {
            _activity = (Activity) context;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        _loadSwitch = findViewById(R.id.interstitialSim_load);
        _showButton = findViewById(R.id.interstitialSim_show);
        _status = findViewById(R.id.interstitialSim_status);

        _handler = new Handler(Looper.getMainLooper());

        _trackA = new Track("Inter Track A");
        _trackB = new Track("Inter Track B");

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

        _aStatus = findViewById(R.id.interstitialSim_statusA);
        _aFill2 = findViewById(R.id.interstitialSim_fill2A);
        _aFill2.setOnClickListener(v -> SimOnAdLoadedEvent(_trackA, true));
        _aFill1 = findViewById(R.id.interstitialSim_fill1A);
        _aFill1.setOnClickListener(v -> SimOnAdLoadedEvent(_trackA, false));
        _aNoFill = findViewById(R.id.interstitialSim_noFillA);
        _aNoFill.setOnClickListener(v -> SimOnAdFailedEvent(_trackA, 2));
        _aOther = findViewById(R.id.interstitialSim_OtherA);
        _aOther.setOnClickListener(v -> SimOnAdFailedEvent(_trackA, 0));
        ToggleTrackA(false, true);

        _bStatus = findViewById(R.id.interstitialSim_statusB);
        _bFill2 = findViewById(R.id.interstitialSim_fill2B);
        _bFill2.setOnClickListener(v -> SimOnAdLoadedEvent(_trackB, true));
        _bFill1 = findViewById(R.id.interstitialSim_fill1B);
        _bFill1.setOnClickListener(v -> SimOnAdLoadedEvent(_trackB, false));
        _bNoFill = findViewById(R.id.interstitialSim_noFillB);
        _bNoFill.setOnClickListener(v -> SimOnAdFailedEvent(_trackB, 2));
        _bOther = findViewById(R.id.interstitialSim_OtherB);
        _bOther.setOnClickListener(v -> SimOnAdFailedEvent(_trackB, 0));
        ToggleTrackB(false, true);
    }

    private boolean TryShow(Track track) {
        track._revenue = -1;
        if (track._interstitial.isReady()) {
            track._state = State.Shown;
            track._interstitial.showAd(_activity);
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
        Log.i("NeftaPluginMAX Sim", "Interstitial " + log);
    }

    private class SimInterstitial extends MaxInterstitialAd {
        public final String _adUnitId;
        public MaxAd _ad;
        public double _floor = -1;
        public MaxAdListener _listener;
        public MaxAdRevenueListener _revenueListener;

        public SimInterstitial(String s) {
            super(s);
            _adUnitId = s;
        }

        @Override
        public void setListener(MaxAdListener listener) {
            _listener = listener;
        }

        @Override
        public void setRevenueListener(MaxAdRevenueListener listener) {_revenueListener = listener; }

        @Override
        public void setExtraParameter(String key, String value) {
            if ("jC7Fp".equals(key)) {
                if (value == null || value.isEmpty()) {
                    _floor = -1;
                } else {
                    _floor = Double.parseDouble(value);
                }
            }
        }

        @Override
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

        @Override
        public void showAd(Activity activity) {
            _revenueListener.onAdRevenuePaid(_ad);

            NetworkConfig.Open("Interstitial", activity,
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

        @Override
        public boolean isReady() {
            return _ad != null;
        }

        @Override
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
        if (track._interstitial._ad != null) {
            track._interstitial._ad = null;

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

        MaxAd ad = new SMaxAd(track._adUnitId, MaxAdFormat.INTERSTITIAL, revenue,
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

        track._interstitial.SimLoad(ad);
    }

    private void SimOnAdFailedEvent(Track track, int status) {
        if (track == _trackA) {
            if (status == 2) {
                _aNoFill.setBackgroundResource(R.drawable.button_no);
            } else {
                _aOther.setBackgroundResource(R.drawable.button_no);
            }
            ToggleTrackA(false, false);
            _aStatus.setText(track._adUnitId + " failed");
        } else {
            if (status == 2) {
                _bNoFill.setBackgroundResource(R.drawable.button_no);
            } else {
                _bOther.setBackgroundResource(R.drawable.button_no);
            }
            _bStatus.setText(track._adUnitId + " failed");
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
        track._interstitial.SimFailLoad(error);
    }
}
