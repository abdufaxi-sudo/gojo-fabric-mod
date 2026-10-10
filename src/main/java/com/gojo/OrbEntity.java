package com.gojo;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/** Blue / Red / Purple kuresi. Bir entity, 3 novu (KIND). */
public class OrbEntity extends Entity {
    public static final int BLUE = 0, RED = 1, PURPLE = 2, DOMAIN = 3;
    private static final TrackedData<Integer> KIND = DataTracker.registerData(OrbEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // yasama muddeti (tick): blue 5s, red 3s, purple 20 tick sarj + 70 tick ucus
    private static final int[] LIFE = {100, 60, 90, 120};
    private static final int PURPLE_CHARGE = 20;
    // Telefon ucun limit: bir tickde en cox nece blok silinsin
    private static final int BLOCKS_PER_TICK = 40;
    private static final float[][] COL = {{0.2f, 0.5f, 1f}, {1f, 0.1f, 0.1f}, {0.65f, 0.15f, 1f}, {0.5f, 0.6f, 1f}};

    private UUID ownerId;
    private Vec3d dir = Vec3d.ZERO;

    public OrbEntity(EntityType<? extends OrbEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public void setup(int kind, UUID owner, Vec3d center, Vec3d direction) {
        dataTracker.set(KIND, kind);
        ownerId = owner;
        dir = direction.normalize();
        setPosition(center.x, center.y - 1.0, center.z);
        if (kind == RED) setVelocity(dir.multiply(1.6));
    }

    public int getKind() {
        return Math.max(0, Math.min(3, dataTracker.get(KIND)));
    }

    /** Render olcusu (radius, blok). */
    public float scale(float td) {
        float a = age + td;
        int k = getKind();
        float s;
        if (k == BLUE) s = 1.0f * Math.min(1f, a / 6f) * (1f + 0.06f * (float) Math.sin(a * 0.8f));
        else if (k == RED) s = 0.4f * Math.min(1f, a / 3f);
        else if (k == PURPLE) s = 2.6f * Math.min(1f, a / PURPLE_CHARGE);
        else s = 14f * Math.min(1f, a / 12f);
        float left = LIFE[k] - a;
        float fade = k == DOMAIN ? 10f : 6f;
        if (left < fade) s *= Math.max(0f, left / fade);
        return s;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(KIND, 0);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        int k = getKind();
        Vec3d c = getPos().add(0, 1, 0);

        if (getWorld().isClient) {
            clientFx(k, c);
            if (k != BLUE && getVelocity().lengthSquared() > 0) setPosition(getPos().add(getVelocity()));
            return;
        }

        ServerWorld sw = (ServerWorld) getWorld();
        Entity oe = ownerId == null ? null : sw.getEntity(ownerId);
        final LivingEntity owner = oe instanceof LivingEntity l ? l : null;

        switch (k) {
            case BLUE -> blue(sw, c, owner);
            case RED -> { if (red(sw, c, owner)) return; }
            case DOMAIN -> domain(sw, c, owner);
            default -> { if (purple(sw, c, owner)) return; }
        }
        if (age >= LIFE[k]) {
            burst(sw, c, k, 12);
            discard();
        }
    }

    // ---------- BLUE: uzaqda mavi kure, her seyi icine cekir ve oldurur ----------
    private void blue(ServerWorld sw, Vec3d c, LivingEntity owner) {
        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(12),
                e -> e.isAlive() && e != owner && !e.isSpectator());
        for (LivingEntity e : list) {
            Vec3d to = c.subtract(e.getPos().add(0, e.getHeight() / 2, 0));
            double d = to.length();
            if (d < 2.0) { kill(sw, e); continue; }
            e.setVelocity(to.normalize().multiply(Math.min(1.4, 0.4 + 5.0 / d)));
            e.velocityModified = true;
            e.fallDistance = 0;
        }
        if (age % 2 == 0) erode(sw, c, 2.2, 20);
        if (age % 10 == 0) sound(sw, c, SoundEvents.BLOCK_BEACON_AMBIENT, 1.5f);
    }

    // ---------- RED: kicik qirmizi top, baxdigin yere gedir, deyende partlayir ----------
    private boolean red(ServerWorld sw, Vec3d c, LivingEntity owner) {
        Vec3d v = getVelocity();
        boolean hit = age >= LIFE[RED] - 1 || !sw.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(1.0),
                e -> e.isAlive() && e != owner && !e.isSpectator()).isEmpty();
        Vec3d at = c;
        if (!hit) {
            BlockHitResult br = sw.raycast(new RaycastContext(c, c.add(v), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            if (br.getType() != HitResult.Type.MISS) { hit = true; at = br.getPos(); }
        }
        if (!hit) {
            setPosition(getPos().add(v));
            return false;
        }
        // PARTLAYIS
        double R = 6;
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(at, at).expand(R), x -> x.isAlive() && x != owner && !x.isSpectator())) {
            Vec3d off = e.getPos().add(0, e.getHeight() / 2, 0).subtract(at);
            double d = off.length();
            if (d < 4) { kill(sw, e); continue; }
            e.setVelocity(off.normalize().multiply(2.0 * (1 - d / R) + 0.8).add(0, 0.5, 0));
            e.velocityModified = true;
            e.damage(sw.getDamageSources().magic(), 20f);
        }
        erode(sw, at, 4.5, 350);
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        Abilities.dust(sw, at, 1f, 0.1f, 0.1f, 30, 2.0);
        sound(sw, at, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.8f);
        discard();
        return true;
    }

