package pt.joaoveiga.aroresources.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import pt.joaoveiga.aroresources.AroResources;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResourcePackListener implements Listener {

    private final AroResources plugin;
    private final Set<UUID> retryScheduled = ConcurrentHashMap.newKeySet();

    public ResourcePackListener(AroResources plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("resource-pack.apply-on-join", true)) {
            return;
        }
        sendJoinFeedback(event.getPlayer());
        long delayTicks = plugin.getResourcePackManager().shouldPreloadBeforeJoin()
                ? 0L
                : Math.max(0L, plugin.getConfig().getLong("resource-pack.join-delay-ticks", 20L));
        debug("Agendado envio do pack para " + event.getPlayer().getName() + " em " + delayTicks + " ticks.");
        if (delayTicks <= 0L) {
            if (event.getPlayer() != null && event.getPlayer().isOnline()) {
                log("A aplicar pack imediatamente ao jogador " + event.getPlayer().getName() + ".");
                plugin.getResourcePackManager().applyTo(event.getPlayer());
            }
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (event.getPlayer() != null && event.getPlayer().isOnline()) {
                    log("A aplicar pack ao jogador " + event.getPlayer().getName() + " depois do delay de join.");
                    plugin.getResourcePackManager().applyTo(event.getPlayer());
                }
            }
        }, delayTicks);
    }

    private void sendJoinFeedback(org.bukkit.entity.Player player) {
        if (player == null || !plugin.getConfig().getBoolean("resource-pack.join-feedback.enabled", true)) {
            return;
        }

        String rawMessage = plugin.getConfig().getString(
                "resource-pack.join-feedback.message",
                "&eA carregar a textura oficial..."
        );
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(rawMessage);
        try {
            player.sendActionBar(component);
        } catch (Throwable ignored) {
            // Keep the pack request working even if the server build lacks action bar support.
        }
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        switch (event.getStatus()) {
            case FAILED_DOWNLOAD:
            case FAILED_RELOAD:
                plugin.getLogger().warning("Jogador " + event.getPlayer().getName() + " falhou ao transferir/carregar o pack: " + event.getStatus());
                scheduleRetry(event.getPlayer().getUniqueId());
                break;
            case DECLINED:
                log("Jogador " + event.getPlayer().getName() + " recusou o resource pack.");
                retryScheduled.remove(event.getPlayer().getUniqueId());
                break;
            case DISCARDED:
                log("Jogador " + event.getPlayer().getName() + " descartou o resource pack.");
                retryScheduled.remove(event.getPlayer().getUniqueId());
                break;
            case SUCCESSFULLY_LOADED:
                log("Jogador " + event.getPlayer().getName() + " carregou o resource pack com sucesso.");
                retryScheduled.remove(event.getPlayer().getUniqueId());
                break;
            case ACCEPTED:
                log("Jogador " + event.getPlayer().getName() + " aceitou o resource pack.");
                retryScheduled.remove(event.getPlayer().getUniqueId());
                break;
            default:
                log("Estado do resource pack para " + event.getPlayer().getName() + ": " + event.getStatus());
                break;
        }
    }

    private void scheduleRetry(UUID uuid) {
        if (uuid == null || !retryScheduled.add(uuid)) {
            debug("Retry do resource pack ignorado para uuid=" + uuid + " porque já estava agendado.");
            return;
        }
        long retryDelayTicks = Math.max(20L, plugin.getConfig().getLong("resource-pack.retry-delay-ticks", 80L));
        debug("Retry do resource pack agendado para uuid=" + uuid + " em " + retryDelayTicks + " ticks.");
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    if (plugin.getServer().getPlayer(uuid) != null && plugin.getServer().getPlayer(uuid).isOnline()) {
                        debug("A reenviar pack para uuid=" + uuid + ".");
                        plugin.getResourcePackManager().applyTo(plugin.getServer().getPlayer(uuid));
                    } else {
                        debug("Retry cancelado porque o jogador uuid=" + uuid + " já não está online.");
                    }
                } finally {
                    retryScheduled.remove(uuid);
                }
            }
        }, retryDelayTicks);
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("resource-pack.debug", false)) {
            plugin.getLogger().info("[ResourcePackDebug] " + message);
        }
    }

    private void log(String message) {
        if (plugin.getResourcePackManager().shouldLogStatuses()) {
            plugin.getLogger().info("[ResourcePack] " + message);
        } else {
            debug(message);
        }
    }
}
