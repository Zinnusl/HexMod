package at.petrak.hexcasting.common.misc;

import at.petrak.hexcasting.common.lib.HexConfiguredFeatures;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.List;
import java.util.Optional;

/**
 * 1.20.5+: AbstractTreeGrower was replaced by {@link TreeGrower}, which is now a final
 * class constructed with a feature key. To keep weighted random akashic tree selection
 * we expose {@link #growTree(ServerLevel, ChunkGenerator, BlockPos, BlockState, RandomSource)}
 * as a free function that edifiy spells call directly, picking a feature per call.
 */
public class AkashicTreeGrower {
    public static final AkashicTreeGrower INSTANCE = new AkashicTreeGrower();

    public static final List<ResourceKey<ConfiguredFeature<?, ?>>> GROWERS = Lists.newArrayList();

    public static void init() {
        GROWERS.add(HexConfiguredFeatures.AMETHYST_EDIFIED_TREE);
        GROWERS.add(HexConfiguredFeatures.AVENTURINE_EDIFIED_TREE);
        GROWERS.add(HexConfiguredFeatures.CITRINE_EDIFIED_TREE);
    }

    public boolean growTree(
            ServerLevel level,
            ChunkGenerator generator,
            BlockPos pos,
            BlockState state,
            RandomSource random
    ) {
        if (GROWERS.isEmpty()) return false;
        ResourceKey<ConfiguredFeature<?, ?>> key = GROWERS.get(random.nextInt(GROWERS.size()));
        // Delegate to a throwaway TreeGrower that always returns our chosen key.
        TreeGrower delegate = new TreeGrower(
                "hexcasting_akashic",
                Optional.empty(),
                Optional.of(key),
                Optional.empty()
        );
        return delegate.growTree(level, generator, pos, state, random);
    }
}
