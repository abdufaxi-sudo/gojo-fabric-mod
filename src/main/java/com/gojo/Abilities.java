package com.gojo;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Abilities {
    public static final int COUNT = 10;
    public static final float MAX_ENERGY = 200f;
    /** Orblar blok qirsin? Telefon lag ederse false et. */
    public static final boolean DESTROY_BLOCKS = true;

    public static final String[] IDS = {"blue", "red", "purple", "infinity", "reverse", "blackflash", "teleport", "domain", "simple", "sixeyes"};
    public static final String[] NAMES = {"Blue (Ao)", "Red (Aka)", "Purple (Murasaki)", "Infinity", "Reverse Technique",
            "Black Flash", "Teleport", "Domain: Unlimited Void", "Simple Domain", "Six Eyes"};
    public static final int[] COST = {30, 30, 80, 0, 30, 10, 15, 100, 25, 0};
    public static final int[] COOLDOWN = {100, 80, 300, 10, 100, 40, 40, 1200, 400, 10};

    public static void use(ServerPlayerEntity p, int id) {
        if (id < 0 || id >= COUNT || p.isSpectator()) return;
        PlayerState s = PlayerState.get(p);
        if (s.cd[id] > 0) return;
        if (s.domainVictim > 0) { msg(p, "Frozen in the Unlimited Void"); return; }

        if (id == 3) {
            if (s.infinity) s.infinity = false;
            else if (s.energy >= 10) s.infinity = true;
            else { msg(p, "Not enough cursed energy"); return; }
            s.cd[id] = COOLDOWN[id];
            msg(p, "Infinity: " + (s.infinity ? "ON" : "OFF"));
            GojoMod.sync(p);
            return;
        }
        if (id == 9) {
            s.sixEyes = !s.sixEyes;
            s.cd[id] = COOLDOWN[id];
            msg(p, "Six Eyes: " + (s.sixEyes ? "ON" : "OFF"));
            GojoMod.sync(p);
            return;
        }
        if (s.energy < COST[id]) { msg(p, "Not enough cursed energy"); return; }

        ServerWorld w = p.getServerWorld();
        switch (id) {
            case 0 -> blue(p, w);
            case 1 -> red(p, w);
            case 2 -> purple(p, w);
            case 4 -> reverse(p, w);
            case 5 -> { s.bf = 20; msg(p, "Black Flash ready - hit now!"); }
            case 6 -> teleport(p, w);
            case 7 -> domain(p, w);
            case 8 -> simpleDomain(p, w);
            default -> { return; }
        }
        s.energy -= COST[id];
        s.cd[id] = COOLDOWN[id];
        GojoMod.sync(p);
    }

    // ---------- abilities ----------

    private static void blue(ServerPlayerEntity p, ServerWorld w) {
        Vec3d dir = p.getRotationVec(1f);
        Vec3d pt = ray(p, w, 25);
        if (pt.distanceTo(p.getEyePos()) < 24.9) pt = pt.subtract(dir.multiply(1.5));
        spawnOrb(w, OrbEntity.BLUE, p, pt, dir);
        sound(w, pt, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.7f);
    }

    private static void red(ServerPlayerEntity p, ServerWorld w) {
        Vec3d dir = p.getRotationVec(1f);
        spawnOrb(w, OrbEntity.RED, p, p.getEyePos().add(dir.multiply(1.2)), dir);
        sound(w, p.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 0.7f);
    }

    private static void purple(ServerPlayerEntity p, ServerWorld w) {
        Vec3d dir = p.getRotationVec(1f);
        spawnOrb(w, OrbEntity.PURPLE, p, p.getEyePos().add(dir.multiply(3.5)), dir);
    }

    private static void spawnOrb(ServerWorld w, int kind, ServerPlayerEntity p, Vec3d center, Vec3d dir) {
        OrbEntity o = new OrbEntity(GojoMod.ORB, w);
        o.setup(kind, p.getUuid(), center, dir);
        w.spawnEntity(o);
    }

    private static void reverse(ServerPlayerEntity p, ServerWorld w) {
        p.heal(10f);
        p.extinguish();
        w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1, p.getZ(), 5, 0.4, 0.5, 0.4, 0);
        sound(w, p.getPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f);
    }

    private static void teleport(ServerPlayerEntity p, ServerWorld w) {
        Vec3d dir = p.getRotationVec(1f);
        Vec3d pt = ray(p, w, 30);
        boolean blocked = pt.distanceTo(p.getEyePos()) < 29.9;
        Vec3d t = blocked ? pt.subtract(dir.multiply(0.7)) : pt;
        double y = t.y - p.getEyeHeight(p.getPose());
        w.spawnParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 15, 0.3, 0.5, 0.3, 0.1);
        p.requestTeleport(t.x, y, t.z);
        p.fallDistance = 0;
        w.spawnParticles(ParticleTypes.PORTAL, t.x, y + 1, t.z, 15, 0.3, 0.5, 0.3, 0.1);
        sound(w, p.getPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f);
    }

    private static void domain(ServerPlayerEntity p, ServerWorld w) {
        spawnOrb(w, OrbEntity.DOMAIN, p, p.getPos().add(0, 1, 0), Vec3d.ZERO);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 1));
        msg(p, "Domain Expansion: Unlimited Void");
    }

    private static void simpleDomain(ServerPlayerEntity p, ServerWorld w) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 2));
        PlayerState.get(p).simple = 200; // 10 san domain-e qarsi toxunulmaz
        w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 10, 0.6, 0.8, 0.6, 0.02);
        msg(p, "Simple Domain (anti-domain)");
    }

    // ---------- helpers ----------

    private static List<LivingEntity> near(ServerWorld w, ServerPlayerEntity p, Vec3d c, double r) {
        return w.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(r), e -> e != p && e.isAlive());
    }

    private static Vec3d ray(ServerPlayerEntity p, ServerWorld w, double range) {
        Vec3d a = p.getEyePos();
        Vec3d b = a.add(p.getRotationVec(1f).multiply(range));
        HitResult h = w.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return h.getPos();
    }

    private static Vec3d clamp(Vec3d v, double max) {
        return v.length() > max ? v.normalize().multiply(max) : v;
    }

    public static void dust(ServerWorld w, Vec3d at, float r, float g, float b, int count, double spread) {
        w.spawnParticles(new DustParticleEffect(new Vector3f(r, g, b), 1.4f), at.x, at.y, at.z, count, spread, spread, spread, 0);
    }

    private static void sound(ServerWorld w, Vec3d at, SoundEvent ev, float pitch) {
        w.playSound(null, at.x, at.y, at.z, ev, SoundCategory.PLAYERS, 1f, pitch);
    }

    private static void msg(ServerPlayerEntity p, String s) {
        p.sendMessage(Text.literal(s), true);
    }
}
