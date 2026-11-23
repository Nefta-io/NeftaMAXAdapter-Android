package com.nefta.max;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.adjust.sdk.AdjustAttribution;
import com.applovin.mediation.adapters.NeftaMediationAdapter;
import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinPrivacySettings;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.appsflyer.AppsFlyerLib;
import com.nefta.sdk.InitConfiguration;
import com.nefta.sdk.NeftaPlugin;
import com.nefta.debug.DebugServer;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private final String[] _dynamicAdUnits = new String[] {
        Interstitial.AdUnitOptimizedA, Interstitial.AdUnitOptimizedB,
        Rewarded.AdUnitOptimizedA, Rewarded.AdUnitOptimizedB
    };

    private CheckBox _consentCheckBox;
    private InterstitialUi _interstitialUi;
    private RewardedUi _rewardedUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        InitUI();
        DebugServer.Init(this, getIntent());
    }

    private void InitializeNefta(NeftaPlugin.OptimizationType optimizationType) {
        NeftaPlugin.EnableLogging(true);
        NeftaPlugin.SetHasUserConsent(_consentCheckBox.isChecked());
        NeftaMediationAdapter.InitWithAppId(getApplicationContext(), "5632029345447936", optimizationType, (InitConfiguration config) -> {
            Log.i("NeftaPluginMAX", "Nefta initialized nuid: " + config._nuid + " should optimize: "+ config._isSessionOptimized);

            boolean isSimulator = ((CheckBox)findViewById(R.id.isSimulator)).isChecked();
            if (isSimulator) {
                ((InterstitialSim) findViewById(R.id.interstitialSim)).Init(config._isSessionOptimized);
                ((RewardedSim) findViewById(R.id.rewardedSim)).Init(config._isSessionOptimized);
            } else if (config._isSessionOptimized) {
                _interstitialUi.Init(new InterstitialOptimized());
                _rewardedUi.Init(new RewardedOptimized());
            } else {
                _interstitialUi.Init(new InterstitialDefault());
                _rewardedUi.Init(new RewardedDefault());
            }
        });
    }

    private void InitializeMAX() {
        Log.i("NeftaPluginMAX", "Initializing MAX");

        AppLovinPrivacySettings.setHasUserConsent(_consentCheckBox.isChecked());
        AppLovinSdk sdk = AppLovinSdk.getInstance(this);
        sdk.getSettings().setVerboseLogging(true);

        sdk.getSettings().setExtraParameter("disable_b2b_ad_unit_ids", String.join(",", _dynamicAdUnits));

        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder(BuildConfig.MAX_KEY)
                .setMediationProvider( AppLovinMediationProvider.MAX)
                .setTestDeviceAdvertisingIds(Arrays.asList("97ec28e2-e65a-4fac-b11e-3975391f7cb7", "dca773a6-3445-4776-b361-4d950a0e212f"))
                .build();
        sdk.initialize( initConfig, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration sdkConfig) {

            }
        });
    }

    private void InitUI() {
        TextView title = findViewById(R.id.title);
        title.setText("MAX Integration "+ AppLovinSdk.VERSION);

        _consentCheckBox = findViewById(R.id.hasConsent);
        _consentCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NeftaPlugin.SetHasUserConsent(_consentCheckBox.isChecked());
            }
        });

        findViewById(R.id.misc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ShowMiscMenu();
            }
        });

        findViewById(R.id.unknown).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Initialize(NeftaPlugin.OptimizationType.Unknown);
            }
        });
        findViewById(R.id.optimized).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Initialize(NeftaPlugin.OptimizationType.Optimized);
            }
        });
        findViewById(R.id.notOptimized).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Initialize(NeftaPlugin.OptimizationType.NotOptimized);
            }
        });

        _interstitialUi = findViewById(R.id.interstitial);
        _rewardedUi = findViewById(R.id.rewarded);
    }

    private void Initialize(NeftaPlugin.OptimizationType optimizationType) {
        findViewById(R.id.groupView).setVisibility(View.GONE);

        InitializeNefta(optimizationType);

        boolean isSimulator = ((CheckBox)findViewById(R.id.isSimulator)).isChecked();
        if (!isSimulator) {
            InitializeMAX();
        }
    }

    private void ShowMiscMenu() {
        final String[] options = {"MMP: Adjust install", "MMP: AppsFlyer campaign", "MMP: AppsFlyer organic", "IAP: product 1", "IAP: product 2", "Exception 1: Res", "Exception 2: Imp"};
        new AlertDialog.Builder(this)
            .setTitle("Miscellaneous menu")
            .setItems(options, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        AdjustAttribution data = GetAdjustAttributionData();

                        NeftaPlugin.OnAttributionChanged(NeftaPlugin.Mmp.Adjust, data.adid, data.network, data.campaign, data.costType, data.costAmount, data.costCurrency);
                    } else if (which == 1 || which == 2) {
                        Map<String, Object> conversionData = GetAppsFlyerConversionData(which == 2);

                        String mmpId = AppsFlyerLib.getInstance().getAppsFlyerUID(MainActivity.this);
                        String network = (String) conversionData.get("media_source");
                        String campaign = (String) conversionData.getOrDefault("campaign", null);
                        String costType = (String) conversionData.getOrDefault("af_cost_model", null);
                        Object costObject = conversionData.getOrDefault("cost_cents_USD", null);
                        double cost = 0.0;
                        if (costObject == null) {
                        } else if (costObject instanceof Number) {
                            cost = ((Number) costObject).doubleValue();
                        } else if (costObject instanceof String) {
                            cost = Double.parseDouble((String) costObject);
                        }
                        NeftaPlugin.OnAttributionChanged(NeftaPlugin.Mmp.AppsFlyer, mmpId, network, campaign, costType, cost, "USD");
                    } else if (which == 3) {
                        NeftaPlugin.OnPurchase("product 1", 2.4, "USD");
                    } else if (which == 4) {
                        NeftaPlugin.OnPurchase("product 2", 1.2, "EUR");
                    } else if (which == 5) {
                        NeftaPlugin.OnExternalMediationResponseAsString("applovin-max", "id1", "id2", 1.2, "exact", 1, "provider status", "network status", "test","{df");
                    } else if (which == 6) {
                        NeftaPlugin.OnExternalMediationImpressionAsString(false, "applovin-max", "{\"someField\":df", "id1", "id2");
                    }
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private static AdjustAttribution GetAdjustAttributionData() {
        AdjustAttribution data = new AdjustAttribution();
        data.adid = "3fa82910b82f013b94cd001a5d28b17a";
        data.network = "test_network";
        data.campaign = "test_campaign";
        data.costType = "cpi";
        data.costAmount = 2.21;
        data.costCurrency = "USD";
        return data;
    }

    private static Map<String, Object> GetAppsFlyerConversionData(boolean isOrganic) {
        String json = null;
        if (isOrganic) {
            json = "{\"af_status\":\"Organic\",\"af_message\":\"organicinstall\",\"install_time\":\"2021-08-2306:59:51.194\"}";
        } else {
            json = "{\"adgroup\":null,\"campaign\":\"campaign_name\",\"media_source\":\"applovin_int\",\"retargeting_conversion_type\":\"none\",\"adset_id\":null,\"campaign_id\":null,\"install_time\":\"2018-12-3023:49:54.186\",\"adgroup_id\":null,\"orig_cost\":\"0.0\",\"click_time\":\"2018-12-2414:29:33.738\",\"agency\":null,\"cost_cents_USD\":\"0\",\"af_ad_type\":\"INTER\",\"af_c_id\":\"b8******************************\",\"adset\":null,\"advertising_id\":\"50********************************\",\"af_sub1\":null,\"clickid\":\"10****************\",\"af_click_lookback\":\"7d\",\"af_siteid\":\"123_site_id\",\"af_ad\":\"ad_name\",\"http_referrer\":null,\"af_channel\":\"video\",\"af_adset\":\"_DEFAULT\",\"af_sub5\":null,\"af_sub4\":null,\"af_cpi\":null,\"af_sub3\":null,\"af_sub2\":null,\"af_status\":\"Non-organic\"}";
        }

        Map<String, Object> map = new HashMap<>();
        try {
            JSONObject jsonObject = new JSONObject(json);
            Iterator<String> keys = jsonObject.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsonObject.get(key);
                map.put(key, value == JSONObject.NULL ? null : value);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return map;
    }
}
