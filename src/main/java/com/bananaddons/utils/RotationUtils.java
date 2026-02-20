package com.bananaddons.utils;
import com.bananaddons.accessor.InputAccessor;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static meteordevelopment.meteorclient.MeteorClient.mc;
public class RotationUtils {
    private static RotationUtils INSTANCE;
    private final List<Rotation> requests = new CopyOnWriteArrayList<>();
    private float serverYaw, serverPitch;
    private float lastServerYaw, lastServerPitch;
    private float prevYaw, prevPitch;
    private float prevJumpYaw;
    private boolean rotate;
    private boolean webJumpFix;
    private boolean preJumpFix;
    private Rotation rotation;
    private int rotateTicks;
    private boolean movementFix = true;
    private boolean mouseSensFix = true;
    private int preserveTicks = 3;
    private boolean webJumpFixEnabled = true;
    private RotationUtils() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }
    public static RotationUtils getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RotationUtils();
        }
        return INSTANCE;
    }
    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;
        if (event.packet instanceof PlayerMoveC2SPacket packet && packet.changesLook()) {
            float packetYaw = packet.getYaw(0.0f);
            float packetPitch = packet.getPitch(0.0f);
            serverYaw = packetYaw;
            serverPitch = packetPitch;
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        webJumpFix = isInWeb();
        lastServerYaw = serverYaw;
        lastServerPitch = serverPitch;
        if (rotation != null) {
            rotateTicks++;
        }
        requests.removeIf(req -> req == null);
        if (requests.isEmpty()) {
            if (isDoneRotating()) {
                rotation = null;
                rotate = false;
            }
            return;
        }
        Rotation request = getRotationRequest();
        if (request == null) {
            if (isDoneRotating()) {
                rotation = null;
                rotate = false;
                return;
            }
        } else {
            rotation = request;
            rotateTicks = 0;
            rotate = true;
        }
        if (rotation != null && rotate) {
            applyRotation();
        }
    }
    @EventHandler
    public void onTickPost(TickEvent.Post event) {
        if (rotation != null && mc.player != null && movementFix) {
            InputAccessor inputAccessor = (InputAccessor) mc.player.input;
            float forward = inputAccessor.getMovementForward();
            float sideways = inputAccessor.getMovementSideways();
            if (forward == 0.0f && sideways == 0.0f) return;
            float delta = (mc.player.getYaw() - rotation.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
            float cos = MathHelper.cos(delta);
            float sin = MathHelper.sin(delta);
            inputAccessor.setMovementSideways(Math.round(sideways * cos - forward * sin));
            inputAccessor.setMovementForward(Math.round(forward * cos + sideways * sin));
        }
    }
    public void setRotation(Rotation rotation) {
        if (mouseSensFix) {
            double fix = Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0) * 1.2;
            rotation.setYaw((float) (rotation.getYaw() - (rotation.getYaw() - serverYaw) % fix));
            rotation.setPitch((float) (rotation.getPitch() - (rotation.getPitch() - serverPitch) % fix));
        }
        if (rotation.getPriority() == Integer.MAX_VALUE) {
            this.rotation = rotation;
        }
        requests.removeIf(r -> r.getPriority() == rotation.getPriority());
        requests.add(rotation);
    }
    public void setRotationClient(float yaw, float pitch) {
        if (mc.player == null) return;
        mc.player.setYaw(yaw);
        mc.player.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    }
    public void setRotationSilent(float yaw, float pitch) {
        setRotationSilent(yaw, pitch, Integer.MAX_VALUE);
    }
    public void setRotationSilent(float yaw, float pitch, int priority) {
        setRotation(new Rotation(priority, yaw, pitch, true));
        mc.getNetworkHandler().sendPacket(
            new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                yaw,
                pitch,
                mc.player.isOnGround(),
                false
            )
        );
    }
    public void setRotationSilentSync() {
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        setRotation(new Rotation(Integer.MAX_VALUE, yaw, pitch, true));
        mc.getNetworkHandler().sendPacket(
            new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                yaw,
                pitch,
                mc.player.isOnGround(),
                false
            )
        );
    }
    private void applyRotation() {
        if (rotation == null) return;
        removeRotation(rotation);
        rotate = false;
        if (rotation.isSnap()) {
            rotation = null;
        }
    }
    public boolean removeRotation(Rotation request) {
        return requests.remove(request);
    }
    public void clearRotations() {
        requests.clear();
        rotation = null;
        rotate = false;
        rotateTicks = 0;
    }
    public void clearRotationsByPriority(int priority) {
        requests.removeIf(req -> req.getPriority() == priority);
        if (rotation != null && rotation.getPriority() == priority) {
            rotation = null;
            rotate = false;
        }
    }
    public boolean isRotationBlocked(int priority) {
        return rotation != null && priority < rotation.getPriority();
    }
    public boolean isDoneRotating() {
        return rotateTicks > preserveTicks;
    }
    public boolean isRotating() {
        return rotation != null;
    }
    public float getRotationYaw() {
        return rotation != null ? rotation.getYaw() : mc.player.getYaw();
    }
    public float getRotationPitch() {
        return rotation != null ? rotation.getPitch() : mc.player.getPitch();
    }
    public float getServerYaw() {
        return serverYaw;
    }
    public float getWrappedYaw() {
        return MathHelper.wrapDegrees(serverYaw);
    }
    public float getServerPitch() {
        return serverPitch;
    }
    public float getLastServerYaw() {
        return lastServerYaw;
    }
    public float getLastServerPitch() {
        return lastServerPitch;
    }
    private Rotation getRotationRequest() {
        Rotation rotationRequest = null;
        int priority = 0;
        for (Rotation request : requests) {
            if (request.getPriority() > priority) {
                rotationRequest = request;
                priority = request.getPriority();
            }
        }
        return rotationRequest;
    }
    private boolean isInWeb() {
        if (mc.player == null || mc.world == null) return false;
        try {
            return mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox()).iterator().hasNext();
        } catch (Exception e) {
            return false;
        }
    }
    public boolean getMovementFix() { return movementFix; }
    public void setMovementFix(boolean movementFix) { this.movementFix = movementFix; }
    public boolean getMouseSensFix() { return mouseSensFix; }
    public void setMouseSensFix(boolean mouseSensFix) { this.mouseSensFix = mouseSensFix; }
    public int getPreserveTicks() { return preserveTicks; }
    public void setPreserveTicks(int preserveTicks) { this.preserveTicks = preserveTicks; }
    public boolean getWebJumpFixEnabled() { return webJumpFixEnabled; }
    public void setWebJumpFixEnabled(boolean webJumpFixEnabled) { this.webJumpFixEnabled = webJumpFixEnabled; }
    public static float[] getRotationsTo(Vec3d src, Vec3d dest) {
        float yaw = (float) (Math.toDegrees(Math.atan2(dest.subtract(src).z,
                dest.subtract(src).x)) - 90);
        float pitch = (float) Math.toDegrees(-Math.atan2(dest.subtract(src).y,
                Math.hypot(dest.subtract(src).x, dest.subtract(src).z)));
        return new float[] {
                MathHelper.wrapDegrees(yaw),
                MathHelper.wrapDegrees(pitch)
        };
    }
    public static float[] getRotationsTo(Entity entity, HitVector hitVector) {
        Vec3d targetEntityPos = getHitVector(entity, hitVector);
        return getRotationsTo(mc.player.getEyePos(), targetEntityPos);
    }
    public static Vec3d getHitVector(Entity entity, HitVector hitVector) {
        Vec3d feetPos = entity.getEntityPos();
        return switch (hitVector) {
            case FEET -> feetPos;
            case TORSO -> feetPos.add(0.0, entity.getHeight() / 2.0f, 0.0);
            case EYES -> entity.getEyePos();
            case CLOSEST -> {
                Vec3d eyePos = mc.player.getEyePos();
                Vec3d torsoPos = feetPos.add(0.0, entity.getHeight() / 2.0f, 0.0);
                Vec3d eyesPos = entity.getEyePos();
                double feetDist = eyePos.squaredDistanceTo(feetPos);
                double torsoDist = eyePos.squaredDistanceTo(torsoPos);
                double eyesDist = eyePos.squaredDistanceTo(eyesPos);
                if (feetDist <= torsoDist && feetDist <= eyesDist) {
                    yield feetPos;
                } else if (torsoDist <= eyesDist) {
                    yield torsoPos;
                } else {
                    yield eyesPos;
                }
            }
        };
    }
    public static float[] smooth(float[] target, float[] previous, float rotationSpeed) {
        float speed = (1.0f - (MathHelper.clamp(rotationSpeed / 100.0f, 0.1f, 0.9f))) * 10.0f;
        float[] rotations = new float[2];
        rotations[0] = previous[0] + (float) (-getAngleDifference(previous[0], target[0]) / speed);
        rotations[1] = previous[1] + (-(previous[1] - target[1]) / speed);
        rotations[1] = MathHelper.clamp(rotations[1], -90.0f, 90.0f);
        return rotations;
    }
    public static double getAngleDifference(float client, float yaw) {
        return ((client - yaw) % 360.0 + 540.0) % 360.0 - 180.0;
    }
    public static double getAnglePitchDifference(float client, float pitch) {
        return ((client - pitch) % 180.0 + 270.0) % 180.0 - 90.0;
    }
    public static Vec3d getRotationVector(float pitch, float yaw) {
        float f = pitch * ((float) Math.PI / 180.0f);
        float g = -yaw * ((float) Math.PI / 180.0f);
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }
    public static boolean canSeePosition(Vec3d from, Vec3d to) {
        BlockHitResult result = mc.world.raycast(new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return result == null || result.getBlockPos().equals(BlockPos.ofFloored(to));
    }
    public static boolean isInFov(Vec3d from, Vec3d to, float fov) {
        if (fov >= 180.0f) return true;
        float[] rotations = getRotationsTo(from, to);
        float yawDiff = MathHelper.wrapDegrees(mc.player.getYaw() - rotations[0]);
        return Math.abs(yawDiff) <= fov;
    }
    public static float wrapDegrees(float degrees) {
        return MathHelper.wrapDegrees(degrees);
    }
    public enum HitVector {
        FEET,
        TORSO,
        EYES,
        CLOSEST
    }
    public static class Rotation {
        private final int priority;
        private float yaw, pitch;
        private boolean snap;
        public Rotation(int priority, float yaw, float pitch, boolean snap) {
            this.priority = priority;
            this.yaw = yaw;
            this.pitch = pitch;
            this.snap = snap;
        }
        public Rotation(int priority, float yaw, float pitch) {
            this(priority, yaw, pitch, false);
        }
        public int getPriority() {
            return priority;
        }
        public void setYaw(float yaw) {
            this.yaw = yaw;
        }
        public void setPitch(float pitch) {
            this.pitch = pitch;
        }
        public float getYaw() {
            return yaw;
        }
        public float getPitch() {
            return pitch;
        }
        public void setSnap(boolean snap) {
            this.snap = snap;
        }
        public boolean isSnap() {
            return snap;
        }
    }
}
