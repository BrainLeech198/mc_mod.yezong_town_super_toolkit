package com.yztc.auth.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class UiSession {
    public static final Map<UUID, UiSession> ALL = new ConcurrentHashMap<>();

    private final Consumer<String> onCommit;
    private final Runnable onCancel;
    private boolean done = false;

    private UiSession(Consumer<String> onCommit, Runnable onCancel) {
        this.onCommit = onCommit;
        this.onCancel = onCancel;
    }

    public static UiSession open(UUID owner, Consumer<String> onCommit, Runnable onCancel) {
        UiSession s = new UiSession(onCommit, onCancel);
        ALL.put(owner, s);
        return s;
    }

    public static boolean isActive(UUID owner) { return ALL.containsKey(owner); }

    public static void forget(UUID owner) { ALL.remove(owner); }

    public static UiSession get(UUID owner) { return ALL.get(owner); }

    public void commit(String value) {
        if (done) return;
        done = true;
        onCommit.accept(value);
    }

    public void cancel() {
        if (done) return;
        done = true;
        onCancel.run();
    }
}
