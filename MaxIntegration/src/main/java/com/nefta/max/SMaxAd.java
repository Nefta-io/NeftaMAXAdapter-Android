package com.nefta.max;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdWaterfallInfo;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxMediatedNetworkInfo;
import com.applovin.mediation.MaxNetworkResponseInfo;
import com.applovin.mediation.nativeAds.MaxNativeAd;
import com.applovin.sdk.AppLovinSdkUtils;

import java.util.ArrayList;
import java.util.List;

public class SMaxAd implements MaxAd {

    private String _adUnitId;
    private MaxAdFormat _format;
    private double _revenue;
    private MaxAdWaterfallInfo _waterfall;


    public SMaxAd(String adUnitId, MaxAdFormat format, double revenue, MaxNetworkResponseInfo.AdLoadState[] items) {
        _adUnitId = adUnitId;
        _format = format;
        _revenue = revenue;
        _waterfall = GetWaterfall(items);
    }

    @Override
    public MaxAdFormat getFormat() {
        return _format;
    }

    @Override
    public AppLovinSdkUtils.Size getSize() {
        return AppLovinSdkUtils.Size.ZERO;
    }

    @Override
    public String getAdUnitId() {
        return _adUnitId;
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
        return _waterfall;
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
        return _revenue;
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

    public static MaxAdWaterfallInfo GetWaterfall(MaxNetworkResponseInfo.AdLoadState[] items) {
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
                List<MaxNetworkResponseInfo> list = new ArrayList<>();
                for (int i = 0; i < items.length; i++) {
                    list.add(GetNetworkResponseInfo(i, items[i]));
                };
                return list;
            }

            @Override
            public long getLatencyMillis() {
                return 0;
            }
        };
    }

    private static MaxNetworkResponseInfo GetNetworkResponseInfo(int index, MaxNetworkResponseInfo.AdLoadState loadState) {
        return new MaxNetworkResponseInfo() {
            @Override
            public AdLoadState getAdLoadState() {
                return loadState;
            }

            @Override
            public MaxMediatedNetworkInfo getMediatedNetwork() {
                return new MaxMediatedNetworkInfo() {
                    @Override
                    public String getName() {
                        return "simulator "+ index;
                    }

                    @Override
                    public String getAdapterClassName() {
                        return "simulator adapter "+ index;
                    }

                    @Override
                    public String getAdapterVersion() {
                        return "1.0.0";
                    }

                    @Override
                    public String getSdkVersion() {
                        return "1.0.0";
                    }

                    @Override
                    public InitializationStatus getInitializationStatus() {
                        return InitializationStatus.INITIALIZED_SUCCESS;
                    }
                };
            }

            @Override
            public Bundle getCredentials() {
                return null;
            }

            @Override
            public boolean isBidding() {
                return true;
            }

            @Override
            public long getLatencyMillis() {
                return 0;
            }

            @Override
            public MaxError getError() {
                if (loadState == AdLoadState.AD_LOADED) {
                    return null;
                }
                return new MaxError() {
                    @Override
                    public int getCode() {
                        return 321;
                    }

                    @Override
                    public String getMessage() {
                        return "simulator error";
                    }

                    @Override
                    public int getMediatedNetworkErrorCode() {
                        return 0;
                    }

                    @Override
                    public String getMediatedNetworkErrorMessage() {
                        return null;
                    }

                    @Nullable
                    @Override
                    public MaxAdWaterfallInfo getWaterfall() {
                        return null;
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
            }
        };
    }
}
