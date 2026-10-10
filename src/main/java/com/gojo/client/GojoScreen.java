package com.gojo.client;

import com.gojo.AbilityPayload;
import com.gojo.Abilities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class GojoScreen extends Screen {
    public GojoScreen() {
        super(Text.literal("Gojo Satoru - Limitless"));
    }

    @Override
    protected void init() {
        int w = 150, h = 20, gap = 4;
        for (int i = 0; i < Abilities.COUNT; i++) {
            final int id = i;
            int x = width / 2 - w - 2 + (i % 2) * (w + 4);
            int y = 40 + (i / 2) * (h + gap);
            String label = Abilities.NAMES[i] + (Abilities.COST[i] > 0 ? "  [" + Abilities.COST[i] + "]" : "");
            addDrawableChild(ButtonWidget.builder(Text.literal(label),
                    b -> ClientPlayNetworking.send(new AbilityPayload(id))).dimensions(x, y, w, h).build());
        }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        c.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        c.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Cursed Energy: " + ClientState.energy + " / " + (int) Abilities.MAX_ENERGY), width / 2, 26, 0x66AAFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
