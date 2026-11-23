package com.nefta.max;

import android.app.Activity;

import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustAttribution;
import com.adjust.sdk.AdjustConfig;
import com.adjust.sdk.OnAttributionChangedListener;
import com.nefta.sdk.NeftaPlugin;

public class AttributionAdjust {
    public void Sample(Activity activity) {
        AdjustConfig config = new AdjustConfig(activity, "appToken", "release");

        config.setOnAttributionChangedListener(new OnAttributionChangedListener() {
            @Override
            public void onAttributionChanged(AdjustAttribution a) {
                NeftaPlugin.OnAttributionChanged(NeftaPlugin.Mmp.Adjust, a.adid, a.network, a.campaign, a.costType, a.costAmount, a.costCurrency);

            }
        });
        Adjust.onCreate(config);
    }
}
