package pt.joaoveiga.aroresources.managers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import pt.joaoveiga.aroresources.AroResources;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResourcePackManager {

    private static final String DEFAULT_PACK_FILE = "UnchartedLand.zip";
    private static final String LEGACY_PACK_FILE = "resourcepack.zip";
    private static final String DEFAULT_GITHUB_PACK_URL = "https://raw.githubusercontent.com/Aromiic/unchartedland/main/UnchartedLand.zip";

    private final AroResources plugin;
    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    private File packFile;
    private String packUrl;
    private String packSha1;
    private String packBuildToken;
    private boolean localServerActive;

    public ResourcePackManager(AroResources plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() {
        stopHttpServer();
        this.packUrl = "";
        this.packSha1 = "";
        this.packBuildToken = "";
        this.localServerActive = false;
        this.packFile = null;

        debug("Inicializar resource pack. local-server.enabled=" + plugin.getConfig().getBoolean("resource-pack.local-server.enabled", true)
                + ", url-configurada=" + hasConfiguredUrl());

        File currentPackFile = ensurePackFile();
        if (!currentPackFile.exists()) {
            plugin.getLogger().warning("Resource pack embutido nao encontrado em " + currentPackFile.getAbsolutePath());
            return;
        }

        this.packFile = currentPackFile;
        this.packSha1 = computeSha1(currentPackFile);
        this.packBuildToken = Long.toHexString(System.currentTimeMillis());
        plugin.getLogger().info("Resource pack pronto: ficheiro=" + currentPackFile.getAbsolutePath()
                + ", tamanho=" + currentPackFile.length() + " bytes"
                + ", sha1=" + safeSha1(this.packSha1));

        boolean localServerEnabled = plugin.getConfig().getBoolean("resource-pack.local-server.enabled", false);
        String source = plugin.getConfig().getString("resource-pack.source", "GITHUB");
        String configuredUrl = normalizeUrl(plugin.getConfig().getString("resource-pack.url", ""));
        String configuredPublicUrl = normalizeUrl(plugin.getConfig().getString("resource-pack.local-server.public-url", ""));
        String publicHost = resolvePublicHost();

        if (localServerEnabled || (source != null && source.trim().equalsIgnoreCase("LOCAL"))) {
            if (!configuredPublicUrl.isEmpty()) {
                this.packUrl = configuredPublicUrl;
                this.localServerActive = true;
                debug("A usar URL publica configurada para o servidor local: " + this.packUrl);
                return;
            }

            if (looksLocalOrUnreachable(publicHost)) {
                plugin.getLogger().warning("resource-pack.local-server parece estar apontado para um endereço local/inacessível (" + publicHost + "). Vou ignorar o servidor local e usar GitHub raw.");
            } else if (startHttpServer(currentPackFile)) {
                return;
            }
            plugin.getLogger().warning("O servidor local do resource pack falhou; a tentar fallback para URL externa.");
        }

        if (!configuredUrl.isEmpty()) {
            this.packUrl = configuredUrl;
            this.localServerActive = false;
            plugin.getLogger().info("Resource pack em modo externo/GitHub: source=" + getPackSource() + ", url-base=" + this.packUrl);
            return;
        }

        this.packUrl = DEFAULT_GITHUB_PACK_URL;
        this.localServerActive = false;
        if (source != null && source.trim().equalsIgnoreCase("LOCAL")) {
            plugin.getLogger().warning("resource-pack.source=LOCAL foi pedido, mas o servidor local nao arrancou. A usar GitHub raw como fallback.");
        }
        plugin.getLogger().info("Resource pack a usar GitHub raw por defeito: source=" + getPackSource() + ", url-base=" + this.packUrl);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("resource-pack.enabled", true);
    }

    public synchronized void reload() {
        initialize();
    }

    public synchronized void shutdown() {
        stopHttpServer();
    }

    public synchronized String getPackUrl() {
        String currentUrl = packUrl != null ? packUrl.trim() : "";
        if (currentUrl.isEmpty()) {
            currentUrl = normalizeUrl(plugin.getConfig().getString("resource-pack.url", ""));
        }
        if (currentUrl.isEmpty()) {
            return "";
        }
        return addCacheBuster(currentUrl, getPackSha1(), packBuildToken);
    }

    public synchronized String getPackUrlForDelivery() {
        String currentUrl = getPackUrl();
        if (currentUrl.isEmpty()) {
            return "";
        }
        return addDeliveryToken(currentUrl, UUID.randomUUID().toString().replace("-", ""));
    }

    public synchronized String getPackSha1() {
        String configuredSha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        if (configuredSha1 != null && !configuredSha1.trim().isEmpty()) {
            return configuredSha1.trim();
        }
        return packSha1 == null ? "" : packSha1.trim();
    }

    public String getPackSource() {
        String source = plugin.getConfig().getString("resource-pack.source", "GITHUB");
        if (source == null || source.trim().isEmpty()) {
            return "GITHUB";
        }
        return source.trim().toUpperCase(Locale.ROOT);
    }

    public synchronized boolean isLocalServerActive() {
        return localServerActive;
    }

    public boolean shouldLogStatuses() {
        return plugin.getConfig().getBoolean("resource-pack.log-statuses", true);
    }

    public boolean shouldPreloadBeforeJoin() {
        return plugin.getConfig().getBoolean("resource-pack.preload-before-join", false);
    }

    public void applyTo(Player player) {
        if (player == null || !isEnabled()) {
            return;
        }

        String url = getPackUrlForDelivery();
        if (url.isEmpty()) {
            plugin.getLogger().warning("Sem URL de resource pack disponivel para enviar a " + player.getName() + ".");
            return;
        }

        String configuredSha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        String effectiveSha1 = getPackSha1();
        byte[] hash = decodeSha1(effectiveSha1);
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", true);
        String prompt = plugin.getConfig().getString(
                "resource-pack.prompt",
                "&6Uncharted Land &7vai enviar-te o pack oficial."
        );

        plugin.getLogger().info("A enviar resource pack para " + player.getName()
                + " source=" + getPackSource()
                + " url=" + url
                + " sha1=" + safeSha1(effectiveSha1)
                + " required=" + required
                + " localServerActive=" + localServerActive);

        try {
            UUID packId = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
            player.setResourcePack(packId, url, hash, prompt, required);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Falha ao aplicar resource pack a " + player.getName() + ": " + throwable.getMessage());
        }
    }

    private synchronized boolean startHttpServer(File file) {
        String bindHost = plugin.getConfig().getString("resource-pack.local-server.bind-host", "0.0.0.0");
        int port = Math.max(1, plugin.getConfig().getInt("resource-pack.local-server.port", 8123));
        String contextPath = normalizePath(plugin.getConfig().getString("resource-pack.local-server.path", "/Uncharted%20Land.zip"));
        String publicScheme = normalizeScheme(plugin.getConfig().getString("resource-pack.local-server.public-scheme", "http"));
        String publicHost = resolvePublicHost();

        try {
            httpServer = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
            httpExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "AroResources-ResourcePack");
                thread.setDaemon(true);
                return thread;
            });
            httpServer.setExecutor(httpExecutor);
            httpServer.createContext(contextPath, exchange -> handlePackRequest(exchange, file));
            httpServer.start();
            this.packUrl = publicScheme + "://" + publicHost + ":" + port + contextPath;
            this.localServerActive = true;
            plugin.getLogger().info("Resource pack local disponivel em " + this.packUrl);
            plugin.getLogger().info("Resource pack a ser servido de " + file.getAbsolutePath());
            if (looksLocalOrUnreachable(publicHost)) {
                plugin.getLogger().warning("O host publico do resource pack parece local/invalido: " + publicHost
                        + ". Configura resource-pack.local-server.public-url ou resource-pack.local-server.public-host com o endereço publico real.");
            }
            debug("Servidor local de resource pack ativo em bind=" + bindHost + ":" + port + " path=" + contextPath + " publicHost=" + publicHost);
            return true;
        } catch (IOException | RuntimeException exception) {
            this.packUrl = "";
            this.localServerActive = false;
            plugin.getLogger().warning("Nao foi possivel iniciar o servidor local do resource pack: " + exception.getMessage());
            return false;
        }
    }

    private void handlePackRequest(HttpExchange exchange, File file) throws IOException {
        if (exchange == null || file == null || !file.exists()) {
            byte[] message = "Resource pack indisponivel".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, message.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(message);
            } finally {
                exchange.close();
            }
            return;
        }

        plugin.getLogger().info("Pedido HTTP " + exchange.getRequestMethod() + " para resource pack de " + exchange.getRemoteAddress());
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        long length = Math.max(0L, file.length());
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        exchange.sendResponseHeaders(200, length);
        try (FileInputStream inputStream = new FileInputStream(file);
             OutputStream outputStream = exchange.getResponseBody()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } finally {
            exchange.close();
        }
    }

    private synchronized void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
    }

    public synchronized File getPackFile() {
        return packFile;
    }

    private File ensurePackFile() {
        String fileName = plugin.getConfig().getString("resource-pack.local-server.file", DEFAULT_PACK_FILE);
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = DEFAULT_PACK_FILE;
        }
        fileName = normalizePackFileName(fileName);

        File target = new File(plugin.getDataFolder(), fileName.trim());
        boolean refreshedFromBundle = refreshBundledPack(target, fileName.trim());
        if (target.exists()) {
            if (refreshedFromBundle) {
                plugin.getLogger().info("Resource pack embutido atualizado em " + target.getAbsolutePath());
            }
            return target;
        }

        File legacyTarget = new File(plugin.getDataFolder(), LEGACY_PACK_FILE);
        if (legacyTarget.exists()) {
            try {
                java.nio.file.Files.copy(legacyTarget.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Resource pack antigo migrado para o novo nome: " + target.getName());
                return target;
            } catch (IOException exception) {
                plugin.getLogger().warning("Falha ao migrar o resource pack antigo para o novo nome: " + exception.getMessage());
                return legacyTarget;
            }
        }

        try {
            refreshBundledPack(target, fileName.trim());
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Resource pack embutido nao encontrado: " + fileName.trim());
        }
        return target;
    }

    private boolean refreshBundledPack(File target, String fileName) {
        if (target == null || fileName == null || fileName.trim().isEmpty()) {
            return false;
        }

        try (InputStream inputStream = plugin.getResource(fileName)) {
            if (inputStream == null) {
                return false;
            }
            java.nio.file.Files.copy(inputStream, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Falha ao atualizar o resource pack embutido " + fileName + ": " + exception.getMessage());
            return false;
        }
    }

    private String resolvePublicHost() {
        String configuredHost = plugin.getConfig().getString("resource-pack.local-server.public-host", "");
        if (configuredHost != null && !configuredHost.trim().isEmpty()) {
            return configuredHost.trim();
        }

        String serverIp = plugin.getServer().getIp();
        if (serverIp != null && !serverIp.trim().isEmpty() && !"0.0.0.0".equals(serverIp.trim())) {
            return serverIp.trim();
        }

        try {
            String localHost = InetAddress.getLocalHost().getHostAddress();
            if (localHost != null && !localHost.trim().isEmpty()) {
                return localHost.trim();
            }
        } catch (UnknownHostException ignored) {
        }

        return "localhost";
    }

    private boolean looksLocalOrUnreachable(String host) {
        if (host == null) {
            return true;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "localhost".equals(normalized) || "127.0.0.1".equals(normalized) || "0.0.0.0".equals(normalized)) {
            return true;
        }
        return normalized.startsWith("10.")
                || normalized.startsWith("192.168.")
                || normalized.startsWith("172.16.")
                || normalized.startsWith("172.17.")
                || normalized.startsWith("172.18.")
                || normalized.startsWith("172.19.")
                || normalized.startsWith("172.2")
                || normalized.startsWith("172.30.")
                || normalized.startsWith("172.31.");
    }

    private String normalizePath(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (trimmed.isEmpty()) {
            return "/UnchartedLand.zip";
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed.replace(" ", "%20");
    }

    private String normalizeScheme(String scheme) {
        String trimmed = scheme == null ? "" : scheme.trim().toLowerCase(Locale.ROOT);
        return "https".equals(trimmed) ? "https" : "http";
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.contains("github.com/") && trimmed.contains("/blob/")) {
            return trimmed.replace("https://github.com/", "https://raw.githubusercontent.com/")
                    .replace("http://github.com/", "https://raw.githubusercontent.com/")
                    .replace("/blob/", "/");
        }
        if (trimmed.contains("resourcepack.zip")) {
            return trimmed.replace("resourcepack.zip", "Uncharted%20Land.zip");
        }
        return trimmed;
    }

    private String normalizePackFileName(String fileName) {
        if (fileName == null) {
            return DEFAULT_PACK_FILE;
        }
        String trimmed = fileName.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_PACK_FILE;
        }
        if ("resourcepack.zip".equalsIgnoreCase(trimmed)) {
            return DEFAULT_PACK_FILE;
        }
        return trimmed;
    }

    private String addCacheBuster(String url, String sha1, String buildToken) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }

        String normalizedUrl = url.trim();
        String hashToken = sha1 == null ? "" : sha1.trim();
        String versionToken = buildToken == null ? "" : buildToken.trim();
        if (hashToken.isEmpty() && versionToken.isEmpty()) {
            return normalizedUrl;
        }

        String result = normalizedUrl;
        String separator = normalizedUrl.contains("?") ? "&" : "?";
        if (!hashToken.isEmpty() && !normalizedUrl.contains("v=" + hashToken)) {
            result += separator + "v=" + hashToken;
            separator = "&";
        }
        if (!versionToken.isEmpty() && !normalizedUrl.contains("build=" + versionToken)) {
            result += separator + "build=" + versionToken;
        }
        return result;
    }

    private String addDeliveryToken(String url, String deliveryToken) {
        if (url == null || url.trim().isEmpty() || deliveryToken == null || deliveryToken.trim().isEmpty()) {
            return url == null ? "" : url.trim();
        }

        String normalizedUrl = url.trim();
        if (normalizedUrl.contains("delivery=" + deliveryToken)) {
            return normalizedUrl;
        }

        String separator = normalizedUrl.contains("?") ? "&" : "?";
        return normalizedUrl + separator + "delivery=" + deliveryToken;
    }

    private boolean hasConfiguredUrl() {
        String configuredUrl = plugin.getConfig().getString("resource-pack.url", "");
        return configuredUrl != null && !configuredUrl.trim().isEmpty();
    }

    private String safeSha1(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "<vazio>";
        }
        return value.trim();
    }

    private byte[] decodeSha1(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() != 40) {
            plugin.getLogger().warning("resource-pack.sha1 configurado com tamanho invalido; a ignorar hash e usar descarga direta.");
            return null;
        }
        byte[] bytes = new byte[20];
        for (int i = 0; i < 20; i++) {
            int index = i * 2;
            int hi = Character.digit(trimmed.charAt(index), 16);
            int lo = Character.digit(trimmed.charAt(index + 1), 16);
            if (hi < 0 || lo < 0) {
                plugin.getLogger().warning("resource-pack.sha1 configurado com caracteres invalidos; a ignorar hash e usar descarga direta.");
                return null;
            }
            bytes[i] = (byte) ((hi << 4) + lo);
        }
        return bytes;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("resource-pack.debug", false)) {
            plugin.getLogger().info("[ResourcePackDebug] " + message);
        }
    }

    private String computeSha1(File file) {
        if (file == null || !file.exists()) {
            return "";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            try (FileInputStream inputStream = new FileInputStream(file)) {
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder builder = new StringBuilder();
            for (byte value : digest.digest()) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | IOException exception) {
            plugin.getLogger().warning("Nao foi possivel calcular o SHA-1 do resource pack: " + exception.getMessage());
            return "";
        }
    }
}
