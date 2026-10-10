package com.gojo;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerState {
    private static final Map<UUID, PlayerState> MAP = new HashMap<>();

    public float energy = Abilities.MAX_ENERGY;
    public final int[] cd = new int[Abilities.COUNT];
    public boolean infinity, sixEyes;
    public int bf; // Black Flash window (ticks)
    public int domainVictim, domainCaster, simple; // domain vəziyyəti (tick sayğacları)

    public static PlayerState get(ServerPlayerEntity p) {
        return MAP.computeIfAbsent(p.getUuid(), k -> new PlayerState());
    }

    public static void remove(UUID id) {
        MAP.remove(id);
    }
}
