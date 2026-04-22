package at.petrak.hexcasting.common.recipe;

import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import org.jetbrains.annotations.NotNull;

/**
 * 1.21: ShapelessRecipe no longer takes a ResourceLocation id in its constructor
 * (RecipeHolder holds the id separately) and assemble takes a CraftingInput +
 * HolderLookup.Provider.
 */
public class SealSpellbookRecipe extends ShapelessRecipe {
    public static final SimpleCraftingRecipeSerializer<SealSpellbookRecipe> SERIALIZER =
        new SimpleCraftingRecipeSerializer<>(SealSpellbookRecipe::new);

    private static ItemStack getSealedStack() {
        ItemStack output = new ItemStack(HexItems.SPELLBOOK);
        ItemSpellbook.setSealed(output, true);
        NBTHelper.putString(output, IotaHolderItem.TAG_OVERRIDE_VISUALLY, "any");
        return output;
    }

    private static NonNullList<Ingredient> createIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.createWithCapacity(2);
        ingredients.add(IXplatAbstractions.INSTANCE.getUnsealedIngredient(new ItemStack(HexItems.SPELLBOOK)));
        ingredients.add(Ingredient.of(Items.HONEYCOMB));
        return ingredients;
    }

    public SealSpellbookRecipe(CraftingBookCategory category) {
        super("", category, getSealedStack(), createIngredients());
    }

    @Override
    public @NotNull ItemStack assemble(CraftingInput inv, HolderLookup.Provider provider) {
        ItemStack out = ItemStack.EMPTY;

        for (int i = 0; i < inv.size(); i++) {
            var stack = inv.getItem(i);
            if (stack.is(HexItems.SPELLBOOK)) {
                out = stack.copy();
                break;
            }
        }

        if (!out.isEmpty()) {
            ItemSpellbook.setSealed(out, true);
            out.setCount(1);
        }

        return out;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
