package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.SigillumMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

public final class SigillumClientConfig {
    private static final String FILE = "aozaink_sigillum-client.properties";
    private static final String KEY_DETAILED = "detailedGlyphInfo";
    private static Boolean detailed;

    private SigillumClientConfig() {}

    public static boolean detailed() {
        if (detailed == null) detailed = load();
        return detailed;
    }

    public static void setDetailed(boolean value) {
        detailed = value;
        save(value);
    }

    public static void toggleDetailed() {
        setDetailed(!detailed());
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    private static boolean load() {
        Path path = path();
        if (!Files.exists(path)) return true;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            SigillumMod.LOGGER.warn("read sigillum client config failed: {}", e.getMessage());
            return true;
        }
        return Boolean.parseBoolean(props.getProperty(KEY_DETAILED, "true"));
    }

    private static void save(boolean value) {
        Properties props = new Properties();
        props.setProperty(KEY_DETAILED, Boolean.toString(value));
        try (OutputStream out = Files.newOutputStream(path())) {
            props.store(out, "aozaink sigillum client settings");
        } catch (IOException e) {
            SigillumMod.LOGGER.warn("write sigillum client config failed: {}", e.getMessage());
        }
    }
}
