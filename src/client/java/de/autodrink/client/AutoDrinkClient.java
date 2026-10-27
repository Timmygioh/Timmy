package de.autodrink.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Properties;

/**
 * Trinkt in festen Intervallen automatisch:
 *  - Ominous Bottle (Standard: alle 30 Minuten)
 *  - Milch          (Standard: alle 32 Minuten)
 * Es wird nur die Hotbar durchsucht. Danach wird der vorherige Slot wieder gewaehlt.
 * Config: config/autodrink.properties
 */
public class AutoDrinkClient implements ClientModInitializer {

    private static final int TICKS_PER_MINUTE = 20 * 60;

    private enum Kind {
        OMINOUS("Ominous Bottle"),
        MILK("Milch");

        final String label;

        Kind(String label) {
            this.label = label;
        }

        Item item() {
            return this == OMINOUS ? Items.OMINOUS_BOTTLE : Items.MILK_BUCKET;
        }
    }

    // Config
    private boolean enabled = true;
    private int ominousIntervalTicks = 30 * TICKS_PER_MINUTE;
    private int milkIntervalTicks = 32 * TICKS_PER_MINUTE;

    // Timer
    private int ominousTimer = 0;
    private int milkTimer = 0;

    // Warteschlange, falls beide gleichzeitig faellig sind
    private final ArrayDeque<Kind> queue = new ArrayDeque<>();

    // Aktueller Trinkvorgang
    private Kind current = null;
    private int previousSlot = -1;
    private int drinkTicks = 0;
    private boolean useStarted = false;

    @Override
    public void onInitializeClient() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;

        if (player == null || mc.gameMode == null || mc.level == null) {
            // nicht im Spiel: alles zuruecksetzen
            ominousTimer = 0;
            milkTimer = 0;
            queue.clear();
            current = null;
            useStarted = false;
            return;
        }
        if (!enabled) {
            return;
        }

        ominousTimer++;
        milkTimer++;

        if (ominousTimer >= ominousIntervalTicks) {
            ominousTimer = 0;
            if (!queue.contains(Kind.OMINOUS) && current != Kind.OMINOUS) {
                queue.add(Kind.OMINOUS);
            }
        }
        if (milkTimer >= milkIntervalTicks) {
            milkTimer = 0;
            if (!queue.contains(Kind.MILK) && current != Kind.MILK) {
                queue.add(Kind.MILK);
            }
        }

        if (current != null) {
            handleDrinking(mc, player);
        } else if (!queue.isEmpty() && mc.screen == null && player.isAlive()) {
            startNext(mc, player);
        }
    }

    private void startNext(Minecraft mc, LocalPlayer player) {
        Kind next = queue.peek();
        Inventory inv = player.getInventory();

        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(next.item())) {
                slot = i;
                break;
            }
        }

        queue.poll();

        if (slot == -1) {
            player.displayClientMessage(
                    Component.literal("[AutoDrink] Kein(e) " + next.label + " in der Hotbar gefunden."), true);
            return;
        }

        current = next;
        previousSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        drinkTicks = 0;
        useStarted = false;
        mc.options.keyUse.setDown(true);
    }

    private void handleDrinking(Minecraft mc, LocalPlayer player) {
        drinkTicks++;

        if (player.isUsingItem()) {
            useStarted = true;
        }

        boolean finished = useStarted && !player.isUsingItem();
        boolean timeout = drinkTicks > 100;
        boolean aborted = mc.screen != null || !player.isAlive();

        if (finished || timeout || aborted) {
            mc.options.keyUse.setDown(false);
            if (previousSlot >= 0) {
                player.getInventory().setSelectedSlot(previousSlot);
            }
            if (finished) {
                player.displayClientMessage(Component.literal("[AutoDrink] " + current.label + " getrunken."), true);
            }
            current = null;
            previousSlot = -1;
            useStarted = false;
        }
    }

    // ---- Config ----

    private void loadConfig() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("autodrink.properties");
        Properties p = new Properties();
        p.setProperty("enabled", "true");
        p.setProperty("ominousIntervalMinutes", "30");
        p.setProperty("milkIntervalMinutes", "32");

        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException ignored) {
            }
        } else {
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "AutoDrink - Neustart des Spiels nach Aenderungen noetig");
            } catch (IOException ignored) {
            }
        }

        enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
        ominousIntervalTicks = Math.max(1, parseInt(p.getProperty("ominousIntervalMinutes"), 30)) * TICKS_PER_MINUTE;
        milkIntervalTicks = Math.max(1, parseInt(p.getProperty("milkIntervalMinutes"), 32)) * TICKS_PER_MINUTE;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
