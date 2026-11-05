package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.CreativeVariants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 创造模式物品栏。原作靠 DummyCore 给每个模组建「方块」「物品」两页，这里照样分两页，
 * 第五批加第三页「装饰」（F5-3：花纹方块、花纹拟态、魔力凿，规格上标 {@code .decoration()} 的；不塞进方块页，那会从 100 个变成 212 个）。
 * 物品实现 {@link CreativeVariants} 可以放多个变体（比如 MRU 储存物品的空/满两份，原作 getSubItems 的做法）。
 * 装饰页没有东西时原版自动不显示这一页（{@code CreativeModeTab.shouldDisplay}）。
 */
public final class ECCreativeTabs {

    public static final RegistryObject<CreativeModeTab> BLOCKS = ECRegistries.CREATIVE_TABS.register("blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + EssentialCraft5.MODID + ".blocks"))
                    .icon(() -> new ItemStack(ECBlocks.MOON_WELL.get()))
                    .displayItems((params, output) -> ECBlocks.entries().stream()
                            .filter(entry -> entry.spec().inCreativeTab() && !entry.spec().isDecoration())
                            .forEach(entry -> acceptBlock(entry, output)))
                    .build());

    public static final RegistryObject<CreativeModeTab> ITEMS = ECRegistries.CREATIVE_TABS.register("items",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + EssentialCraft5.MODID + ".items"))
                    .icon(() -> new ItemStack(ECItems.BOUND_GEM.get()))
                    .withTabsBefore(BLOCKS.getKey())
                    .displayItems((params, output) -> {
                        for (ECItems.Entry entry : ECItems.entries()) {
                            if (entry.spec().inCreativeTab() && !entry.spec().isDecoration()) {
                                acceptItem(entry, output);
                            }
                        }
                    })
                    .build());

    /** 装饰页图标：魔力凿（第五批 ECDecorations 注册）；还没接线时用拟态方块 */
    private static final ResourceLocation DECORATIONS_ICON = EssentialCraft5.id("magical_chisel");

    public static final RegistryObject<CreativeModeTab> DECORATIONS = ECRegistries.CREATIVE_TABS.register("decorations",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + EssentialCraft5.MODID + ".decorations"))
                    .icon(() -> ForgeRegistries.ITEMS.containsKey(DECORATIONS_ICON)
                            ? new ItemStack(ForgeRegistries.ITEMS.getValue(DECORATIONS_ICON))
                            : new ItemStack(ECRedstoneTools.MIMIC.get()))
                    .withTabsBefore(ITEMS.getKey())
                    .displayItems((params, output) -> {
                        // 先放工具（魔力凿），再放方块（花纹方块按注册顺序 = 底材为主、花纹按原作 meta 顺序，然后花纹拟态）
                        for (ECItems.Entry entry : ECItems.entries()) {
                            if (entry.spec().inCreativeTab() && entry.spec().isDecoration()) {
                                acceptItem(entry, output);
                            }
                        }
                        ECBlocks.entries().stream()
                                .filter(entry -> entry.spec().inCreativeTab() && entry.spec().isDecoration())
                                .forEach(entry -> acceptBlock(entry, output));
                    })
                    .build());

    private ECCreativeTabs() {
    }

    private static void acceptBlock(ECBlocks.Entry entry, CreativeModeTab.Output output) {
        // 方块物品也可以有变体（元素水晶按大小、元素放一排，原作 getSubItems）
        Item item = entry.block().get().asItem();
        if (item instanceof CreativeVariants variants) {
            variants.creativeVariants().forEach(output::accept);
        } else {
            output.accept(entry.block().get());
        }
    }

    private static void acceptItem(ECItems.Entry entry, CreativeModeTab.Output output) {
        Item item = entry.item().get();
        if (item instanceof CreativeVariants variants) {
            variants.creativeVariants().forEach(output::accept);
        } else {
            output.accept(item);
        }
    }

    static void init() {
    }
}
