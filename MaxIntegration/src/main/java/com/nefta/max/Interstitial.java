package com.nefta.max;

public interface Interstitial {
    String AdUnitDefault = "e5dc3548d4a0913f";
    String AdUnitOptimizedA = "0u6jgm23ggqso85n";
    String AdUnitOptimizedB = "wrzl86if1sqfxquc";

    public void Init(InterstitialUi ui);
    public void Load();
    public void Show();
}
