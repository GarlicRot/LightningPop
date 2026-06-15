package org.lightningpop.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import org.rusherhack.client.api.events.network.EventPacket;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;

import java.util.HashMap;
import java.util.Map;

/**
 * Module that triggers lightning effects in Minecraft when certain events occur,
 * such as totem pops, player deaths, or mob deaths.
 */
public class LightningPopModule extends ToggleableModule {
    private final Minecraft minecraft = Minecraft.getInstance();

    // Settings for different lightning effects
    private final BooleanSetting totemPop = new BooleanSetting("TotemPop", "Lightning on totem pop", true);
    private final BooleanSetting totemPopSelf = new BooleanSetting("Self", "Lightning when you pop a totem", true);
    private final BooleanSetting totemPopPlayer = new BooleanSetting("Player", "Lightning when another player pops a totem", true);

    private final BooleanSetting playerDeath = new BooleanSetting("PlayerDeath", "Lightning on player death", true);
    private final BooleanSetting attackDeath = new BooleanSetting("AttackDeath", "Lightning on player attack death", true);
    private final BooleanSetting anyDeath = new BooleanSetting("AnyDeath", "Lightning on any player death within visual range", true);

    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Lightning on mob death", true);
    private final BooleanSetting attackMob = new BooleanSetting("AttackMob", "Lightning on mob attack kill", true);
    private final BooleanSetting anyMob = new BooleanSetting("AnyMob", "Lightning on any mob death within visual range", true);

    private final Map<Entity, Entity> playerAttackerMap = new HashMap<>();

    public LightningPopModule() {
        super("LightningPop", ModuleCategory.MISC);
        this.totemPop.addSubSettings(this.totemPopSelf, this.totemPopPlayer);
        this.playerDeath.addSubSettings(this.attackDeath, this.anyDeath);
        this.mobs.addSubSettings(this.attackMob, this.anyMob);
        this.registerSettings(this.totemPop, this.playerDeath, this.mobs);
    }

    @Subscribe
    public void onPacketReceive(EventPacket.Receive event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundDamageEventPacket) {
            handleDamagePacket((ClientboundDamageEventPacket) packet);
        } else if (packet instanceof ClientboundEntityEventPacket) {
            handleEntityEventPacket((ClientboundEntityEventPacket) packet);
        }
    }

    private void handleDamagePacket(ClientboundDamageEventPacket damagePacket) {
        if (minecraft.level == null) return;

        DamageSource source = damagePacket.getSource(minecraft.level);
        Entity directEntity = source.getDirectEntity(); // Direct entity causing the damage
        Entity attacker = source.getEntity(); // Entity indirectly causing the damage (e.g., player)

        // Handle melee attacks
        if (source.is(DamageTypes.PLAYER_ATTACK)) {
            trackAttacker(damagePacket, attacker);
            return;
        }

        // Handle player-triggered explosions
        if (source.is(DamageTypes.PLAYER_EXPLOSION) && attacker instanceof Player) {
            trackAttacker(damagePacket, attacker);
            return;
        }

        // Handle End Crystal explosions
        if (source.is(DamageTypes.EXPLOSION) && directEntity != null && directEntity.getType() == EntityType.END_CRYSTAL) {
            if (attacker instanceof Player) {
                trackAttacker(damagePacket, attacker);
            }
            return;
        }

        // Handle projectiles (arrows and tridents)
        if (directEntity instanceof Projectile projectile && projectile.getOwner() instanceof Player owner) {
            trackAttacker(damagePacket, owner);
        }
    }


    private void trackAttacker(ClientboundDamageEventPacket damagePacket, Entity attacker) {
        Entity entity = minecraft.level.getEntity(damagePacket.entityId());
        if (entity instanceof Player || entity instanceof Mob) {
            playerAttackerMap.put(entity, attacker);
        }
    }

    private void handleEntityEventPacket(ClientboundEntityEventPacket entityPacket) {
        if (minecraft.level == null) return;

        byte eventId = entityPacket.getEventId();

        if (eventId == 35) {
            handleTotemPopEvent(entityPacket);
        } else if (eventId == 3) {
            handlePlayerDeathEvent(entityPacket);
        } else {
            // Check if the entity is a mob and handle mob deaths
            Entity entity = entityPacket.getEntity(minecraft.level);
            if (entity instanceof Mob) {
                handleMobDeathEvent(entity);
            }
        }
    }

    private void handleTotemPopEvent(ClientboundEntityEventPacket entityPacket) {
        Entity entity = entityPacket.getEntity(minecraft.level);
        if (!(entity instanceof Player player)) return;

        if (totemPop.getValue()) {
            if (player == minecraft.player && totemPopSelf.getValue()) {
                // Trigger lightning when you pop a totem
                spawnLightning(player);
            } else if (player != minecraft.player && totemPopPlayer.getValue()) {
                // Trigger lightning when another player pops a totem
                spawnLightning(player);
            }
        }
    }

    private void handlePlayerDeathEvent(ClientboundEntityEventPacket entityPacket) {
        Entity entity = entityPacket.getEntity(minecraft.level);
        if (!(entity instanceof Player player)) return;

        Entity attacker = playerAttackerMap.get(player);
        if (attacker != null) {
            playerAttackerMap.remove(player);
        }

        if (playerDeath.getValue() && ((attacker != null && attackDeath.getValue()) || anyDeath.getValue())) {
            spawnLightning(player);
        }
    }

    private void handleMobDeathEvent(Entity entity) {
        if (!(entity instanceof Mob mob)) return;

        // Check if the mob was killed by a player
        Entity attacker = playerAttackerMap.get(mob);
        if (!(attacker instanceof Player)) return;

        // Handle the AttackMob and AnyMob settings
        if (mobs.getValue()) {
            if (attacker == minecraft.player && attackMob.getValue()) {
                // Trigger lightning only if "AttackMob" is enabled and you killed the mob
                spawnLightning(mob);
            } else if (attacker != minecraft.player && anyMob.getValue()) {
                // Trigger lightning only if "AnyMob" is enabled and another player killed the mob
                spawnLightning(mob);
            }
        }

        playerAttackerMap.remove(mob);  // Clean up attacker map after handling
        // Thank you y.a.g.a. for the newly added mob section
    }

    private void spawnLightning(Entity entity) {
        if (minecraft.level != null && minecraft.level.isClientSide()) {
            // Thank you kybe236
            minecraft.execute(() -> {
                LightningBolt lightningBolt = new LightningBolt(EntityType.LIGHTNING_BOLT, minecraft.level);
                lightningBolt.setPos(entity.position());
                this.minecraft.level.addEntity(lightningBolt);
            });
        }
    }
}
