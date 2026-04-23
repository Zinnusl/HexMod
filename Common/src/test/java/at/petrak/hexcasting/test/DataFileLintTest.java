package at.petrak.hexcasting.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static lint against the anti-patterns each previous runtime crash taught us.
 * Every rule here was added after a specific bug hit; the test fails if any of
 * those patterns creep back in. Runs fast (no MC bootstrap, no registry access),
 * so it's the first line of defence — cheaper than waiting for a smoketest boot
 * to surface the same mistake as a parse error.
 */
public final class DataFileLintTest {
    /** Roots that hex ships as data/assets — the files MC / Patchouli actually load. */
    private static final List<Path> ROOTS = List.of(
        Path.of("src/main/resources"),
        Path.of("../Fabric/src/generated/resources"),
        Path.of("../Neoforge/src/generated/resources"),
        Path.of("../Neoforge/src/main/resources")
    );

    /**
     * 1.20 SNBT `{Key:value}` syntax attached to item IDs. 1.21's ItemParser only
     * accepts `[component=value]` bracket form; a legacy curly-brace shim breaks
     * parsing entirely — see the `Invalid icon item stack: Expected '#' at position
     * 16: …aft:potion` crash the potions book page hit.
     * <p>
     * We match any item-id followed by `{` as the next non-whitespace token. False
     * positives on legitimate NBT snippets unrelated to item parsing are unlikely
     * because hex's data dir doesn't ship raw NBT files.
     */
    private static final Pattern LEGACY_NBT_ON_ITEM = Pattern.compile(
        "\"[a-z0-9_]+:[a-z0-9/_]+\\{"
    );

    /** 1.20 BlockEntityTag NBT path. 1.21 moved to DataComponents.BLOCK_ENTITY_DATA. */
    private static final Pattern BLOCK_ENTITY_TAG = Pattern.compile("BlockEntityTag");

    /** `forge:` tag prefix — 1.21 common tags live under `c:`. */
    private static final Pattern FORGE_TAG = Pattern.compile("\"#?forge:");

    /** Renamed / removed loot functions. */
    private static final Pattern REMOVED_LOOT_FUNCS = Pattern.compile(
        "\"(minecraft:copy_nbt|minecraft:set_nbt|minecraft:set_lore)\""
    );

    /**
     * Recipe result shape. 1.21 crafting_shaped/shapeless results use {id: …};
     * the old {item: …} shape is gone. Match `"result"` objects containing "item".
     * Tolerates `result` used as an output collection in modded crafting types
     * (FarmersDelight, Create) — those legitimately use `{item, count}`.
     */
    private static final Pattern RESULT_WITH_ITEM_KEY = Pattern.compile(
        "\"result\"\\s*:\\s*\\{\\s*\"item\""
    );

    @Test
    public void noLegacyNBTSyntaxOnItems() throws IOException {
        // Patchouli book icons are the main victims. Grep only under assets/hexcasting/.
        var hits = scan(new String[]{"assets/hexcasting/patchouli_books"}, LEGACY_NBT_ON_ITEM);
        assertTrue(hits.isEmpty(),
            "Found legacy 1.20 `{Key:value}` NBT syntax on item IDs (should be `[component=value]`):\n  "
                + String.join("\n  ", hits));
    }

    @Test
    public void noBlockEntityTagReferences() throws IOException {
        // Allow the one block-entity loot function that explicitly references the
        // 1.21-style "pattern" path — grep the whole string for the literal.
        var hits = scan(new String[]{"data/hexcasting"}, BLOCK_ENTITY_TAG);
        assertTrue(hits.isEmpty(),
            "BlockEntityTag NBT path is 1.20-only; 1.21 uses DataComponents.BLOCK_ENTITY_DATA:\n  "
                + String.join("\n  ", hits));
    }

    @Test
    public void noForgeTagsInData() throws IOException {
        var hits = scan(new String[]{"data"}, FORGE_TAG);
        assertTrue(hits.isEmpty(),
            "`forge:` tag prefix — 1.21 common tags live under `c:`:\n  "
                + String.join("\n  ", hits));
    }

    @Test
    public void noRemovedLootFunctionsReferenced() throws IOException {
        var hits = scan(new String[]{"data/hexcasting/loot_table", "data/hexcasting/item_modifier"},
            REMOVED_LOOT_FUNCS);
        assertTrue(hits.isEmpty(),
            "Loot functions removed/renamed in 1.21 (use minecraft:copy_custom_data, set_components):\n  "
                + String.join("\n  ", hits));
    }

    @Test
    public void noLegacyItemKeyInResult() throws IOException {
        // Only crafting-type recipes — modded output codecs use {item,count} form.
        // Gate by recipe type string ahead of `result`.
        var hits = new ArrayList<String>();
        for (var root : ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> p.toString().contains("recipe") || p.toString().contains("recipes"))
                    .forEach(p -> {
                        String s;
                        try { s = Files.readString(p, StandardCharsets.UTF_8); }
                        catch (IOException e) { return; }
                        boolean isVanillaShapedOrShapeless =
                            s.contains("\"minecraft:crafting_shaped\"") ||
                            s.contains("\"minecraft:crafting_shapeless\"") ||
                            s.contains("\"minecraft:stonecutting\"") ||
                            s.contains("\"minecraft:smithing_transform\"");
                        if (!isVanillaShapedOrShapeless) return;
                        Matcher m = RESULT_WITH_ITEM_KEY.matcher(s);
                        if (m.find()) hits.add(p.toString());
                    });
            }
        }
        assertTrue(hits.isEmpty(),
            "Vanilla crafting/stonecutting recipe `result` still uses `{\"item\": …}` — 1.21 wants `{\"id\": …}`:\n  "
                + String.join("\n  ", hits));
    }

    // ---- helpers ---------------------------------------------------------------

    private static List<String> scan(String[] subdirs, Pattern pattern) throws IOException {
        var hits = new ArrayList<String>();
        for (var root : ROOTS) {
            for (var sub : subdirs) {
                Path dir = root.resolve(sub);
                if (!Files.isDirectory(dir)) continue;
                try (Stream<Path> files = Files.walk(dir)) {
                    files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .forEach(p -> {
                            String s;
                            try { s = Files.readString(p, StandardCharsets.UTF_8); }
                            catch (IOException e) { return; }
                            Matcher m = pattern.matcher(s);
                            if (m.find()) {
                                hits.add(p + " :: " + m.group());
                            }
                        });
                }
            }
        }
        return hits;
    }
}
