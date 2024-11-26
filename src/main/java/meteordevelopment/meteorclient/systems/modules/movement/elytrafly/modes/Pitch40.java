/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class Pitch40 extends ElytraFlightMode {
    private boolean pitchingDown = true;
    private int pitch;
    private int fireworkCooldown = 0;
    private static final int FIREWORK_COOLDOWN_TICKS = 300; // 15 seconds * 20 ticks/second
    private int ticksSincePitchUp = 0;
    private static final int PITCH_UP_GRACE_PERIOD = 50; // 2.5 seconds * 20 ticks/second
    private static final double VELOCITY_THRESHOLD = -0.05; // Small negative velocity threshold

    public Pitch40() {
        super(ElytraFlightModes.Pitch40);
    }

    @Override
    public void onActivate() {
        if (mc.player.getY() < elytraFly.pitch40upperBounds.get()) {
            elytraFly.error("Player must be above upper bounds!");
            elytraFly.toggle();
        }

        pitch = 40;
    }

    @Override
    public void onDeactivate() {}

    @Override
    public void onTick() {
        super.onTick();

        if (fireworkCooldown > 0) {
            fireworkCooldown--;
        }

        if (ticksSincePitchUp < PITCH_UP_GRACE_PERIOD) {
            ticksSincePitchUp++;
        }

        if (pitchingDown && mc.player.getY() <= elytraFly.pitch40lowerBounds.get()) {
            pitchingDown = false;
            ticksSincePitchUp = 0;
        }
        else if (!pitchingDown && mc.player.getY() >= elytraFly.pitch40upperBounds.get()) {
            pitchingDown = true;
        }

        // Pitch upwards
        if (!pitchingDown && mc.player.getPitch() > -40) {
            pitch -= elytraFly.pitch40rotationSpeed.get();

            if (pitch < -40) pitch = -40;
        // Pitch downwards
        } else if (pitchingDown && mc.player.getPitch() < 40) {
            pitch += elytraFly.pitch40rotationSpeed.get();

            if (pitch > 40) pitch = 40;
        }

        // Check if we need to use a firework
        if (!pitchingDown && mc.player.getVelocity().y < VELOCITY_THRESHOLD
                && mc.player.getY() < elytraFly.pitch40upperBounds.get()
                && ticksSincePitchUp >= PITCH_UP_GRACE_PERIOD) {
            if (fireworkCooldown == 0) {
                firework();
                fireworkCooldown = FIREWORK_COOLDOWN_TICKS;
            }
        }

        mc.player.setPitch(pitch);
    }

    public void firework() {
        FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!itemResult.found())
            return;

        if (itemResult.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
        } else {
            InvUtils.swap(itemResult.slot(), true);

            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);

            InvUtils.swapBack();
        }
    }

    @Override
    public void autoTakeoff() {}

    @Override
    public void handleHorizontalSpeed(PlayerMoveEvent event) {
        velX = event.movement.x;
        velZ = event.movement.z;
    }

    @Override
    public void handleVerticalSpeed(PlayerMoveEvent event) {}

    @Override
    public void handleFallMultiplier() {}

    @Override
    public void handleAutopilot() {}
}
