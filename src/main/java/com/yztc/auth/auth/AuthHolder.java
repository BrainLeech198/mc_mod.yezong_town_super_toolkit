package com.yztc.auth.auth;

/** 装配期绑定、供事件与指令共享的 AuthManager 单例。 */
public final class AuthHolder {
    private AuthHolder() {}
    public static AuthManager AUTH;
}
