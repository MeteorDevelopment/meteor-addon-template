package com.example.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

/**
 * The Meteor Client command API uses the <a href="https://github.com/Mojang/brigadier">same command system as Minecraft does</a>.
 */
public class CommandExample extends Command {
    /**
     * The {@code name} parameter should be in kebab-case.
     */
    public CommandExample() {
        super("example", "Sends a message.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            info("hi");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("name").then(argument("nameArgument", StringArgumentType.word()).executes(context -> {
            String argument = StringArgumentType.getString(context, "nameArgument");
            info("hi, " + argument);
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("player").then(argument("playerArgument", EntityArgumentType.player()).executes(context -> {
            PlayerEntity player = context.getArgument("playerArgument", PlayerEntity.class);
            String playerName = player.getGameProfile().getName();
            info("hi, " + playerName);
            return SINGLE_SUCCESS;
        })));
    }
}
