package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECFarmDevices;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.IForgeShearable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 魔力剪毛器（原作 {@code BlockRedstoneDeviceNotSided.shear} L88~100，查询 L207~221）：红石从无到有那一下，
 * 给周围 25×25×25 里能剪的生物剪毛，剪下来的东西掉在它们脚下。用的是一把看不见的剪刀：不耗耐久、时运 {@value #FORTUNE}（照原作；1.20 的羊不看时运，每次 1~3 块羊毛）。
 *
 * <h2>和原作不同（batch4 §3.1、§7，policy §4.3）</h2>
 * <ul>
 *   <li>不剪实体类型标签 {@code essentialcraft5:shearing_station_blacklist} 里的（默认哞菇：剪了会变成普通牛；雪傀儡：会被摘掉南瓜头）。
 *       服主可以用数据包往里加别的模组的生物。</li>
 *   <li>过归属关：起了名字、拴着绳的不剪，驯服的只剪主人就是机器主人的（{@code ECEntityRules.belongsToOther}，D1 选 B）；生物所在格要主人能动。</li>
 *   <li>只查活着的生物（原作查全部实体再挑）；红石上升沿 + 最小间隔 20 刻；每次最多剪 32 只。候选表按离机器远近截断之前先去掉没毛可剪的，
 *       范围里有毛的都轮得到（原作不截断）。</li>
 *   <li>掉落物照 Forge 剪刀的写法（{@code ShearsItem.interactLivingEntity} L58~64）：从生物位置高 1 格处生成、带一点随机速度（原作直接放在生物脚下）。</li>
 * </ul>
 * 只认 Forge 的 {@link IForgeShearable}（原版的羊、哞菇、雪傀儡和大多数模组的可剪生物都实现它），剪的人传 null（同 Forge 的发射器剪羊）。
 */
public class ShearingStationBlockEntity extends FarmDeviceBlockEntity {

    /** 剪刀的时运等级（原作 {@code onSheared(…, 2)}） */
    public static final int FORTUNE = 2;

    /** 服务端配置 machines.shearing_station（新加的限制，原作没有） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxPerPulse;
        public static ForgeConfigSpec.IntValue pulseInterval;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("shearing_station");
            maxPerPulse = builder.comment("At most this many creatures are sheared per redstone pulse.",
                            "每次红石脉冲最多剪几只")
                    .defineInRange("max_per_pulse", 32, 1, 128);
            pulseInterval = builder.comment("Minimum ticks between two redstone pulses (a pulse inside the interval waits for it to end).",
                            "Not below 10: every pulse queries all creatures in a 25x25x25 area.",
                            "两次红石脉冲之间至少隔几刻（间隔内来的脉冲等间隔结束再做）；每次脉冲都要查 25×25×25 的生物，所以不低于 10")
                    .defineInRange("pulse_interval", 20, 10, 1200);
            builder.pop();
        }
    }

    private final List<LivingEntity> candidates = new ArrayList<>();

    public ShearingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ECFarmDevices.SHEARING_STATION_BE.get(), pos, state, () -> ECConfig.get(Config.pulseInterval));
    }

    @Override
    protected int maxPerPulse() {
        return ECConfig.get(Config.maxPerPulse);
    }

    /** 能不能剪（不探测、不看有没有毛）：活着、实现了 IForgeShearable、不在黑名单、不是别人的（名字、拴绳、驯服的只认机器主人的） */
    public static boolean canShear(LivingEntity entity, @Nullable UUID owner) {
        return entity.isAlive() && entity instanceof IForgeShearable && !entity.getType().is(ECFarmDevices.SHEARING_STATION_BLACKLIST)
                && !ECEntityRules.belongsToOther(entity, owner);
    }

    @Override
    protected void collect(ServerLevel level) {
        UUID owner = machineOwner();
        candidates.addAll(level.getEntitiesOfClass(LivingEntity.class, scanArea(0D), entity -> canShear(entity, owner)));
        // 先去掉没毛可剪的（刚剪过的羊、小羊）再按距离截断（4a 审查 FARM-1）：否则离机器近的光羊会把远处有毛的挤出表外。
        // isShearable 是纯内存判断；放在查询之外做，不在实体分区的遍历里调别的模组的代码
        ItemStack shears = new ItemStack(Items.SHEARS);
        candidates.removeIf(entity -> !((IForgeShearable) entity).isShearable(shears, level, entity.blockPosition()));
        nearestFirst(candidates);
    }

    @Override
    protected boolean process(ServerLevel level) {
        UUID owner = machineOwner();
        ItemStack shears = new ItemStack(Items.SHEARS);
        RandomSource random = level.getRandom();
        boolean unresolved = false;
        for (Iterator<LivingEntity> it = candidates.iterator(); it.hasNext() && !full(); ) {
            LivingEntity entity = it.next();
            if (!canShear(entity, owner) || !(entity instanceof IForgeShearable shearable)) {
                it.remove();
                continue;
            }
            BlockPos pos = entity.blockPosition();
            // 没毛可剪（羊刚剪过、小羊）的不探测
            if (!shearable.isShearable(shears, level, pos)) {
                it.remove();
                continue;
            }
            ECProtection.Verdict verdict = check(pos);
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                unresolved = true;
                continue;
            }
            it.remove();
            if (verdict == ECProtection.Verdict.DENY) {
                continue;
            }
            List<ItemStack> drops = shearable.onSheared(null, shears, level, pos, FORTUNE);
            for (ItemStack drop : drops) {
                ItemEntity dropped = entity.spawnAtLocation(drop, 1.0F);
                if (dropped != null) {
                    dropped.setDeltaMovement(dropped.getDeltaMovement().add(
                            (random.nextFloat() - random.nextFloat()) * 0.1F, random.nextFloat() * 0.05F, (random.nextFloat() - random.nextFloat()) * 0.1F));
                }
            }
            markDone();
        }
        return unresolved;
    }

    @Override
    protected void clearCandidates() {
        candidates.clear();
    }
}
