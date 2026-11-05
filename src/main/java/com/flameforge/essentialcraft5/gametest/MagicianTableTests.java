package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicianTableBlockEntity;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 魔力工作台的自动化测试（./gradlew runGameTestServer）。配方来自 data/essentialcraft5/recipes/magician_table/ 的 JSON，
 * 所以也顺带验证了 JSON 能正常加载。MRU 直接充满，不接绑定宝石（拉 MRU 在框架测试里测过）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MagicianTableTests {

    private static final String EMPTY = "empty";

    private MagicianTableTests() {
    }

    private static MagicianTableBlockEntity placeTable(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECMagicianTable.MAGICIAN_TABLE.get());
        MagicianTableBlockEntity table = (MagicianTableBlockEntity) helper.getBlockEntity(pos);
        table.getMRUStorage().setMRU(table.getMRUStorage().getMaxMRU());
        return table;
    }

    /** 两块铁锭放中心：做两次魔力锭（每次 50 MRU），铁锭吃完、MRU 正好少 100 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void magicianTableCraftsMagicalIngot(GameTestHelper helper) {
        MagicianTableBlockEntity table = placeTable(helper, new BlockPos(2, 1, 2));
        int before = table.getMRUStorage().getMRU();
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, new ItemStack(Items.IRON_INGOT, 2));

        helper.succeedWhen(() -> {
            ItemStack output = table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(output.is(ECMaterials.MAGICAL_INGOT.get()) && output.getCount() == 2,
                    "输出槽应有 2 块魔力锭，实际 " + output);
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).isEmpty(),
                    "中心的铁锭应被吃完");
            int used = before - table.getMRUStorage().getMRU();
            helper.assertTrue(used == 100, "两次魔力锭应正好耗 100 MRU，实际 " + used);
            helper.assertTrue(table.getProgress() == 0D, "做完后进度应清零，实际 " + table.getProgress());
        });
    }

    /** 右键装吸收之眼（×3）后进度三倍速；潜行空手右键卸下，速度回到 ×1 */
    @GameTest(template = EMPTY)
    public static void magicianTableUpgradeChangesSpeed(GameTestHelper helper) {
        BlockPos plainPos = new BlockPos(1, 1, 2);
        BlockPos upgradedPos = new BlockPos(3, 1, 2);
        MagicianTableBlockEntity plain = placeTable(helper, plainPos);
        MagicianTableBlockEntity upgraded = placeTable(helper, upgradedPos);

        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECMaterials.EYE_OF_ABSORPTION.get()));
        helper.useBlock(upgradedPos, player);
        helper.assertTrue(upgraded.getUpgrade().is(ECMaterials.EYE_OF_ABSORPTION.get()), "拿吸收之眼右键应装上升级");
        helper.assertTrue(upgraded.speedFactor() == 3D, "吸收之眼的速度系数应为 3，实际 " + upgraded.speedFactor());
        helper.assertTrue(plain.speedFactor() == 1D, "没装升级的速度系数应为 1");

        plain.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, new ItemStack(Items.IRON_INGOT));
        upgraded.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, new ItemStack(Items.IRON_INGOT));
        int max = plain.getMRUStorage().getMaxMRU();

        // 10 tick 后：普通的进度 ≤ 10，装了升级的正好是它的 3 倍（≤ 30，还没到魔力锭的 50）
        helper.runAtTickTime(10, () -> {
            double base = plain.getProgress();
            double fast = upgraded.getProgress();
            helper.assertTrue(base > 0D, "普通工作台应在工作，进度 " + base);
            helper.assertTrue(Math.abs(fast - 3D * base) < 1e-6, "装了升级的进度应为 3 倍：" + fast + " 对 " + base);
            int usedPlain = max - plain.getMRUStorage().getMRU();
            int usedFast = max - upgraded.getMRUStorage().getMRU();
            helper.assertTrue(usedFast == 3 * usedPlain, "装了升级的 MRU 消耗也应为 3 倍：" + usedFast + " 对 " + usedPlain);

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            helper.useBlock(upgradedPos, player);
            helper.assertTrue(!upgraded.hasUpgrade(), "潜行空手右键应卸下升级");
            helper.assertTrue(upgraded.speedFactor() == 1D, "卸下后速度系数应回到 1");
            helper.succeed();
        });
    }

    /** 单格配方要求四角为空：中心铁锭 + 左上随便放个东西，不应开工 */
    @GameTest(template = EMPTY)
    public static void magicianTableRequiresEmptyCorners(GameTestHelper helper) {
        MagicianTableBlockEntity table = placeTable(helper, new BlockPos(2, 1, 2));
        int before = table.getMRUStorage().getMRU();
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, new ItemStack(Items.IRON_INGOT));
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT, new ItemStack(Items.DIRT));

        helper.runAtTickTime(60, () -> {
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT).isEmpty(),
                    "四角不空时不应出产物");
            helper.assertTrue(table.getProgress() == 0D, "四角不空时不应有进度，实际 " + table.getProgress());
            helper.assertTrue(table.getMRUStorage().getMRU() == before, "四角不空时不应耗 MRU");
            helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).is(Items.IRON_INGOT),
                    "铁锭应原样留着");
            helper.succeed();
        });
    }

    /** 做到一半把铁锭换成石英：从头做 MRU 碎片，不能按旧配方出魔力锭（原作的刷物品漏洞） */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void magicianTableSwappingInputRestarts(GameTestHelper helper) {
        MagicianTableBlockEntity table = placeTable(helper, new BlockPos(2, 1, 2));
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, new ItemStack(Items.IRON_INGOT));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(table.getProgress() > 0D, "铁锭应已开始加工");
                    table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, new ItemStack(Items.QUARTZ));
                })
                .thenWaitUntil(() -> {
                    ItemStack output = table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(output.is(ECMaterials.MRU_SHARD.get()), "换成石英后应产出 MRU 碎片，实际 " + output);
                })
                .thenSucceed();
    }

    /**
     * 漏斗只往配方用得上的格子放：铁锭只有魔力锭用、放中心，所以空工作台的铁锭进中心格，中心满 64 以后多出来的留在外面，
     * 不会塞进四角把配方堵死；四角手动摆了东西以后，同种物品能补进去，和已有原料凑不成配方的物品插不进。
     * 玩家菜单不受限制（这里只测自动化那一层）。
     */
    @GameTest(template = EMPTY)
    public static void magicianTableHopperOnlyTopsUp(GameTestHelper helper) {
        MagicianTableBlockEntity table = placeTable(helper, new BlockPos(2, 1, 2));
        // 红石停机，免得测的时候把原料吃掉
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
        IItemHandler automation = table.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("工作台应提供物品能力"));

        ItemStack rest = ItemHandlerHelper.insertItem(automation, new ItemStack(Items.IRON_INGOT, 64), false);
        rest = ItemHandlerHelper.insertItem(automation, rest.isEmpty() ? new ItemStack(Items.IRON_INGOT, 10) : rest, false);
        helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).getCount() == 64,
                "中心格应被补满 64");
        helper.assertTrue(rest.getCount() == 10, "中心满了以后多出的 10 个应插不进，实际剩 " + rest.getCount());
        for (int slot = MagicianTableBlockEntity.SLOT_TOP_LEFT; slot <= MagicianTableBlockEntity.SLOT_BOTTOM_RIGHT; slot++) {
            helper.assertTrue(table.getInventory().getStackInSlot(slot).isEmpty(), "四角应保持为空，第 " + slot + " 格有东西");
        }

        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT, new ItemStack(Items.GOLD_INGOT));
        ItemStack gold = ItemHandlerHelper.insertItem(automation, new ItemStack(Items.GOLD_INGOT, 5), false);
        helper.assertTrue(gold.isEmpty() && table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT).getCount() == 6,
                "已有金锭的左上格应能补进 5 个");
        ItemStack diamond = ItemHandlerHelper.insertItem(automation, new ItemStack(Items.DIAMOND), false);
        helper.assertTrue(diamond.getCount() == 1, "没有同种物品的格子，钻石应插不进");
        helper.succeed();
    }

    /**
     * 多原料配方（魔力板：中心魔力强化板、四角魔力锭）全靠漏斗供料：强化板进中心，魔力锭在四角之间轮流补，
     * 做完一份后吃空的角照样补得进，工作台不停机。原作和第一版补货规则都会把锭全堆进左上格、其它角吃空后卡死。
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void magicianTableHopperBalancesCorners(GameTestHelper helper) {
        MagicianTableBlockEntity table = placeTable(helper, new BlockPos(2, 1, 2));
        IItemHandler automation = table.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("工作台应提供物品能力"));

        // 五格全空时直接送角料：没有配方把魔力锭放中心，应进左上角而不是中心
        ItemStack first = ItemHandlerHelper.insertItem(automation, new ItemStack(ECMaterials.MAGICAL_INGOT.get()), false);
        helper.assertTrue(first.isEmpty() && table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT).getCount() == 1,
                "空工作台送来的第一块魔力锭应进左上角");
        for (int i = 0; i < 7; i++) {
            ItemHandlerHelper.insertItem(automation, new ItemStack(ECMaterials.MAGICAL_INGOT.get()), false);
        }
        for (int slot = MagicianTableBlockEntity.SLOT_TOP_LEFT; slot <= MagicianTableBlockEntity.SLOT_BOTTOM_RIGHT; slot++) {
            int count = table.getInventory().getStackInSlot(slot).getCount();
            helper.assertTrue(count == 2, "8 块魔力锭应在四角均摊成各 2 块，第 " + slot + " 格有 " + count);
        }
        helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).isEmpty(), "魔力锭不应进中心");
        ItemStack plating = ItemHandlerHelper.insertItem(automation, new ItemStack(ECMaterials.MAGIC_FORTIFIED_PLATING.get(), 2), false);
        helper.assertTrue(plating.isEmpty() && table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).getCount() == 2,
                "魔力强化板应进中心");

        // 做完两份后五格吃空，再送 4 块锭、1 块板，应各进各位，第三份照样开工
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT).getCount() == 2, "应做出 2 块魔力板"))
                .thenExecute(() -> {
                    for (int i = 0; i < 4; i++) {
                        ItemHandlerHelper.insertItem(automation, new ItemStack(ECMaterials.MAGICAL_INGOT.get()), false);
                    }
                    ItemHandlerHelper.insertItem(automation, new ItemStack(ECMaterials.MAGIC_FORTIFIED_PLATING.get()), false);
                    for (int slot = MagicianTableBlockEntity.SLOT_TOP_LEFT; slot <= MagicianTableBlockEntity.SLOT_BOTTOM_RIGHT; slot++) {
                        helper.assertTrue(table.getInventory().getStackInSlot(slot).getCount() == 1, "吃空的角应补得进，第 " + slot + " 格");
                    }
                    helper.assertTrue(table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).getCount() == 1,
                            "吃空的中心应补得进强化板");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT).getCount() == 3, "应做出第 3 块魔力板"))
                .thenSucceed();
    }

    /**
     * 漏斗记住上一次出产物用的配方（第四批 4b 审查 INT-2）：MRU 碎片在 MRU 水晶里放中心、在灵魂碎片里放四角，各一条配方。
     * 没做过东西时碎片按槽号进中心；手摆一份做出灵魂碎片以后，五格吃空、碎片先到也进左上角，石英随后进中心；存盘读回后照样记得。
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void magicianTableHopperRemembersLastRecipe(GameTestHelper helper) {
        MagicianTableBlockEntity table = placeTable(helper, new BlockPos(2, 1, 2));
        IItemHandler automation = table.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("工作台应提供物品能力"));
        ResourceLocation soulShardId = ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "magician_table/soul_shard");

        helper.assertTrue(table.getLastRecipe() == null, "新放的工作台不应记着配方");
        ItemStack first = ItemHandlerHelper.insertItem(automation, new ItemStack(ECMaterials.MRU_SHARD.get()), false);
        helper.assertTrue(first.isEmpty() && table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).is(ECMaterials.MRU_SHARD.get()),
                "没做过东西时 MRU 碎片应进中心（MRU 水晶的位置，和四角各一条配方，按槽号取中心）");

        // 手摆一份灵魂碎片：中心石英、四角 MRU 碎片
        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, new ItemStack(Items.QUARTZ));
        for (int slot = MagicianTableBlockEntity.SLOT_TOP_LEFT; slot <= MagicianTableBlockEntity.SLOT_BOTTOM_RIGHT; slot++) {
            table.getInventory().setStackInSlot(slot, new ItemStack(ECMaterials.MRU_SHARD.get()));
        }

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_OUTPUT).is(ECItems.SOUL_SHARD.get()), "等待产出灵魂碎片"))
                .thenExecute(() -> {
                    for (int slot = MagicianTableBlockEntity.SLOT_CENTER; slot <= MagicianTableBlockEntity.SLOT_BOTTOM_RIGHT; slot++) {
                        helper.assertTrue(table.getInventory().getStackInSlot(slot).isEmpty(), "五格应都吃空，第 " + slot + " 格有东西");
                    }
                    helper.assertTrue(soulShardId.equals(table.getLastRecipe()), "应记住灵魂碎片配方，实际 " + table.getLastRecipe());
                    // 存盘读回（和区块重新加载走同一条路）
                    table.load(table.saveWithoutMetadata());
                    helper.assertTrue(soulShardId.equals(table.getLastRecipe()), "存盘读回后应仍记着灵魂碎片配方，实际 " + table.getLastRecipe());

                    ItemStack shard = ItemHandlerHelper.insertItem(automation, new ItemStack(ECMaterials.MRU_SHARD.get()), false);
                    helper.assertTrue(shard.isEmpty()
                                    && table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_TOP_LEFT).is(ECMaterials.MRU_SHARD.get())
                                    && table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).isEmpty(),
                            "做过灵魂碎片后，五格吃空时 MRU 碎片应进左上角而不是中心");
                    ItemStack quartz = ItemHandlerHelper.insertItem(automation, new ItemStack(Items.QUARTZ), false);
                    helper.assertTrue(quartz.isEmpty() && table.getInventory().getStackInSlot(MagicianTableBlockEntity.SLOT_CENTER).is(Items.QUARTZ),
                            "石英应随后进中心");
                })
                .thenSucceed();
    }
}
