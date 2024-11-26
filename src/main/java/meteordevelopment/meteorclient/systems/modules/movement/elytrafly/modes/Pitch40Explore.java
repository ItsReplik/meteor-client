package meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pitch40Explore extends ElytraFlightMode {
    public static final Logger LOGGER = LoggerFactory.getLogger("Pitch40Explore");
    // Flight control constants
    private static final int FIREWORK_COOLDOWN_TICKS = 300; // 15 seconds
    private static final int PITCH_UP_GRACE_PERIOD = 50; // 2.5 seconds
    private static final double VELOCITY_THRESHOLD = -0.05;

    // Flight control variables
    private boolean pitchingDown = true;
    private int pitch;
    private int fireworkCooldown = 0;
    private int ticksSincePitchUp = 0;

    // Scanning state
    private Vec3d startPosition;
    private boolean isGoingRight = true;
    private int currentLine = 0;
    private boolean hasInitialized = false;
    private static final double POSITION_THRESHOLD = 20.0; // Distance threshold to consider position reached

    private double SCAN_AREA_SIZE = 1000;
    private double SCAN_LINE_SPACING = 100;

    private Vec3d currentTarget;
    private boolean isMovingDown = false;

    public Pitch40Explore() {
        super(ElytraFlightModes.Pitch40Explore);
    }

    /**
     * Utility function to calculate yaw angle between current position and target
     * position
     *
     * @param current Current position
     * @param target  Target position
     * @return Yaw angle in degrees (0-360)
     */
    private double calculateYawToTarget(Vec3d current, Vec3d target) {
        double deltaX = target.x - current.x;
        double deltaZ = target.z - current.z;
        double yaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        return (yaw + 360) % 360;
    }

    /**
     * Utility function to check if we're close enough to a target position
     *
     * @param current Current position
     * @param target  Target position
     * @return true if within threshold distance
     */
    private boolean hasReachedPosition(Vec3d current, Vec3d target) {
        return current.subtract(target).horizontalLength() < POSITION_THRESHOLD;
    }

    /**
     * Get the target position for the current scan line
     */
    private Vec3d getCurrentScanTarget() {
        double targetX = startPosition.x + (isGoingRight ? SCAN_AREA_SIZE : 0);
        double targetZ = startPosition.z + (currentLine * SCAN_LINE_SPACING);
        return new Vec3d(targetX, startPosition.y, targetZ);
    }

    @Override
    public void onActivate() {
        if (mc.player.getY() < elytraFly.pitch40upperBounds.get()) {
            elytraFly.error("Player must be above upper bounds!");
            elytraFly.toggle();
            return;
        }

        if (elytraFly.pitch40ExploreRememberPosition.get() && startPosition != null) {
            LOGGER.info("[Pitch40Explore] Using remembered position as top-left corner: {}", startPosition);
            elytraFly.info("Using remembered position as top-left corner: " + startPosition.toString());
        } else {
            startPosition = mc.player.getPos();
            LOGGER.info("[Pitch40Explore] Using current position as top-left corner: {}", startPosition);
            elytraFly.info("Using current position as top-left corner: " + startPosition.toString());
        }

        hasInitialized = true;
        pitch = 40;
        SCAN_AREA_SIZE = elytraFly.pitch40ExploreSquareSize.get();
        SCAN_LINE_SPACING = elytraFly.pitch40ExploreLineHeight.get();
        currentTarget = getCurrentScanTarget();
    }

    private double getTargetYaw() {
        if (!hasInitialized)
            return mc.player.getYaw();

        Vec3d currentPos = mc.player.getPos();

        // Calculate area scanned
        double areaScanned = currentLine * SCAN_LINE_SPACING * SCAN_AREA_SIZE;

        // Check if we've reached the current target position
        if (hasReachedPosition(currentPos, currentTarget)) {
            if (isMovingDown) {
                // Finished moving down, start moving horizontally
                isMovingDown = false;
                isGoingRight = !isGoingRight;
                currentTarget = getCurrentScanTarget();
            } else {
                // Reached side, start moving down
                isMovingDown = true;
                currentLine++;
                currentTarget = new Vec3d(currentPos.x, currentPos.y, currentPos.z + SCAN_LINE_SPACING);
            }
            LOGGER.info("[Pitch40Explore] New target: {}, currentLine: {}, areaScanned: {}", currentTarget, currentLine,
                    areaScanned);
        }

        if (mc.player.age % 200 == 0) {
            int remainingDistanceToTarget = (int) currentPos.distanceTo(currentTarget);
            LOGGER.info("[Pitch40Explore] Current line: {}, currentTarget: {}, remainingDistanceToTarget: {}",
                    currentLine,
                    currentTarget, remainingDistanceToTarget);
            elytraFly
                    .info("Current line: " + currentLine + ", Area Scanned: " + (int) areaScanned
                            + ", remainingDistanceToTarget: " + remainingDistanceToTarget);
        }

        return calculateYawToTarget(currentPos, currentTarget);
    }

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
        } else if (!pitchingDown && mc.player.getY() >= elytraFly.pitch40upperBounds.get()) {
            pitchingDown = true;
        }

        // Handle pitch adjustment
        if (!pitchingDown && mc.player.getPitch() > -40) {
            pitch -= elytraFly.pitch40rotationSpeed.get();
            if (pitch < -40)
                pitch = -40;
        } else if (pitchingDown && mc.player.getPitch() < 40) {
            pitch += elytraFly.pitch40rotationSpeed.get();
            if (pitch > 40)
                pitch = 40;
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

        // Set pitch and yaw
        mc.player.setPitch(pitch);
        mc.player.setYaw((float) getTargetYaw());
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
    public void autoTakeoff() {
    }

    @Override
    public void handleHorizontalSpeed(PlayerMoveEvent event) {
        velX = event.movement.x;
        velZ = event.movement.z;
    }

    @Override
    public void handleVerticalSpeed(PlayerMoveEvent event) {
    }

    @Override
    public void handleFallMultiplier() {
    }

    @Override
    public void handleAutopilot() {
    }
}
