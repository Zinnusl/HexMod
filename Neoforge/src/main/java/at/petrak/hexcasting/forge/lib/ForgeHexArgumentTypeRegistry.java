package at.petrak.hexcasting.forge.lib;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.common.command.PatternResLocArgument;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 1.21 NeoForge: DeferredRegister.create takes a ResourceKey (Registries.COMMAND_ARGUMENT_TYPE)
 * and returns DeferredHolder, not RegistryObject. ForgeRegistries is gone.
 */
public class ForgeHexArgumentTypeRegistry {
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES = DeferredRegister.create(
        Registries.COMMAND_ARGUMENT_TYPE, HexAPI.MOD_ID);

    public static final DeferredHolder<ArgumentTypeInfo<?, ?>,
        ArgumentTypeInfo<PatternResLocArgument, SingletonArgumentInfo<PatternResLocArgument>.Template>>
        PATTERN_RESLOC = register(PatternResLocArgument.class,
        "pattern",
        SingletonArgumentInfo.contextFree(PatternResLocArgument::id)
    );

    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>>
    DeferredHolder<ArgumentTypeInfo<?, ?>, ArgumentTypeInfo<A, T>> register(
        Class<A> clazz,
        String name,
        ArgumentTypeInfo<A, T> ati) {
        var holder = ARGUMENT_TYPES.<ArgumentTypeInfo<A, T>>register(name, () -> ati);
        ArgumentTypeInfos.registerByClass(clazz, ati);
        return holder;
    }
}