    // ---------- PURPLE: neheng benovseyi kure, onunde her seyi silir ----------
    private boolean purple(ServerWorld sw, Vec3d c, LivingEntity owner) {
        if (age < PURPLE_CHARGE) {
            if (owner == null) { discard(); return true; }
            dir = owner.getRotationVec(1f);
            Vec3d p = owner.getEyePos().add(dir.multiply(3.5));
            setPosition(p.x, p.y - 1.0, p.z);
            if (age % 5 == 0) sound(sw, p, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.5f + age * 0.05f);
            return false;
        }
        if (age == PURPLE_CHARGE) {
            setVelocity(dir.multiply(0.9));
            velocityModified = true;
            sound(sw, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1f);
        }
        setPosition(getPos().add(getVelocity()));
        Vec3d n = getPos().add(0, 1, 0);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(n, n).expand(4.5),
                x -> x.isAlive() && x != owner && !x.isSpectator())) {
            kill(sw, e);
        }
        erode(sw, n, 3.5, BLOCKS_PER_TICK);
        if (age % 3 == 0) sw.spawnParticles(ParticleTypes.END_ROD, n.x, n.y, n.z, 3, 1.5, 1.5, 1.5, 0.02);
        return false;
    }

    // ---------- DOMAIN: Unlimited Void - 14 blok radius, icindeki canlilar tamamen doner ----------
    private void domain(ServerWorld sw, Vec3d c, LivingEntity owner) {
        final double R2 = 14.0 * 14.0;
        if (age == 1) sound(sw, c, SoundEvents.BLOCK_END_PORTAL_SPAWN, 0.8f);
        if (age % 30 == 0) sound(sw, c, SoundEvents.AMBIENT_CAVE.value(), 0.5f);

        if (owner instanceof ServerPlayerEntity op && op.squaredDistanceTo(c) <= R2) {
            PlayerState s = PlayerState.get(op);
            boolean was = s.domainCaster > 0;
            s.domainCaster = 6;
            if (!was) GojoMod.sync(op);
        }
        if (age < 6) return; // dome acilarken qisa gecikme

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(14),
                x -> x.isAlive() && x != owner && !x.isSpectator() && x.squaredDistanceTo(c) <= R2)) {
            if (e instanceof ServerPlayerEntity sp) {
                PlayerState s = PlayerState.get(sp);
                if (s.simple > 0) continue; // Simple Domain anti-domain muhafizesi
                boolean was = s.domainVictim > 0;
                s.domainVictim = 6;
                if (!was) GojoMod.sync(sp);
                if (age % 2 == 0) {
                    sp.setVelocity(0, Math.min(0, sp.getVelocity().y), 0);
                    sp.velocityModified = true;
                }
            } else {
                Vec3d v = e.getVelocity();
                e.setVelocity(0, Math.min(0, v.y), 0);
            }
            if (age % 5 == 0) e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 10, false, false, false));
        }
    }

    // ---------- helpers ----------
    private static void kill(ServerWorld sw, LivingEntity e) {
        if (e instanceof PlayerEntity p && p.isCreative()) return;
        e.damage(sw.getDamageSources().genericKill(), Float.MAX_VALUE);
    }

    /** Sferik sahede blok silir (hardness < 0 = bedrock ve s. toxunulmaz). */
    private static void erode(ServerWorld sw, Vec3d c, double r, int cap) {
        if (!Abilities.DESTROY_BLOCKS) return;
        BlockPos cp = BlockPos.ofFloored(c);
        int R = (int) Math.ceil(r);
        double r2 = r * r;
        int broken = 0;
        for (int dx = -R; dx <= R; dx++)
            for (int dy = -R; dy <= R; dy++)
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    BlockPos bp = cp.add(dx, dy, dz);
                    BlockState st = sw.getBlockState(bp);
                    if (st.isAir() || st.getHardness(sw, bp) < 0) continue;
                    sw.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    if (++broken >= cap) return;
                }
    }

    private static void burst(ServerWorld sw, Vec3d c, int k, int n) {
        float[] col = COL[k];
        Abilities.dust(sw, c, col[0], col[1], col[2], n, 1.0);
    }

    private static void sound(ServerWorld sw, Vec3d at, SoundEvent ev, float pitch) {
        sw.playSound(null, at.x, at.y, at.z, ev, SoundCategory.PLAYERS, 1f, pitch);
    }

    private void clientFx(int k, Vec3d c) {
        if (k == DOMAIN || age % 2 != 0) return;
        float[] col = COL[k];
        double r = k == PURPLE ? 2.2 : k == RED ? 0.3 : 0.9;
        getWorld().addParticle(new DustParticleEffect(new Vector3f(col[0], col[1], col[2]), 1.2f),
                c.x + (random.nextDouble() - 0.5) * 2 * r, c.y + (random.nextDouble() - 0.5) * 2 * r,
                c.z + (random.nextDouble() - 0.5) * 2 * r, 0, 0, 0);
    }
}
