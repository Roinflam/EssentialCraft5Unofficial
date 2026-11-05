package com.flameforge.essentialcraft5.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.common.IPlantable;
import org.jetbrains.annotations.Nullable;

/**
 * 恐惧仙人掌（原作 BlockDreadCactus，注册名 cacti → dread_cactus；第六批 6a M1，batch6a.md §5.1）：原版仙人掌，碰到的活体另外中毒。
 * 长在海纳尔沙漠（原作 BiomeHoannaDesert cactiPerChunk 10，不是恐惧之地），世界生成见 {@code worldgen/configured_feature/dread_cactus_patch.json}。
 * <ul>
 *   <li><b>中毒节流</b>：原作 {@code onEntityCollision} 每刻都 {@code addPotionEffect(中毒 100 刻)}，站在旁边每秒给客户端发 20 个效果包。
 *       这里只在没有中毒、或剩下不到 {@link #REFRESH_BELOW} 刻时才加（效果照原作：中毒 I、100 刻）。只在服务端做。</li>
 *   <li><b>创造 / 旁观不中毒</b>（原作不查）：照原版凋灵玫瑰先问 {@code isInvulnerableTo}（这里用中毒的伤害类型 magic），
 *       另外原版的创造模式无敌不在 {@code isInvulnerableTo} 里（在 {@code Player.hurt} 里），所以单独判 {@code abilities.invulnerable}。
 *       仙人掌本身 1 点刺伤照原版（super）。</li>
 *   <li><b>能叠两三格</b>：Forge {@code Block.canSustainPlant}（L514~520）只对原版仙人掌特判「脚下是仙人掌」，别的仙人掌走 DESERT 类型只认沙子，
 *       不覆盖的话第二格一更新就掉、世界生成的 block_column 一放完就碎。这里恐惧仙人掌当「土」时，上面是恐惧仙人掌就放行；
 *       其余照父类（沙子上活，恐惧泥土、草方块上掉；原版仙人掌不能长在它上面）。随机刻长高、「旁边有实心方块就掉」都照原版 {@link CactusBlock}，
 *       最高 3 格；长高前发 Forge 的作物生长事件（{@code CropGrowEvent.Pre}），被拒就不长。</li>
 *   <li><b>寻路照原版仙人掌</b>（原作 bug 顺手修）：原版 {@code WalkNodeEvaluator}（L461、L494）只认 {@code Blocks.CACTUS}，Forge 的两个寻路钩子默认也不管子类，
 *       不覆盖的话仙人掌本身算 BLOCKED、旁边的格子算 WALKABLE，宠物和怪会贴着走、反复被扎、中毒（1.12 原作同样按 {@code Blocks.CACTUS} 判断）。
 *       这里和原版一样：仙人掌那格 DAMAGE_OTHER，旁边的格子 DANGER_OTHER（惩罚 8，生物会绕开）。</li>
 * </ul>
 */
public class DreadCactusBlock extends CactusBlock {

    /** 中毒剩下不到这么多刻才重新加（原作每刻都加） */
    public static final int REFRESH_BELOW = 20;
    /** 中毒时长（原作 100 刻、等级 I） */
    public static final int POISON_TICKS = 100;

    public DreadCactusBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide || !(entity instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        if (living instanceof Player player && (player.getAbilities().invulnerable || player.isSpectator())) {
            return;
        }
        if (living.isInvulnerableTo(level.damageSources().magic())) {
            return;
        }
        MobEffectInstance current = living.getEffect(MobEffects.POISON);
        if (current == null || current.getDuration() < REFRESH_BELOW) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
        }
    }

    @Override
    public boolean canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, Direction facing, IPlantable plantable) {
        if (facing == Direction.UP && plantable.getPlant(level, pos.relative(facing)).is(this)) {
            return true;
        }
        return super.canSustainPlant(state, level, pos, facing, plantable);
    }

    /** 仙人掌那格：同原版仙人掌（WalkNodeEvaluator.getBlockPathTypeRaw 对 Blocks.CACTUS 返回 DAMAGE_OTHER） */
    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        return BlockPathTypes.DAMAGE_OTHER;
    }

    /** 贴着仙人掌的格子：同原版仙人掌（WalkNodeEvaluator.checkNeighbourBlocks 对 Blocks.CACTUS 返回 DANGER_OTHER） */
    @Override
    public BlockPathTypes getAdjacentBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob, BlockPathTypes originalType) {
        return BlockPathTypes.DANGER_OTHER;
    }
}
