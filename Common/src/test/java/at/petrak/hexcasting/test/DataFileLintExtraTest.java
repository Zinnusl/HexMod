package at.petrak.hexcasting.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extra static lint beyond {@link DataFileLintTest}. These rules cover shapes
 * that parse as valid JSON but wouldn't make it past Minecraft / Patchouli's
 * codec layer — silent renderer failures, missing codec fields, legacy result
 * shapes, and writer-level bugs like Windows path separators leaking into the
 * generated JSON. Every rule walks the same root set as the sibling class.
 * <p>
 * Pure filesystem walk — no MC bootstrap needed, but {@link TestBootstrap#init}
 * is called for consistency with the rest of the suite.
 */
public final class DataFileLintExtraTest {
    /** Same root set as {@link DataFileLintTest}. */
    private static final List<Path> ROOTS = List.of(
        Path.of("src/main/resources"),
        Path.of("../Fabric/src/generated/resources"),
        Path.of("../Neoforge/src/generated/resources"),
        Path.of("../Neoforge/src/main/resources")
    );

    /**
     * Loot tables that deliberately ship with an empty {@code pools} array —
     * hex's "random cypher" / "random scroll" tables are placeholders used as
     * injection targets and are meant to be empty at publish time.
     */
    private static final Set<String> ALLOWED_EMPTY_POOLS = Set.of(
        "random_cypher.json",
        "random_scroll.json"
    );

    @BeforeAll
    public static void bootstrap() {
        // Harmless consistency with the rest of the suite; this test doesn't
        // actually need MC registries.
        TestBootstrap.init();
    }

    /**
     * Every Patchouli book entry under {@code thehexbook/en_us/entries/} must
     * carry {@code icon}, {@code category}, and have a {@code type} on every
     * element of {@code pages}. Missing fields render as blank pages / entries
     * with no warning.
     */
    @Test
    public void patchouliEntriesHaveIconCategoryAndPageTypes() throws IOException {
        var hits = new ArrayList<String>();
        for (var root : ROOTS) {
            Path entriesRoot = root.resolve(
                "assets/hexcasting/patchouli_books/thehexbook/en_us/entries");
            if (!Files.isDirectory(entriesRoot)) continue;
            try (Stream<Path> files = Files.walk(entriesRoot)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> checkPatchouliEntry(p, hits));
            }
        }
        assertTrue(hits.isEmpty(),
            "Patchouli book entries missing icon/category or pages[*].type:\n  "
                + String.join("\n  ", hits));
    }

    private static void checkPatchouliEntry(Path p, List<String> hits) {
        JsonObject obj = readObject(p, hits);
        if (obj == null) return;
        if (!obj.has("icon"))
            hits.add(p + " :: missing \"icon\"");
        if (!obj.has("category"))
            hits.add(p + " :: missing \"category\"");
        if (obj.has("pages") && obj.get("pages").isJsonArray()) {
            JsonArray pages = obj.getAsJsonArray("pages");
            for (int i = 0; i < pages.size(); i++) {
                JsonElement page = pages.get(i);
                if (!page.isJsonObject()) continue;
                if (!page.getAsJsonObject().has("type"))
                    hits.add(p + " :: pages[" + i + "] missing \"type\"");
            }
        }
    }

    /**
     * Brainsweep recipe codec (see {@link at.petrak.hexcasting.common.recipe.BrainsweepRecipe})
     * requires all four fields: {@code blockIn}, {@code entityIn}, {@code cost}, {@code result}.
     * Missing any one makes {@code MapCodec.decode} fail hard on datapack reload.
     * <p>
     * Note: hex's on-disk directory is {@code recipe/brainsweep/} (singular, no -ing suffix).
     */
    @Test
    public void brainsweepRecipesHaveAllRequiredFields() throws IOException {
        var hits = new ArrayList<String>();
        String[] requiredFields = {"blockIn", "entityIn", "cost", "result"};
        for (var root : ROOTS) {
            // Hex ships recipes under data/hexcasting/recipe/brainsweep/...
            // We look at all recipe JSONs that declare hexcasting:brainsweep as their type,
            // rather than hardcoding the subdirectory, in case datagen output changes.
            Path recipeRoot = root.resolve("data/hexcasting/recipe");
            if (!Files.isDirectory(recipeRoot)) continue;
            try (Stream<Path> files = Files.walk(recipeRoot)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        JsonObject obj = readObject(p, hits);
                        if (obj == null) return;
                        if (!obj.has("type")) return;
                        String type = obj.get("type").isJsonPrimitive()
                            ? obj.get("type").getAsString() : "";
                        if (!"hexcasting:brainsweep".equals(type)) return;
                        for (var field : requiredFields) {
                            if (!obj.has(field))
                                hits.add(p + " :: brainsweep missing \"" + field + "\"");
                        }
                    });
            }
        }
        assertTrue(hits.isEmpty(),
            "Brainsweep recipe(s) missing codec-required fields:\n  "
                + String.join("\n  ", hits));
    }

    /**
     * 1.21 recipe results point at items via {@code id}; an empty / null id
     * crashes the recipe loader with "Unknown item" well before the book page
     * would even render.
     */
    private static final Pattern EMPTY_OR_NULL_ID = Pattern.compile(
        "\"id\"\\s*:\\s*(\"\\s*\"|null)"
    );

    @Test
    public void noEmptyOrNullRecipeIds() throws IOException {
        var hits = new ArrayList<String>();
        for (var root : ROOTS) {
            Path recipeRoot = root.resolve("data/hexcasting/recipe");
            if (!Files.isDirectory(recipeRoot)) continue;
            try (Stream<Path> files = Files.walk(recipeRoot)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        String s;
                        try { s = Files.readString(p, StandardCharsets.UTF_8); }
                        catch (IOException e) { return; }
                        Matcher m = EMPTY_OR_NULL_ID.matcher(s);
                        if (m.find()) hits.add(p + " :: " + m.group());
                    });
            }
        }
        assertTrue(hits.isEmpty(),
            "Recipe result(s) with empty or null \"id\":\n  "
                + String.join("\n  ", hits));
    }

    /**
     * In shaped / shapeless crafting, every ingredient object must carry at
     * least one of {@code item}, {@code tag}, or {@code type}. A bare empty
     * object would make {@code Ingredient.CODEC} fail. Arrays-of-ingredients
     * (shapeless OR) are walked element-wise.
     */
    @Test
    public void craftingIngredientsHaveItemOrTagOrType() throws IOException {
        var hits = new ArrayList<String>();
        for (var root : ROOTS) {
            Path recipeRoot = root.resolve("data/hexcasting/recipe");
            if (!Files.isDirectory(recipeRoot)) continue;
            try (Stream<Path> files = Files.walk(recipeRoot)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> checkRecipeIngredients(p, hits));
            }
        }
        assertTrue(hits.isEmpty(),
            "Crafting recipe ingredient(s) missing item/tag/type:\n  "
                + String.join("\n  ", hits));
    }

    private static void checkRecipeIngredients(Path p, List<String> hits) {
        JsonObject obj = readObject(p, hits);
        if (obj == null) return;
        if (!obj.has("type")) return;
        String type = obj.get("type").isJsonPrimitive()
            ? obj.get("type").getAsString() : "";
        if (!"minecraft:crafting_shaped".equals(type)
            && !"minecraft:crafting_shapeless".equals(type)) return;

        // shaped: object of key → ingredient(s)
        if (obj.has("key") && obj.get("key").isJsonObject()) {
            for (var e : obj.getAsJsonObject("key").entrySet()) {
                checkIngredientElement(p, "key[\"" + e.getKey() + "\"]", e.getValue(), hits);
            }
        }
        // shapeless: array of ingredient(s)
        if (obj.has("ingredients") && obj.get("ingredients").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("ingredients");
            for (int i = 0; i < arr.size(); i++) {
                checkIngredientElement(p, "ingredients[" + i + "]", arr.get(i), hits);
            }
        }
    }

    /**
     * One ingredient slot can be either an object (single ingredient) or an
     * array of ingredient objects (OR). Walk both shapes.
     */
    private static void checkIngredientElement(
        Path p, String location, JsonElement el, List<String> hits) {
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                checkIngredientElement(p, location + "[" + i + "]", arr.get(i), hits);
            }
            return;
        }
        if (!el.isJsonObject()) {
            hits.add(p + " :: " + location + " is not an object or array");
            return;
        }
        JsonObject io = el.getAsJsonObject();
        if (!io.has("item") && !io.has("tag") && !io.has("type")) {
            hits.add(p + " :: " + location + " has none of item/tag/type");
        }
    }

    /**
     * Loot tables shouldn't ship with an empty {@code pools} array unless the
     * file is deliberately a placeholder. An empty pools makes the loot table
     * silently roll nothing — a mistake in most cases. Hex's
     * {@code random_cypher} / {@code random_scroll} are the known-good
     * exceptions (see {@link #ALLOWED_EMPTY_POOLS}).
     */
    @Test
    public void lootTablePoolsAreNonEmpty() throws IOException {
        var hits = new ArrayList<String>();
        for (var root : ROOTS) {
            Path lootRoot = root.resolve("data/hexcasting/loot_table");
            if (!Files.isDirectory(lootRoot)) continue;
            try (Stream<Path> files = Files.walk(lootRoot)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> !ALLOWED_EMPTY_POOLS.contains(p.getFileName().toString()))
                    .forEach(p -> {
                        JsonObject obj = readObject(p, hits);
                        if (obj == null) return;
                        if (!obj.has("pools")) return;
                        JsonElement pools = obj.get("pools");
                        if (!pools.isJsonArray()) {
                            hits.add(p + " :: \"pools\" is not an array");
                            return;
                        }
                        if (pools.getAsJsonArray().isEmpty()) {
                            hits.add(p + " :: \"pools\" is empty");
                        }
                    });
            }
        }
        assertTrue(hits.isEmpty(),
            "Loot table(s) with non-array / empty \"pools\":\n  "
                + String.join("\n  ", hits));
    }

    /**
     * A literal backslash in a JSON string means the writer leaked a Windows
     * path separator. JSON-escaped control characters (\n \t \" \\) are fine;
     * the bug shape is a backslash followed by alphanumeric or another
     * backslash, which is how {@code File.toString()} paths serialize on win32.
     * <p>
     * Scope limited to data & asset JSON values (not code files, which aren't
     * data).
     */
    private static final Pattern WINDOWS_PATH_IN_STRING = Pattern.compile(
        "\"[^\"\\\\]*\\\\\\\\[^\"]*\""
    );

    @Test
    public void noWindowsPathSeparatorsInJsonStrings() throws IOException {
        var hits = new ArrayList<String>();
        for (var root : ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        String s;
                        try { s = Files.readString(p, StandardCharsets.UTF_8); }
                        catch (IOException e) { return; }
                        Matcher m = WINDOWS_PATH_IN_STRING.matcher(s);
                        if (m.find()) hits.add(p + " :: " + m.group());
                    });
            }
        }
        assertTrue(hits.isEmpty(),
            "JSON string value contains \\\\ — writer leaked a Windows path separator:\n  "
                + String.join("\n  ", hits));
    }

    // ---- helpers ---------------------------------------------------------------

    /** Read a JSON file as an object; return null and record a parse error on failure. */
    private static JsonObject readObject(Path p, List<String> hits) {
        try {
            String s = Files.readString(p, StandardCharsets.UTF_8);
            JsonElement el = JsonParser.parseString(s);
            if (!el.isJsonObject()) {
                hits.add(p + " :: root is not a JSON object");
                return null;
            }
            return el.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            hits.add(p + " :: parse error: " + e.getMessage());
            return null;
        }
    }
}
