package meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import meteordevelopment.meteorclient.pathing.PathManagers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class FollowNetherTrails extends ElytraFlightMode {
    public static final Logger LOGGER = LoggerFactory.getLogger("FollowNetherTrails");
    public boolean hasInitialized = false;

    private String databasePath;

    private static final int TRAIL_CHUNK_COUNT = 20;
    private static final double CHUNK_SIZE = 16.0;
    private Vec3d trailDirection;
    private boolean isFollowingTrail = false;
    private int trailFollowingTicks = 0;
    private static final int MAX_TRAIL_FOLLOWING_TICKS = 20 * 60 * 60 * 6; // 20 ticks / second so 6 hours
    private static final double TRAIL_FOLLOW_DISTANCE = 500; // Distance to set target when following trail

    private Vec3d lastKnownDirection;
    private static final int DIRECTION_TIMEOUT = 20 * 60; // 1 minute
    private int ticksSinceLastUpdate = 0;

    private long lastProcessedFoundTime = 0;

    public FollowNetherTrails() {
        super(ElytraFlightModes.FollowNetherTrails);
    }

    @Override
    public void onActivate() {
        databasePath = elytraFly.xaeroPlusDbPath.get();
        if (databasePath.equals("FILL_THIS/XaeroPlusOldChunks.db")) {
            elytraFly.error("XaeroPlus database path is not set!");
            elytraFly.toggle();
            return;
        }
        hasInitialized = true;
    }

    @Override
    public void onTick() {
        super.onTick();
        if (mc.player.age % (5 * 20) == 0) { // Do it every 5 seconds
            updateTrailDirection();
        }

        if (isFollowingTrail) {
            trailFollowingTicks++;
            ticksSinceLastUpdate++;
            if (trailFollowingTicks > MAX_TRAIL_FOLLOWING_TICKS) {
                isFollowingTrail = false;
                LOGGER.info("[FollowNetherTrails] Trail following timeout reached, resuming normal flight");
            }
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
            Vec3d averagePosition = calculateAveragePosition(recentChunks);
            Vec3d playerPos = mc.player.getPos();
            Vec3d horizontalDifference = new Vec3d(
                    averagePosition.x - playerPos.x,
                    0,
                    averagePosition.z - playerPos.z);

            // Use PathManager for nether mode
            Vec3d direction = horizontalDifference.normalize();
            Vec3d targetPos = playerPos.add(direction.multiply(TRAIL_FOLLOW_DISTANCE));
            BlockPos targetBlockPos = new BlockPos((int) targetPos.x, 120, (int) targetPos.z);
            PathManagers.get().moveTo(targetBlockPos, true);
            elytraFly.info("[FollowNetherTrails] Set Baritone goal to " + targetPos);
            LOGGER.info("[FollowNetherTrails] Nether mode: Moving to {}", targetPos);
        } else if (isFollowingTrail) {
            if (ticksSinceLastUpdate > DIRECTION_TIMEOUT) {
                isFollowingTrail = false;
                LOGGER.info("[FollowNetherTrails] No recent old chunks found, resuming normal flight");
            } else if (lastKnownDirection != null) {
                // Circle over the most recent chunk
                double angle = (mc.player.age % 360) * Math.PI / 180; // Convert age to radians
                trailDirection = new Vec3d(Math.cos(angle), 0, Math.sin(angle)).normalize();
                if (mc.player.age % 40 == 0) {
                    LOGGER.info("[FollowNetherTrails] No new chunks, circling over last known position: {}",
                            trailDirection);
                }
            }
        }
    }

    private List<Vec3d> getRecentOldChunks() {
        List<Vec3d> chunks = new ArrayList<>();
        String tableName = "minecraft:the_nether";

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
                LOGGER.info("[FollowNetherTrails] Processed {} new chunks. Last foundTime: {}", chunks.size(),
                        lastProcessedFoundTime);
            } else {
                if (mc.player.age % 200 == 0) {
                    elytraFly.info("[FollowNetherTrails] No new chunks found");
                }
            }

        } catch (Exception e) {
            LOGGER.error("[FollowNetherTrails] Error reading from database: " + e.getMessage());
        }
        return chunks;
    }

    private Vec3d calculateAveragePosition(List<Vec3d> positions) {
        double sumX = 0, sumZ = 0;
        for (Vec3d pos : positions) {
            sumX += pos.x;
            sumZ += pos.z;
        }
        return new Vec3d(sumX / positions.size(), 0, sumZ / positions.size());
    }
}
