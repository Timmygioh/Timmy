package de.autodrink.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Einstellungen, gespeichert in config/autodrink.properties. */
public final class AutoDrinkConfig {

    /** Zyklus automatisch starten, sobald man eine Welt/einen Server betritt (z. B. nach Reconnect). */
    public static boolean autoStartOnJoin = false;
    /** Laenge eines Zyklus in Minuten (danach beginnt er von vorn). */
    public static int cycleMinutes = 30;
    /** Minute im Zyklus, in der die Ominous Bottle getrunken wird. */
    public static int ominousAtMinute = 2;
    /** Soll am Ende der ominoesen Welle Milch getrunken werden? */
    public static boolean milkEnabled = true;
    /** Minute im Zyklus, in der die Milch getrunken wird. */
    public static int milkAtMinute = 10;
    /** Automatisch Golden Apple essen, wenn der Hunger niedrig ist. */
    public static boolean autoEat = true;
    /** Essen, wenn weniger als so viele Hungerkeulen uebrig sind (3 = unter 3 Keulen). */
    public static int eatBelowDrumsticks = 3;
    /** Lieber verzauberten Golden Apple essen, falls vorhanden. */
    public static boolean preferEnchantedApple = false;

    private AutoDrinkConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("autodrink.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException ignored) {
            return;
        }
        autoStartOnJoin = bool(p, "autoStartOnJoin", autoStartOnJoin);
        cycleMinutes = num(p, "cycleMinutes", cycleMinutes);
        ominousAtMinute = num(p, "ominousAtMinute", ominousAtMinute);
        milkEnabled = bool(p, "milkEnabled", milkEnabled);
        milkAtMinute = num(p, "milkAtMinute", milkAtMinute);
        autoEat = bool(p, "autoEat", autoEat);
        eatBelowDrumsticks = num(p, "eatBelowDrumsticks", eatBelowDrumsticks);
        preferEnchantedApple = bool(p, "preferEnchantedApple", preferEnchantedApple);
        clamp();
    }

    public static void save() {
        clamp();
        Properties p = new Properties();
        p.setProperty("autoStartOnJoin", String.valueOf(autoStartOnJoin));
        p.setProperty("cycleMinutes", String.valueOf(cycleMinutes));
        p.setProperty("ominousAtMinute", String.valueOf(ominousAtMinute));
        p.setProperty("milkEnabled", String.valueOf(milkEnabled));
        p.setProperty("milkAtMinute", String.valueOf(milkAtMinute));
        p.setProperty("autoEat", String.valueOf(autoEat));
        p.setProperty("eatBelowDrumsticks", String.valueOf(eatBelowDrumsticks));
        p.setProperty("preferEnchantedApple", String.valueOf(preferEnchantedApple));
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Auto Drink");
        } catch (IOException ignored) {
        }
    }

    private static void clamp() {
        cycleMinutes = Math.max(1, Math.min(600, cycleMinutes));
        ominousAtMinute = Math.max(1, Math.min(600, ominousAtMinute));
        milkAtMinute = Math.max(1, Math.min(600, milkAtMinute));
        eatBelowDrumsticks = Math.max(1, Math.min(10, eatBelowDrumsticks));
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static int num(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key).trim());
        } catch (Exception e) {
            return def;
        }
    }
}
