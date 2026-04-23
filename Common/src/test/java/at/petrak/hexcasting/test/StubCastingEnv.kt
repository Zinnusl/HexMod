package at.petrak.hexcasting.test

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.MishapEnvironment
import at.petrak.hexcasting.api.pigment.FrozenPigment
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate

/**
 * A no-op [CastingEnvironment] for tests that only want to invoke a `SpecialHandler.Factory`'s
 * `tryMatch`, `Mishap` construction, or similar pure logic. The abstract surface is wide, but
 * everything here throws — use only in tests where the env is provably unused.
 * <p>
 * Passing a null `ServerLevel` works because the protected constructor doesn't touch it; don't
 * call [getWorld] or anything that reaches through it.
 */
class StubCastingEnv : CastingEnvironment(null as ServerLevel?) {
    private fun nope(): Nothing = throw UnsupportedOperationException("StubCastingEnv is not usable as a real env")
    override fun getCastingEntity(): LivingEntity = nope()
    override fun getMishapEnvironment(): MishapEnvironment = nope()
    override fun mishapSprayPos(): Vec3 = nope()
    override fun extractMediaEnvironment(cost: Long, simulate: Boolean): Long = nope()
    override fun isVecInRangeEnvironment(vec: Vec3?): Boolean = nope()
    override fun hasEditPermissionsAtEnvironment(pos: BlockPos?): Boolean = nope()
    override fun getCastingHand(): InteractionHand = nope()
    override fun getUsableStacks(mode: StackDiscoveryMode?): MutableList<ItemStack> = nope()
    override fun getPrimaryStacks(): MutableList<HeldItemInfo> = nope()
    override fun replaceItem(
        stackOk: Predicate<ItemStack>?, replaceWith: ItemStack?, hand: InteractionHand?
    ): Boolean = nope()
    override fun getPigment(): FrozenPigment = nope()
    override fun setPigment(pigment: FrozenPigment?): FrozenPigment? = nope()
    override fun produceParticles(
        particles: at.petrak.hexcasting.api.casting.ParticleSpray, colorizer: FrozenPigment
    ) = nope()
    override fun printMessage(message: Component) = nope()
}
