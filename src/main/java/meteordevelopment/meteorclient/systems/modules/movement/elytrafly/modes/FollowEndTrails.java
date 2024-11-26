package meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class FollowEndTrails extends ElytraFlightMode {
    public static final Logger LOGGER = LoggerFactory.getLogger("FollowEndTrails");
    // Flight control constants
    private static final int FIREWORK_COOLDOWN_TICKS = 200; // 10 seconds
    private static final int PITCH_UP_GRACE_PERIOD = 50; // 2.5 seconds
    private static final double VELOCITY_THRESHOLD = -0.05;

    // Flight control variables
    private boolean pitchingDown = true;
    private int pitch;
    private int ticksSincePitchUp = 0;

    private boolean hasInitialized = false;

    private String databasePath;

    private static final int TRAIL_CHUNK_COUNT = 20;
    private static final double CHUNK_SIZE = 16.0;
    private Vec3d trailDirection;
    private boolean isFollowingTrail = false;
    private int trailFollowingTicks = 0;
    private static final int MAX_TRAIL_FOLLOWING_TICKS = 20 * 60 * 60 * 6; // 20 ticks / second so 6 hours
    private static final double TRAIL_FOLLOW_DISTANCE = 500; // Distance to set target when following trail

    private Vec3d lastKnownDirection;
    private static final int DIRECTION_TIMEOUT = 20 * 60 * 5; // 5 minutes
    private int ticksSinceLastUpdate = 0;

    private long lastProcessedFoundTime = 0;

    private static final double YAW_SMOOTHING_FACTOR = 0.07; // Adjust this value to control smoothing (0.05 to 0.2
                                                             // recommended)
    private double currentYaw;
    private int fireworkCooldown = 0;

    public FollowEndTrails() {
        super(ElytraFlightModes.FollowEndTrails);
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

    @Override
    public void onDeactivate() {
        unpress();
        isFollowingTrail = false;
        trailDirection = null;
    }

    @Override
    public void onActivate() {
        databasePath = elytraFly.xaeroPlusDbPath.get();
        elytraFly.info("XaeroPlus database path: " + databasePath);
        LOGGER.info("XaeroPlus database path: " + databasePath);
        if (databasePath.equals("FILL_THIS/XaeroPlusOldChunks.db")) {
            elytraFly.error("XaeroPlus database path is not set!");
            elytraFly.toggle();
            return;
        }

        if (!elytraFly.followEndTrailsOnlyYawControl.get()
                && mc.player.getY() < elytraFly.followEndTrailsUpperBounds.get()) {
            elytraFly.error("Player must be above upper bounds!");
            elytraFly.toggle();
            return;
        }

        hasInitialized = true;
        pitch = 40;
        currentYaw = mc.player.getYaw();
    }

    private double getTargetYaw() {
        if (!hasInitialized)
            return mc.player.getYaw();

        Vec3d currentPos = mc.player.getPos();

        if (isFollowingTrail && trailDirection != null) {
            Vec3d targetPos = currentPos.add(trailDirection.multiply(TRAIL_FOLLOW_DISTANCE));
            return calculateYawToTarget(currentPos, targetPos);
        } else {
            return mc.player.getYaw(); // Default to current yaw when not following a trail
        }
    }

    @Override
    public void onTick() {
        super.onTick();

        if (mc.player.age % (3 * 20) == 0) { // Do it every 3 seconds
            updateTrailDirection();
        }

        if (!elytraFly.followOverworldTrailsOnlyYawControl.get()) {
            if (fireworkCooldown > 0) {
                fireworkCooldown--;
            }

            if (ticksSincePitchUp < PITCH_UP_GRACE_PERIOD) {
                ticksSincePitchUp++;
            }

            if (pitchingDown && mc.player.getY() <= elytraFly.followEndTrailsLowerBounds.get()) {
                pitchingDown = false;
                ticksSincePitchUp = 0;
            } else if (!pitchingDown && mc.player.getY() >= elytraFly.followEndTrailsUpperBounds.get()) {
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

        }
        // Replace the direct yaw setting with smooth rotation
        double targetYaw = getTargetYaw();
        currentYaw = smoothRotation(currentYaw, targetYaw);
        mc.player.setYaw((float) currentYaw);
        mc.player.setPitch(pitch);

        if (isFollowingTrail) {
            trailFollowingTicks++;
            ticksSinceLastUpdate++;
            if (trailFollowingTicks > MAX_TRAIL_FOLLOWING_TICKS) {
                isFollowingTrail = false;
                LOGGER.info("[FollowEndTrails] Trail following timeout reached, resuming normal flight");
            }
        }
    }

    private void unpress() {
        setPressed(mc.options.forwardKey, false);
        setPressed(mc.options.backKey, false);
        setPressed(mc.options.leftKey, false);
        setPressed(mc.options.rightKey, false);
    }

    private void setPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
        Input.setKeyState(key, pressed);
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

    private void updateTrailDirection() {
        List<Vec3d> recentChunks = getRecentOldChunks();
        if (!recentChunks.isEmpty()) {
            Vec3d averagePosition = calculateWeightedAveragePosition(recentChunks);
            Vec3d playerPos = mc.player.getPos();
            Vec3d horizontalDifference = new Vec3d(
                    averagePosition.x - playerPos.x,
                    0,
                    averagePosition.z - playerPos.z);

            // Apply smoothing to the trail direction
            if (trailDirection == null) {
                trailDirection = horizontalDifference.normalize();
            } else {
                trailDirection = horizontalDifference.normalize().multiply(0.8).add(trailDirection.multiply(0.2))
                        .normalize();
            }
            lastKnownDirection = trailDirection;
            isFollowingTrail = true;
            trailFollowingTicks = 0;
            ticksSinceLastUpdate = 0;
            elytraFly.info("[FollowEndTrails] Following trail direction: " + trailDirection);
            LOGGER.info("[FollowEndTrails] Following trail direction: {}", trailDirection);
        } else if (isFollowingTrail) {
            if (ticksSinceLastUpdate > DIRECTION_TIMEOUT) {
                isFollowingTrail = false;
                elytraFly.info("[FollowEndTrails] No recent old chunks found, resuming normal flight");
                LOGGER.info("[FollowEndTrails] No recent old chunks found, resuming normal flight");
            } else if (lastKnownDirection != null) {
                // Circle over the most recent chunk
                double angle = (mc.player.age % 360) * Math.PI / 180; // Convert age to radians
                trailDirection = new Vec3d(Math.cos(angle), 0, Math.sin(angle)).normalize();
                if (mc.player.age % 200 == 0) {
                    elytraFly.info("[FollowEndTrails] No new chunks, circling over last known position: "
                            + trailDirection);
                    LOGGER.info("[FollowEndTrails] No new chunks, circling over last known position: {}",
                            trailDirection);
                }
            }
        }
    }

    private List<Vec3d> getRecentOldChunks() {
        List<Vec3d> chunks = new ArrayList<>();
        String tableName = "minecraft:the_end";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement stmt = conn.createStatement()) {

            String query = "SELECT x, z, foundTime FROM '" + tableName + "' WHERE foundTime > "
                    + lastProcessedFoundTime + " ORDER BY foundTime DESC LIMIT " + TRAIL_CHUNK_COUNT;
            ResultSet rs = stmt.executeQuery(query);

            long maxFoundTime = lastProcessedFoundTime;
            while (rs.next()) {
                int x = rs.getInt("x");
                int z = rs.getInt("z");
                long foundTime = rs.getLong("foundTime");
                chunks.add(new Vec3d(x * CHUNK_SIZE, 0, z * CHUNK_SIZE));
                maxFoundTime = Math.max(maxFoundTime, foundTime);
            }

            if (!chunks.isEmpty()) {
                lastProcessedFoundTime = maxFoundTime;
                LOGGER.info("[FollowEndTrails] Processed {} new chunks. Last foundTime: {}", chunks.size(),
                        lastProcessedFoundTime);
            }

        } catch (Exception e) {
            LOGGER.error("[FollowEndTrails] Error reading from database: " + e.getMessage());
        }
        return chunks;
    }

    private Vec3d calculateWeightedAveragePosition(List<Vec3d> positions) {
        double sumX = 0, sumZ = 0;
        double totalWeight = 0;
        int size = positions.size();
        for (int i = 0; i < size; i++) {
            Vec3d pos = positions.get(i);
            double weight = 1.0 - Math.abs((i - size / 2.0) / (size / 2.0)); // More weight to middle positions
            sumX += pos.x * weight;
            sumZ += pos.z * weight;
            totalWeight += weight;
        }
        return new Vec3d(sumX / totalWeight, 0, sumZ / totalWeight);
    }

    private double smoothRotation(double current, double target) {
        double difference = angleDifference(target, current);
        return current + difference * YAW_SMOOTHING_FACTOR;
    }

    private double angleDifference(double target, double current) {
        double diff = (target - current + 180) % 360 - 180;
        return diff < -180 ? diff + 360 : diff;
    }
}
