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
import net.minecraft.world.entity.player.Player;
import org.rusherhack.client.api.events.network.EventPacket;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class LightningPopModule extends ToggleableModule {
    private static final Logger LOGGER = LogManager.getLogger("LightningPopPlugin");
    private final Minecraft minecraft = Minecraft.getInstance();

    private final BooleanSetting totemPop = new BooleanSetting("TotemPop", "Lightning on totem pop", true);
    private final BooleanSetting selfTotemPop = new BooleanSetting("Self", "Include your own totem pops", true);
    private final BooleanSetting playerDeath = new BooleanSetting("PlayerDeath", "Lightning on player death", true);

    private final Map<Entity, Entity> playerAttackerMap = new HashMap<>();

    public LightningPopModule() {
        super("LightningPop", ModuleCategory.MISC);
        this.totemPop.addSubSettings(this.selfTotemPop);
        this.registerSettings(this.totemPop, this.playerDeath);
    }

    @Subscribe
    public void onPacketReceive(EventPacket.Receive event) {
        LOGGER.debug("onPacketReceive method invoked");

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
        if (!source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypes.PLAYER_EXPLOSION)) return;

        Entity entity = minecraft.level.getEntity(damagePacket.entityId());
        if (entity instanceof Player) {
            Entity attacker = minecraft.level.getEntity(damagePacket.sourceCauseId());
            playerAttackerMap.put(entity, attacker);
        }
    }

    private void handleEntityEventPacket(ClientboundEntityEventPacket entityPacket) {
        if (minecraft.level == null) return;

        byte eventId = entityPacket.getEventId();
        LOGGER.debug("Entity event packet received with Event ID: {}", eventId);

        if (eventId == 35) {
            handleTotemPopEvent(entityPacket);
        } else if (eventId == 3) {
            handlePlayerDeathEvent(entityPacket);
        }
    }

    private void handleTotemPopEvent(ClientboundEntityEventPacket entityPacket) {
        Entity entity = entityPacket.getEntity(minecraft.level);
        if (!(entity instanceof Player player)) return;

        if (totemPop.getValue() && (selfTotemPop.getValue() || player != minecraft.player)) {
            spawnLightning(player);
        }
    }

    private void handlePlayerDeathEvent(ClientboundEntityEventPacket entityPacket) {
        Entity entity = entityPacket.getEntity(minecraft.level);
        if (!(entity instanceof Player player)) return;

        Entity attacker = playerAttackerMap.get(player);
        if (attacker == null) return;

        playerAttackerMap.remove(player);

        if (playerDeath.getValue() && (attacker == minecraft.player)) {
            spawnLightning(player);
        }
    }

    private void spawnLightning(Player player) {
        if (minecraft.level != null && minecraft.level.isClientSide) {
            LightningBolt lightningBolt = new LightningBolt(EntityType.LIGHTNING_BOLT, minecraft.level);
            lightningBolt.setPos(player.position());
            this.minecraft.level.addEntity(lightningBolt);
            LOGGER.debug("Spawned lightning at {}", player.getName().getString());
        } else {
            LOGGER.debug("Minecraft level is not client-side or is null, cannot spawn lightning.");
        }
    }
}