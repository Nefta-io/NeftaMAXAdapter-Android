package com.nefta.max;

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
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxAdWaterfallInfo;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.applovin.mediation.nativeAds.MaxNativeAd;
import com.applovin.sdk.AppLovinSdkUtils;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

import java.util.Locale;

public class RewardedWrapper extends TableLayout {
    private final String AdUnitA = "a4b93fe91b278c75";
    private final String AdUnitB = "72458470d47ee781";
    private final int TimeoutInSeconds = 5;

    private enum State {
        Idle,
        LoadingWithInsights,
        Loading,
        Ready
    }

    private class AdRequest implements MaxRewardedAdListener, MaxAdRevenueListener {
        public final String _adUnitId;
        public MaxRewardedAd _rewarded;
        public State _state = State.Idle;
        public AdInsight _insight;
        public double _revenue;
        public int _consecutiveAdFails;

        public AdRequest(String adUnit) {
            _adUnitId = adUnit;
        }

        @Override
        public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError maxError) {
            NeftaMediationAdapter.OnExternalMediationRequestFailed(_rewarded, maxError);

            Log("Load failed " + adUnitId + ": " + maxError.getMessage());

            _rewarded = null;
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
                RetryLoading();
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

    private MainActivity _activity;
    private Switch _loadSwitch;
    private Button _showButton;
    private TextView _status;

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

        NeftaPlugin._instance.GetInsights(Insights.REWARDED, request._insight, (Insights insights) -> {
            Log("LoadWithInsights: " + insights);
            if (insights._rewarded != null) {
                request._insight = insights._rewarded;
                String bidFloor = String.format(Locale.ROOT, "%.10f", request._insight._floorPrice);
                request._rewarded = MaxRewardedAd.getInstance(request._adUnitId);
                request._rewarded.setListener(request);
                request._rewarded.setRevenueListener(request);
                request._rewarded.setExtraParameter("disable_auto_retries", "true");
                request._rewarded.setExtraParameter("jC7Fp", bidFloor);

                NeftaMediationAdapter.OnExternalMediationRequest(request._rewarded, request._insight);

                Log("Loading "+ request._adUnitId + " as Optimized with floor: " + bidFloor);
                request._rewarded.loadAd();
            } else {
                request.OnLoadFail();
            }
        }, TimeoutInSeconds);
    }

    private void LoadDefault(AdRequest request) {
        request._state = State.Loading;

        Log("Loading "+ request._adUnitId + " as Default");

        request._rewarded = MaxRewardedAd.getInstance(request._adUnitId);
        request._rewarded.setListener(request);
        request._rewarded.setRevenueListener(request);

        NeftaMediationAdapter.OnExternalMediationRequest(request._rewarded);

        request._rewarded.loadAd();
    }

    public RewardedWrapper(Context context) {
        super(context);
        _activity = (MainActivity) context;
    }

    public RewardedWrapper(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        _activity = (MainActivity) context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        _adRequestA = new AdRequest(AdUnitA);
        _adRequestB = new AdRequest(AdUnitB);

        _loadSwitch = findViewById(R.id.rewarded_load);;
        _showButton = findViewById(R.id.rewarded_show);;
        _status = findViewById(R.id.rewarded_status);;

        _handler = new Handler(Looper.getMainLooper());

        _loadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    StartLoading();
                }
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
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
            }
        });
        _showButton.setEnabled(false);
    }

    private boolean TryShow(AdRequest request) {
        request._state = State.Idle;
        request._revenue = -1;

        if (request._rewarded.isReady()) {
            request._rewarded.showAd(_activity);
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
        Log.i("NeftaPluginMAX", "Rewarded " + log);
    }
}