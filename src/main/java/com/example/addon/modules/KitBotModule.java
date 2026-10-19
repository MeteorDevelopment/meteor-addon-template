package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KitBotModule extends Module {
    private static final int TELEPORT_TIMEOUT_TICKS = 2400;
    private static final int POST_CHEST_TPA_DELAY_TICKS = 20;
    private static final long TELEPORT_MOVEMENT_WINDOW_NANOS = 2_000_000_000L;
    private static final double TELEPORT_DISTANCE_SQUARED = 10_000.0;
    private static final int RESPAWN_TIMEOUT_TICKS = 200;
    private static final int MAX_KIT_AMOUNT = 6;
    private static final long REQUEST_COOLDOWN_NANOS = 5L * 60 * 1_000_000_000;
    private static final long POST_TELEPORT_DELAY_NANOS = 2L * 1_000_000_000;
    private static final Pattern SERVER_TELEPORT_COOLDOWN_PATTERN = Pattern.compile(
        "(?i)you have to wait\\s+([0-9]+\\s*[dhms](?:\\s*[0-9]+\\s*[dhms])*)\\s+to teleport again"
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTesting = settings.createGroup("Testing");
    private final Queue<KitRequest> requests = new ArrayDeque<>();
    private final Deque<PositionSample> teleportPositionSamples = new ArrayDeque<>();
    private final Map<String, Long> lastRequestNanos = new HashMap<>();
    private final Map<KitRequest, Integer> lastNotifiedQueuePositions = new IdentityHashMap<>();

    private final Setting<Integer> defaultKitAmount = sgGeneral.add(new IntSetting.Builder()
        .name("default-kit-amount")
        .description("Shulker boxes to collect when a friend does not specify an amount (maximum 6).")
        .defaultValue(MAX_KIT_AMOUNT)
        .range(1, MAX_KIT_AMOUNT)
        .sliderRange(1, MAX_KIT_AMOUNT)
        .build()
    );

    private final Setting<Boolean> resetCooldownButton = sgTesting.add(new BoolSetting.Builder()
        .name("reset-all-cooldowns")
        .description("Press to clear the request cooldown for every friend.")
        .defaultValue(false)
        .onChanged(this::onResetCooldownButtonChanged)
        .build()
    );

    private volatile State state = State.IDLE;
    private volatile boolean respawnPacketReceived;
    private KitRequest activeRequest;
    private int stateTicks;
    private boolean sawDeath;
    private long killDeadlineNanos;
    private int lastAnnouncedKillSecond;

    public KitBotModule() {
        super(AddonTemplate.CATEGORY, "kit-bot", "Fulfills kit requests from Meteor friends using matching signs, then drops the kit with /kill.");
    }

    @Override
    public void onDeactivate() {
        requests.clear();
        lastNotifiedQueuePositions.clear();
        activeRequest = null;
        state = State.IDLE;
        teleportPositionSamples.clear();
        respawnPacketReceived = false;
        killDeadlineNanos = 0;
        lastAnnouncedKillSecond = 0;
        stateTicks = 0;
        Modules.get().get(ChestTakerModule.class).cancelKitPickup();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null || mc.player.connection == null) return;

        String message = event.getMessage().getString();
        String serverCooldown = getServerTeleportCooldown(message);
        if (serverCooldown != null && activeRequest != null
            && (state == State.SENDING_TPA || state == State.WAITING_FOR_TELEPORT)) {
            String requester = activeRequest.requester();
            lastRequestNanos.remove(requester.toLowerCase(Locale.ROOT));
            mc.player.connection.sendCommand("msg " + requester
                + " server cooldown try again in " + serverCooldown);
            warning("Server teleport cooldown blocked %s's kit delivery; it was not counted as delivered.", requester);
            finishRequest();
            return;
        }

        KitRequest request = parseFriendRequest(message);
        if (request == null) {
            handleWhisperCommand(message);
            return;
        }

        String requesterKey = request.requester().toLowerCase(Locale.ROOT);
        long secondsRemaining = cooldownSecondsRemaining(requesterKey);
        if (secondsRemaining > 0) {
            mc.player.connection.sendCommand("msg " + request.requester()
                + " kit request cooldown active; try again in " + secondsRemaining + " seconds");
            return;
        }

        requests.add(request);
        notifyQueuePositions();
        info("Queued %s's %s request for %d shulker(s), position %d.",
            request.requester(), request.kitType(), request.amount(), requests.size() + (activeRequest == null ? 0 : 1));
        startNextRequest();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null) {
            requests.clear();
            lastNotifiedQueuePositions.clear();
            activeRequest = null;
            state = State.IDLE;
            teleportPositionSamples.clear();
            respawnPacketReceived = false;
            killDeadlineNanos = 0;
            lastAnnouncedKillSecond = 0;
            return;
        }

        if (state == State.IDLE) {
            startNextRequest();
            return;
        }

        if (state == State.SENDING_TPA) {
            if (activeRequest == null) {
                finishRequest();
                return;
            }

            if (++stateTicks < POST_CHEST_TPA_DELAY_TICKS) return;

            stateTicks = 0;
            teleportPositionSamples.clear();
            teleportPositionSamples.addLast(new PositionSample(System.nanoTime(), mc.player.position()));
            state = State.WAITING_FOR_TELEPORT;
            mc.player.connection.sendCommand("tpa " + activeRequest.requester());
            info("Sent /tpa request to %s.", activeRequest.requester());
            return;
        }

        if (state == State.WAITING_FOR_TELEPORT) {
            long now = System.nanoTime();
            Vec3 currentPosition = mc.player.position();
            teleportPositionSamples.addLast(new PositionSample(now, currentPosition));
            while (teleportPositionSamples.size() > 1
                && now - teleportPositionSamples.peekFirst().timestampNanos() > TELEPORT_MOVEMENT_WINDOW_NANOS) {
                teleportPositionSamples.removeFirst();
            }
            PositionSample oldestSample = teleportPositionSamples.peekFirst();
            boolean fastCoordinateChange = oldestSample != null
                && now - oldestSample.timestampNanos() <= TELEPORT_MOVEMENT_WINDOW_NANOS
                && currentPosition.distanceToSqr(oldestSample.position()) >= TELEPORT_DISTANCE_SQUARED;
            if (fastCoordinateChange) {
                teleportPositionSamples.clear();
                startPostTeleportDelay();
                return;
            }

            if (++stateTicks >= TELEPORT_TIMEOUT_TICKS) {
                warning("No accepted teleport message was detected; cancelling %s's request.", activeRequest.requester());
                finishRequest();
            }
            return;
        }

        if (state == State.POST_TELEPORT_DELAY) {
            long remainingNanos = killDeadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                state = State.WAITING_FOR_RESPAWN;
                stateTicks = 0;
                sawDeath = false;
                respawnPacketReceived = false;
                info("Sending /kill command packet now for %s's kit request.", activeRequest.requester());
                mc.player.connection.sendCommand("kill");
            } else {
                int remainingSeconds = (int) ((remainingNanos + 999_999_999L) / 1_000_000_000L);
                if (remainingSeconds < lastAnnouncedKillSecond) {
                    lastAnnouncedKillSecond = remainingSeconds;
                    info("/kill in %d second(s).", remainingSeconds);
                }
            }
            return;
        }

        if (state == State.WAITING_FOR_RESPAWN) {
            if (mc.player.getHealth() <= 0) sawDeath = true;
            if ((respawnPacketReceived || sawDeath) && mc.player.getHealth() > 0) {
                info("Respawn detected; moving to the next queued request.");
                finishRequest();
            } else if (++stateTicks >= RESPAWN_TIMEOUT_TICKS) {
                warning("Respawn was not detected after /kill; moving to the next queued request.");
                finishRequest();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (state == State.WAITING_FOR_RESPAWN && event.packet instanceof ClientboundRespawnPacket) {
            respawnPacketReceived = true;
        }
    }

    private void startPostTeleportDelay() {
        state = State.POST_TELEPORT_DELAY;
        killDeadlineNanos = System.nanoTime() + POST_TELEPORT_DELAY_NANOS;
        lastAnnouncedKillSecond = 2;
        info("Teleport detected; /kill in 2 seconds.");
    }

    public boolean beginKitDelivery(String requester) {
        if (activeRequest == null || !activeRequest.requester().equalsIgnoreCase(requester)
            || mc.player == null || mc.player.connection == null || state != State.WAITING_FOR_CHEST) return false;

        if (PathManagers.get() != null) PathManagers.get().stop();
        lastRequestNanos.put(activeRequest.requester().toLowerCase(Locale.ROOT), System.nanoTime());
        stateTicks = 0;
        state = State.SENDING_TPA;
        info("Collected %d %s shulker(s); sending /tpa to %s in 1 second.",
            activeRequest.amount(), activeRequest.kitType(), activeRequest.requester());
        return true;
    }

    public void onCoordinateChangeKillSent() {
        if (activeRequest == null || (state != State.SENDING_TPA
            && state != State.WAITING_FOR_TELEPORT && state != State.POST_TELEPORT_DELAY)) return;

        state = State.WAITING_FOR_RESPAWN;
        stateTicks = 0;
        sawDeath = false;
        respawnPacketReceived = false;
        killDeadlineNanos = 0;
        info("Coordinate Change Kill sent /kill; counting %s's kit as delivered.", activeRequest.requester());
    }

    public void cancelActiveRequest() {
        if (activeRequest == null) return;
        warning("Cancelled %s's %s kit request.", activeRequest.requester(), activeRequest.kitType());
        finishRequest();
    }

    public void reportOutOfStock(String requester) {
        if (activeRequest == null || !activeRequest.requester().equalsIgnoreCase(requester)) return;
        whisperOutOfStock(activeRequest);
        info("Not sending a teleport request to %s because the requested kit is out of stock.", activeRequest.requester());
        finishRequest();
    }

    public boolean hasPendingKitRequests() {
        return activeRequest != null || !requests.isEmpty() || state != State.IDLE;
    }

    private void startNextRequest() {
        if (activeRequest != null || state != State.IDLE || mc.player == null || mc.player.connection == null) return;

        while (!requests.isEmpty()) {
            activeRequest = requests.remove();
            long secondsRemaining = cooldownSecondsRemaining(activeRequest.requester().toLowerCase(Locale.ROOT));
            if (secondsRemaining > 0) {
                mc.player.connection.sendCommand("msg " + activeRequest.requester()
                    + " kit request cooldown active; try again in " + secondsRemaining + " seconds");
                activeRequest = null;
                continue;
            }

            state = State.WAITING_FOR_CHEST;

            ChestTakerModule chestTaker = Modules.get().get(ChestTakerModule.class);
            if (!chestTaker.isActive()) chestTaker.enable();
            if (chestTaker.requestKit(activeRequest.requester(), activeRequest.kitType(), activeRequest.amount())) {
                return;
            }

            warning("Could not start %s's %s kit pickup.", activeRequest.requester(), activeRequest.kitType());
            whisperOutOfStock(activeRequest);
            activeRequest = null;
            state = State.IDLE;
        }
    }

    private long cooldownSecondsRemaining(String requesterKey) {
        Long lastRequest = lastRequestNanos.get(requesterKey);
        if (lastRequest == null) return 0;
        long remainingNanos = REQUEST_COOLDOWN_NANOS - (System.nanoTime() - lastRequest);
        return remainingNanos <= 0 ? 0 : (remainingNanos + 999_999_999) / 1_000_000_000;
    }

    private void onResetCooldownButtonChanged(Boolean pressed) {
        if (!pressed) return;

        lastRequestNanos.clear();
        info("All friend request cooldowns have been reset.");
        resetCooldownButton.set(false);
    }

    private void whisperOutOfStock(KitRequest request) {
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("msg " + request.requester() + " out of stock");
        }
    }

    private void handleWhisperCommand(String message) {
        Whisper whisper = parseWhisper(message);
        if (whisper == null) return;

        boolean friend = false;
        for (Friend entry : Friends.get()) {
            if (entry.getName().equalsIgnoreCase(whisper.sender())) {
                friend = true;
                break;
            }
        }
        if (!friend) {
            mc.player.connection.sendCommand("msg " + whisper.sender() + " not whitelisted");
            return;
        }

        if (whisper.content().equalsIgnoreCase("!help")) {
            mc.player.connection.sendCommand("msg " + whisper.sender()
                + " Kits: pvp, obsidian, totems, random, build, terracotta, random concrete, refill.");
            mc.player.connection.sendCommand("msg " + whisper.sender()
                + " Example: !kit obsidian [amount] (amount 1-6).");
        } else {
            mc.player.connection.sendCommand("msg " + whisper.sender() + " invallid command !help for commands");
        }
    }

    private String getServerTeleportCooldown(String message) {
        Matcher matcher = SERVER_TELEPORT_COOLDOWN_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private Whisper parseWhisper(String message) {
        String name = "([A-Za-z0-9_]{1,16})";
        String prefix = "(?i)^\\s*(?:\\[[^\\]]+\\]\\s*)*";
        String[] patterns = {
            prefix + name + "\\s+(?:whispers(?:\\s+to\\s+you)?|tells\\s+you)(?:\\s*:\\s*|\\s+)(.*)$",
            prefix + name + "\\s*->\\s*(?:you|me)\\s*:\\s*(.*)$",
            prefix + "(?:from|whisper\\s+from)\\s+" + name + "\\s*:\\s*(.*)$"
        };

        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(message);
            if (!matcher.matches()) continue;

            return new Whisper(matcher.group(1), matcher.group(2));
        }
        return null;
    }

    private void finishRequest() {
        activeRequest = null;
        teleportPositionSamples.clear();
        stateTicks = 0;
        sawDeath = false;
        respawnPacketReceived = false;
        killDeadlineNanos = 0;
        lastAnnouncedKillSecond = 0;
        state = State.IDLE;
        startNextRequest();
        notifyQueuePositions();
    }

    private void notifyQueuePositions() {
        if (mc.player == null || mc.player.connection == null) return;

        Map<KitRequest, Integer> currentPositions = new IdentityHashMap<>();
        int position = 1;
        if (activeRequest != null) currentPositions.put(activeRequest, position++);
        for (KitRequest request : requests) currentPositions.put(request, position++);

        for (Map.Entry<KitRequest, Integer> entry : currentPositions.entrySet()) {
            KitRequest request = entry.getKey();
            int currentPosition = entry.getValue();
            Integer previousPosition = lastNotifiedQueuePositions.get(request);
            if (previousPosition == null || previousPosition != currentPosition) {
                mc.player.connection.sendCommand("msg " + request.requester()
                    + " position #" + currentPosition + " in *queue*");
            }
        }

        lastNotifiedQueuePositions.keySet().retainAll(currentPositions.keySet());
        lastNotifiedQueuePositions.putAll(currentPositions);
    }

    private KitRequest parseFriendRequest(String message) {
        String commandPattern = "(!kit\\s+(?:random\\s+concrete|pvp|obsidian|totems?|random|build|terracotta|refill)(?:\\s+\\d+)?)";
        for (Friend friend : Friends.get()) {
            String name = Pattern.quote(friend.getName());
            String prefix = "(?i)^\\s*(?:\\[[^\\]]+\\]\\s*)*";
            String[] formats = {
                "<" + name + ">\\s*" + commandPattern,
                name + "\\s*:\\s*" + commandPattern,
                name + "\\s+(?:whispers(?:\\s+to\\s+you)?|tells\\s+you)(?:\\s*:\\s*|\\s+)" + commandPattern,
                name + "\\s*->\\s*(?:you|me)\\s*:\\s*" + commandPattern,
                "(?:from|whisper\\s+from)\\s+" + name + "\\s*:\\s*" + commandPattern
            };

            for (String format : formats) {
                Matcher messageMatcher = Pattern.compile(prefix + format + "\\s*$").matcher(message);
                if (!messageMatcher.matches()) continue;

                Matcher commandMatcher = Pattern.compile(
                    "(?i)!kit\\s+(random\\s+concrete|pvp|obsidian|totems?|random|build|terracotta|refill)(?:\\s+(\\d+))?")
                    .matcher(messageMatcher.group(1));
                if (!commandMatcher.matches()) continue;

                String kitType = commandMatcher.group(1).toLowerCase(Locale.ROOT).replace(' ', '_');
                if (kitType.equals("totem")) kitType = "totems";
                String amountText = commandMatcher.group(2);
                if (amountText != null && amountText.length() > 1) {
                    warning("Kit amount must be between 1 and %d.", MAX_KIT_AMOUNT);
                    return null;
                }

                int amount = amountText == null ? defaultKitAmount.get() : Integer.parseInt(amountText);
                if (amount < 1 || amount > MAX_KIT_AMOUNT) {
                    warning("Kit amount must be between 1 and %d.", MAX_KIT_AMOUNT);
                    return null;
                }

                return new KitRequest(friend.getName(), kitType, amount);
            }
        }

        return null;
    }

    private enum State {
        IDLE,
        WAITING_FOR_CHEST,
        SENDING_TPA,
        WAITING_FOR_TELEPORT,
        POST_TELEPORT_DELAY,
        WAITING_FOR_RESPAWN
    }

    private record KitRequest(String requester, String kitType, int amount) {
    }

    private record PositionSample(long timestampNanos, Vec3 position) {
    }

    private record Whisper(String sender, String content) {
    }
}
