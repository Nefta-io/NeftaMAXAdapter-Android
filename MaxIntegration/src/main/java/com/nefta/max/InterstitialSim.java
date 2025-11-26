package com.nefta.max;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
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
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.applovin.mediation.nativeAds.MaxNativeAd;
import com.applovin.sdk.AppLovinSdkUtils;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

import java.util.List;
import java.util.Locale;


public class InterstitialSim extends TableLayout {
    private final int TimeoutInSeconds = 5;

    private enum State {
        Idle,
        LoadingWithInsights,
        Loading,
        Ready
    }

    private class AdRequest implements MaxAdListener, MaxAdRevenueListener {
        public final String _adUnitId;
        public SimInterstitial _interstitial;
        public State _state = State.Idle;
        public AdInsight _insight;
        public double _revenue;
        public int _consecutiveAdFails;

        public AdRequest(String adUnit) {
            _adUnitId = adUnit;
        }

        @Override
        public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_interstitial, maxError);

            Log("Load failed " + adUnitId + ": " + maxError.getMessage());

            _interstitial = null;
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
                RetryLoading();
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

            RetryLoading();
        }

        @Override
        public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError maxError) {
            Log("onAdDisplayFailed "+ ad.getAdUnitId());

            RetryLoading();
        }
    }

    private AdRequest _adRequestA;
    private AdRequest _adRequestB;
    private boolean _isFirstResponseReceived = false;

    private final MainActivity _activity;
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

    private void StartLoading() {
        Load(_adRequestA, _adRequestB._state);
        Load(_adRequestB, _adRequestA._state);
    }

    private void Load(AdRequest request, State otherState) {
        if (request._state == State.Idle) {
            if (otherState != State.LoadingWithInsights) {
                GetInsightsAndLoad(request);
            } else if (_isFirstResponseReceived) {
                LoadDefault(request);
            }
        }
    }

    private void GetInsightsAndLoad(AdRequest request) {
        request._state = State.LoadingWithInsights;

        NeftaPlugin._instance.GetInsights(Insights.INTERSTITIAL, request._insight, (Insights insights) -> {
            Log("LoadWithInsights: " + insights);
            if (insights._interstitial != null) {
                request._insight = insights._interstitial;
                String bidFloor = String.format(Locale.ROOT, "%.10f", request._insight._floorPrice);
                request._interstitial = new SimInterstitial(request._adUnitId);
                request._interstitial.setListener(request);
                request._interstitial.setRevenueListener(request);
                request._interstitial.setExtraParameter("disable_auto_retries", "true");
                request._interstitial.setExtraParameter("jC7Fp", bidFloor);

                NeftaMediationAdapter.OnExternalMediationRequest(request._interstitial, request._insight);

                Log("Loading "+ request._adUnitId + " as Optimized with floor: " + bidFloor);
                request._interstitial.loadAd();
            } else {
                request.OnLoadFail();
            }
        }, TimeoutInSeconds);
    }

    private void LoadDefault(AdRequest request) {
        request._state = State.Loading;

        Log("Loading "+ request._adUnitId + " as Default");

        request._interstitial = new SimInterstitial(request._adUnitId);
        request._interstitial.setListener(request);
        request._interstitial.setRevenueListener(request);

        NeftaMediationAdapter.OnExternalMediationRequest(request._interstitial);

        request._interstitial.loadAd();
    }

    public InterstitialSim(Context context) {
        super(context);
        _activity = (MainActivity)context;
    }

    public InterstitialSim(Context context, @Nullable AttributeSet attrs) {
        super(context);
        _activity = (MainActivity)context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        _loadSwitch = findViewById(R.id.interstitialSim_load);
        _showButton = findViewById(R.id.interstitialSim_show);
        _status = findViewById(R.id.interstitialSim_status);

        _handler = new Handler(Looper.getMainLooper());

        String adUnitA = "Track A";
        _adRequestA = new AdRequest(adUnitA);
        String adUnitB = "Track B";
        _adRequestB = new AdRequest(adUnitB);

        _loadSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                StartLoading();
            }
        });
        _showButton.setOnClickListener(view -> {
            boolean isShown = false;
            if (_adRequestA._state == State.Ready) {
                if (_adRequestB._state == State.Ready && _adRequestB._revenue > _adRequestA._revenue) {
                    isShown = TryShow(_adRequestB);
                }
                if (!isShown) {
                    isShown = TryShow(_adRequestA);
                }
            }
            if (!isShown && _adRequestB._state == State.Ready) {
                TryShow(_adRequestB);
            }
            UpdateShowButton();
        });
        _showButton.setEnabled(false);

        _aStatus = findViewById(R.id.interstitialSim_statusA);
        _aFill2 = findViewById(R.id.interstitialSim_fill2A);
        _aFill2.setOnClickListener(v -> SimOnAdLoadedEvent(_adRequestA, 2.0));
        _aFill1 = findViewById(R.id.interstitialSim_fill1A);
        _aFill1.setOnClickListener(v -> SimOnAdLoadedEvent(_adRequestA, 1.0));
        _aNoFill = findViewById(R.id.interstitialSim_noFillA);
        _aNoFill.setOnClickListener(v -> SimOnAdFailedEvent(_adRequestA, 2));
        _aOther = findViewById(R.id.interstitialSim_OtherA);
        _aOther.setOnClickListener(v -> SimOnAdFailedEvent(_adRequestA, 0));
        ToggleTrackA(false, true);

        _bStatus = findViewById(R.id.interstitialSim_statusB);
        _bFill2 = findViewById(R.id.interstitialSim_fill2B);
        _bFill2.setOnClickListener(v -> SimOnAdLoadedEvent(_adRequestB, 2.0));
        _bFill1 = findViewById(R.id.interstitialSim_fill1B);
        _bFill1.setOnClickListener(v -> SimOnAdLoadedEvent(_adRequestB, 1.0));
        _bNoFill = findViewById(R.id.interstitialSim_noFillB);
        _bNoFill.setOnClickListener(v -> SimOnAdFailedEvent(_adRequestB, 2));
        _bOther = findViewById(R.id.interstitialSim_OtherB);
        _bOther.setOnClickListener(v -> SimOnAdFailedEvent(_adRequestB, 0));
        ToggleTrackB(false, true);

        setVisibility(BuildConfig.IS_SIMULATOR ? View.VISIBLE : View.GONE);
    }

    private boolean TryShow(AdRequest request) {
        request._state = State.Idle;
        request._revenue = -1;

        if (request._interstitial.isReady()) {
            request._interstitial.showAd(_activity);
            return true;
        }
        RetryLoading();
        return false;
    }

    public void RetryLoading() {
        if (_loadSwitch.isChecked()) {
            StartLoading();
        }
    }

    public void OnTrackLoad(boolean success) {
        if (success) {
            UpdateShowButton();
        }

        _isFirstResponseReceived = true;
        RetryLoading();
    }

    private void UpdateShowButton() {
        _showButton.setEnabled(_adRequestA._state == State.Ready || _adRequestB._state == State.Ready);
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
        public void setRevenueListener(MaxAdRevenueListener listener) {
            _revenueListener = listener;
        }

        @Override
        public void setExtraParameter(String key, String value) {
            if ("jC7Fp".equals(key)) {
                _floor = Double.parseDouble(value);
            }
        }

        @Override
        public void loadAd() {
            String status = _adUnitId + " loading " + (_floor >= 0 ? " as Optimized" : "as Default");

            if (_adRequestA._adUnitId.equals(_adUnitId)) {
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

            SimulatorAd.Instance.Show("Interstitial",
                    () -> { _listener.onAdDisplayed(_ad); },
                    () -> { _listener.onAdClicked(_ad); },
                    null,
                    () -> { _listener.onAdHidden(_ad); }
            );

            if (_adRequestA._adUnitId.equals(_adUnitId)) {
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

    private void SimOnAdLoadedEvent(AdRequest request, double revenue) {
        if (request._interstitial._ad != null) {
            request._interstitial._ad = null;

            if (request == _adRequestA) {
                if (revenue >= 2) {
                    _aFill2.setBackgroundResource(R.drawable.button);
                    _aFill2.setEnabled(false);
                } else {
                    _aFill1.setBackgroundResource(R.drawable.button);
                    _aFill1.setEnabled(false);
                }
            } else {
                if (revenue >= 2) {
                    _bFill2.setBackgroundResource(R.drawable.button);
                    _bFill2.setEnabled(false);
                } else {
                    _bFill1.setBackgroundResource(R.drawable.button);
                    _bFill1.setEnabled(false);
                }
            }
            return;
        }

        MaxAd ad = new MaxAd() {
            @Override
            public MaxAdFormat getFormat() {
                return MaxAdFormat.INTERSTITIAL;
            }

            @Override
            public AppLovinSdkUtils.Size getSize() {
                return AppLovinSdkUtils.Size.ZERO;
            }

            @Override
            public String getAdUnitId() {
                return request._adUnitId;
            }

            @Override
            public String getNetworkName() {
                return "simulator";
            }

            @Override
            public String getNetworkPlacement() {
                return null;
            }

            @Override
            public String getPlacement() {
                return null;
            }

            @Override
            public MaxAdWaterfallInfo getWaterfall() {
                return new MaxAdWaterfallInfo() {
                    @Override
                    public MaxAd getLoadedAd() {
                        return null;
                    }

                    @Override
                    public String getName() {
                        return "simulator waterfall";
                    }

                    @Override
                    public String getTestName() {
                        return "simulator waterfall test";
                    }

                    @Override
                    public List<MaxNetworkResponseInfo> getNetworkResponses() {
                        return null;
                    }

                    @Override
                    public long getLatencyMillis() {
                        return 0;
                    }
                };
            }

            @Override
            public long getRequestLatencyMillis() {
                return 0;
            }

            @Nullable
            @Override
            public String getCreativeId() {
                return null;
            }

            @Override
            public double getRevenue() {
                return revenue;
            }

            @Override
            public String getRevenuePrecision() {
                return "exact";
            }

            @Nullable
            @Override
            public String getDspName() {
                return null;
            }

            @Nullable
            @Override
            public String getDspId() {
                return null;
            }

            @Override
            public String getAdValue(String s) {
                return null;
            }

            @Override
            public String getAdValue(String s, String s1) {
                return null;
            }

            @Nullable
            @Override
            public MaxNativeAd getNativeAd() {
                return null;
            }

            @Nullable
            @Override
            public String getAdReviewCreativeId() {
                return "simulator creative";
            }
        };

        if (request == _adRequestA) {
            ToggleTrackA(false, false);
            if (revenue >= 2) {
                _aFill2.setBackgroundResource(R.drawable.button_fill);
                _aFill2.setEnabled(true);
            } else {
                _aFill1.setBackgroundResource(R.drawable.button_fill);
                _aFill1.setEnabled(true);
            }
            _aStatus.setText(request._adUnitId + " loaded " + revenue);
        } else {
            ToggleTrackB(false, false);
            if (revenue >= 2) {
                _bFill2.setBackgroundResource(R.drawable.button_fill);
                _bFill2.setEnabled(true);
            } else {
                _bFill1.setBackgroundResource(R.drawable.button_fill);
                _bFill1.setEnabled(true);
            }
            _bStatus.setText(request._adUnitId + " loaded " + revenue);
        }

        request._interstitial.SimLoad(ad);
    }

    private void SimOnAdFailedEvent(AdRequest request, int status) {
        if (request == _adRequestA) {
            if (status == 2) {
                _aNoFill.setBackgroundResource(R.drawable.button_no);
            } else {
                _aOther.setBackgroundResource(R.drawable.button_no);
            }
            ToggleTrackA(false, false);
            _aStatus.setText(request._adUnitId + " failed");
        } else {
            if (status == 2) {
                _bNoFill.setBackgroundResource(R.drawable.button_no);
            } else {
                _bOther.setBackgroundResource(R.drawable.button_no);
            }
            _bStatus.setText(request._adUnitId + " failed");
            ToggleTrackB(false, false);
        }

        MaxError error = status == 2 ? MaxAdapterError.NO_FILL : MaxAdapterError.INTERNAL_ERROR;
        request._interstitial.SimFailLoad(error);
    }
}
