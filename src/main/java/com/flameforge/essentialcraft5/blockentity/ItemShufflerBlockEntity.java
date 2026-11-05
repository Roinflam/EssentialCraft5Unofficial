package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECFarmDevices;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 魔力物品洗牌器（原作 {@code BlockRedstoneDeviceNotSided.shuffle} L119~123，触发 L185~195）：红石从无到有那一下，
 * 把周围 25×25×25 里地上的掉落物各自往水平方向随机挪一点（每个轴 {@code nextDouble() - nextDouble()}，(-1, 1) 的三角分布，y 不变）。
 * 用途：把挤成一堆的东西散开（食物散开了繁殖器才喂得到更多动物、东西抖到漏斗上面）。
 *
 * <h2>和原作不同（batch4 §3.1、policy §4.3）</h2>
 * <ul>
 *   <li>放不下不挪：目的位置和方块、会挡路的实体、世界边界有碰撞就不挪（原作不查碰撞，会把东西挪进方块里）。</li>
 *   <li>原来那格和目的格都要主人能动（{@code checkAsOwner}）；指定了拾取人、又不是主人的掉落物不碰；永远捡不起来的（展示用的）不碰。</li>
 *   <li>挪完设 {@code hasImpulse}：掉落物的位置默认 20 刻才同步一次，不设的话客户端要等最多 1 秒才看到它动。</li>
 *   <li>红石上升沿 + 最小间隔 20 刻（原作有信号期间每次邻居更新都挪一遍全部），每次最多挪 128 个。</li>
 * </ul>
 */
public class ItemShufflerBlockEntity extends FarmDeviceBlockEntity {

    /** 服务端配置 machines.item_shuffler（新加的限制，原作没有） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxPerPulse;
        public static ForgeConfigSpec.IntValue pulseInterval;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("item_shuffler");
            maxPerPulse = builder.comment("At most this many dropped items are moved per redstone pulse.",
                            "每次红石脉冲最多挪几个掉落物")
                    .defineInRange("max_per_pulse", 128, 1, 512);
            pulseInterval = builder.comment("Minimum ticks between two redstone pulses (a pulse inside the interval waits for it to end).",
                            "Not below 10: every pulse queries all items in a 25x25x25 area.",
                            "两次红石脉冲之间至少隔几刻（间隔内来的脉冲等间隔结束再做）；每次脉冲都要查 25×25×25 的掉落物，所以不低于 10")
                    .defineInRange("pulse_interval", 20, 10, 1200);
            builder.pop();
        }
    }

    /** 一个候选：要挪的掉落物和这次抽到的偏移（续做时沿用同一个偏移，不重抽） */
    private record Move(ItemEntity item, double dx, double dz) {
    }

    private final List<Move> candidates = new ArrayList<>();

    public ItemShufflerBlockEntity(BlockPos pos, BlockState state) {
        super(ECFarmDevices.ITEM_SHUFFLER_BE.get(), pos, state, () -> ECConfig.get(Config.pulseInterval));
    }

    @Override
    protected int maxPerPulse() {
        return ECConfig.get(Config.maxPerPulse);
    }

    /** 这个掉落物能不能碰：活着、不空、不是永远捡不起来的、没指定拾取人或指定的就是主人 */
    private static boolean eligible(ItemEntity item, @Nullable UUID owner) {
        return item.isAlive() && !item.getItem().isEmpty() && !ECItemEntities.neverPickUp(item) && ECItemEntities.pickableBy(item, owner);
    }

    @Override
    protected void collect(ServerLevel level) {
        UUID owner = machineOwner();
        List<ItemEntity> items = nearestFirst(level.getEntitiesOfClass(ItemEntity.class, scanArea(0D), item -> eligible(item, owner)));
        RandomSource random = level.getRandom();
        for (ItemEntity item : items) {
            // 原作 DummyCore MathUtils.randomDouble：rand.nextDouble() - rand.nextDouble()
            candidates.add(new Move(item, random.nextDouble() - random.nextDouble(), random.nextDouble() - random.nextDouble()));
        }
    }

    @Override
    protected boolean process(ServerLevel level) {
        UUID owner = machineOwner();
        AABB area = scanArea(0D);
        boolean unresolved = false;
        for (Iterator<Move> it = candidates.iterator(); it.hasNext() && !full(); ) {
            Move move = it.next();
            ItemEntity item = move.item();
            if (!eligible(item, owner) || !item.getBoundingBox().intersects(area)) {
                it.remove();
                continue;
            }
            // 先做不用探测的判断：放不下就不挪
            AABB moved = item.getBoundingBox().move(move.dx(), 0D, move.dz());
            if (!level.noCollision(item, moved)) {
                it.remove();
                continue;
            }
            BlockPos from = item.blockPosition();
            BlockPos to = BlockPos.containing(item.getX() + move.dx(), item.getY(), item.getZ() + move.dz());
            // 不往没加载完的区块里挪（主人离线且关了在线要求时 checkAsOwner 直接放行，不看区块）
            if (!ECLevels.isFullyLoaded(level, to)) {
                it.remove();
                continue;
            }
            ECProtection.Verdict verdict = check(from);
            if (verdict == ECProtection.Verdict.ALLOW && !to.equals(from)) {
                verdict = check(to);
            }
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                unresolved = true;
                continue;
            }
            it.remove();
            if (verdict == ECProtection.Verdict.ALLOW) {
                item.setPos(item.getX() + move.dx(), item.getY(), item.getZ() + move.dz());
                item.hasImpulse = true;
                markDone();
            }
        }
        return unresolved;
    }

    @Override
    protected void clearCandidates() {
        candidates.clear();
    }
}
