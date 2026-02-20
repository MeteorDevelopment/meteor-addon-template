package com.bananaddons;

import com.bananaddons.hud.BananaHud;
import com.bananaddons.modules.ArmorNotify;
import com.bananaddons.modules.AntiFeetPlace;
import com.bananaddons.modules.AutoPortal;
import com.bananaddons.modules.SurroundPlus;
import com.bananaddons.modules.NoJumpDelay;
import com.bananaddons.modules.Phase;
import com.bananaddons.modules.DiscordNotifs;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

    public class BananaAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Banana");
    public static final HudGroup HUD_GROUP = new HudGroup("Banana");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Banana Addon");

        // Modules
        Modules.get().add(new ArmorNotify());
        Modules.get().add(new AntiFeetPlace());
        Modules.get().add(new AutoPortal());
        Modules.get().add(new SurroundPlus());
        Modules.get().add(new NoJumpDelay());
        Modules.get().add(new Phase());
        Modules.get().add(new DiscordNotifs());

        // HUD
        Hud.get().register(BananaHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.bananaddons";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("StubbledBannana", "banana-addon");
    }
}
