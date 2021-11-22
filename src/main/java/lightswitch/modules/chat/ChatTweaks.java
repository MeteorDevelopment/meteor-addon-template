package lightswitch.modules.chat;

import lightswitch.Lightswitch;
import lightswitch.utils.chat.Emotes;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.RainbowColor;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.BaseText;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;


public class ChatTweaks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();


    public final Setting<Boolean> emotes = sgGeneral.add(new BoolSetting.Builder().name("emotes").description("Enables the Ghostware emote system.").defaultValue(false).build());
    public final Setting<Boolean> customPrefix = sgGeneral.add(new BoolSetting.Builder().name("custom-prefix").description("Lets you set a custom prefix.").defaultValue(false).build());
    public final Setting<String> prefixText = sgGeneral.add(new StringSetting.Builder().name("custom-prefix-text").description("Override the [Orion] prefix.").defaultValue("Orion").visible(customPrefix :: get).build());
    public final Setting<Boolean> customPrefixColor = sgGeneral.add(new BoolSetting.Builder().name("custom-prefix-color").description("Lets you set a custom prefix.").defaultValue(false).build());
    public final Setting<Boolean> chromaPrefix = sgGeneral.add(new BoolSetting.Builder().name("chroma-prefix").description("Lets you set a custom prefix.").defaultValue(false).build());
    public final Setting<Double> chromaSpeed = sgGeneral.add(new DoubleSetting.Builder().name("chroma-speed").description("Speed of the chroma animation.").defaultValue(0.09).min(0.01).sliderMax(5).decimalPlaces(2).visible(chromaPrefix :: get).build());
    public final Setting<SettingColor> prefixColor = sgGeneral.add(new ColorSetting.Builder().name("prefix-color").description("Color of the prefix text.").defaultValue(new SettingColor(255, 255, 255)).visible(customPrefixColor :: get).build());
    public final Setting<Boolean> themeBrackets = sgGeneral.add(new BoolSetting.Builder().name("apply-to-brackets").description("Apply the current prefix theme to the brackets.").defaultValue(false).build());
    public final Setting<Boolean> customBrackets = sgGeneral.add(new BoolSetting.Builder().name("custom-brackets").description("Set custom brackets.").defaultValue(false).build());
    public final Setting<String> leftBracket = sgGeneral.add(new StringSetting.Builder().name("left-bracket").description("").defaultValue("[").visible(customBrackets :: get).build());
    public final Setting<String> rightBracket = sgGeneral.add(new StringSetting.Builder().name("right-bracket").description("").defaultValue("]").visible(customBrackets :: get).build());

    RainbowColor prefixChroma = new RainbowColor();

    public ChatTweaks() {
        super(Lightswitch.CATEGORY, "chat-tweaks", "Various chat improvements.");
    }


    @EventHandler
    private void onMessageSend(SendMessageEvent event) {
        String message = event.message;
        if (emotes.get()) message = Emotes.apply(message);
        event.message = message;
    }

    @Override
    public void onActivate() {
        ChatUtils.registerCustomPrefix("me.ghosttypes.orion", this::getPrefix);
    }


    public LiteralText getPrefix() {
        BaseText logo = new LiteralText("");
        LiteralText prefix = new LiteralText("");
        String logoT = "Orion";
        if (customPrefix.get()) logoT = prefixText.get();
        if (customPrefixColor.get() && !chromaPrefix.get()) logo.append(new LiteralText(logoT).setStyle(logo.getStyle().withColor(TextColor.fromRgb(prefixColor.get().getPacked()))));
        if (chromaPrefix.get() && !customPrefixColor.get()) {
            prefixChroma.setSpeed(chromaSpeed.get() / 100);
            for(int i = 0, n = logoT.length() ; i < n ; i++) logo.append(new LiteralText(String.valueOf(logoT.charAt(i)))).setStyle(logo.getStyle().withColor(TextColor.fromRgb(prefixChroma.getNext().getPacked())));
        }
        if (!customPrefixColor.get() && !chromaPrefix.get()) {
            if (customPrefix.get()) { logo.append(prefixText.get());
            } else { logo.append("Orion"); }
            logo.setStyle(logo.getStyle().withFormatting(Formatting.RED));
        }
        if (themeBrackets.get()) {
            if (customPrefixColor.get() && !chromaPrefix.get()) prefix.setStyle(prefix.getStyle().withColor(TextColor.fromRgb(prefixColor.get().getPacked())));
            if (chromaPrefix.get() && !customPrefixColor.get()) {
                prefixChroma.setSpeed(chromaSpeed.get() / 100);
                prefix.setStyle(prefix.getStyle().withColor(TextColor.fromRgb(prefixChroma.getNext().getPacked())));
            }
            if (customBrackets.get()) {
                prefix.append(leftBracket.get());
                prefix.append(logo);
                prefix.append(rightBracket.get() + " ");
            } else {
                prefix.append("[");
                prefix.append(logo);
                prefix.append("] ");
            }
        } else {
            prefix.setStyle(prefix.getStyle().withFormatting(Formatting.GRAY));
            prefix.append("[");
            prefix.append(logo);
            prefix.append("] ");
        }
        return prefix;
    }
}
