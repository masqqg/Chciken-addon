package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;

/**
 * Chat, nametag ve tab listesinde seçilen gerçek isimleri sahte isimlerle değiştirir.
 * Sadece görseldir; sunucuya hiçbir şey gönderilmez.
 */
public class NameProtect extends Module {
    private static final String[] RANDOM_NAMES = {
        "Alex", "Steve", "Kai", "Mert", "Efe", "Arda", "Can", "Deniz", "Ege", "Kaan",
        "Luna", "Mira", "Nova", "Raven", "Sora", "Yuki", "Aslan", "Bora", "Cem", "Duru"
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> protectSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("protect-self")
        .description("Also replace your own name.")
        .defaultValue(true)
        .build());

    private final Setting<String> selfName = sgGeneral.add(new StringSetting.Builder()
        .name("self-name")
        .description("Fake name for yourself. Leave blank for a random name.")
        .defaultValue("You")
        .build());

    private final Setting<Boolean> randomReplacements = sgGeneral.add(new BoolSetting.Builder()
        .name("random-replacements")
        .description("Give every protected player a random fake name.")
        .defaultValue(true)
        .build());

    private final Setting<String> replacement = sgGeneral.add(new StringSetting.Builder()
        .name("replacement")
        .description("Fixed fake name when random-replacements is off.")
        .defaultValue("Player")
        .build());

    private final Setting<List<String>> targets = sgGeneral.add(new StringListSetting.Builder()
        .name("targets")
        .description("Real player names to protect (one entry per name).")
        .defaultValue()
        .build());

    private final Setting<Boolean> chat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat")
        .description("Replace names in received chat messages.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> nametags = sgGeneral.add(new BoolSetting.Builder()
        .name("nametags")
        .description("Replace names above player heads.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> tabList = sgGeneral.add(new BoolSetting.Builder()
        .name("tab-list")
        .description("Replace names in the player list.")
        .defaultValue(true)
        .build());

    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Random random = new Random();
    private String aliasSignature = "";

    public NameProtect() {
        super(AddonTemplate.CATEGORY, "name-protect", "Replaces selected player names in chat, nametags and tab list.");
    }

    @Override
    public void onActivate() {
        aliasSignature = "";
        ensureAliases();
    }

    @Override
    public void onDeactivate() {
        aliases.clear();
        aliasSignature = "";
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!chat.get() || event.getMessage() == null) return;

        ensureAliases();
        if (aliases.isEmpty() || !hasProtectedName(event.getMessage().getString())) return;

        event.setMessage(replaceComponent(event.getMessage()));
    }

    /** Nametag mixin'inden çağrılır; değişiklik yoksa null döner. */
    public static Component replaceDisplayName(Player player, Component original) {
        if (player == null || original == null) return null;

        NameProtect module = Modules.get().get(NameProtect.class);
        if (module == null || !module.isActive() || !module.nametags.get()) return null;

        module.ensureAliases();
        if (module.aliases.isEmpty() || !module.hasProtectedName(original.getString())) return null;

        return module.replaceComponent(original);
    }

    /** Tab list mixin'inden çağrılır; değişiklik yoksa null döner. */
    public static Component replaceTabName(PlayerInfo info, Component original) {
        if (info == null) return null;

        NameProtect module = Modules.get().get(NameProtect.class);
        if (module == null || !module.isActive() || !module.tabList.get()) return null;

        module.ensureAliases();
        String fake = module.aliases.get(info.getProfile().name());
        if (fake == null) return null;

        return Component.literal(fake).withStyle(original != null ? original.getStyle() : Style.EMPTY);
    }

    private void ensureAliases() {
        String self = mc.player != null ? mc.player.getName().getString() : "";
        String signature = protectSelf.get() + "|" + selfName.get() + "|" + replacement.get() + "|"
            + randomReplacements.get() + "|" + String.join(",", targets.get()) + "|" + self;

        if (signature.equals(aliasSignature)) return;
        aliasSignature = signature;
        aliases.clear();

        Set<String> used = new HashSet<>();

        if (protectSelf.get() && !self.isBlank()) {
            String fake = selfName.get() != null && !selfName.get().isBlank()
                ? selfName.get().trim()
                : randomAlias(used, self);
            aliases.put(self, fake);
            used.add(fake);
        }

        if (randomReplacements.get()) {
            for (String target : targets.get()) {
                String real = target == null ? "" : target.trim();
                if (real.isEmpty() || aliases.containsKey(real)) continue;
                aliases.put(real, randomAlias(used, real));
            }
        } else {
            String fake = replacement.get() == null || replacement.get().isBlank()
                ? "Player"
                : replacement.get().trim();

            for (String target : targets.get()) {
                String real = target == null ? "" : target.trim();
                if (!real.isEmpty() && !aliases.containsKey(real)) aliases.put(real, fake);
            }
        }
    }

    private String randomAlias(Set<String> used, String real) {
        for (int i = 0; i < 50; i++) {
            String candidate = RANDOM_NAMES[random.nextInt(RANDOM_NAMES.length)];
            if (!candidate.equalsIgnoreCase(real) && used.add(candidate)) return candidate;
        }

        String fallback = "Player" + (used.size() + 1);
        used.add(fallback);
        return fallback;
    }

    private boolean hasProtectedName(String text) {
        for (String real : aliases.keySet()) {
            if (text.contains(real)) return true;
        }
        return false;
    }

    private Component replaceComponent(Component component) {
        MutableComponent out = Component.empty();

        component.visit((style, text) -> {
            out.append(Component.literal(replaceText(text)).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        return out;
    }

    private String replaceText(String text) {
        String out = text;

        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String real = entry.getKey();
            String fake = entry.getValue();
            out = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(real) + "(?![A-Za-z0-9_])")
                .matcher(out)
                .replaceAll(Matcher.quoteReplacement(fake));
        }

        return out;
    }
}
