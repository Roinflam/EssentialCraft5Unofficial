package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 创世神之杖测试用的假随身容器：{@link #enabled} 打开时（只在 {@code BuilderUpgradeTests} 的 batch 里，{@code @BeforeBatch} 开、{@code @AfterBatch} 关），
 * 给几种普通物品挂上 Forge 的物品处理能力，测「随身容器取料」的通用路径（游戏里装真的背包类模组时走的也是这条）。
 * <ul>
 *   <li>收纳袋（{@link Items#BUNDLE}）：9 格的普通容器；纸（{@link Items#PAPER}，能叠的）同样，用来测「数量不是 1 的不碰」；</li>
 *   <li>书与笔（{@link Items#WRITABLE_BOOK}）：9 格，但取出一律返回空（拒绝取出的处理器）；</li>
 *   <li>知识之书（{@link Items#KNOWLEDGE_BOOK}）：9 格，取出时给的是带了 NBT 的那种物品（给错东西的处理器，东西本身不丢）。</li>
 * </ul>
 * 能力挂在物品创建的那一刻，所以要先打开开关再 new 物品。关着时什么都不挂，游戏里不起作用。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class BuilderTestContainers {

    private static final ResourceLocation KEY = EssentialCraft5.id("builder_test_container");

    /** 只在创世神之杖升级测试的 batch 里打开 */
    public static volatile boolean enabled;

    private BuilderTestContainers() {
    }

    @SubscribeEvent
    public static void onBuilderTestAttachCaps(AttachCapabilitiesEvent<ItemStack> event) {
        if (!enabled) {
            return;
        }
        ItemStack stack = event.getObject();
        int kind;
        if (stack.is(Items.BUNDLE) || stack.is(Items.PAPER)) {
            kind = 0;
        } else if (stack.is(Items.WRITABLE_BOOK)) {
            kind = 1;
        } else if (stack.is(Items.KNOWLEDGE_BOOK)) {
            kind = 2;
        } else {
            return;
        }
        event.addCapability(KEY, new Provider(kind));
    }

    /** 测试里拿处理器 */
    public static IItemHandler handler(ItemStack container) {
        return container.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("不是测试容器：" + container));
    }

    private static final class Provider implements ICapabilityProvider {
        private final LazyOptional<IItemHandler> handler;

        Provider(int kind) {
            ItemStackHandler inner = switch (kind) {
                case 1 -> new ItemStackHandler(9) {
                    @Override
                    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                        return ItemStack.EMPTY;
                    }
                };
                case 2 -> new ItemStackHandler(9) {
                    @Override
                    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                        ItemStack out = super.extractItem(slot, amount, simulate);
                        if (!out.isEmpty()) {
                            CompoundTag tag = new CompoundTag();
                            tag.putBoolean("tampered", true);
                            out.setTag(tag);
                        }
                        return out;
                    }
                };
                default -> new ItemStackHandler(9);
            };
            this.handler = LazyOptional.of(() -> inner);
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeCapabilities.ITEM_HANDLER ? handler.cast() : LazyOptional.empty();
        }
    }
}
