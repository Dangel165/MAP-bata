package com.authplugin.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages localized messages loaded from language YAML files.
 * Falls back to built-in defaults when a key is missing.
 */
public class MessageManager {

    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, String> messages = new HashMap<>();
    private String language;

    public MessageManager(Plugin plugin, String language) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.language = language;
        load(language);
    }

    /** Reload messages for the given language. */
    public void reload(String language) {
        this.language = language;
        messages.clear();
        load(language);
    }

    /**
     * Get a message by key, replacing {placeholders} with provided values.
     * @param key message key
     * @param replacements alternating placeholder/value pairs, e.g. "player","Steve"
     */
    public String get(String key, String... replacements) {
        String msg = messages.getOrDefault(key, defaults().getOrDefault(key, "§c[Auth] Missing message: " + key));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return msg;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void load(String lang) {
        // Try to load from plugin data folder first (user-customizable)
        File langFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
        if (!langFile.exists()) {
            saveDefaultLangFile(lang);
        }

        if (langFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(langFile), StandardCharsets.UTF_8)) {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(reader);
                for (String key : cfg.getKeys(true)) {
                    if (cfg.isString(key)) {
                        messages.put(key, cfg.getString(key).replace("&", "§"));
                    }
                }
                logger.info("[MessageManager] Loaded language: " + lang);
            } catch (IOException e) {
                logger.warning("[MessageManager] Failed to load lang file: " + e.getMessage());
            }
        }

        // Fill in any missing keys from built-in defaults
        defaults().forEach(messages::putIfAbsent);
    }

    private void saveDefaultLangFile(String lang) {
        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists()) dir.mkdirs();

        InputStream resource = plugin.getResource("lang/" + lang + ".yml");
        if (resource == null) return;

        File dest = new File(dir, lang + ".yml");
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = resource.read(buf)) > 0) out.write(buf, 0, len);
        } catch (IOException e) {
            logger.warning("[MessageManager] Could not save default lang file: " + e.getMessage());
        }
    }

    /** Built-in English defaults — used as fallback for any missing key. */
    private static Map<String, String> defaults() {
        Map<String, String> d = new HashMap<>();
        // Register
        d.put("register.success",          "§a회원가입 완료! 자동으로 로그인되었습니다.");
        d.put("register.already-registered","§c이미 등록된 계정입니다. /login 으로 로그인하세요.");
        d.put("register.password-too-short","§c비밀번호가 너무 짧습니다. 최소 {min}자 이상이어야 합니다.");
        d.put("register.password-too-long", "§c비밀번호가 너무 깁니다. 최대 {max}자 이하여야 합니다.");
        d.put("register.password-mismatch", "§c비밀번호가 일치하지 않습니다.");
        d.put("register.usage",             "§e사용법: /register <비밀번호> <비밀번호확인>");
        // Login
        d.put("login.success",             "§a로그인 성공! 환영합니다, {player}님.");
        d.put("login.already-logged-in",   "§c이미 로그인되어 있습니다.");
        d.put("login.not-registered",      "§c등록되지 않은 계정입니다. /register 로 회원가입하세요.");
        d.put("login.wrong-password",      "§c비밀번호가 틀렸습니다. 남은 시도: {attempts}회");
        d.put("login.account-locked",      "§c계정이 잠겼습니다. {minutes}분 후 다시 시도하세요.");
        d.put("login.ip-blocked",          "§cIP가 일시적으로 차단되었습니다. {seconds}초 후 다시 시도하세요.");
        d.put("login.usage",               "§e사용법: /login <비밀번호>");
        // Logout
        d.put("logout.success",            "§a로그아웃되었습니다.");
        d.put("logout.not-logged-in",      "§c로그인 상태가 아닙니다.");
        // Auth
        d.put("auth.not-authenticated",    "§c인증이 필요합니다. /login 또는 /register 를 사용하세요.");
        d.put("auth.timeout",              "§c인증 시간이 초과되었습니다. 다시 접속해주세요.");
        d.put("auth.timeout-warning",      "§e인증까지 {seconds}초 남았습니다!");
        // Admin
        d.put("admin.no-permission",       "§c권한이 없습니다.");
        d.put("admin.player-not-found",    "§c플레이어를 찾을 수 없습니다: {player}");
        d.put("admin.reload-success",      "§a설정이 성공적으로 다시 로드되었습니다.");
        d.put("admin.unregister-success",  "§a{player}의 계정이 삭제되었습니다.");
        d.put("admin.changepass-success",  "§a{player}의 비밀번호가 변경되었습니다.");
        d.put("admin.forcelogin-success",  "§a{player}를 강제 로그인했습니다.");
        return d;
    }
}
