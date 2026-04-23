package at.petrak.hexcasting.test;

import at.petrak.hexcasting.common.lib.hex.HexActions;
import at.petrak.hexcasting.common.lib.hex.HexSpecialHandlers;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Patchouli-specific book lint. The page templates under
 * {@code assets/hexcasting/patchouli_books/thehexbook/en_us/templates/} pull variables
 * like {@code #op_id} out of every entry that uses them; any silent mismatch (missing
 * {@code op_id}, dangling action reference, missing processor class) ends up as a
 * blank book page or a runtime NPE — both of which slipped through port-1.21 before.
 * <p>
 * Rules covered:
 * <ol>
 *     <li>{@code hexcasting:pattern} and {@code hexcasting:manual_pattern} pages must
 *         have either {@code op_id} or {@code header} — those are the two branches
 *         {@link at.petrak.hexcasting.interop.patchouli.PatternProcessor#setup}
 *         accepts. Pages with neither render with a blank title.</li>
 *     <li>When {@code op_id} is present, it must exist in {@link HexActions}, in
 *         {@link HexSpecialHandlers}, or in {@link #KNOWN_VIRTUAL_OP_IDS} (ids that
 *         are deliberate display-only "virtual" headings — e.g. the shared heading
 *         used for {@code const/vec/x} when the backing actions are actually
 *         {@code const/vec/px}+{@code nx}).</li>
 *     <li>{@code hexcasting:crafting_multi} pages must carry {@code heading} and a
 *         non-empty {@code recipes} array.</li>
 *     <li>Any page {@code type} starting with {@code hexcasting:} must resolve to
 *         either a processor/component class under {@code interop.patchouli} OR a
 *         template JSON under {@code en_us/templates/}.</li>
 * </ol>
 */
public final class PatchouliLintTest {
    /** Only the main resources root actually ships patchouli books. */
    private static final Path BOOK_ROOT = Path.of(
        "src/main/resources/assets/hexcasting/patchouli_books/thehexbook/en_us");

    private static final Path ENTRIES_ROOT = BOOK_ROOT.resolve("entries");
    private static final Path TEMPLATES_ROOT = BOOK_ROOT.resolve("templates");

    /** Where the mod's Patchouli processor/component classes live on disk. */
    private static final Path PROCESSOR_ROOT = Path.of(
        "src/main/java/at/petrak/hexcasting/interop/patchouli");

    /** Page types that derive their heading from {@code op_id} via {@code PatternProcessor}. */
    private static final Set<String> PATTERN_LIKE_TYPES = Set.of(
        "hexcasting:pattern",
        "hexcasting:manual_pattern"
    );

    /**
     * {@code op_id}s that name a display-only translation key rather than a registered
     * action. {@link at.petrak.hexcasting.interop.patchouli.PatternProcessor} uses
     * {@code op_id} exclusively to build {@code hexcasting.action.<opId>}, so a book
     * page can legitimately carry an id that has no matching {@code ActionRegistryEntry}
     * as long as the lang key exists.
     * <p>
     * The const/vec {x,y,z} entries are the canonical case: the book page displays a
     * shared heading for the pair of positive/negative actions
     * ({@code const/vec/px}+{@code nx}). The heading text is picked up from the lang
     * file, and the ids below are the id-shapes the lang file uses.
     * <p>
     * Grow this set cautiously — every entry here is a case where the lint's registry
     * check is admitting a deliberate exception.
     */
    private static final Set<String> KNOWN_VIRTUAL_OP_IDS = Set.of(
        "hexcasting:const/vec/x",
        "hexcasting:const/vec/y",
        "hexcasting:const/vec/z",
        // "number" is a special-handler factory registered in HexSpecialHandlers;
        // we list it here too because the id shape differs slightly and it's easier
        // to document all SpecialHandler-backed op_ids in one place.
        "hexcasting:number"
    );

    @BeforeAll
    public static void bootstrap() {
        // We need HexActions populated to check op_id references.
        TestBootstrap.init();
    }

    /**
     * Every {@code hexcasting:pattern} / {@code hexcasting:manual_pattern} page must
     * provide either an {@code op_id} OR a {@code header}. When {@code op_id} is used,
     * it has to map to something real in the action / special-handler registries (or
     * an explicit allow-listed virtual id). A missing field renders a blank header —
     * the exact bug this rule was added to guard against.
     */
    @Test
    public void patternPagesHaveOpIdOrHeaderAndReferenceKnownAction() throws IOException {
        if (!Files.isDirectory(ENTRIES_ROOT)) return; // Not Common build — nothing to lint.

        Set<String> knownOpIds = new HashSet<>();
        HexActions.register((entry, loc) -> knownOpIds.add(loc.toString()));
        HexSpecialHandlers.register((factory, loc) -> knownOpIds.add(loc.toString()));
        knownOpIds.addAll(KNOWN_VIRTUAL_OP_IDS);
        assertTrue(!knownOpIds.isEmpty(),
            "No known op ids — test setup broke before we could lint");

        var hits = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(ENTRIES_ROOT)) {
            files.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .forEach(p -> {
                    JsonObject obj = readObject(p, hits);
                    if (obj == null || !obj.has("pages")
                        || !obj.get("pages").isJsonArray()) return;
                    // Skip entries gated by a {@code flag}; Patchouli only renders these
                    // when the named mod / advancement is present, and the matching
                    // {@code make()} in HexActions is then inside a conditional static
                    // block. In a headless test the stub XplatAbstractions says no mod
                    // is present, so these actions legitimately aren't registered.
                    if (obj.has("flag")) return;
                    JsonArray pages = obj.getAsJsonArray("pages");
                    for (int i = 0; i < pages.size(); i++) {
                        JsonElement page = pages.get(i);
                        if (!page.isJsonObject()) continue;
                        JsonObject pageObj = page.getAsJsonObject();
                        if (!pageObj.has("type")) continue;
                        String type = pageObj.get("type").isJsonPrimitive()
                            ? pageObj.get("type").getAsString() : "";
                        if (!PATTERN_LIKE_TYPES.contains(type)) continue;

                        boolean hasHeader = pageObj.has("header");
                        boolean hasOpId = pageObj.has("op_id");
                        if (!hasHeader && !hasOpId) {
                            hits.add(p + " :: pages[" + i + "] (" + type
                                + ") missing both \"op_id\" and \"header\"");
                            continue;
                        }
                        if (hasOpId) {
                            String opId = pageObj.get("op_id").isJsonPrimitive()
                                ? pageObj.get("op_id").getAsString() : "";
                            if (!knownOpIds.contains(opId)) {
                                hits.add(p + " :: pages[" + i + "] (" + type + ") op_id \""
                                    + opId + "\" is not registered in HexActions or "
                                    + "HexSpecialHandlers, and is not in the known-virtual "
                                    + "allowlist");
                            }
                        }
                    }
                });
        }
        assertTrue(hits.isEmpty(),
            "Pattern pages failing op_id / header rule:\n  "
                + String.join("\n  ", hits));
    }

    /**
     * {@code hexcasting:crafting_multi} wraps a list of recipes with a page heading.
     * The template binds {@code #heading} and iterates {@code #recipes}; both must be
     * present or the page renders as an empty crafting grid with no title.
     */
    @Test
    public void craftingMultiPagesHaveHeadingAndRecipes() throws IOException {
        if (!Files.isDirectory(ENTRIES_ROOT)) return;

        var hits = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(ENTRIES_ROOT)) {
            files.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .forEach(p -> {
                    JsonObject obj = readObject(p, hits);
                    if (obj == null || !obj.has("pages")
                        || !obj.get("pages").isJsonArray()) return;
                    JsonArray pages = obj.getAsJsonArray("pages");
                    for (int i = 0; i < pages.size(); i++) {
                        JsonElement page = pages.get(i);
                        if (!page.isJsonObject()) continue;
                        JsonObject pageObj = page.getAsJsonObject();
                        if (!pageObj.has("type")) continue;
                        String type = pageObj.get("type").isJsonPrimitive()
                            ? pageObj.get("type").getAsString() : "";
                        if (!"hexcasting:crafting_multi".equals(type)) continue;

                        if (!pageObj.has("heading")) {
                            hits.add(p + " :: pages[" + i + "] (crafting_multi) missing \"heading\"");
                        }
                        if (!pageObj.has("recipes") || !pageObj.get("recipes").isJsonArray()) {
                            hits.add(p + " :: pages[" + i + "] (crafting_multi) missing \"recipes\" array");
                        } else if (pageObj.getAsJsonArray("recipes").isEmpty()) {
                            hits.add(p + " :: pages[" + i + "] (crafting_multi) \"recipes\" is empty");
                        }
                    }
                });
        }
        assertTrue(hits.isEmpty(),
            "crafting_multi pages with missing heading / recipes:\n  "
                + String.join("\n  ", hits));
    }

    /**
     * Any page {@code type} starting with {@code hexcasting:} must resolve to either
     * a template file ({@code en_us/templates/<name>.json}) or a processor/component
     * class under {@code interop.patchouli}. A mismatched type crashes Patchouli at
     * book load with "unknown page type".
     */
    @Test
    public void hexcastingPageTypesHaveProcessorOrTemplate() throws IOException {
        if (!Files.isDirectory(ENTRIES_ROOT)) return;

        Set<String> availableTemplates = new HashSet<>();
        if (Files.isDirectory(TEMPLATES_ROOT)) {
            try (Stream<Path> templateFiles = Files.list(TEMPLATES_ROOT)) {
                templateFiles
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        String fn = p.getFileName().toString();
                        availableTemplates.add(fn.substring(0, fn.length() - ".json".length()));
                    });
            }
        }

        Set<String> availableProcessors = new HashSet<>();
        if (Files.isDirectory(PROCESSOR_ROOT)) {
            try (Stream<Path> srcFiles = Files.list(PROCESSOR_ROOT)) {
                srcFiles
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> {
                        String fn = p.getFileName().toString();
                        availableProcessors.add(fn.substring(0, fn.length() - ".java".length()));
                    });
            }
        }

        Set<String> seenTypes = new HashSet<>();
        var hits = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(ENTRIES_ROOT)) {
            files.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .forEach(p -> {
                    JsonObject obj = readObject(p, hits);
                    if (obj == null || !obj.has("pages")
                        || !obj.get("pages").isJsonArray()) return;
                    JsonArray pages = obj.getAsJsonArray("pages");
                    for (JsonElement page : pages) {
                        if (!page.isJsonObject()) continue;
                        JsonObject pageObj = page.getAsJsonObject();
                        if (!pageObj.has("type")) continue;
                        String type = pageObj.get("type").isJsonPrimitive()
                            ? pageObj.get("type").getAsString() : "";
                        if (!type.startsWith("hexcasting:")) continue;
                        seenTypes.add(type);
                    }
                });
        }

        for (String type : seenTypes) {
            String bare = type.substring("hexcasting:".length());
            // Templates match by filename (case-sensitive).
            if (availableTemplates.contains(bare)) continue;
            // Processor classes match by class-name substring (case-insensitive). The type
            // "crafting_multi" maps to MultiCraftingProcessor, "brainsweep" maps to
            // BrainsweepProcessor, etc. — we accept any processor class whose name contains
            // the camel-cased type. Relaxed to catch both naming conventions.
            boolean hasProcessor = availableProcessors.stream().anyMatch(cls -> {
                String lowerCls = cls.toLowerCase().replace("_", "");
                String lowerType = bare.toLowerCase().replace("_", "");
                return lowerCls.contains(lowerType);
            });
            if (!hasProcessor) {
                hits.add("page type \"" + type + "\" has no template "
                    + "(" + TEMPLATES_ROOT + "/" + bare + ".json) and no matching processor "
                    + "class under " + PROCESSOR_ROOT);
            }
        }

        assertTrue(hits.isEmpty(),
            "Unresolved hexcasting: page types:\n  "
                + String.join("\n  ", hits));
    }

    // ---- helpers ---------------------------------------------------------------

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
