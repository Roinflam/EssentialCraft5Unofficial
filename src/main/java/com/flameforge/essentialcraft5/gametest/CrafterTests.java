package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.CrafterBlockEntity;
import com.flameforge.essentialcraft5.menu.CrafterMenu;
import com.flameforge.essentialcraft5.menu.CraftingFrameMenu;
import com.flameforge.essentialcraft5.menu.GhostSlots;
import com.flameforge.essentialcraft5.menu.HeldItemMenu;
import com.flameforge.essentialcraft5.recipe.CraftingPattern;
import com.flameforge.essentialcraft5.recipe.CraftingRecipeIndex;
import com.flameforge.essentialcraft5.registry.ECCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动合成模块（ECCrafter，batch4 §3.3）的测试：魔力合成器、合成框架。
 * <p>
 * 站位（{@code empty}，5×4×5）：合成器在正中 (2, 1, 2)，红石块放在东边 (3, 1, 2)。多数测试直接调 {@link CrafterBlockEntity#craftOnce}（同步、确定）；
 * 上升沿和最小间隔用真的红石块走一遍方块的计划刻。框架菜单用 {@link ProtectionFrameworkTests#realPlayer} 直接 new 菜单、调 {@code clicked}。
 * 用到的都是原版配方（木棍、工作台、熔炉、蛋糕、蜂蜜块、橡木楼梯）。
 * 全部放在自己的批次 {@value #BATCH}：剩余物掉落的测试要数机器顶上的掉落物，不和会搬掉落物的模块（漏斗、洗牌器）同时跑。
 * <p>
 * 合成器只动自己的物品栏、不需要主人（policy §1.1「不算触发」）：{@link #crafterNeedsNoOwnerOrClaim} 测没有主人、脚下被圈成别人领地时照常合成、也不发探测。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CrafterTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "eccrafter";
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);
    private static final BlockPos POWER = new BlockPos(3, 1, 2);

    private static final ResourceLocation STICK = ResourceLocation.withDefaultNamespace("stick");
    private static final ResourceLocation FURNACE = ResourceLocation.withDefaultNamespace("furnace");

    /** 菜单槽号：样本 0~8、产物 9，背包 27 格从 10 起，快捷栏 k 是 37 + k */
    private static final int MENU_INVENTORY_START = CraftingFrameMenu.SLOT_RESULT + 1;

    private CrafterTests() {
    }

    // ------------------------------------------------------------------ 工具

    private static CrafterBlockEntity place(GameTestHelper helper) {
        helper.setBlock(MACHINE, ECCrafter.CRAFTER.get().defaultBlockState());
        BlockEntity be = helper.getBlockEntity(MACHINE);
        if (!(be instanceof CrafterBlockEntity crafter)) {
            throw new GameTestAssertException("合成器应带 CrafterBlockEntity，实际 " + be);
        }
        return crafter;
    }

    private static void put(CrafterBlockEntity be, int slot, ItemStack stack) {
        be.getInventory().setStackInSlot(slot, stack);
    }

    private static ItemStack cell(CrafterBlockEntity be, int slot) {
        return be.getInventory().getStackInSlot(slot);
    }

    private static IItemHandler automation(CrafterBlockEntity be) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("合成器应暴露物品栏"));
    }

    /** 照 Forge 的漏斗（VanillaInventoryCodeHooks.putStackInInventoryAllSlots）逐格试着塞一个；返回塞没塞进去 */
    private static boolean hopperInsert(IItemHandler handler, ItemStack one) {
        ItemStack stack = one.copy();
        for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++) {
            if (handler.insertItem(slot, stack, true).isEmpty()) {
                stack = handler.insertItem(slot, stack, false);
            }
        }
        return stack.isEmpty();
    }

    /** 一件写好样板的框架 */
    private static ItemStack frame(ItemStack[] samples, CraftingPattern.Mode mode, ResourceLocation recipe) {
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingPattern.write(frame, samples, mode, recipe);
        return frame;
    }

    /** 木棍的样板：0、3 号格（第一列上下两格）放木板 */
    private static ItemStack[] stickSamples(Item plank) {
        ItemStack[] samples = CraftingPattern.emptySamples();
        samples[0] = new ItemStack(plank);
        samples[3] = new ItemStack(plank);
        return samples;
    }

    /** 熔炉的样板：除正中外 8 格圆石 */
    private static ItemStack[] furnaceSamples() {
        ItemStack[] samples = CraftingPattern.emptySamples();
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                samples[i] = new ItemStack(Items.COBBLESTONE);
            }
        }
        return samples;
    }

    private static void requireRecipe(GameTestHelper helper, ResourceLocation id) {
        if (CraftingPattern.craftingRecipe(helper.getLevel().getRecipeManager(), id) == null) {
            throw new GameTestAssertException("测试依赖的原版配方 " + id + " 不存在");
        }
    }

    private static List<ItemEntity> itemsAbove(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(MACHINE);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(1.5D, 2D, 1.5D));
    }

    /** 真玩家拿着一件空框架（快捷栏 0 号，选中）并打开它的菜单 */
    private static CraftingFrameMenu openFrame(ServerPlayer player, ItemStack frame, InteractionHand hand) {
        Inventory inventory = player.getInventory();
        inventory.selected = 0;
        if (hand == InteractionHand.MAIN_HAND) {
            inventory.setItem(0, frame);
        } else {
            inventory.setItem(Inventory.SLOT_OFFHAND, frame);
        }
        return new CraftingFrameMenu(0, inventory, hand);
    }

    private static int hotbarMenuSlot(int k) {
        return MENU_INVENTORY_START + 27 + k;
    }

    // ==================================================================
    // 合成器
    // ==================================================================

    /** 常亮的红石只合成一次；64 个原料每格只扣 1 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterCraftsOncePerRisingEdge(GameTestHelper helper) {
        CrafterBlockEntity be = place(helper);
        put(be, 0, new ItemStack(Items.OAK_PLANKS, 64));
        put(be, 3, new ItemStack(Items.OAK_PLANKS, 64));
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenExecuteAfter(12, () -> {
                    ItemStack output = cell(be, CrafterBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(output.is(Items.STICK) && output.getCount() == 4, "常亮信号应只合成一次（4 根木棍），实际 " + output);
                    helper.assertTrue(cell(be, 0).getCount() == 63 && cell(be, 3).getCount() == 63, "每格应只扣 1 个");
                })
                .thenSucceed();
    }

    /** 相隔 2 刻的两个上升沿：第二次推迟到上一次之后满一个间隔才合成，一共合两次 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterMinInterval(GameTestHelper helper) {
        CrafterBlockEntity be = place(helper);
        put(be, 0, new ItemStack(Items.OAK_PLANKS, 64));
        put(be, 3, new ItemStack(Items.OAK_PLANKS, 64));
        int interval = be.pulseGate().interval();
        long[] first = new long[1];
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_OUTPUT).getCount() == 4, "第一个上升沿应合成一次");
                    first[0] = be.pulseGate().lastFire();
                    // 同一刻里先断后通：第二个上升沿
                    helper.setBlock(POWER, Blocks.AIR);
                    helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(1, () -> helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_OUTPUT).getCount() == 4,
                        "间隔没到时第二个上升沿应推迟"))
                .thenWaitUntil(() -> helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_OUTPUT).getCount() == 8, "第二个上升沿最后应合成"))
                .thenExecute(() -> helper.assertTrue(be.pulseGate().lastFire() - first[0] == interval,
                        "第二次应正好在上一次之后 " + interval + " 刻合成，实际隔 " + (be.pulseGate().lastFire() - first[0])))
                .thenSucceed();
    }

    /** 产物格：同种叠加；叠不下、或是别的东西时不合成、也不扣原料 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterStacksOutputNoOverwrite(GameTestHelper helper) {
        CrafterBlockEntity be = place(helper);
        put(be, 0, new ItemStack(Items.OAK_PLANKS, 10));
        put(be, 3, new ItemStack(Items.OAK_PLANKS, 10));
        put(be, CrafterBlockEntity.SLOT_OUTPUT, new ItemStack(Items.STICK, 60));
        helper.assertTrue(be.craftOnce(), "60 根木棍再加 4 根应放得下");
        helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_OUTPUT).getCount() == 64, "产物应叠到 64");
        helper.assertFalse(be.craftOnce(), "满了不应再合成");
        helper.assertTrue(cell(be, 0).getCount() == 9 && cell(be, 3).getCount() == 9, "放不下时不应扣原料");

        put(be, CrafterBlockEntity.SLOT_OUTPUT, new ItemStack(Items.STICK, 61));
        helper.assertFalse(be.craftOnce(), "61 + 4 超过 64，不应合成");
        put(be, CrafterBlockEntity.SLOT_OUTPUT, new ItemStack(Items.DIRT));
        helper.assertFalse(be.craftOnce(), "产物格里是别的东西时不应合成（原作会覆盖）");
        helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_OUTPUT).is(Items.DIRT) && cell(be, 0).getCount() == 9, "什么都不应变");
        helper.succeed();
    }

    /** 蛋糕：3 个牛奶桶用完，空桶回到原来那格 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterReturnsContainer(GameTestHelper helper) {
        CrafterBlockEntity be = place(helper);
        for (int i = 0; i < 3; i++) {
            put(be, i, new ItemStack(Items.MILK_BUCKET));
            put(be, 6 + i, new ItemStack(Items.WHEAT, 5));
        }
        put(be, 3, new ItemStack(Items.SUGAR, 5));
        put(be, 4, new ItemStack(Items.EGG, 5));
        put(be, 5, new ItemStack(Items.SUGAR, 5));
        helper.assertTrue(be.craftOnce(), "蛋糕应能合成");
        helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_OUTPUT).is(Items.CAKE), "产物应是蛋糕");
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(cell(be, i).is(Items.BUCKET) && cell(be, i).getCount() == 1, "第 " + i + " 格应放回空桶，实际 " + cell(be, i));
            helper.assertTrue(cell(be, 6 + i).getCount() == 4, "小麦每格应只扣 1");
        }
        helper.assertTrue(cell(be, 4).getCount() == 4 && cell(be, 3).getCount() == 4, "蛋、糖每格应只扣 1");
        helper.assertTrue(itemsAbove(helper).isEmpty(), "放得回原格的剩余物不应掉出来");
        helper.succeed();
    }

    /** 蜂蜜块：一格 2 瓶蜂蜜，用掉一瓶后空瓶放不回原格，也并不进产物格，从机器顶上掉出来 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterDropsLeftoverOnTop(GameTestHelper helper) {
        CrafterBlockEntity be = place(helper);
        for (int i : new int[] {0, 1, 3, 4}) {
            put(be, i, new ItemStack(Items.HONEY_BOTTLE, 2));
        }
        helper.assertTrue(be.craftOnce(), "蜂蜜块应能合成");
        helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_OUTPUT).is(Items.HONEY_BLOCK), "产物应是蜂蜜块");
        int bottles = 0;
        for (ItemEntity item : itemsAbove(helper)) {
            if (item.getItem().is(Items.GLASS_BOTTLE)) {
                bottles += item.getItem().getCount();
                helper.assertTrue(item.getY() >= helper.absolutePos(MACHINE).getY() + 1, "空瓶应从机器顶上出来");
            }
            item.discard();
        }
        helper.assertTrue(bottles == 4, "4 个空瓶应从顶上掉出来，实际 " + bottles);
        helper.assertTrue(cell(be, 0).is(Items.HONEY_BOTTLE) && cell(be, 0).getCount() == 1, "每格应还剩 1 瓶蜂蜜");
        helper.succeed();
    }

    /** 空框架当没装：红石照样按格子里的东西合成，不抛异常（原作每 tick 空指针） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterEmptyFrameNoCrash(GameTestHelper helper) {
        CrafterBlockEntity be = place(helper);
        put(be, CrafterBlockEntity.SLOT_FRAME, new ItemStack(ECCrafter.CRAFTING_FRAME.get()));
        put(be, 0, new ItemStack(Items.OAK_PLANKS, 4));
        put(be, 3, new ItemStack(Items.OAK_PLANKS, 4));
        helper.assertTrue(be.frameState() == CraftingPattern.State.NONE, "空框架应当成没装");
        helper.assertTrue(automation(be).insertItem(0, new ItemStack(Items.OAK_PLANKS), true).isEmpty(), "空框架时漏斗应照没装框架的规则补料");
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenExecuteAfter(4, () -> helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_OUTPUT).getCount() == 4, "空框架时应照常合成"))
                .thenSucceed();
    }

    /** 框架的配方不存在（或样本配不上它）：失效，不合成、不扣料，玩家和漏斗都放不进合成格 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterInvalidFrameIdles(GameTestHelper helper) {
        CrafterBlockEntity be = place(helper);
        put(be, CrafterBlockEntity.SLOT_FRAME, frame(stickSamples(Items.OAK_PLANKS), CraftingPattern.Mode.INGREDIENT,
                EssentialCraft5.id("crafter_test_missing_recipe")));
        put(be, 0, new ItemStack(Items.OAK_PLANKS, 4));
        put(be, 3, new ItemStack(Items.OAK_PLANKS, 4));
        helper.assertTrue(be.frameState() == CraftingPattern.State.INVALID, "配方不存在的框架应失效");
        helper.assertFalse(be.craftOnce(), "失效框架不应合成");
        helper.assertTrue(cell(be, 0).getCount() == 4 && cell(be, CrafterBlockEntity.SLOT_OUTPUT).isEmpty(), "不应扣料");
        helper.assertFalse(be.isItemValid(1, new ItemStack(Items.OAK_PLANKS)), "失效框架时玩家不应能往合成格放东西");
        helper.assertFalse(hopperInsert(automation(be), new ItemStack(Items.OAK_PLANKS)), "失效框架时漏斗不应能补料");

        // 样本配不上记下的配方（木棍的配方 + 石头样本）同样失效
        ItemStack[] wrong = CraftingPattern.emptySamples();
        wrong[0] = new ItemStack(Items.STONE);
        put(be, CrafterBlockEntity.SLOT_FRAME, frame(wrong, CraftingPattern.Mode.INGREDIENT, STICK));
        helper.assertTrue(be.frameState() == CraftingPattern.State.INVALID, "样本配不上配方的框架应失效");
        helper.assertFalse(be.craftOnce(), "失效框架不应合成");
        helper.succeed();
    }

    /** 精确模式：只收和样本一样的木板 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterFrameExactRejectsOthers(GameTestHelper helper) {
        requireRecipe(helper, STICK);
        CrafterBlockEntity be = place(helper);
        put(be, CrafterBlockEntity.SLOT_FRAME, frame(stickSamples(Items.OAK_PLANKS), CraftingPattern.Mode.EXACT, STICK));
        helper.assertTrue(be.frameState() == CraftingPattern.State.VALID, "木棍框架应有效");
        helper.assertTrue(be.isItemValid(0, new ItemStack(Items.OAK_PLANKS)), "精确模式应收橡木木板");
        helper.assertFalse(be.isItemValid(0, new ItemStack(Items.BIRCH_PLANKS)), "精确模式不应收白桦木板");
        helper.assertFalse(be.isItemValid(1, new ItemStack(Items.OAK_PLANKS)), "样本空着的格子什么都不收");
        helper.assertFalse(hopperInsert(automation(be), new ItemStack(Items.BIRCH_PLANKS)), "漏斗也不应送进白桦木板");
        helper.assertTrue(hopperInsert(automation(be), new ItemStack(Items.OAK_PLANKS)), "漏斗应能送进橡木木板");
        helper.succeed();
    }

    /** 按配方原料：做木棍的格子收白桦和橡木木板，混着放也能合成 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterFrameIngredientAcceptsAlternatives(GameTestHelper helper) {
        requireRecipe(helper, STICK);
        CrafterBlockEntity be = place(helper);
        put(be, CrafterBlockEntity.SLOT_FRAME, frame(stickSamples(Items.OAK_PLANKS), CraftingPattern.Mode.INGREDIENT, STICK));
        helper.assertTrue(be.frameState() == CraftingPattern.State.VALID, "木棍框架应有效");
        helper.assertTrue(be.isItemValid(0, new ItemStack(Items.BIRCH_PLANKS)) && be.isItemValid(3, new ItemStack(Items.OAK_PLANKS)),
                "按配方原料应收各种木板");
        helper.assertFalse(be.isItemValid(0, new ItemStack(Items.STONE)), "配方不要的东西不应收");
        helper.assertFalse(be.isItemValid(1, new ItemStack(Items.OAK_PLANKS)), "样本空着的格子什么都不收");
        put(be, 0, new ItemStack(Items.OAK_PLANKS, 2));
        put(be, 3, new ItemStack(Items.BIRCH_PLANKS, 2));
        helper.assertTrue(be.craftOnce(), "橡木 + 白桦应合成木棍");
        helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_OUTPUT).is(Items.STICK), "产物应是木棍");
        helper.succeed();
    }

    /** 装了熔炉框架：漏斗送来的 8 个圆石按格轮流补，正中不收；装满后合成出熔炉 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterHopperBalances(GameTestHelper helper) {
        requireRecipe(helper, FURNACE);
        CrafterBlockEntity be = place(helper);
        IItemHandler handler = automation(be);
        helper.assertTrue(handler.insertItem(CrafterBlockEntity.SLOT_FRAME,
                frame(furnaceSamples(), CraftingPattern.Mode.INGREDIENT, FURNACE), false).isEmpty(), "框架格空着时漏斗应能送进框架");
        helper.assertFalse(handler.insertItem(CrafterBlockEntity.SLOT_FRAME, new ItemStack(ECCrafter.CRAFTING_FRAME.get()), true).isEmpty(),
                "框架格有框架时不应再收");
        helper.assertTrue(handler.extractItem(CrafterBlockEntity.SLOT_FRAME, 1, true).isEmpty(), "漏斗不应抽走框架");
        for (int n = 0; n < 8; n++) {
            helper.assertTrue(hopperInsert(handler, new ItemStack(Items.COBBLESTONE)), "第 " + (n + 1) + " 个圆石应能送进去");
        }
        for (int i = 0; i < 9; i++) {
            int expected = i == 4 ? 0 : 1;
            helper.assertTrue(cell(be, i).getCount() == expected, "第 " + i + " 格应有 " + expected + " 个，实际 " + cell(be, i));
        }
        for (int n = 0; n < 8; n++) {
            hopperInsert(handler, new ItemStack(Items.COBBLESTONE));
        }
        helper.assertTrue(cell(be, 0).getCount() == 2 && cell(be, 8).getCount() == 2, "第二轮也应每格一个");
        helper.assertTrue(be.craftOnce() && cell(be, CrafterBlockEntity.SLOT_OUTPUT).is(Items.FURNACE), "应合成出熔炉");
        helper.assertTrue(handler.extractItem(CrafterBlockEntity.SLOT_OUTPUT, 1, true).is(Items.FURNACE), "漏斗应能抽走产物");
        helper.assertTrue(handler.extractItem(0, 1, true).isEmpty(), "漏斗不应抽走样板要的原料");
        // 和样板不符的东西（比如放回去的空桶）能被抽走
        put(be, 4, new ItemStack(Items.BUCKET));
        helper.assertTrue(handler.extractItem(4, 1, true).is(Items.BUCKET), "和样板不符的格子应能被抽走");
        helper.succeed();
    }

    /** 没装框架：漏斗只往已经有同种物品的格子补（均摊），不往空格塞 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterNoFrameTopUpOnly(GameTestHelper helper) {
        CrafterBlockEntity be = place(helper);
        IItemHandler handler = automation(be);
        helper.assertFalse(hopperInsert(handler, new ItemStack(Items.OAK_PLANKS)), "全空时漏斗不应往空格塞");
        put(be, 0, new ItemStack(Items.OAK_PLANKS, 3));
        put(be, 3, new ItemStack(Items.OAK_PLANKS, 1));
        helper.assertTrue(hopperInsert(handler, new ItemStack(Items.OAK_PLANKS)), "应能补进已有木板的格子");
        helper.assertTrue(cell(be, 3).getCount() == 2 && cell(be, 0).getCount() == 3, "应先补数量少的那格");
        helper.assertFalse(hopperInsert(handler, new ItemStack(Items.BIRCH_PLANKS)), "没有白桦木板的格子，不应收白桦木板");
        helper.assertTrue(cell(be, 1).isEmpty(), "空格不应被塞东西");
        helper.assertTrue(be.isItemValid(1, new ItemStack(Items.BIRCH_PLANKS)), "玩家手放不受这条限制");
        helper.assertFalse(handler.insertItem(CrafterBlockEntity.SLOT_OUTPUT, new ItemStack(Items.STICK), true).isEmpty(), "产物格不收东西");
        helper.succeed();
    }

    /** 不需要主人、不看领地：/setblock 放的（没有主人）、脚下被圈成别人的领地，照样合成，也不发任何探测事件 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterNeedsNoOwnerOrClaim(GameTestHelper helper) {
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(MACHINE).deny(MACHINE.above())) {
            CrafterBlockEntity be = place(helper);
            helper.assertTrue(be.getOwner() == null, "直接放下的合成器没有主人");
            put(be, 0, new ItemStack(Items.OAK_PLANKS, 2));
            put(be, 3, new ItemStack(Items.OAK_PLANKS, 2));
            helper.assertTrue(be.craftOnce(), "没有主人、在别人领地里也应照常合成（只动自己的物品栏）");
            helper.assertTrue(claims.probes(MACHINE) == 0 && claims.breaks(MACHINE) == 0 && claims.places(MACHINE) == 0,
                    "合成器不应发任何破坏 / 放置事件");
        }
        helper.succeed();
    }

    /** 玩家的槽位规则：产物格不能放；框架格只收框架；客户端菜单的物品栏按自己框架格里那件判断，和服务端一致 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterSlotRules(GameTestHelper helper) {
        requireRecipe(helper, STICK);
        CrafterBlockEntity be = place(helper);
        helper.assertFalse(be.isItemValid(CrafterBlockEntity.SLOT_OUTPUT, new ItemStack(Items.STICK)), "产物格不应能放东西");
        helper.assertTrue(be.isItemValid(CrafterBlockEntity.SLOT_FRAME, new ItemStack(ECCrafter.CRAFTING_FRAME.get())), "框架格应收框架");
        helper.assertFalse(be.isItemValid(CrafterBlockEntity.SLOT_FRAME, new ItemStack(Items.PAPER)), "框架格不应收别的");
        helper.assertTrue(be.isItemValid(5, new ItemStack(Items.DIAMOND)), "没装框架时合成格随便放");

        ItemStack frame = frame(stickSamples(Items.OAK_PLANKS), CraftingPattern.Mode.EXACT, STICK);
        put(be, CrafterBlockEntity.SLOT_FRAME, frame.copy());
        ItemStackHandler client = CrafterBlockEntity.clientHandler(helper.getLevel());
        client.setStackInSlot(CrafterBlockEntity.SLOT_FRAME, frame.copy());
        for (ItemStack probe : new ItemStack[] {new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS), new ItemStack(Items.STONE)}) {
            for (int slot = 0; slot < CrafterBlockEntity.SLOTS; slot++) {
                helper.assertTrue(client.isItemValid(slot, probe) == be.isItemValid(slot, probe),
                        "客户端和服务端对第 " + slot + " 格放 " + probe + " 的判断应一致");
            }
        }
        helper.succeed();
    }

    /** 没装框架（或空框架）时从背包 Shift 点合成框架：进 10 号框架格，不落进空着的 0 号合成格；框架格占着就留在背包里（4a 审查 CRAFT-2） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterShiftClickFrameGoesToFrameSlot(GameTestHelper helper) {
        CrafterBlockEntity be = place(helper);
        ItemStack probe = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        helper.assertFalse(be.isItemValid(0, probe), "没装框架时合成格不应收合成框架");
        helper.assertFalse(CrafterBlockEntity.clientHandler(helper.getLevel()).isItemValid(0, probe), "客户端菜单的规则应一致");
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Inventory inventory = player.getInventory();
        CrafterMenu menu = new CrafterMenu(0, inventory, be);
        // 背包 9 号格（主背包第一格）在菜单里紧跟 11 个机器槽之后
        int backpackSlot = CrafterBlockEntity.SLOTS;
        ItemStack first = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        inventory.setItem(9, first);
        menu.quickMoveStack(player, backpackSlot);
        helper.assertTrue(cell(be, CrafterBlockEntity.SLOT_FRAME).is(ECCrafter.CRAFTING_FRAME.get()), "框架应进 10 号框架格");
        for (int i = 0; i < CrafterBlockEntity.GRID_SLOTS; i++) {
            helper.assertTrue(cell(be, i).isEmpty(), "第 " + i + " 号合成格不应被放进框架，实际 " + cell(be, i));
        }
        helper.assertTrue(inventory.getItem(9).isEmpty(), "背包那格应被移空");
        // 装着的是空框架（当没装），再 Shift 点一件：合成格照样不收，框架留在背包
        inventory.setItem(9, new ItemStack(ECCrafter.CRAFTING_FRAME.get()));
        menu.quickMoveStack(player, backpackSlot);
        helper.assertTrue(inventory.getItem(9).is(ECCrafter.CRAFTING_FRAME.get()), "框架格占着时第二件框架应留在背包");
        for (int i = 0; i < CrafterBlockEntity.GRID_SLOTS; i++) {
            helper.assertTrue(cell(be, i).isEmpty(), "第二件框架不应落进第 " + i + " 号合成格");
        }
        helper.succeed();
    }

    /**
     * 漏斗拿同一个物品对象连着问 11 个槽时，均摊的结果在一轮里复用（CRAFT-1）；但格子一变——换了物品对象、或原地改了数量（菜单的
     * moveItemStackTo 就是这样改的）——下一问就要重算，不能拿旧结果往错的格子塞。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterBalancedCellCacheFollowsGrid(GameTestHelper helper) {
        requireRecipe(helper, FURNACE);
        CrafterBlockEntity be = place(helper);
        put(be, CrafterBlockEntity.SLOT_FRAME, frame(furnaceSamples(), CraftingPattern.Mode.INGREDIENT, FURNACE));
        IItemHandler handler = automation(be);
        ItemStack probe = new ItemStack(Items.COBBLESTONE);
        helper.assertTrue(handler.insertItem(0, probe, true).isEmpty(), "全空时应先补 0 号格");
        helper.assertFalse(handler.insertItem(1, probe, true).isEmpty(), "同一轮里别的格子不应收");
        put(be, 0, new ItemStack(Items.COBBLESTONE, 2));
        helper.assertFalse(handler.insertItem(0, probe, true).isEmpty(), "0 号格换了东西以后不应再补 0 号");
        helper.assertTrue(handler.insertItem(1, probe, true).isEmpty(), "应改补 1 号格");
        for (int i = 1; i < CrafterBlockEntity.GRID_SLOTS; i++) {
            if (i != 4) {
                put(be, i, new ItemStack(Items.COBBLESTONE, 3));
            }
        }
        helper.assertTrue(handler.insertItem(0, probe, true).isEmpty(), "0 号格 2 个最少，应补 0 号");
        cell(be, 0).setCount(5);
        helper.assertFalse(handler.insertItem(0, probe, true).isEmpty(), "0 号原地改成 5 个以后不再最少");
        helper.assertTrue(handler.insertItem(1, probe, true).isEmpty(), "应改补 1 号格（3 个里槽号最小）");
        helper.succeed();
    }

    /**
     * 精确模式的哈希样本（NBT 超过 512 字节的木板）：漏斗只收一模一样的，反复问结果不变；和样板不符的能被抽走（CRAFT-1）。
     * 候选物品的哈希按 NBT 内容缓存：同一件原地改了 NBT 再比，结果跟着变。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterHashedSampleMatchesByContent(GameTestHelper helper) {
        requireRecipe(helper, STICK);
        ItemStack big = bigNbt(Items.OAK_PLANKS, "a");
        ItemStack other = bigNbt(Items.OAK_PLANKS, "b");
        ItemStack[] samples = CraftingPattern.emptySamples();
        samples[0] = big.copy();
        samples[3] = big.copy();
        ItemStack frame = frame(samples, CraftingPattern.Mode.EXACT, STICK);
        ItemStack sample = CraftingPattern.samples(frame)[0];
        helper.assertTrue(GhostSlots.isHashed(sample), "超过 512 字节的样本应只存哈希");
        CrafterBlockEntity be = place(helper);
        put(be, CrafterBlockEntity.SLOT_FRAME, frame);
        helper.assertTrue(be.frameState() == CraftingPattern.State.VALID, "哈希样本的木棍框架应有效");
        IItemHandler handler = automation(be);
        for (int round = 0; round < 3; round++) {
            helper.assertFalse(hopperInsert(handler, other), "第 " + (round + 1) + " 遍：内容不同的大 NBT 木板不应收");
            helper.assertFalse(hopperInsert(handler, new ItemStack(Items.OAK_PLANKS)), "第 " + (round + 1) + " 遍：不带 NBT 的木板不应收");
            helper.assertTrue(GhostSlots.matches(sample, big.copy()), "第 " + (round + 1) + " 遍：一模一样的应匹配");
        }
        helper.assertTrue(hopperInsert(handler, big.copy()) && hopperInsert(handler, big.copy()), "和样本一模一样的应收");
        helper.assertTrue(cell(be, 0).getCount() == 1 && cell(be, 3).getCount() == 1, "两件应各补一格");

        ItemStack mutated = big.copy();
        helper.assertTrue(GhostSlots.matches(sample, mutated), "改之前应匹配");
        String original = mutated.getOrCreateTag().getString(PAYLOAD);
        mutated.getOrCreateTag().putString(PAYLOAD, "b".repeat(original.length()));
        helper.assertFalse(GhostSlots.matches(sample, mutated), "原地改了 NBT 以后不应再匹配（缓存按内容认，不按对象认）");
        mutated.getOrCreateTag().putString(PAYLOAD, original);
        helper.assertTrue(GhostSlots.matches(sample, mutated), "改回来应又匹配");

        helper.assertTrue(handler.extractItem(0, 1, true).isEmpty(), "和样板相符的原料不应被抽走");
        put(be, 3, other.copy());
        helper.assertTrue(handler.extractItem(3, 1, true).is(Items.OAK_PLANKS), "和样板不符的大 NBT 木板应能被抽走");
        put(be, 3, big.copy());
        helper.assertTrue(be.craftOnce() && cell(be, CrafterBlockEntity.SLOT_OUTPUT).is(Items.STICK), "照样本放好应合成木棍");
        helper.succeed();
    }

    private static final String PAYLOAD = "ec5_test_payload";

    /** NBT 超过 {@link GhostSlots#MAX_SAMPLE_BYTES} 的物品（样本会存成哈希） */
    private static ItemStack bigNbt(Item item, String fill) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString(PAYLOAD, fill.repeat(GhostSlots.MAX_SAMPLE_BYTES));
        return stack;
    }

    /** 两个工作台配方都在，产物对得上（原料的注册名都存在，配方才会被加载） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void crafterRecipesExist(GameTestHelper helper) {
        for (String name : new String[] {"crafter", "crafting_frame"}) {
            CraftingRecipe recipe = CraftingPattern.craftingRecipe(helper.getLevel().getRecipeManager(), EssentialCraft5.id("crafter/" + name));
            helper.assertTrue(recipe != null, "应有配方 essentialcraft5:crafter/" + name);
            ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(result.is(name.equals("crafter") ? ECCrafter.CRAFTER.get().asItem() : ECCrafter.CRAFTING_FRAME.get()),
                    "配方 " + name + " 的产物不对：" + result);
        }
        helper.succeed();
    }

    // ==================================================================
    // 合成框架
    // ==================================================================

    /** 拿工作台点产物格：反查出 2×2 配方，样本在左上角，不抛异常（原作下标越界）；光标上的东西不动 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameReverseLookup2x2(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingFrameMenu menu = openFrame(player, frame, InteractionHand.MAIN_HAND);
        menu.setCarried(new ItemStack(Items.CRAFTING_TABLE, 3));
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.CRAFTING_TABLE) && menu.getCarried().getCount() == 3, "反查不应动光标上的东西");
        ItemStack[] samples = CraftingPattern.samples(frame);
        for (int i = 0; i < 9; i++) {
            boolean corner = i == 0 || i == 1 || i == 3 || i == 4;
            helper.assertTrue(corner ? samples[i].is(ItemTags.PLANKS) : samples[i].isEmpty(),
                    "工作台的样本应在左上 2×2，第 " + i + " 格实际 " + samples[i]);
        }
        helper.assertTrue(ResourceLocation.withDefaultNamespace("crafting_table").equals(CraftingPattern.recipeId(frame)), "应记下工作台的配方");
        helper.assertTrue(menu.slots.get(CraftingFrameMenu.SLOT_RESULT).getItem().is(Items.CRAFTING_TABLE), "产物格应显示工作台");
        // 空手点产物格：清空整个样板
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 0, ClickType.PICKUP, player);
        helper.assertTrue(CraftingPattern.allEmpty(CraftingPattern.samples(frame)) && CraftingPattern.recipeId(frame) == null, "空手点产物格应清空样板");
        helper.assertTrue(menu.slots.get(CraftingFrameMenu.SLOT_RESULT).getItem().isEmpty(), "清空后产物格应空着");
        helper.succeed();
    }

    /** 楼梯（不对称）：反查出的方向对，装进合成器能合出来（原作行列没转置，摆反了配不上） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameReverseLookupAsymmetric(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingFrameMenu menu = openFrame(player, frame, InteractionHand.MAIN_HAND);
        menu.setCarried(new ItemStack(Items.OAK_STAIRS));
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 0, ClickType.PICKUP, player);
        ItemStack[] samples = CraftingPattern.samples(frame);
        int[] filled = {0, 3, 4, 6, 7, 8};
        for (int i = 0; i < 9; i++) {
            boolean expected = false;
            for (int f : filled) {
                expected |= f == i;
            }
            helper.assertTrue(expected ? samples[i].is(Items.OAK_PLANKS) : samples[i].isEmpty(),
                    "楼梯样本应是「#  / ## / ###」，第 " + i + " 格实际 " + samples[i]);
        }
        CrafterBlockEntity be = place(helper);
        put(be, CrafterBlockEntity.SLOT_FRAME, frame.copy());
        helper.assertTrue(be.frameState() == CraftingPattern.State.VALID, "楼梯框架装进合成器应有效");
        for (int f : filled) {
            put(be, f, new ItemStack(Items.OAK_PLANKS, 2));
        }
        helper.assertTrue(be.craftOnce(), "照框架摆好的楼梯应能合成");
        ItemStack output = cell(be, CrafterBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(output.is(Items.OAK_STAIRS) && output.getCount() == 4, "应合出 4 个橡木楼梯，实际 " + output);
        helper.succeed();
    }

    /** 同一样产物再点一次换下一条配方（木棍：木板 / 竹子） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameCyclesRecipes(GameTestHelper helper) {
        List<CraftingRecipe> recipes = CraftingRecipeIndex.recipesFor(helper.getLevel(), new ItemStack(Items.STICK));
        helper.assertFalse(recipes.isEmpty(), "应能查到木棍的配方");
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingFrameMenu menu = openFrame(player, frame, InteractionHand.MAIN_HAND);
        menu.setCarried(new ItemStack(Items.STICK));
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 0, ClickType.PICKUP, player);
        ResourceLocation first = CraftingPattern.recipeId(frame);
        helper.assertTrue(recipes.get(0).getId().equals(first), "第一次应选排在最前的配方，实际 " + first);
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 0, ClickType.PICKUP, player);
        ResourceLocation second = CraftingPattern.recipeId(frame);
        if (recipes.size() >= 2) {
            helper.assertTrue(recipes.get(1).getId().equals(second), "再点一次应换成下一条配方，实际 " + second);
        } else {
            helper.assertTrue(first.equals(second), "只有一条配方时再点应不变");
        }
        helper.succeed();
    }

    /** 点样本格：配方跟着样本重算；产物格跟着变 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameSampleClickRecomputesRecipe(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingFrameMenu menu = openFrame(player, frame, InteractionHand.MAIN_HAND);
        menu.setCarried(new ItemStack(Items.OAK_PLANKS, 5));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(3, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 5, "放样本不应消耗光标");
        helper.assertTrue(STICK.equals(CraftingPattern.recipeId(frame)), "两格竖着的木板应配出木棍，实际 " + CraftingPattern.recipeId(frame));
        ItemStack shown = menu.slots.get(CraftingFrameMenu.SLOT_RESULT).getItem();
        helper.assertTrue(shown.is(Items.STICK) && shown.getCount() == 4, "产物格应显示 4 根木棍，实际 " + shown);
        menu.clicked(0, 1, ClickType.PICKUP, player);
        menu.clicked(3, 1, ClickType.PICKUP, player);
        helper.assertTrue(CraftingPattern.recipeId(frame) == null, "样本清空后不应再记配方");
        helper.assertTrue(menu.slots.get(CraftingFrameMenu.SLOT_RESULT).getItem().isEmpty(), "样本清空后产物格应空着");
        helper.succeed();
    }

    /** 数字键换到手持那格无效（样本格、产物格都一样）；换别的快捷栏照常设样本 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameSwapBlocked(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingFrameMenu menu = openFrame(player, frame, InteractionHand.MAIN_HAND);
        Inventory inventory = player.getInventory();
        inventory.setItem(1, new ItemStack(Items.DIAMOND, 3));
        helper.assertTrue(menu.slots.get(hotbarMenuSlot(0)) instanceof HeldItemMenu.LockedSlot, "手持那格应锁住");
        menu.clicked(0, menu.heldSlot(), ClickType.SWAP, player);
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, menu.heldSlot(), ClickType.SWAP, player);
        menu.clicked(hotbarMenuSlot(1), menu.heldSlot(), ClickType.SWAP, player);
        helper.assertTrue(inventory.getItem(0) == frame && inventory.getItem(1).is(Items.DIAMOND), "数字键不应把框架换走");
        helper.assertTrue(CraftingPattern.allEmpty(CraftingPattern.samples(frame)), "换到手持那格的数字键不应改样板");
        menu.clicked(0, 1, ClickType.SWAP, player);
        helper.assertTrue(CraftingPattern.samples(frame)[0].is(Items.DIAMOND), "别的快捷栏的数字键应设样本");
        helper.assertTrue(inventory.getItem(1).getCount() == 3, "设样本不应消耗那格的东西");
        helper.succeed();
    }

    /** 从副手打开：改的是副手那件，主手那件不动（原作改的是主手） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameOffhandEditsOffhand(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack main = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        player.getInventory().setItem(0, main);
        ItemStack offhand = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingFrameMenu menu = openFrame(player, offhand, InteractionHand.OFF_HAND);
        helper.assertTrue(menu.heldSlot() == Inventory.SLOT_OFFHAND && menu.stillValid(player), "副手打开应记 40 号格");
        menu.setCarried(new ItemStack(Items.OAK_PLANKS));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.setCarried(new ItemStack(Items.OAK_STAIRS));
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 0, ClickType.PICKUP, player);
        helper.assertTrue(CraftingPattern.samples(offhand)[0].is(Items.OAK_PLANKS), "副手那件应被改");
        helper.assertTrue(main.getTag() == null, "主手那件不应被改");
        helper.succeed();
    }

    /** 各种点法之后背包、光标里的东西一件不少也一件不多，也不掉东西 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameGhostNoItems(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingFrameMenu menu = openFrame(player, frame, InteractionHand.MAIN_HAND);
        Inventory inventory = player.getInventory();
        inventory.setItem(20, new ItemStack(Items.OAK_PLANKS, 20));
        inventory.setItem(2, new ItemStack(Items.FURNACE, 2));
        int before = itemsAround(helper, player);
        menu.setCarried(new ItemStack(Items.CRAFTING_TABLE, 7));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 0, ClickType.PICKUP, player);
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 1, ClickType.PICKUP, player);
        menu.clicked(20 - 9 + MENU_INVENTORY_START, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 2, ClickType.SWAP, player);
        menu.clicked(1, 1, ClickType.THROW, player);
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 1, ClickType.THROW, player);
        menu.clicked(0, 0, ClickType.PICKUP_ALL, player);
        menu.clicked(CraftingFrameMenu.SLOT_RESULT, 0, ClickType.CLONE, player);
        helper.assertTrue(menu.getCarried().is(Items.CRAFTING_TABLE) && menu.getCarried().getCount() == 7, "光标上的东西不应变");
        helper.assertTrue(inventory.getItem(20).getCount() == 20 && inventory.getItem(2).getCount() == 2, "背包里的东西不应变");
        helper.assertTrue(inventory.getItem(0) == frame, "框架应还在手上");
        helper.assertTrue(itemsAround(helper, player) == before, "不应掉出任何东西");
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "Shift 幽灵格不应产出物品");
        helper.succeed();
    }

    private static int itemsAround(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4)).size();
    }

    /**
     * 特殊配方（药箭，原版 CustomRecipe 的 getResultItem 恒为空）的框架：物品提示写按样本现算的产物，不写红字「配方已不存在」；
     * 装进合成器照常合成。配方真没了的框架仍写「配方已不存在」（4a 审查 CRAFT-3）。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameTooltipSpecialRecipe(GameTestHelper helper) {
        ResourceLocation tippedArrow = ResourceLocation.withDefaultNamespace("tipped_arrow");
        requireRecipe(helper, tippedArrow);
        ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.POISON);
        ItemStack[] samples = CraftingPattern.emptySamples();
        for (int i = 0; i < 9; i++) {
            samples[i] = i == 4 ? potion.copy() : new ItemStack(Items.ARROW);
        }
        ItemStack frame = frame(samples, CraftingPattern.Mode.INGREDIENT, tippedArrow);

        Component line = tooltip(helper, frame).get(0);
        helper.assertTrue("tooltip.essentialcraft5.crafting_frame.result".equals(translationKey(line)),
                "药箭框架的第一行应是「产物：…」，实际 " + line);
        Object[] args = line.getContents() instanceof TranslatableContents contents ? contents.getArgs() : new Object[0];
        Component count = args.length > 0 && args[0] instanceof Component c ? c : Component.empty();
        Object[] countArgs = count.getContents() instanceof TranslatableContents contents ? contents.getArgs() : new Object[0];
        helper.assertTrue("tooltip.essentialcraft5.crafting_frame.result_count".equals(translationKey(count))
                        && countArgs.length == 2 && Integer.valueOf(8).equals(countArgs[1]),
                "产物应是现算出来的 8 支药箭，实际 " + count);

        ItemStack gone = frame(samples, CraftingPattern.Mode.INGREDIENT, EssentialCraft5.id("crafter_test_missing_recipe"));
        helper.assertTrue("tooltip.essentialcraft5.crafting_frame.missing".equals(translationKey(tooltip(helper, gone).get(0))),
                "配方不存在的框架应写「配方已不存在」");

        CrafterBlockEntity be = place(helper);
        put(be, CrafterBlockEntity.SLOT_FRAME, frame.copy());
        helper.assertTrue(be.frameState() == CraftingPattern.State.VALID, "药箭框架装进合成器应有效");
        for (int i = 0; i < 9; i++) {
            put(be, i, i == 4 ? potion.copy() : new ItemStack(Items.ARROW, 2));
        }
        helper.assertTrue(be.craftOnce(), "药箭框架应能合成");
        ItemStack output = cell(be, CrafterBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(output.is(Items.TIPPED_ARROW) && output.getCount() == 8 && PotionUtils.getPotion(output) == Potions.POISON,
                "应合出 8 支剧毒药箭，实际 " + output);
        helper.succeed();
    }

    private static List<Component> tooltip(GameTestHelper helper, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, helper.getLevel(), lines, TooltipFlag.NORMAL);
        helper.assertFalse(lines.isEmpty(), "物品提示不应为空");
        return lines;
    }

    private static String translationKey(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    /** 模式按钮：两种模式来回切，写进框架；别的按钮号不理 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameModeButton(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CraftingFrameMenu menu = openFrame(player, frame, InteractionHand.MAIN_HAND);
        helper.assertTrue(menu.mode() == CraftingPattern.Mode.INGREDIENT, "默认应是按配方原料");
        helper.assertTrue(menu.clickMenuButton(player, CraftingFrameMenu.BUTTON_MODE), "模式按钮应生效");
        helper.assertTrue(CraftingPattern.mode(frame) == CraftingPattern.Mode.EXACT && menu.mode() == CraftingPattern.Mode.EXACT, "应切到精确并写进框架");
        helper.assertTrue(menu.clickMenuButton(player, CraftingFrameMenu.BUTTON_MODE), "再按一次应切回");
        helper.assertTrue(CraftingPattern.mode(frame) == CraftingPattern.Mode.INGREDIENT, "应切回按配方原料");
        helper.assertFalse(menu.clickMenuButton(player, 7), "伪造的按钮号不应生效");
        helper.succeed();
    }

    /** 框架 NBT 被改过：越界的格子号、写坏的配方 ID、不认识的模式都当没有，解析成失效而不是抛异常 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void frameNbtValidation(GameTestHelper helper) {
        ItemStack frame = new ItemStack(ECCrafter.CRAFTING_FRAME.get());
        CompoundTag data = new CompoundTag();
        ListTag grid = new ListTag();
        for (int slot : new int[] {-1, 12, 4}) {
            CompoundTag entry = GhostSlots.save(new ItemStack(Items.DIAMOND));
            entry.putByte("Slot", (byte) slot);
            grid.add(entry);
        }
        grid.add(GhostSlots.save(new ItemStack(Items.EMERALD)));   // 没有 Slot
        data.put("Grid", grid);
        data.putString("Recipe", "Not A Valid Id!");
        data.putString("Mode", "weird");
        frame.getOrCreateTag().put(CraftingPattern.TAG, data);
        ItemStack[] samples = CraftingPattern.samples(frame);
        for (int i = 0; i < 9; i++) {
            helper.assertTrue(i == 4 ? samples[i].is(Items.DIAMOND) : samples[i].isEmpty(), "只有合法的 4 号格应读出来，第 " + i + " 格实际 " + samples[i]);
        }
        helper.assertTrue(CraftingPattern.recipeId(frame) == null, "写坏的配方 ID 应读成没有");
        helper.assertTrue(CraftingPattern.mode(frame) == CraftingPattern.Mode.INGREDIENT, "不认识的模式按默认");
        helper.assertTrue(CraftingPattern.resolve(frame, helper.getLevel()).state() == CraftingPattern.State.INVALID, "有样本没配方应失效");
        helper.succeed();
    }
}
