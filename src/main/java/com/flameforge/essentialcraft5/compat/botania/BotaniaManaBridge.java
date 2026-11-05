package com.flameforge.essentialcraft5.compat.botania;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaCollector;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;

/**
 * {@link ManaBridge} 的植物魔法实现。<b>全工程只有这个类引用 {@code vazkii.*}</b>，只由 {@link ManaBridge#get()} 在装了植物魔法时按类名反射加载；
 * 别的类不许写它的类名常量以外的任何引用（{@code new}、强转、instanceof、方法签名都不行）。
 * <p>
 * 编译期只挂植物魔法的 api 构件（BlameJared maven，build.gradle 的 compileOnly），用到的只有 {@link ManaReceiver}、{@link ManaPool}、
 * {@link ManaCollector} 和 {@link BotaniaForgeCapabilities#MANA_RECEIVER}：1.20.1 的植物魔法 Forge 版给自己的魔力池、发射器、
 * 通量场、符文祭坛等方块实体挂的就是这个能力（{@code ForgeCommonInitializer.attachBeCaps}）。不对方块实体做 instanceof，一律查能力，
 * 别的模组的魔力方块只要挂了这个能力也认。
 * <p>
 * 推多少：接收方是魔力池或魔力收集器（发射器）时最多推到它的上限（它们的 {@code receiveMana} 把超出上限的部分丢掉）；
 * 别的接收方（符文祭坛、泰拉凝聚板、魔力附魔台、酿造台、通量场）推调用方给的量。
 * <p>
 * 记账按<b>实收</b>：推之前、推之后各读一次 {@code getCurrentMana()}，只算增量（不超过推出去的量），没收进去的由调用方留在缓存里。
 * 被接收方丢掉的那部分本来就没存进去，留在缓存不会多出魔力。456 版 javap 核过：通量场 {@code min(12 800, 能量 + x × 10)}、
 * 符文祭坛 / 附魔台 / 酿造台封到这一份配方要的量，快满时都会截掉一截，按推出去的量记账的话转换器白吃 MRU。
 * 通量场的存量是 {@code 能量 / 10} 取整，截掉时增量最多多算不到 1 魔力（只会多扣，不会多出）。泰拉凝聚板不封顶（{@code max(0, mana + x)}），
 * 凝出泰拉钢前最后一刻多推的那一点连同这一份一起清掉，每刻上限默认只有几十魔力，可以忽略。
 * <p>
 * 推完存量不动的接收方认不出收了多少：魔力分配器（{@code getCurrentMana} 恒为 0，收到的按池子数整除分下去，除不尽的丢掉）、
 * 魔力销毁器（Mana Void，收多少吞多少）。这一次照推出去的量记账（宁可算它收了，也不让魔力凭空多出来），返回值带 {@link #BLIND}，
 * 转换器此后不再往这一面推，直到这一面的邻居变了。
 */
public final class BotaniaManaBridge extends ManaBridge {

    public BotaniaManaBridge() {
        // 先碰一下能力字段：api 和运行时的植物魔法对不上时在这里就抛 LinkageError，ManaBridge.create 退回空实现
        if (BotaniaForgeCapabilities.MANA_RECEIVER == null) {
            throw new IllegalStateException("植物魔法的魔力接收能力还没登记");
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public @Nullable Object find(BlockEntity blockEntity, Direction side) {
        if (blockEntity.isRemoved()) {
            return null;
        }
        LazyOptional<ManaReceiver> cap = blockEntity.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, side);
        return cap.isPresent() ? cap : null;
    }

    @Override
    public boolean isPresent(Object handle) {
        return receiver(handle) != null;
    }

    @Override
    public int accept(Object handle, int amount) {
        if (amount <= 0) {
            return 0;
        }
        ManaReceiver receiver = receiver(handle);
        if (receiver == null || !receiver.canReceiveManaFromBursts() || receiver.isFull()) {
            return 0;
        }
        int give = amount;
        int max = maxOf(receiver);
        int before = receiver.getCurrentMana();
        if (max >= 0) {
            give = Math.min(give, Math.max(0, max - before));
        }
        if (give <= 0) {
            return 0;
        }
        receiver.receiveMana(give);
        long got = (long) receiver.getCurrentMana() - before;
        if (got <= 0L) {
            // 存量没动：分配器、魔力销毁器这类，收进去多少认不出来（见类注释）
            return give | BLIND;
        }
        return (int) Math.min(give, got);
    }

    @Override
    public void onInvalidate(Object handle, Runnable listener) {
        LazyOptional<?> cap = optional(handle);
        if (cap != null) {
            cap.addListener(invalidated -> listener.run());
        }
    }

    @Override
    public int currentMana(Object handle) {
        ManaReceiver receiver = receiver(handle);
        return receiver == null ? 0 : receiver.getCurrentMana();
    }

    @Override
    public int maxMana(Object handle) {
        ManaReceiver receiver = receiver(handle);
        return receiver == null ? -1 : maxOf(receiver);
    }

    @Override
    public boolean receiving(Object handle) {
        ManaReceiver receiver = receiver(handle);
        return receiver != null && receiver.canReceiveManaFromBursts() && !receiver.isFull();
    }

    /** 魔力池、魔力收集器（发射器）有上限；别的返回 −1 */
    private static int maxOf(ManaReceiver receiver) {
        if (receiver instanceof ManaPool pool) {
            return pool.getMaxMana();
        }
        if (receiver instanceof ManaCollector collector) {
            return collector.getMaxMana();
        }
        return -1;
    }

    @Nullable
    private static LazyOptional<?> optional(Object handle) {
        return handle instanceof LazyOptional<?> cap ? cap : null;
    }

    @Nullable
    private static ManaReceiver receiver(Object handle) {
        LazyOptional<?> cap = optional(handle);
        if (cap == null) {
            return null;
        }
        Object value = cap.resolve().orElse(null);
        return value instanceof ManaReceiver receiver ? receiver : null;
    }
}
