package com.gojo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.List;

public class GojoMod implements ModInitializer {
    public static final EntityType<OrbEntity> ORB = Registry.register(Registries.ENTITY_TYPE, Identifier.of("gojo", "orb"),
            EntityType.Builder.<OrbEntity>create(OrbEntity::new, SpawnGroup.MISC)
                    .dimensions(2f, 2f).maxTrackingRange(10).trackingTickInterval(1)
                    .disableSaving().makeFireImmune().build("orb"));

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(AbilityPayload.ID, AbilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.ID,
                (payload, ctx) -> Abilities.use(ctx.player(), payload.id()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int t = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) tick(p, t);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlayerState.remove(handler.player.getUuid()));

        // Black Flash
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp && entity instanceof LivingEntity le) {
                PlayerState s = PlayerState.get(sp);
                if (s.bf > 0) {
                    s.bf = 0;
                    ServerWorld sw = sp.getServerWorld();
                    le.damage(sw.getDamageSources().playerAttack(sp), 18f);
                    Vec3d at = le.getPos().add(0, le.getHeight() / 2, 0);
                    Abilities.dust(sw, at, 0.05f, 0.05f, 0.05f, 8, 0.4);
                    Abilities.dust(sw, at, 1f, 0.05f, 0.1f, 8, 0.4);
                    sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.8f, 1.5f);
                    sp.sendMessage(Text.literal("BLACK FLASH!"), true);
                }
            }
            return ActionResult.PASS;
        });
    }

    private static void tick(ServerPlayerEntity p, int ticks) {
        PlayerState s = PlayerState.get(p);
        for (int i = 0; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
        if (s.bf > 0) s.bf--;
        if (s.simple > 0) s.simple--;
        if (s.domainVictim > 0 && --s.domainVictim == 0) sync(p);
        if (s.domainCaster > 0 && --s.domainCaster == 0) sync(p);

        s.energy = Math.min(Abilities.MAX_ENERGY, s.energy + (s.sixEyes ? 0.6f : 0.2f));

        if (s.sixEyes && ticks % 20 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 1, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 0, true, false, false));
        }

        if (s.infinity) {
            s.energy -= 0.3f;
            if (s.energy <= 1f) {
                s.infinity = false;
                p.sendMessage(Text.literal("Infinity: OFF (no energy)"), true);
            } else if (ticks % 2 == 0) {
                ServerWorld w = p.getServerWorld();
                Box box = p.getBoundingBox().expand(3.5);
                List<Entity> list = w.getOtherEntities(p, box, e -> e.isAlive() && (e instanceof LivingEntity || e instanceof ProjectileEntity));
                for (Entity e : list) {
                    Vec3d away = e.getPos().subtract(p.getPos()).multiply(1, 0, 1).normalize();
                    if (e instanceof ProjectileEntity) {
                        e.setVelocity(away.multiply(0.4).add(0, 0.05, 0));
                    } else {
                        e.addVelocity(away.x * 0.35, 0, away.z * 0.35);
                    }
                    e.velocityModified = true;
                }
            }
        }

        if (ticks % 4 == 0) sync(p);
    }

    public static void sync(ServerPlayerEntity p) {
        PlayerState s = PlayerState.get(p);
        int flags = (s.infinity ? 1 : 0) | (s.sixEyes ? 2 : 0) | (s.bf > 0 ? 4 : 0)
                | (s.domainVictim > 0 ? 8 : 0) | (s.domainCaster > 0 ? 16 : 0);
        ServerPlayNetworking.send(p, new StatePayload((int) s.energy, flags, s.cd.clone()));
    }
}
