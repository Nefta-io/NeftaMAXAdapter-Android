package com.nefta.max;

import static com.applovin.mediation.adapter.MaxAdapterError.ERROR_CODE_INTERNAL_ERROR;
import static com.applovin.mediation.adapter.MaxAdapterError.ERROR_CODE_NO_FILL;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
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
import com.nefta.debug.Callback;
import com.nefta.debug.NDebug;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.Locale;

public class InterstitialSim extends LinearLayout {

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
        public SimInterstitial _interstitial;
        public State _state = State.Idle;
        public AdInsight _insight;
        public double _revenue;

        public Track(String adUnit, boolean isOptimized) {
            _adUnitId = adUnit;
            _isOptimized = isOptimized;

            _interstitial = new SimInterstitial(_adUnitId);
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
        public void onAdRevenuePaid(@NonNull final MaxAd ad) {
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

    private Activity _activity;
    private Switch _loadSwitch;
    private Button _showButton;
    private TextView _status;

    private TextView[] _statuses;
    private Button[] _fill3;
    private Button[] _fill2;
    private Button[] _fill1;
    private Button[] _noFill;
    private Button[] _other;

    private Handler _handler;
    private boolean _isOptimized;

    public void Init(boolean isOptimized) {
        _isOptimized = isOptimized;
        setVisibility(View.VISIBLE);

        if (NeftaPlugin._instance._state._isDebugEnabled) {
            NeftaPlugin._extraParams.put("max_test_mode", true);
            HashMap<String, String> extra = new HashMap<String, String>();
            extra.put("disable_b2b_ad_unit_ids","e5dc3548d4a0913f,6d318f954e2630a8,e0b0d20088d60ec5,918acf84edf9c034");
            NeftaPlugin._extraParams.put("max_extra", extra);
        }
        NeftaPlugin._instance._state._adProviderCountry = "SIM";

        if (_isOptimized) {
            JSONArray networks = new JSONArray();
            networks.put("simulator");
            NeftaPlugin._instance._state._availableNetworks = networks;
        }
    }

    private void Load() {
        if (_isFirstRequest && _isOptimized) {
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
                _handler.postDelayed(() -> {
                    Load(_tracks[0]);
                }, backFillTrackDelayInMs);
            }

            _isFirstRequest = false;
        } else {
            ContinueLoading();
        }
    }

    private void Load(Track track) {
        track._state = State.Loading;

        if (track._isOptimized) {
            NeftaPlugin._instance.GetInsights(Insights.INTERSTITIAL, track._insight, (Insights insights) -> {
                Log("LoadWithInsights: " + insights);
                if (insights._interstitial != null) {
                    track._insight = insights._interstitial;
                    String bidFloor = "";
                    if (track._insight._floorPrice >= 0) {
                        bidFloor = String.format(Locale.ROOT, "%.10f", track._insight._floorPrice);
                    }

                    track._interstitial.setExtraParameter("jC7Fp", bidFloor);

                    NeftaMediationAdapter.OnExternalMediationRequest(track._interstitial, track._insight);

                    Log("Loading " + track._adUnitId + " as Optimized with floor: " + bidFloor);
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
        if (!_loadSwitch.isChecked()) {
            return;
        }

        if (_tracks[0]._state == State.Idle) {
            Load(_tracks[0]);
        }
        if (_isGlobalCooldown > 0 || !_isOptimized) {
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

        _tracks = new Track[] {
            new Track("Inter Track D", false),
            new Track("Inter Track A", true),
            new Track("Inter Track B", true)
        };

        _loadSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Load();
        });
        _showButton.setOnClickListener(view -> {
            boolean isShown = false;
            Track readyTrack = GetReadyTrack();
            if (readyTrack != null) {
                isShown = TryShow(readyTrack);
            }
            if (!isShown) {
                ContinueLoading();
            }
            readyTrack = GetReadyTrack();
            _showButton.setEnabled(readyTrack != null);
        });
        _showButton.setEnabled(false);

        _statuses = new TextView[] {
            findViewById(R.id.interstitialSim_statusD),
            findViewById(R.id.interstitialSim_statusA),
            findViewById(R.id.interstitialSim_statusB)
        };
        _fill3 = new Button[] {
            findViewById(R.id.interstitialSim_fill3D),
            findViewById(R.id.interstitialSim_fill3A),
            findViewById(R.id.interstitialSim_fill3B)
        };
        _fill2 = new Button[] {
            findViewById(R.id.interstitialSim_fill2D),
            findViewById(R.id.interstitialSim_fill2A),
            findViewById(R.id.interstitialSim_fill2B)
        };
        _fill1 = new Button[] {
            findViewById(R.id.interstitialSim_fill1D),
            findViewById(R.id.interstitialSim_fill1A),
            findViewById(R.id.interstitialSim_fill1B)
        };
        _noFill = new Button[] {
            findViewById(R.id.interstitialSim_noFillD),
            findViewById(R.id.interstitialSim_noFillA),
            findViewById(R.id.interstitialSim_noFillB)
        };
        _other = new Button[] {
            findViewById(R.id.interstitialSim_OtherD),
            findViewById(R.id.interstitialSim_OtherA),
            findViewById(R.id.interstitialSim_OtherB)
        };

        for (int t = 0; t < 3; t++) {
            int ti = t;
            _fill3[t].setOnClickListener(v -> SimOnAdLoadedEvent(ti, _tracks[ti], 3));
            _fill2[t].setOnClickListener(v -> SimOnAdLoadedEvent(ti, _tracks[ti], 2));
            _fill1[t].setOnClickListener(v -> SimOnAdLoadedEvent(ti, _tracks[ti], 1));
            _noFill[t].setOnClickListener(v -> SimOnAdFailedEvent(_tracks[ti], 2));
            _other[t].setOnClickListener(v -> SimOnAdFailedEvent(_tracks[ti], 0));
            ToggleTrack(t, false, true);
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        if (visibility == GONE) {
            _loadSwitch.setChecked(false);
        }
        super.onVisibilityChanged(changedView, visibility);
    }

    private boolean TryShow(Track track) {
        track._revenue = -1;
        if (track._interstitial.isReady()) {
            track._state = State.Shown;
            track._interstitial.showAd(_activity);
            return true;
        }
        track._state = State.Idle;
        return false;
    }

    public void OnTrackLoad(Track track, boolean success) {
        if (success) {
            _showButton.setEnabled(true);
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
            for (int i = 0; i < _tracks.length; i++) {
                if (_tracks[i]._adUnitId.equals(_adUnitId)) {
                    ToggleTrack(i, true, true);
                    _statuses[i].setText("Loading " +_adUnitId + (_floor >= 0 ? " as Optimized" : "as Default"));
                    break;
                }
            }
        }

        @Override
        public void showAd(Activity activity) {
            MaxAd ad = _ad;
            if (ad == null) {
                return;
            }

            _ad = null;
            for (int i = 0; i < _tracks.length; i++) {
                if (_tracks[i]._adUnitId.equals(_adUnitId)) {
                    ToggleTrack(i, false, false);
                    _statuses[i].setText("Showing "+ _adUnitId);
                    break;
                }
            }

            NDebug.Open("Interstitial", activity,
                    new Callback() {
                        @Override
                        public void onShow() {
                            _listener.onAdDisplayed(ad);
                            _revenueListener.onAdRevenuePaid(ad);
                        }

                        @Override
                        public void onClick() {
                            _listener.onAdClicked(ad);
                        }

                        @Override
                        public void onReward() {

                        }

                        @Override
                        public void onClose() {
                            _listener.onAdHidden(ad);
                        }
                    });
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

        @Override
        public void destroy() {
            _ad = null;

            for (int i = 0; i < _tracks.length; i++) {
                if (_tracks[i]._adUnitId.equals(_adUnitId)) {
                    ToggleTrack(i, false, true);
                    _statuses[i].setText("");
                    break;
                }
            }
        }
    }

    private void ToggleTrack(int t, boolean on, boolean refresh) {
        _fill3[t].setEnabled(on);
        _fill2[t].setEnabled(on);
        _fill1[t].setEnabled(on);
        _noFill[t].setEnabled(on);
        _other[t].setEnabled(on);

        if (refresh) {
            _fill3[t].setBackgroundResource(R.drawable.button);
            _fill2[t].setBackgroundResource(R.drawable.button);
            _fill1[t].setBackgroundResource(R.drawable.button);
            _noFill[t].setBackgroundResource(R.drawable.button);
            _other[t].setBackgroundResource(R.drawable.button);
        }

        _fill3[t].refreshDrawableState();
        _fill2[t].refreshDrawableState();
        _fill1[t].refreshDrawableState();
        _noFill[t].refreshDrawableState();
        _other[t].refreshDrawableState();
    }

    private void SimOnAdLoadedEvent(int t, Track track, int r) {
        double revenue = r == 3 ? 0.003 : r == 2 ? 0.002 : 0.001;
        if (track._interstitial._ad != null) {
            track._interstitial._ad = null;

            _fill3[t].setBackgroundResource(R.drawable.button);
            _fill3[t].setEnabled(false);

            _fill2[t].setBackgroundResource(R.drawable.button);
            _fill2[t].setEnabled(false);

            _fill1[t].setBackgroundResource(R.drawable.button);
            _fill1[t].setEnabled(false);

            return;
        }

        MaxAd ad = new SMaxAd(track._adUnitId, MaxAdFormat.INTERSTITIAL, revenue,
                new MaxNetworkResponseInfo.AdLoadState[] { MaxNetworkResponseInfo.AdLoadState.AD_LOADED, MaxNetworkResponseInfo.AdLoadState.AD_LOAD_NOT_ATTEMPTED });

        ToggleTrack(t, false, false);
        if (r == 3) {
            _fill3[t].setBackgroundResource(R.drawable.button_fill);
            _fill3[t].setEnabled(true);
        } else if (r == 2) {
            _fill2[t].setBackgroundResource(R.drawable.button_fill);
            _fill2[t].setEnabled(true);
        } else if (r == 1) {
            _fill1[t].setBackgroundResource(R.drawable.button_fill);
            _fill1[t].setEnabled(true);
        }
        _statuses[t].setText(track._adUnitId + " loaded " + revenue);

        track._interstitial.SimLoad(ad);
    }

    private void SimOnAdFailedEvent(Track track, int status) {
        for (int t = 0; t < _tracks.length; t++) {
            if (_tracks[t] == track) {
                if (status == 2) {
                    _noFill[t].setBackgroundResource(R.drawable.button_no);
                } else {
                    _other[t].setBackgroundResource(R.drawable.button_no);
                }
                ToggleTrack(t, false, false);
                _statuses[t].setText(track._adUnitId + " failed");
            }
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
