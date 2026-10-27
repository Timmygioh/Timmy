package de.autodrink.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Einstellungsmenue (ueber Mod Menu erreichbar). Aenderungen gelten sofort. */
public class AutoDrinkConfigScreen extends Screen {

    private final Screen parent;

    public AutoDrinkConfigScreen(Screen parent) {
        super(Component.literal("Auto Drink"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 28;
        final int step = 24;

        addRenderableWidget(Button.builder(Component.literal("Auto Drink - Einstellungen"), b -> {
        }).bounds(cx - 100, y, 200, 20).build());
        y += step + 4;

        intRow(cx, y, "Zyklus (Min)", () -> AutoDrinkConfig.cycleMinutes, v -> AutoDrinkConfig.cycleMinutes = v, 1, 600);
        y += step;
        intRow(cx, y, "Bottle ab Minute", () -> AutoDrinkConfig.ominousAtMinute, v -> AutoDrinkConfig.ominousAtMinute = v, 1, 600);
        y += step;
        boolRow(cx, y, "Milch trinken", () -> AutoDrinkConfig.milkEnabled, v -> AutoDrinkConfig.milkEnabled = v);
        y += step;
        intRow(cx, y, "Milch ab Minute", () -> AutoDrinkConfig.milkAtMinute, v -> AutoDrinkConfig.milkAtMinute = v, 1, 600);
        y += step;
        boolRow(cx, y, "Auto essen", () -> AutoDrinkConfig.autoEat, v -> AutoDrinkConfig.autoEat = v);
        y += step;
        intRow(cx, y, "Essen unter Keulen", () -> AutoDrinkConfig.eatBelowDrumsticks, v -> AutoDrinkConfig.eatBelowDrumsticks = v, 1, 10);
        y += step;
        boolRow(cx, y, "Verzauberter Apfel", () -> AutoDrinkConfig.preferEnchantedApple, v -> AutoDrinkConfig.preferEnchantedApple = v);
        y += step;
        boolRow(cx, y, "Autostart beim Beitritt", () -> AutoDrinkConfig.autoStartOnJoin, v -> AutoDrinkConfig.autoStartOnJoin = v);
        y += step + 6;

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx - 100, y, 200, 20).build());
    }

    private void intRow(int cx, int y, String label, IntSupplier get, IntConsumer set, int min, int max) {
        Button value = Button.builder(Component.literal(label + ": " + get.getAsInt()), b -> {
        }).bounds(cx - 100, y, 160, 20).build();
        addRenderableWidget(value);

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            set.accept(Math.max(min, get.getAsInt() - 1));
            value.setMessage(Component.literal(label + ": " + get.getAsInt()));
        }).bounds(cx + 64, y, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            set.accept(Math.min(max, get.getAsInt() + 1));
            value.setMessage(Component.literal(label + ": " + get.getAsInt()));
        }).bounds(cx + 88, y, 20, 20).build());
    }

    private interface BooleanSetter {
        void accept(boolean v);
    }

    private void boolRow(int cx, int y, String label, BooleanSupplier get, BooleanSetter set) {
        addRenderableWidget(Button.builder(boolText(label, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(boolText(label, get.getAsBoolean()));
        }).bounds(cx - 100, y, 208, 20).build());
    }

    private static Component boolText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "AN" : "AUS"));
    }

    @Override
    public void onClose() {
        AutoDrinkConfig.save();
        this.minecraft.gui.setScreen(this.parent);
    }
}
