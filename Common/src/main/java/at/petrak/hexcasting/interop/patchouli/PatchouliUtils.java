package at.petrak.hexcasting.interop.patchouli;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import vazkii.patchouli.api.IVariable;

import java.util.List;

/**
 * 1.21 Patchouli shifted IVariable.from signatures and Recipe<C extends Container> →
 * Recipe<I extends RecipeInput>. Rather than chase the moving API here, the two helpers
 * return harmless placeholders — book pages that rely on them will render empty until
 * the full Patchouli interop is reimplemented.
 * TODO(port-1.21): replace with real lookups once Patchouli 1.21 API stabilises.
 */
public class PatchouliUtils {
    public static <T extends Recipe<?>> T getRecipe(RecipeType<T> type, ResourceLocation id) {
        return null;
    }

    public static IVariable interweaveIngredients(List<Ingredient> ingredients, int longestIngredientSize) {
        return IVariable.wrap("");
    }

    public static IVariable interweaveIngredients(List<Ingredient> ingredients) {
        return IVariable.wrap("");
    }
}
