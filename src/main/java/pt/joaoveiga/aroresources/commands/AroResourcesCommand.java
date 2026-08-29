package pt.joaoveiga.aroresources.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pt.joaoveiga.aroresources.AroResources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.io.File;

public class AroResourcesCommand implements CommandExecutor, TabCompleter {

    private final AroResources plugin;

    public AroResourcesCommand(AroResources plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aresources.admin")) {
            sender.sendMessage("§cSem permissao.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§e/txtpack reload");
            sender.sendMessage("§e/txtpack send");
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            plugin.reloadAll();
            sender.sendMessage("§aAroResources recarregado.");
            return true;
        }

        if ("send".equalsIgnoreCase(args[0]) || "pack".equalsIgnoreCase(args[0])) {
            if (sender instanceof Player) {
                plugin.getResourcePackManager().applyTo((Player) sender);
                sender.sendMessage("§aResource pack enviado.");
                sendPackState(sender);
            } else {
                sender.sendMessage("§cApenas jogadores podem receber o pack.");
            }
            return true;
        }

        sender.sendMessage("§cSubcomando desconhecido.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("reload", "send", "pack"), args[0]);
        }
        return Collections.emptyList();
    }

    private void sendPackState(CommandSender sender) {
        File packFile = plugin.getResourcePackManager().getPackFile();
        sender.sendMessage("§7Ficheiro: §f" + (packFile == null ? "desconhecido" : packFile.getAbsolutePath()));
        sender.sendMessage("§7URL: §f" + safe(plugin.getResourcePackManager().getPackUrl()));
        sender.sendMessage("§7SHA-1: §f" + safe(plugin.getResourcePackManager().getPackSha1()));
        sender.sendMessage("§7Servidor local: §f" + (plugin.getResourcePackManager().isLocalServerActive() ? "ativo" : "desativado"));
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "<vazio>" : value.trim();
    }

    private List<String> filter(List<String> values, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return values;
        }
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(value);
            }
        }
        return result;
    }
}
