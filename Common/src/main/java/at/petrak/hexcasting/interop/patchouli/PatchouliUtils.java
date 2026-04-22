package at.petrak.hexcasting.interop.patchouli;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import vazkii.patchouli.api.IVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * > no this is a "literally copy these files/parts of file into your mod"
 * > we should put this in patchy but lol
 * > lazy
 * -- Hubry Vazcord
 * <p>
 * 1.21: Recipe<C extends Container> became Recipe<T extends RecipeInput>; the recipe
 * manager returns RecipeHolder<T> values, so byKey produces a RecipeHolder lookup.
 */
public class PatchouliUtils {
    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> T getRecipe(RecipeType<T> type, ResourceKey<Recipe<?>> key) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        var manager = Minecraft.getInstance().level.getRecipeManager();
        return (T) manager.byKey(key)
            .map(net.minecraft.world.item.crafting.RecipeHolder::value)
            .filter(recipe -> recipe.getType() == type)
            .orElse(null);
    }

    /**
     * Combines the ingredients, returning the first matching stack of each, then the second stack of each, etc.
     * looping back ingredients that run out of matched stacks, until the ingredients reach the length
     * of the longest ingredient in the recipe set.
     */
    public static IVariable interweaveIngredients(List<Ingredient> ingredients, int longestIngredientSize) {
        if (ingredients.size() == 1) {
            return IVariable.wrapList(Arrays.stream(ingredients.get(0).getItems())
                .map(IVariable::from).collect(Collectors.toList()));
        }

        ItemStack[] empty = {ItemStack.EMPTY};
        List<ItemStack[]> stacks = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient != null && !ingredient.isEmpty()) {
                stacks.add(ingredient.getItems());
            } else {
                stacks.add(empty);
            }
        }
        List<IVariable> list = new ArrayList<>(stacks.size() * longestIngredientSize);
        for (int i = 0; i < longestIngredientSize; i++) {
            for (ItemStack[] stack : stacks) {
                list.add(IVariable.from(stack[i % stack.length]));
            }
        }
        return IVariable.wrapList(list);
    }

    public static IVariable interweaveIngredients(List<Ingredient> ingredients) {
        return interweaveIngredients(ingredients,
            ingredients.stream().mapToInt(ingr -> ingr.getItems().length).max().orElse(1));
    }
}
