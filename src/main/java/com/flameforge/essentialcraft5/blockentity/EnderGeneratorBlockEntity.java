package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.EnderGeneratorMenu;
import com.flameforge.essentialcraft5.registry.ECSolar;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 末影产能器。对应原作 TileEnderGenerator（原样保留）。
 * <p>
 * 没有红石信号时每刻：把周围 8 格（17×17×17 的方盒）里的末影人拉到自己头顶（x+0.5, y+1, z+0.5），
 * 再对 ±2 格方盒里的末影人各打 1 点魔法伤害，每打中一次（hurt 返回 true）产出 500 MRU。
 * 末影人有 10 刻无敌帧，所以同一只每 10 刻最多打中一次；挨打（不管打没打中）后九成几率瞬移走，瞬移落点还在 8 格内就会被拉回来。
 * 平衡值和热力产能器一样放下时随机一次（{@link RandomBalanceBlockEntity}），容量 1 万。满了也照打（同原作）。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>删掉 Hardcore Ender Expansion 联动（按类名反射抓它的五种末影人）；原版 EnderMan 的子类照样算。</li>
 *   <li>性能：原作每刻查两次实体（17³ 和 4³ 的方盒）。这里大范围只每 {@link #SCAN_INTERVAL} 刻查一次，
 *       查到的末影人记在列表里，每刻照原作的顺序对列表里的逐个「拉回、再打」——被打后瞬移走的末影人下一刻照样被拉回来，
 *       产量和原作一样；只是新走进范围的末影人最多晚 3 刻被发现。</li>
 *   <li>平衡值放下时随机一次并存盘（原作 firstTick 不存盘，每次加载都重新随机）。</li>
 *   <li><b>性能修正（全模组安全原则 P2，policy.md §5，第三批回补 R）</b>：原作几台产能器挨在一起时，同一只末影人每刻被每一台各传送一次
 *       （拉到 A 头顶、再拉到 B 头顶……），100 台 × 50 只 = 每刻 5 000 次传送、每次都发位置包。现在每只末影人每刻最多被一台拉
 *       （静态 {@link #PULLED}：末影人 → 最后一次被认领的游戏时间，只在服务端线程读写）；已经站在某台头顶的也算被那台认领，
 *       别的产能器这刻不再去抢，所以末影人会留在先 tick 到的那台头顶，不再来回闪。
 *       打（±2 格内每台照旧各打一次，无敌帧决定实际打中几次）完全不变，单台的产量和原作一样。</li>
 *   <li>起了名字、拴着绳的末影人不拉也不打（第十批权限修补 A7，{@link #isValid}）：范围会伸进邻居的领地，别人起了名字留着的末影人以前会被拉过来打。</li>
 * </ul>
 */
public class EnderGeneratorBlockEntity extends RandomBalanceBlockEntity {

    /** 原作 tileentities.endergenerator */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.IntValue mruGenerated;
        public static ForgeConfigSpec.IntValue radius;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("ender_generator");
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU, 1, Integer.MAX_VALUE);
            balance = builder.comment("Balance of the MRU produced. -1 = random in [0, 2), rolled once when the generator is placed.",
                            "产出 MRU 的平衡值。-1 表示放下时在 [0, 2) 里随机一次")
                    .defineInRange("balance", RANDOM, -1D, 2D);
            mruGenerated = builder.comment("MRU per successful hit on an Enderman.", "每打中末影人一次的产量")
                    .defineInRange("mru_generated", 500, 0, 1_000_000);
            radius = builder.comment("Endermen within this many blocks are pulled on top of the generator.",
                            "多少格内的末影人会被拉到产能器头顶")
                    .defineInRange("radius", 8, 0, 32);
            builder.pop();
        }
    }

    /** 大范围找末影人的间隔（刻） */
    public static final int SCAN_INTERVAL = 4;
    /** 打末影人的范围：±2 格（原作 AABB(pos-2, pos+2)） */
    private static final int ATTACK_RANGE = 2;
    private static final float ATTACK_DAMAGE = 1F;

    /**
     * 本刻已经被某台末影产能器认领（拉过来，或者本来就站在它头顶）的末影人 → 认领时的游戏时间。
     * 只在服务端线程读写；弱引用，末影人没了自动清掉。Entity 的 equals / hashCode 按实体 id，不会认错。
     */
    private static final Map<EnderMan, Long> PULLED = new WeakHashMap<>();

    /** 最近一次大范围查到的末影人（不存盘，每 SCAN_INTERVAL 刻重建） */
    private final List<EnderMan> tracked = new ArrayList<>();
    private int scanCooldown;
    /** 这台一共传送过几次末影人（不存盘，只给 GameTest 看叠放时有没有抢来抢去） */
    private int teleports;

    public EnderGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ECSolar.ENDER_GENERATOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 0);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected void serverTick() {
        applyBalance(ECConfig.get(Config.balance));
        if (level == null || neighborPowered()) {
            tracked.clear();
            scanCooldown = 0;
            return;
        }
        if (scanCooldown > 0 && tracked.isEmpty()) {
            // 没到下次大范围查找、手上也没有追踪着的末影人：什么都不用算（范围盒也不用建）
            scanCooldown--;
            return;
        }
        int x = worldPosition.getX();
        int y = worldPosition.getY();
        int z = worldPosition.getZ();
        int r = ECConfig.get(Config.radius);
        AABB catchBox = new AABB(x - r, y - r, z - r, x + r + 1, y + r + 1, z + r + 1);
        AABB attackBox = new AABB(x - ATTACK_RANGE, y - ATTACK_RANGE, z - ATTACK_RANGE,
                x + ATTACK_RANGE, y + ATTACK_RANGE, z + ATTACK_RANGE);
        if (scanCooldown-- <= 0) {
            scanCooldown = SCAN_INTERVAL - 1;
            tracked.clear();
            // 半径配得小于 2 时打的范围比拉的范围大，两个一起查
            tracked.addAll(level.getEntitiesOfClass(EnderMan.class, catchBox.minmax(attackBox)));
        }
        if (tracked.isEmpty()) {
            return;
        }
        double tx = x + 0.5D;
        double ty = y + 1D;
        double tz = z + 0.5D;
        long now = level.getGameTime();
        for (EnderMan enderman : tracked) {
            // 这刻已经被别的产能器认领的不再拉（P2：叠放时每只每刻最多传送一次）
            if (isValid(enderman) && enderman.getBoundingBox().intersects(catchBox) && claim(enderman, now)
                    && enderman.distanceToSqr(tx, ty, tz) > 1.0E-4D) {
                enderman.teleportTo(tx, ty, tz);
                teleports++;
            }
        }
        int generated = ECConfig.get(Config.mruGenerated);
        for (EnderMan enderman : tracked) {
            if (isValid(enderman) && enderman.getBoundingBox().intersects(attackBox)
                    && enderman.hurt(level.damageSources().magic(), ATTACK_DAMAGE)) {
                mru.addMRU(generated, true);
            }
        }
    }

    /**
     * 认领这只末影人这一刻的「拉」：这刻还没有别的产能器认领过就记下并返回 true，否则 false。
     * 只在服务端线程调（产能器的 serverTick、GameTest）。
     */
    public static boolean claim(EnderMan enderman, long gameTime) {
        Long last = PULLED.get(enderman);
        if (last != null && last == gameTime) {
            return false;
        }
        PULLED.put(enderman, gameTime);
        return true;
    }

    /** 这台一共传送过几次末影人（不存盘；GameTest 用） */
    public int getTeleportCount() {
        return teleports;
    }

    /**
     * 列表里的末影人可能已经死了、被移除、换了维度；起了名字、拴着绳的（{@link ECEntityRules#isSpared}）不拉也不打
     * （第十批权限修补 A7：8 格的范围会伸进邻居的领地，别人特意留着的末影人不该被拉走）
     */
    private boolean isValid(EnderMan enderman) {
        return enderman.isAlive() && enderman.level() == level && !ECEntityRules.isSpared(enderman);
    }

    /** 这只末影人这台会不会拉、会不会打（GameTest 用；不看范围） */
    public boolean handles(EnderMan enderman) {
        return isValid(enderman);
    }

    /** 每打中一次的产量（界面显示用，同原作 GuiMRUGenerated 的 "enderGenerator" 分支） */
    public int getGeneration() {
        return ECConfig.get(Config.mruGenerated);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new EnderGeneratorMenu(id, inventory, this);
    }
}
