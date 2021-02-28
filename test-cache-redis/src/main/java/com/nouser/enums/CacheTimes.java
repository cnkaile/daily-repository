package com.nouser.enums;

import java.time.Duration;

public enum CacheTimes {

    S_10("cache_10s", Duration.ofSeconds(10)),
    M_5("cache_5m", Duration.ofMinutes(5)),
    M_30("cache_30m", Duration.ofMinutes(30)),
    M_60("cache_60m", Duration.ofMinutes(60)),
    H_2("cache_2h", Duration.ofHours(2)),
    H_6("cache_6h", Duration.ofHours(6)),
    H_12("cache_12h", Duration.ofHours(12)),
    D_1("cache_1d", Duration.ofDays(1)),
    D_7("cache_7d", Duration.ofDays(7))
    ;

    CacheTimes(String cacheName, Duration cacheTime) {
        this.cacheName = cacheName;
        this.cacheTime = cacheTime;
    }

    private String cacheName;
    private Duration cacheTime;

    public String getCacheName() {
        return cacheName;
    }

    public Duration getCacheTime() {
        return cacheTime;
    }

    public static final String S10 = "cache_10s";
    public static final String M5 = "cache_5m";
    public static final String M30 = "cache_30m";
    public static final String M60 = "cache_60m";
    public static final String H2 = "cache_2h";
    public static final String H6 = "cache_6h";
    public static final String H12 = "cache_12h";
    public static final String D1 = "cache_1d";
    public static final String D7 = "cache_7d";
}
