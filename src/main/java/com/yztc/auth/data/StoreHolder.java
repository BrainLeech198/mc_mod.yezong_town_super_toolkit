package com.yztc.auth.data;

public final class StoreHolder {
    private static AccountStore instance;
    private StoreHolder() {}
    public static void set(AccountStore s) { instance = s; }
    public static AccountStore get() { return instance; }
}
