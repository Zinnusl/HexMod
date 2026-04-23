package at.petrak.hexcasting.interop.patchouli;

import at.petrak.hexcasting.common.recipe.BrainsweepRecipe;
import at.petrak.hexcasting.common.recipe.HexRecipeStuffRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.IComponentProcessor;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.api.IVariableProvider;

/**
 * Drives the {@code hexcasting:brainsweep} page template in the guidebook: looks up
 * a {@link BrainsweepRecipe} by id, exposes its block input / entity / cost / result
 * to the page's Patchouli components.
 * <p>
 * 1.21: IComponentProcessor.setup/process took on a Level parameter, recipe listing
 * returns {@link RecipeHolder} instead of bare Recipe, and the Patchouli entity
 * component expects an ID string rather than SNBT — so we return just the entity
 * type id and let the default rendering handle the rest.
 */
public class BrainsweepProcessor implements IComponentProcessor {
    private @Nullable BrainsweepRecipe recipe;
    private @Nullable String exampleEntityId;

    @Override
    public void setup(Level level, IVariableProvider vars) {
        var id = ResourceLocation.parse(
            vars.get("recipe", level.registryAccess()).asString());

        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var recman = mc.level.getRecipeManager();
        for (RecipeHolder<BrainsweepRecipe> holder :
            recman.getAllRecipesFor(HexRecipeStuffRegistry.BRAINSWEEP_TYPE)) {
            if (holder.id().equals(id)) {
                this.recipe = holder.value();
                break;
            }
        }
    }

    @Override
    public IVariable process(Level level, String key) {
        if (this.recipe == null) return null;
        var regs = level.registryAccess();
        switch (key) {
            case "header": {
                return IVariable.from(this.recipe.result().getBlock().getName(), regs);
            }
            case "input": {
                var inputStacks = this.recipe.blockIn().getDisplayedStacks();
                return IVariable.from(inputStacks.toArray(new ItemStack[0]), regs);
            }
            case "result": {
                return IVariable.from(new ItemStack(this.recipet0BlockItem()), regs);
            }
            case "entity": {
                if (this.exampleEntityId == null) {
                    var entity = this.recipe.entityIn().exampleEntity(Minecraft.getInstance().level);
                    if (entity == null) return null;
                    // Patchouli 1.21's entity component parses the string through
                    // brigadier's EntitySummonArgument-style parser; a bare type id is
                    // the simplest form that always succeeds.
                    var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    if (typeId == null) return null;
                    this.exampleEntityId = typeId.toString();
                }
                return IVariable.wrap(this.exampleEntityId, regs);
            }
            case "entityTooltip": {
                var mc = Minecraft.getInstance();
                boolean advanced = mc.options.advancedItemTooltips;
                return IVariable.wrapList(
                    this.recipe.entityIn()
                        .getTooltip(advanced)
                        .stream()
                        .map(comp -> IVariable.from(comp, regs))
                        .toList(),
                    regs);
            }
            default:
                return null;
        }
    }

    // BlockState → block's item form. Extracted so the switch-case stays a one-liner.
    private net.minecraft.world.level.block.Block recipet0BlockItem() {
        return this.recipe.result().getBlock();
    }
}
