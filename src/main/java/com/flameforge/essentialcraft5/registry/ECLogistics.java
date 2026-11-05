package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.FacingMachineBlock;
import com.flameforge.essentialcraft5.block.MagicalHopperBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalHopperBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.FilterItem;
import com.flameforge.essentialcraft5.menu.FilterMenu;
import com.flameforge.essentialcraft5.menu.MagicalHopperMenu;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物流模块（第四批 4a，batch4 §3.6）：魔力漏斗、物品过滤器（白名单 / 黑名单）；第六批 6b 加高级过滤器 ×2（batch6b §4.4）。
 * <p>
 * 新旧注册名：magicalHopper → magical_hopper；itemFilter meta 0 → whitelist_filter、meta 2 → blacklist_filter、
 * meta 1 → advanced_whitelist_filter、meta 3 → advanced_blacklist_filter（要虚空板，普通过滤器升级，配方 recipes/logistics/advanced_*.json）。
 * 过滤器原名「魔力仓库节点过滤器」，魔力仓库已去掉，命名表改成「物品过滤器（白名单 / 黑名单）」。
 * <p>
 * 过滤器物品实现框架的 {@code api/IItemFilter}，进物品标签 {@code essentialcraft5:filters}（tools/tags/eclogistics.json）；
 * 以后别的模块要用过滤器，只认接口和标签，不 import 这里的类（原作里另外两个用过滤器的——高级方块破坏器、魔力仓库节点——都已去掉，
 * 现在只有本模块的魔力漏斗在用）。
 * 工作台配方在 data/essentialcraft5/recipes/logistics/，文字在 tools/lang/keys/eclogistics.json，
 * 手册「物流」章 assets/essentialcraft5/guide/logistics.json（ECStorage、以后的 ECMirror 交部件进来）。客户端部分见 client/LogisticsClient。
 */
public final class ECLogistics {

    // ---------------- 魔力漏斗 ----------------

    /** 原作 registerBlockSimple(…, "magicalHopper", 1, 1, 15)：先抗爆后硬度，不透光（普通实心方块） */
    public static final RegistryObject<MagicalHopperBlock> MAGICAL_HOPPER = ECBlocks.register("magical_hopper",
            () -> new MagicalHopperBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true))),
            BlockSpec.of((provider, block) -> {
                // 原作 blockstate magicalhopper.json：原版 orientable（正面朝北），top / side = 强化石，front = 漏斗口；uvlock。
                // 朝上 / 朝下照原作转 x（原作写的 -90 在 1.20 要写 270）
                ModelFile model = provider.models().orientable(provider.name(block), provider.tex("block/fortifiedstone"),
                        provider.tex("block/magicalhopper"), provider.tex("block/fortifiedstone"));
                provider.getVariantBuilder(block).forAllStates(state -> {
                    Direction facing = FacingMachineBlock.facing(state);
                    int x = facing == Direction.DOWN ? 90 : facing == Direction.UP ? 270 : 0;
                    int y = facing.getAxis().isHorizontal() ? ((int) facing.toYRot() + 180) % 360 : 0;
                    return ConfiguredModel.builder().modelFile(model).rotationX(x).rotationY(y).uvLock(true).build();
                });
                provider.simpleBlockItem(block, model);
            }));

    public static final RegistryObject<BlockEntityType<MagicalHopperBlockEntity>> MAGICAL_HOPPER_BE =
            ECBlockEntities.type("magical_hopper", MagicalHopperBlockEntity::new, MAGICAL_HOPPER);

    public static final RegistryObject<MenuType<MagicalHopperMenu>> MAGICAL_HOPPER_MENU =
            ECMenus.register("magical_hopper", MagicalHopperMenu::new);

    // ---------------- 物品过滤器 ----------------

    public static final RegistryObject<FilterItem> WHITELIST_FILTER = ECItems.register("whitelist_filter",
            () -> new FilterItem(new Item.Properties().stacksTo(1), false), ItemSpec.generated("item/itemfilter"));

    public static final RegistryObject<FilterItem> BLACKLIST_FILTER = ECItems.register("blacklist_filter",
            () -> new FilterItem(new Item.Properties().stacksTo(1), true), ItemSpec.generated("item/itemfilterb"));

    /**
     * 高级过滤器（原作 meta 1 / 3）：多三个开关（忽略耐久、忽略 NBT、按标签），界面同一个 {@link #FILTER_MENU}。
     * 普通过滤器 + 4 块虚空板升级，保留 NBT 的有序配方把样本和开关原样带过来（拿白名单当中心做不出高级黑名单）。
     */
    public static final RegistryObject<FilterItem> ADVANCED_WHITELIST_FILTER = ECItems.register("advanced_whitelist_filter",
            () -> new FilterItem(new Item.Properties().stacksTo(1), false, true), ItemSpec.generated("item/itemfilteradvanced"));

    public static final RegistryObject<FilterItem> ADVANCED_BLACKLIST_FILTER = ECItems.register("advanced_blacklist_filter",
            () -> new FilterItem(new Item.Properties().stacksTo(1), true, true), ItemSpec.generated("item/itemfilteradvancedb"));

    /** 白 / 黑名单与两种高级过滤器共用；打开见 {@code HeldItemMenu.open} */
    public static final RegistryObject<MenuType<FilterMenu>> FILTER_MENU = ECMenus.register("filter", FilterMenu::new);

    private ECLogistics() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(MagicalHopperBlockEntity.Config::define);
    }
}
