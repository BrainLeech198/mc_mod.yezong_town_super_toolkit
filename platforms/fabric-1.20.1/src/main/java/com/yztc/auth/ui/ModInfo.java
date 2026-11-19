package com.yztc.auth.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.SharedConstants;

import java.util.stream.Collectors;

public final class ModInfo {
    private ModInfo() {}

    private static ModMetadata metadata(String id) {
        return FabricLoader.getInstance().getModContainer(id).orElseThrow().getMetadata();
    }

    public static String name() { return metadata("yzt_super").getName(); }
    public static String version() { return metadata("yzt_super").getVersion().getFriendlyString(); }
    public static String author() {
        return metadata("yzt_super").getAuthors().stream()
                .map(Person::getName).collect(Collectors.joining(", "));
    }
    public static String minecraft() { return SharedConstants.getCurrentVersion().getName(); }
    public static String loaderLabel() { return "Fabric"; }
    public static String loaderVersion() { return metadata("fabricloader").getVersion().getFriendlyString(); }
    public static boolean isLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
}
