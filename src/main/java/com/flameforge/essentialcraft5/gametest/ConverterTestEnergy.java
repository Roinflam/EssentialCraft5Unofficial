package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MRU 转换器测试用的能量接收方：GameTest 世界里没有收 FE 的方块，这里给「登记过的绝对坐标」上的木桶挂一份 {@link ForgeCapabilities#ENERGY}。
 * <p>
 * 用法：先 {@link #expect} 登记绝对坐标（同 {@code ProtectionFrameworkTests.Claims} 的思路，按绝对坐标、不分维度），再在那里放木桶；
 * 木桶的方块实体一建好（构造时收集能力，Forge 的 {@link AttachCapabilitiesEvent}）就挂上登记时给的 {@link Receiver}，拆了重放还是同一份。
 * 测完 {@link #forget} 撤掉登记。没有任何登记时监听直接返回，正式环境里每建一个方块实体只多一次空表判断。
 * <p>
 * 类名别叫 Events / Registration（Forge 按包名 + 简单类名 + 方法名 + 事件类型认监听器，同一个包里撞名只调一个；提交前跑 tools/check_event_handlers.py）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ConverterTestEnergy {

    private static final ResourceLocation KEY = EssentialCraft5.id("converter_test_energy");
    private static final Map<BlockPos, Receiver> EXPECTED = new ConcurrentHashMap<>();

    private ConverterTestEnergy() {
    }

    /** 测试用的接收方：Forge 自带的 {@link EnergyStorage}（每次最多收 maxReceive，不往外给），另外记一个实收合计 */
    public static final class Receiver extends EnergyStorage {
        private long received;
        private int calls;

        public Receiver(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int got = super.receiveEnergy(maxReceive, simulate);
            if (!simulate) {
                received += got;
                calls++;
            }
            return got;
        }

        /** 实收合计（不算模拟） */
        public long received() {
            return received;
        }

        /** 被推过几次（不算模拟） */
        public int calls() {
            return calls;
        }

        /** 测试里直接改存量（模拟「满了」「被用掉了」） */
        public void setStored(int amount) {
            energy = Math.max(0, Math.min(amount, capacity));
        }
    }

    /** 登记一个绝对坐标：之后在那里建的木桶挂上返回的接收方 */
    public static Receiver expect(BlockPos absolute, int capacity, int maxReceive) {
        Receiver receiver = new Receiver(capacity, maxReceive);
        EXPECTED.put(absolute.immutable(), receiver);
        return receiver;
    }

    /** 撤掉登记（已经挂上的木桶不受影响，拆了以后重放就没有了） */
    public static void forget(BlockPos absolute) {
        EXPECTED.remove(absolute);
    }

    @SubscribeEvent
    public static void converterTestEnergyAttach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (EXPECTED.isEmpty() || !(event.getObject() instanceof BarrelBlockEntity barrel)) {
            return;
        }
        Receiver receiver = EXPECTED.get(barrel.getBlockPos());
        if (receiver == null) {
            return;
        }
        LazyOptional<Receiver> optional = LazyOptional.of(() -> receiver);
        event.addCapability(KEY, new ICapabilityProvider() {
            @Override
            public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                return cap == ForgeCapabilities.ENERGY ? optional.cast() : LazyOptional.empty();
            }
        });
        event.addListener(optional::invalidate);
    }
}
