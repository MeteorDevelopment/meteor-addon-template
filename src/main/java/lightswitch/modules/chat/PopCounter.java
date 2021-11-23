package lightswitch.modules.chat;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lightswitch.Lightswitch;
import lightswitch.utils.Wrapper;
import lightswitch.utils.chat.EzUtil;
import lightswitch.utils.misc.Placeholders;
import lightswitch.utils.misc.Stats;
import lightswitch.utils.misc.StringHelper;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class PopCounter extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAutoEz = settings.createGroup("AutoEz");
    private final SettingGroup sgMessages = settings.createGroup("Messages");

    private final Setting<Boolean> own = sgGeneral.add(new BoolSetting.Builder().name("own").description("Notifies you of your own totem pops.").defaultValue(false).build());
    private final Setting<Boolean> friends = sgGeneral.add(new BoolSetting.Builder().name("friends").description("Notifies you of your friends totem pops.").defaultValue(true).build());
    private final Setting<Boolean> others = sgGeneral.add(new BoolSetting.Builder().name("others").description("Notifies you of other players totem pops.").defaultValue(true).build());
    private final Setting<Boolean> announceOthers = sgGeneral.add(new BoolSetting.Builder().name("announce").description("Announces when other players pop totems in global chat.").defaultValue(false).visible(others::get).build());
    private final Setting<Boolean> pmOthers = sgGeneral.add(new BoolSetting.Builder().name("pm").description("Message players when they pop a totem.").defaultValue(false).visible(announceOthers::get).build());
    private final Setting<Integer> announceDelay = sgGeneral.add(new IntSetting.Builder().name("announce-delay").description("How many seconds between announcing totem pops.").defaultValue(5).min(1).sliderMax(100).visible(announceOthers::get).build());
    private final Setting<Double> announceRange = sgGeneral.add(new DoubleSetting.Builder().name("announce-range").description("How close players need to be to announce pops or AutoEz.").defaultValue(3).min(0).sliderMax(10).visible(announceOthers::get).build());
    private final Setting<Boolean> dontAnnounceFriends = sgGeneral.add(new BoolSetting.Builder().name("dont-announce-friends").description("Don't annnounce when your friends pop.").defaultValue(true).build());
    public final Setting<Boolean> doPlaceholders = sgGeneral.add(new BoolSetting.Builder().name("placeholders").description("Enable global placeholders for pop/auto ez messages.").defaultValue(false).build());
    public final Setting<Boolean> autoEz = sgAutoEz.add(new BoolSetting.Builder().name("auto-ez").description("Sends a message when you kill players.").defaultValue(false).build());
    public final Setting<Boolean> suffix = sgAutoEz.add(new BoolSetting.Builder().name("suffix").description("Add Lightswitch suffix to the end of pop messages.").defaultValue(false).visible(autoEz::get).build());
    public final Setting<Boolean> killStr = sgAutoEz.add(new BoolSetting.Builder().name("killstreak").description("Add your killstreak to the end of autoez messages").defaultValue(false).visible(autoEz::get).build());
    public final Setting<Boolean> pmEz = sgAutoEz.add(new BoolSetting.Builder().name("pm-ez").description("Send the autoez message to the player's dm.").defaultValue(false).visible(autoEz::get).build());
    private final Setting<List<String>> popMessages = sgMessages.add(new StringListSetting.Builder().name("pop-messages").description("Messages to use when announcing pops.").defaultValue("Popped by Lightswitch"/*Collections.emptyList()*/).build());
    public final Setting<List<String>> ezMessages = sgMessages.add(new StringListSetting.Builder().name("ez-messages").description("Messages to use for autoez.").defaultValue("Lightswitch on top!"/*Collections.emptyList()*/).visible(autoEz::get).build());

    public final Object2IntMap<UUID> totemPops = new Object2IntOpenHashMap<>();
    private final Object2IntMap<UUID> chatIds = new Object2IntOpenHashMap<>();

    private final Random random = new Random();
    private int updateWait = 45;

    public PopCounter() {
        super(Lightswitch.CATEGORY, "pop-counter", "Count player's totem pops.");
    }
    private int announceWait;


    @Override
    public void onActivate() {
        EzUtil.updateTargets();
        totemPops.clear();
        chatIds.clear();
        announceWait = announceDelay.get() * 20;
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        Stats.reset();
        totemPops.clear();
        chatIds.clear();
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket)) return;

        EntityStatusS2CPacket p = (EntityStatusS2CPacket) event.packet;
        if (p.getStatus() != 35) return;

        Entity entity = p.getEntity(mc.world);
        if (entity != null && ! (entity instanceof PlayerEntity)) return;
        if (entity == null
            || (entity.equals(mc.player) && !own.get())
            || (Friends.get().isFriend(((PlayerEntity) entity)) && !others.get())
            || (!Friends.get().isFriend(((PlayerEntity) entity)) && !friends.get())
        ) return;

        synchronized (totemPops) {
            int pops = totemPops.getOrDefault(entity.getUuid(), 0);
            totemPops.put(entity.getUuid(), ++pops);

            ChatUtils.sendMsg(getChatId(entity), Formatting.GRAY, "(highlight)%s (default)popped (highlight)%d (default)%s.", entity.getEntityName(), pops, pops == 1 ? "totem" : "totems");
        }
        if (announceOthers.get() && announceWait <= 1 && mc.player.distanceTo(entity) <= announceRange.get()) {
            if (dontAnnounceFriends.get() && Friends.get().isFriend((PlayerEntity) entity)) return;
            String popMessage = getPopMessage((PlayerEntity) entity);
            if (doPlaceholders.get()) popMessage = Placeholders.apply(popMessage);
            String name = entity.getEntityName();
            if (suffix.get()) { popMessage = popMessage + " | Lightswitch " + Lightswitch.VERSION; }
            mc.player.sendChatMessage(popMessage);
            if (pmOthers.get()) Wrapper.messagePlayer(name, StringHelper.stripName(name, popMessage));
            announceWait = announceDelay.get() * 20;
        }
    }


    @EventHandler
    private void onTick(TickEvent.Post event) {
        updateWait--;
        if (updateWait <= 0) {
            EzUtil.updateTargets();
            updateWait = 45;
        }
        announceWait--;
        synchronized (totemPops) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (!totemPops.containsKey(player.getUuid())) continue;

                if (player.deathTime > 0 || player.getHealth() <= 0) {
                    int pops = totemPops.removeInt(player.getUuid());

                    ChatUtils.sendMsg(getChatId(player), Formatting.GRAY, "(highlight)%s (default)popped (highlight)%d (default)%s.", player.getEntityName(), pops, pops == 1 ? "totem" : "totems");
                    chatIds.removeInt(player.getUuid());
                    if (EzUtil.currentTargets.contains(player.getEntityName())) EzUtil.sendAutoEz(player.getEntityName());
                }
            }
        }
    }

    private int getChatId(Entity entity) {
        return chatIds.computeIntIfAbsent(entity.getUuid(), value -> random.nextInt());
    }

    private String getPopMessage(PlayerEntity p) {
        if (popMessages.get().isEmpty()) {
            ChatUtils.warning("Your pop message list is empty!");
            return "Ez pop";
        }
        String playerName = p.getEntityName();
        String popMessage = popMessages.get().get(new Random().nextInt(popMessages.get().size()));
        if (popMessage.contains("{pops}")) {
            if (totemPops.containsKey(p.getUuid())) {
                int pops = totemPops.getOrDefault(p.getUuid(), 0);
                if (pops == 1) {
                    popMessage = popMessage.replace("{pops}", pops + " totem");
                } else {
                    popMessage = popMessage.replace("{pops}", pops + " totems");
                }
            } else {
                popMessage = "Ezz pop";
            }
        }
        if (popMessage.contains("{player}")) popMessage = popMessage.replace("{player}", playerName);
        return popMessage;
    }

}
