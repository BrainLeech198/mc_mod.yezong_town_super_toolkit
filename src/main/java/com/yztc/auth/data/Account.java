package com.yztc.auth.data;

public final class Account {
    public String algo;
    public Integer iterations;
    public String salt;
    public String hash;
    public String email;
    public Long registeredAt;
    public Long lastLogin;
    public int failedAttempts;
    public long lockedUntil;
    public Long migratedAt;

    public boolean isLegacy() {
        return "sha256-legacy".equals(algo);
    }
}
