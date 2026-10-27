package de.autodrink.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;

/**
 * Auto Drink:
 *  - Hotkey startet/stoppt einen Zyklus (Start setzt den Zyklus neu).
 *  - Im Zyklus: Ominous Bottle ab Minute X, Milch ab Minute Y, Zyklus-Ende nach Z Minuten, dann von vorn.
 *  - Unabhaengig davon: bei wenig Hunger automatisch Golden Apple essen.
 * Es wird nur die Hotbar durchsucht. Danach wird der vorherige Slot wieder gewaehlt.
 */
public class AutoDrinkClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("autodrink");
    private static final int TICKS_PER_MINUTE = 20 * 60;

    private enum Kind {
        OMINOUS("Ominous Bottle"),
        MILK("Milch"),
        APPLE("Golden Apple");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private static KeyMapping toggleKey;

    // Zyklus
    private boolean running = false;
    private int cycleTick = 0;
    private boolean wasInWorld = false;

    // Aufgaben
    private final ArrayDeque<Kind> queue = new ArrayDeque<>();
    private Kind current = null;
    private int previousSlot = -1;
    private int useTicks = 0;
    private boolean useStarted = false;
    private int eatCooldown = 0;

    @Override
    public void onInitializeClient() {
        AutoDrinkConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("auto-drink", "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.auto-drink.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;

        if (player == null || mc.gameMode == null || mc.level == null) {
            wasInWorld = false;
            running = false;
            cycleTick = 0;
            queue.clear();
            current = null;
            useStarted = false;
            eatCooldown = 0;
            return;
        }

        if (!wasInWorld) {
            wasInWorld = true;
            if (AutoDrinkConfig.autoStartOnJoin) {
                startCycle(mc, player);
            }
        }

        while (toggleKey.consumeClick()) {
            if (running) {
                stopCycle(mc, player);
            } else {
                startCycle(mc, player);
            }
        }

        if (running) {
            advanceCycle();
        }
        if (eatCooldown > 0) {
            eatCooldown--;
        }

        if (current != null) {
            handleUsing(mc, player);
            return;
        }

        if (AutoDrinkConfig.autoEat && eatCooldown == 0 && !queue.contains(Kind.APPLE)
                && player.getFoodData().getFoodLevel() < AutoDrinkConfig.eatBelowDrumsticks * 2) {
            queue.addFirst(Kind.APPLE);
        }

        if (!queue.isEmpty() && player.isAlive()) {
            startNext(mc, player);
        }
    }

    // ---- Zyklus ----

    private void startCycle(Minecraft mc, LocalPlayer player) {
        running = true;
        cycleTick = 0;
        queue.removeIf(k -> k != Kind.APPLE);
        player.sendSystemMessage(Component.literal("[AutoDrink] Zyklus gestartet (Minute 0)."));
    }

    private void stopCycle(Minecraft mc, LocalPlayer player) {
        running = false;
        cycleTick = 0;
        queue.removeIf(k -> k != Kind.APPLE);
        if (current != null && current != Kind.APPLE) {
            abortCurrent(mc, player);
        }
        player.sendSystemMessage(Component.literal("[AutoDrink] Zyklus gestoppt."));
    }

    private void advanceCycle() {
        cycleTick++;

        if (cycleTick == AutoDrinkConfig.ominousAtMinute * TICKS_PER_MINUTE) {
            addOnce(Kind.OMINOUS);
        }
        if (AutoDrinkConfig.milkEnabled && cycleTick == AutoDrinkConfig.milkAtMinute * TICKS_PER_MINUTE) {
            addOnce(Kind.MILK);
        }
        if (cycleTick >= AutoDrinkConfig.cycleMinutes * TICKS_PER_MINUTE) {
            cycleTick = 0;
        }
    }

    private void addOnce(Kind kind) {
        if (!queue.contains(kind) && current != kind) {
            queue.add(kind);
        }
    }

    // ---- Benutzen von Items ----

    private int findSlot(Inventory inv, Kind kind) {
        Item first;
        Item second = null;
        switch (kind) {
            case OMINOUS -> first = Items.OMINOUS_BOTTLE;
            case MILK -> first = Items.MILK_BUCKET;
            default -> {
                if (AutoDrinkConfig.preferEnchantedApple) {
                    first = Items.ENCHANTED_GOLDEN_APPLE;
                    second = Items.GOLDEN_APPLE;
                } else {
                    first = Items.GOLDEN_APPLE;
                    second = Items.ENCHANTED_GOLDEN_APPLE;
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(first)) {
                return i;
            }
        }
        if (second != null) {
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).is(second)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void startNext(Minecraft mc, LocalPlayer player) {
        Kind next = queue.poll();
        Inventory inv = player.getInventory();
        int slot = findSlot(inv, next);

        if (slot == -1) {
            if (next == Kind.APPLE) {
                eatCooldown = TICKS_PER_MINUTE; // nicht jede Sekunde meckern
            }
            player.sendSystemMessage(Component.literal(
                    "[AutoDrink] Kein(e) " + next.label + " in der Hotbar gefunden."));
            return;
        }

        current = next;
        previousSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        useTicks = 0;
        useStarted = false;
        mc.options.keyUse.setDown(true);
    }

    private void handleUsing(Minecraft mc, LocalPlayer player) {
        useTicks++;

        if (player.isUsingItem()) {
            useStarted = true;
        }

        boolean finished = useStarted && !player.isUsingItem();
        boolean timeout = useTicks > 100;
        boolean aborted = !player.isAlive();

        if (finished || timeout || aborted) {
            Kind done = current;
            abortCurrent(mc, player);
            if (done == Kind.APPLE) {
                eatCooldown = 100; // kurz warten, bis der Hunger aktualisiert ist
            }
            if (finished) {
                LOGGER.info("{} verwendet.", done.label);
            }
        }
    }

    private void abortCurrent(Minecraft mc, LocalPlayer player) {
        mc.options.keyUse.setDown(false);
        if (previousSlot >= 0) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        current = null;
        previousSlot = -1;
        useStarted = false;
    }
}
