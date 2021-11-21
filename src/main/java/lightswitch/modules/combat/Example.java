package lightswitch.modules.combat;

import lightswitch.Lightswitch;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class Example extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder().name("enabled").description("Exmaple boolean setting").defaultValue(false).build());

    public Example() {
        super(Lightswitch.CATEGORY, "example", "Example module");
    }

    @Override
    public void onActivate() {
        warning("This module is useless.");
    }

}
