package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.CrafterBlock;
import com.flameforge.essentialcraft5.blockentity.CrafterBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.CraftingFrameItem;
import com.flameforge.essentialcraft5.menu.CrafterMenu;
import com.flameforge.essentialcraft5.menu.CraftingFrameMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 自动合成模块（第四批 4a，batch4 §3.3）：魔力合成器、合成框架。
 * <p>
 * 新旧注册名：device（meta 2，BlockRedstoneDeviceNotSided 按子类型拆开）→ crafter；craftingFrame → crafting_frame。
 * 方块实体、菜单同名 crafter；合成框架的菜单 crafting_frame。原作合成器 Material.ROCK、硬度 1、抗爆 1（BlocksCore L168 先硬度后抗爆），整块不透明。
 * <p>
 * 配方在 data/essentialcraft5/recipes/crafter/，文字在 tools/lang/keys/eccrafter.json，手册「自动化」章 assets/essentialcraft5/guide/automation.json
 * （本模块拥有这一章；ECRedstoneTools 交部件进来）。客户端部分见 client/CrafterClient。
 */
public final class ECCrafter {

    // ---------------- 魔力合成器 ----------------

    public static final RegistryObject<CrafterBlock> CRAFTER = ECBlocks.register("crafter",
            () -> new CrafterBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, false))),
            BlockSpec.of(BlockSpec.cubeAll("block/crafter")));

    public static final RegistryObject<BlockEntityType<CrafterBlockEntity>> CRAFTER_BE =
            ECBlockEntities.type("crafter", CrafterBlockEntity::new, CRAFTER);

    public static final RegistryObject<MenuType<CrafterMenu>> CRAFTER_MENU =
            ECMenus.register("crafter", CrafterMenu::new);

    // ---------------- 合成框架 ----------------

    public static final RegistryObject<CraftingFrameItem> CRAFTING_FRAME = ECItems.register("crafting_frame",
            () -> new CraftingFrameItem(new Item.Properties().stacksTo(1)),
            ItemSpec.generated("item/itemcrafter"));

    public static final RegistryObject<MenuType<CraftingFrameMenu>> CRAFTING_FRAME_MENU =
            ECMenus.register("crafting_frame", CraftingFrameMenu::new);

    private ECCrafter() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(CrafterBlockEntity.Config::define);
    }
}
