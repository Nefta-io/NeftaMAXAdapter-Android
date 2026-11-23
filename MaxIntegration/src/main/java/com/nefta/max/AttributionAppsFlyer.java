package com.nefta.max;

import android.app.Activity;

import com.appsflyer.AppsFlyerConversionListener;
import com.appsflyer.AppsFlyerLib;
import com.nefta.sdk.NeftaPlugin;

import java.util.Map;

public class AttributionAppsFlyer {
    public void Sample(Activity activity) {
        AppsFlyerConversionListener conversionListener = new AppsFlyerConversionListener() {
            @Override
            public void onConversionDataSuccess(Map<String, Object> conversionData) {
                String mmpId = AppsFlyerLib.getInstance().getAppsFlyerUID(activity);
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
            }

            @Override
            public void onConversionDataFail(String s) {}

            @Override
            public void onAppOpenAttribution(Map<String, String> map) {}

            @Override
            public void onAttributionFailure(String s) {}
        };
        AppsFlyerLib.getInstance().init("key", conversionListener, activity);
        AppsFlyerLib.getInstance().start(activity);
    }
}
