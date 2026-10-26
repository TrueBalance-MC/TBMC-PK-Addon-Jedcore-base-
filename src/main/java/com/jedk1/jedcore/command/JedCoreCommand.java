package com.jedk1.jedcore.command;

import com.jedk1.jedcore.JedCore;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.projectkorra.projectkorra.command.PKCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JedCoreCommand extends PKCommand {
	private static final String DOWNLOAD_URL = "https://github.com/CozmycDev/JedCore";

	public JedCoreCommand() {
		super("jedcore", "/bending jedcore", "This command will show the statistics and version of JedCore.", new String[] { "jedcore", "jc" });
	}

	@Override
	protected void buildArguments(LiteralArgumentBuilder<CommandSourceStack> node) {
		// /bending jedcore [refresh]
		node.then(word("action"));
	}

	@Override
	protected List<String> getTabCompletion(CommandSender sender, List<String> args) {
		List<String> completions = new ArrayList<>();
		if (args.isEmpty() && canUseDebug(sender)) {
			completions.add("refresh");
		}
		return completions;
	}

	@Override
	public boolean canUse(CommandSender sender) {
		return super.canUse(sender) || isSenderJedCoreDev(sender);
	}

	@Override
	public void execute(CommandSender sender, List<String> args) {
		if (!correctLength(sender, args.size(), 0, 1) || (!hasPermission(sender) && !isSenderJedCoreDev(sender))) {
			return;
		}
		if (args.size() == 0) {
			sendBuildInfo(sender);
		} else if (args.size() == 1 && (hasPermission(sender, "debug") || isSenderJedCoreDev(sender))) {
			//Dev commands for debugging etc.
			if (args.get(0).equalsIgnoreCase("refresh")) {
				sender.sendMessage(Component.text("Jedcore refreshed.", NamedTextColor.AQUA));
			}
		} else {
			help(sender, false);
		}
	}

	public static void sendBuildInfo(CommandSender sender) {
		sender.sendMessage(line("Running JedCore Build: ", Component.text(JedCore.plugin.getPluginMeta().getVersion())));
		sender.sendMessage(line("Developed by: ", Component.text(String.join(", ", JedCore.plugin.getPluginMeta().getAuthors()))));
		sender.sendMessage(line("Modified by: ", Component.text("plushmonkey")));
		sender.sendMessage(line("Maintained by: ", Component.text("Cozmyc")));
		sender.sendMessage(line("URL: ", Component.text(DOWNLOAD_URL)
				.decorate(TextDecoration.ITALIC)
				.clickEvent(ClickEvent.openUrl(DOWNLOAD_URL))));
	}

	private static Component line(String label, Component value) {
		return Component.text(label, NamedTextColor.GRAY).append(value.colorIfAbsent(NamedTextColor.RED));
	}

	private boolean canUseDebug(CommandSender sender) {
		return sender.hasPermission("bending.command." + getName() + ".debug") || isSenderJedCoreDev(sender);
	}

	private boolean isSenderJedCoreDev(CommandSender sender) {
		UUID[] devs = {
				UUID.fromString("4eb6315e-9dd1-49f7-b582-c1170e497ab0"),
				UUID.fromString("d57565a5-e6b0-44e3-a026-979d5de10c4d"),
				UUID.fromString("e98a2f7d-d571-4900-a625-483cbe6774fe")
		};
		if (sender instanceof Player player) {
			return Arrays.asList(devs).contains(player.getUniqueId());
		}
		return false;
	}
}
