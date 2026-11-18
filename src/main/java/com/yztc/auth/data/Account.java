package com.yztc.auth.data;

public final class Account {
    public String algo;
    public Integer iterations;
    public String salt;
    public String hash;
    public String email;
    public String lastIp;
    public Long registeredAt;
    public Long lastLogin;
    public int failedAttempts;
    public long lockedUntil;
    public Long migratedAt;

    public boolean isLegacy() {
        return "sha256-legacy".equals(algo);
    }

    public Account copy() {
        Account copy = new Account();
        copy.algo = algo;
        copy.iterations = iterations;
        copy.salt = salt;
        copy.hash = hash;
        copy.email = email;
        copy.lastIp = lastIp;
        copy.registeredAt = registeredAt;
        copy.lastLogin = lastLogin;
        copy.failedAttempts = failedAttempts;
        copy.lockedUntil = lockedUntil;
        copy.migratedAt = migratedAt;
        return copy;
    }
}
