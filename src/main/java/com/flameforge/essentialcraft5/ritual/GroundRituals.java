package com.flameforge.essentialcraft5.ritual;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 地面物品仪式。对应原作 PlayerTickHandler.doGroundItemChecks（L593-746）。
 * <p>
 * 把催化物丢在地上：物品所在格必须是空气，正下方是中心方块，中心方块东南西北相邻的四格是外圈方块。
 * 条件满足就消耗 1 个催化物（不论成败），冒一团烟，再按概率出结果：
 * <table>
 *   <tr><th>催化物</th><th>中心</th><th>外圈</th><th>概率</th><th>结果</th></tr>
 *   <tr><td>烈焰粉</td><td>下界岩</td><td>岩浆</td><td>80%</td><td>物品所在格变成凝聚火元素</td></tr>
 *   <tr><td>黏土球</td><td>冰</td><td>水</td><td>80%</td><td>凝聚水元素</td></tr>
 *   <tr><td>黏液球</td><td>黏液块</td><td>草方块</td><td>80%</td><td>凝聚地元素</td></tr>
 *   <tr><td>火药</td><td>石英块</td><td>沙子</td><td>80%</td><td>凝聚风元素</td></tr>
 *   <tr><td>钻石</td><td>绿宝石块</td><td>灵魂沙</td><td>60%</td><td>该处的 MRU 聚集体 +5000（附近没有就新建）</td></tr>
 *   <tr><td>绿宝石</td><td>绿宝石块</td><td>灵魂沙</td><td>50%</td><td>掉出一块灵魂石</td></tr>
 * </table>
 *
 * <h2>检查频率（概率不用换算）</h2>
 * 原作这段代码在 PlayerTickHandler L528 的 {@code if (e.ticksExisted % 20 != 0) return;} 之后，
 * 本来就是每个玩家每 20 tick 扫一次身边（脚下 ±2.5 × ±1.5 × ±2.5 格）的掉落物（batch1.md 写的「每 tick」是误读）。
 * 这里同样每 20 tick 检查一次、每次每个掉落物最多消耗 1 个，所以每个催化物的成功率、消耗速度都和原作相同，概率照搬。
 * 唯一的区别：原作每个玩家各扫各的，两个玩家站在一起时同一个掉落物每 20 tick 被消耗两次；
 * 这里所有玩家在同一 tick 扫，合并去重后每个掉落物只检查一次。
 *
 * <h2>权限（全模组安全原则 P1，policy.md §5，第三批回补 R）</h2>
 * 原作四种元素仪式直接把掉落物所在的空气格换成魔晶簇，谁的地上都行。现在放魔晶簇的仪式要算在<b>扔东西的人</b>头上
 * （{@link ItemEntity#getOwner()}：玩家按 Q 丢出的物品记着他；发射器吐的、怪物掉的、玩家死亡掉的没有）：
 * <ul>
 *   <li>扔东西的人必须是在这个维度、在线的真玩家（不是 FakePlayer），而且能放方块（冒险、旁观模式不行，{@link Player#mayBuild()}）；
 *       没有这样的人就<b>不触发、不消耗</b>催化物。</li>
 *   <li>掷骰成功：以他的身份 {@code ECProtection.placeGuarded(…, UP)} 放魔晶簇，被领地插件拒绝就还原、<b>不消耗</b>。</li>
 *   <li>掷骰失败（照原作也要消耗）：先 {@code ECProtection.canEdit} 问一次那一格他能不能动（缓存 200 刻），不能动就不消耗。</li>
 * </ul>
 * 钻石仪式（给聚集体加 MRU）、绿宝石仪式（掉灵魂石）不改方块，照旧触发；钻石仪式把扔东西的人交给泄漏结算，
 * 只汇进他能动的聚集体（{@code general.corruption.respect_claims}，见 mru/ECCorruption），没有这个人就照旧找最近的。
 *
 * <h2>1.12 → 1.20 的方块对应</h2>
 * <ul>
 *   <li>岩浆、水：原作比较 Material.LAVA / WATER，流动液体也算；这里看流体标签（含流动液体，也含含水方块）。</li>
 *   <li>草方块：原作 Blocks.GRASS 在 1.12 是草方块，1.20 是草丛，这里用 grass_block。</li>
 *   <li>沙子：1.12 的 SAND 含红沙，这里两种都算。可疑的沙子是新方块，不算。</li>
 *   <li>石英块：1.12 的 QUARTZ_BLOCK 含錾制石英块和石英柱（meta 1~4），这里三种都算；石英砖、平滑石英块是新方块，不算。</li>
 *   <li>灵魂沙：灵魂土是 1.16 的新方块，不算。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class GroundRituals {

    /** 检查间隔（原作 ticksExisted % 20） */
    public static final int INTERVAL = 20;
    /** 钻石仪式给 MRU 聚集体加的量 */
    public static final int DIAMOND_MRU = 5000;

    /** 仪式的结果：pos 是物品所在格 */
    @FunctionalInterface
    private interface Outcome {
        void apply(ServerLevel level, BlockPos pos, ItemEntity catalyst);
    }

    /**
     * 一种仪式。放魔晶簇的（cluster 不为 null）按扔东西的人的权限走（见类注释），outcome 为 null；
     * 不改方块的（钻石、绿宝石）cluster 为 null、结果在 outcome 里。
     */
    private record Ritual(Item catalyst, Predicate<BlockState> center, Predicate<BlockState> ring, float chance,
                          @Nullable Supplier<? extends Block> cluster, @Nullable Outcome outcome) {
    }

    private static final List<Ritual> RITUALS = List.of(
            clusterRitual(Items.BLAZE_POWDER, state -> state.is(Blocks.NETHERRACK),
                    state -> state.getFluidState().is(FluidTags.LAVA), ECMaterials.FIRE_DROPS_CLUSTER),
            clusterRitual(Items.CLAY_BALL, state -> state.is(Blocks.ICE),
                    state -> state.getFluidState().is(FluidTags.WATER), ECMaterials.WATER_DROPS_CLUSTER),
            clusterRitual(Items.SLIME_BALL, state -> state.is(Blocks.SLIME_BLOCK),
                    state -> state.is(Blocks.GRASS_BLOCK), ECMaterials.EARTH_DROPS_CLUSTER),
            clusterRitual(Items.GUNPOWDER,
                    state -> state.is(Blocks.QUARTZ_BLOCK) || state.is(Blocks.CHISELED_QUARTZ_BLOCK) || state.is(Blocks.QUARTZ_PILLAR),
                    state -> state.is(Blocks.SAND) || state.is(Blocks.RED_SAND), ECMaterials.AIR_DROPS_CLUSTER),
            new Ritual(Items.DIAMOND, state -> state.is(Blocks.EMERALD_BLOCK), state -> state.is(Blocks.SOUL_SAND), 0.6F, null,
                    // 记下丢钻石的人：领地那一关只把这 5000 MRU 汇进他能动的聚集体（mru/ECCorruption 类注释「领地」）
                    (level, pos, catalyst) -> ECCorruption.increaseAt(level, pos, DIAMOND_MRU, throwerOf(level, catalyst))),
            new Ritual(Items.EMERALD, state -> state.is(Blocks.EMERALD_BLOCK), state -> state.is(Blocks.SOUL_SAND), 0.5F, null,
                    (level, pos, catalyst) -> {
                        // 原作 new EntityItem(world, x, y, z, stack)：带一点随机初速度，1.20 的同名构造器一样
                        ItemEntity soulStone = new ItemEntity(level, catalyst.getX(), catalyst.getY(), catalyst.getZ(),
                                new ItemStack(ECBootstrap.SOUL_STONE.get()));
                        level.addFreshEntity(soulStone);
                    })
    );

    private GroundRituals() {
    }

    /** 元素仪式：80% 把物品所在格变成对应的魔晶簇 */
    private static Ritual clusterRitual(Item catalyst, Predicate<BlockState> center, Predicate<BlockState> ring,
                                        Supplier<? extends Block> cluster) {
        return new Ritual(catalyst, center, ring, 0.8F, cluster, null);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % INTERVAL != 0 || level.players().isEmpty()) {
            return;
        }
        // 几个玩家挨在一起时，同一个掉落物只检查一次
        Set<ItemEntity> items = new LinkedHashSet<>();
        for (ServerPlayer player : level.players()) {
            // 原作：以玩家脚下为中心的 1×1×1 方框向外扩 (2, 1, 2)
            AABB box = new AABB(player.getX() - 2.5D, player.getY() - 1.5D, player.getZ() - 2.5D,
                    player.getX() + 2.5D, player.getY() + 1.5D, player.getZ() + 2.5D);
            items.addAll(level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive));
        }
        for (ItemEntity item : items) {
            if (item.isAlive()) {
                check(level, item, () -> level.random.nextFloat());
            }
        }
    }

    /**
     * 检查一个掉落物是否满足某个仪式，满足就消耗 1 个并按概率出结果。扔东西的人取 {@link #throwerOf}。
     *
     * @param roll 掷骰（[0, 1)），不大于仪式概率就成功（原作 {@code rand.nextFloat() <= p}）
     * @return 是否触发了仪式（消耗了物品，不论成败）
     */
    public static boolean check(ServerLevel level, ItemEntity item, DoubleSupplier roll) {
        return check(level, item, throwerOf(level, item), roll);
    }

    /**
     * 同上，扔东西的人由调用方给（GameTest 用：测试玩家不在世界里，{@link ItemEntity#getOwner()} 找不到它）。
     * 放魔晶簇的仪式：thrower 不是真玩家、或者不能放方块（冒险 / 旁观模式）就不触发；以他的身份放不下（被拒）、
     * 或者掷骰失败时那一格他不能动，都不触发、不消耗。
     *
     * @param thrower 扔东西的人；null / 假玩家 = 没有
     */
    public static boolean check(ServerLevel level, ItemEntity item, @Nullable Player thrower, DoubleSupplier roll) {
        ItemStack stack = item.getItem();
        if (stack.isEmpty()) {
            return false;
        }
        Ritual ritual = null;
        for (Ritual candidate : RITUALS) {
            if (stack.is(candidate.catalyst())) {
                ritual = candidate;
                break;
            }
        }
        if (ritual == null) {
            return false;
        }
        BlockPos pos = item.blockPosition();
        BlockPos center = pos.below();
        if (!ritual.center().test(level.getBlockState(center)) || !level.isEmptyBlock(pos)) {
            return false;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!ritual.ring().test(level.getBlockState(center.relative(direction)))) {
                return false;
            }
        }
        boolean success = roll.getAsDouble() <= ritual.chance();
        Supplier<? extends Block> cluster = ritual.cluster();
        if (cluster != null) {
            // 放魔晶簇（P1）：要有扔东西的真玩家；成功时以他的身份放（被拒还原），失败时问一句那格他能不能动。
            // 这两种被拒都当作没触发：不消耗、不冒烟（原作失败也消耗，这里只在他自己能动的地方照原作消耗）
            if (!ECProtection.isRealPlayer(thrower)) {
                return false;
            }
            // 冒险 / 旁观模式不能放方块：placeGuarded、canEdit 按设计不查（右键放方块那条路原版已经查过 mayBuild），
            // 丢东西不走那条路，这里自己查（第三批审查 RB-6）
            if (!thrower.mayBuild()) {
                return false;
            }
            boolean allowed = success
                    ? ECProtection.placeGuarded(thrower, level, pos, cluster.get().defaultBlockState(), Direction.UP)
                    : ECProtection.canEdit(thrower, level, pos);
            if (!allowed) {
                return false;
            }
        }
        consumeOne(item);
        // 原作 MiscUtils.spawnParticlesOnServer("explosion_normal", …)：1.13 起叫 poof
        level.sendParticles(ParticleTypes.POOF, item.getX(), item.getY(), item.getZ(), 1, 0D, 0D, 0D, 0D);
        Outcome outcome = ritual.outcome();
        if (success && outcome != null) {
            outcome.apply(level, pos, item);
        }
        return true;
    }

    /**
     * 扔出这个掉落物的人：{@link ItemEntity#getOwner()}（只在这个维度里按 UUID 找，人下线了就找不到），是真玩家才算。
     * 玩家按 Q 丢的记着他；发射器、漏斗、怪物掉落、方块掉落、玩家死亡掉落都没有。
     */
    @Nullable
    public static Player throwerOf(ServerLevel level, ItemEntity item) {
        Entity owner = item.getOwner();
        return ECProtection.isRealPlayer(owner) && owner.level() == level ? (Player) owner : null;
    }

    /** 消耗 1 个：换一份新的堆叠写回，客户端才看得到数量变化（原地 shrink 不会同步）；用完直接移除（原作置 lifespan = 0，下一 tick 消失） */
    private static void consumeOne(ItemEntity item) {
        ItemStack remaining = item.getItem().copy();
        remaining.shrink(1);
        if (remaining.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remaining);
        }
    }
}
