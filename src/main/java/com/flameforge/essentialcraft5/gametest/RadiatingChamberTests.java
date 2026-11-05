package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import com.flameforge.essentialcraft5.registry.ECTesla;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 辐射仓的自动化测试（./gradlew runGameTestServer）。
 * <p>
 * 测试里不放绑定宝石，MRU 和平衡值直接写进机器的存储（没有宝石就不会被拉取改掉）。
 * 投影配方要 1 万~2 万 tick，测试用「存盘数据改进度再读回」把进度快进到只差 1 tick，顺带验证了进度存盘。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class RadiatingChamberTests {

    private static final String EMPTY = "empty";

    private RadiatingChamberTests() {
    }

    private static RadiatingChamberBlockEntity place(GameTestHelper helper, BlockPos pos, float balance) {
        helper.setBlock(pos, ECRadiatingChamber.RADIATING_CHAMBER.get());
        RadiatingChamberBlockEntity be = (RadiatingChamberBlockEntity) helper.getBlockEntity(pos);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        be.getMRUStorage().setBalance(balance);
        return be;
    }

    private static ItemStack emptyProjection() {
        return new ItemStack(ECMaterials.EMPTY_MATRIX_PROJECTION.get());
    }

    /** 把进度改成 value（经存盘数据读回，和区块重新加载走同一条路） */
    private static void fastForward(RadiatingChamberBlockEntity be, int value) {
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putInt(RadiatingChamberBlockEntity.TAG_PROGRESS, value);
        be.load(tag);
    }

    private static boolean produces(RadiatingChamberRecipe recipe, Item item) {
        return recipe != null && recipe.getResult().is(item);
    }

    private static void expectPick(GameTestHelper helper, List<RadiatingChamberRecipe> candidates, float balance, Item expected) {
        RadiatingChamberRecipe picked = RadiatingChamberBlockEntity.pick(candidates, balance);
        helper.assertTrue(produces(picked, expected),
                "平衡值 " + balance + " 应产出 " + expected + "，实际 " + (picked == null ? "无配方" : picked.getId()));
    }

    /**
     * 平衡值窗口（闭区间）：原作按注册顺序取第一条，所以边界 0.5 算冰冻、1.5 算混沌，中间开区间 (0.5, 1.5) 是纯净（魔力投影）。
     * 自带配方的区间写成不重叠，这里连边界两侧紧挨着的 float 一起查。
     */
    @GameTest(template = EMPTY)
    public static void radiatingChamberBalanceWindows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<RadiatingChamberRecipe> candidates = RadiatingChamberBlockEntity.findCandidates(level.getRecipeManager(), level,
                emptyProjection(), ItemStack.EMPTY);
        helper.assertTrue(candidates.size() == 3, "空白矩阵投影应对上 3 条配方，实际 " + candidates.size());

        Item frozen = ECMaterials.FROZEN_MATRIX_PROJECTION.get().asItem();
        Item magic = ECMaterials.MAGIC_MATRIX_PROJECTION.get().asItem();
        Item chaos = ECMaterials.CHAOS_MATRIX_PROJECTION.get().asItem();
        expectPick(helper, candidates, 0F, frozen);
        expectPick(helper, candidates, 0.25F, frozen);
        expectPick(helper, candidates, 0.5F, frozen);
        expectPick(helper, candidates, Math.nextUp(0.5F), magic);
        expectPick(helper, candidates, 1F, magic);
        expectPick(helper, candidates, Math.nextDown(1.5F), magic);
        expectPick(helper, candidates, 1.5F, chaos);
        expectPick(helper, candidates, 2F, chaos);

        // 单原料配方要求另一格为空（原作 Ingredient.EMPTY 只认空物品）
        helper.assertTrue(RadiatingChamberBlockEntity.findCandidates(level.getRecipeManager(), level,
                        emptyProjection(), new ItemStack(Items.STONE)).isEmpty(),
                "下格有东西时空白投影不应对上任何配方");
        helper.succeed();
    }

    /** 暗影投影（原作做不出来，这里补的）：空白投影 + 下格暗黑灵魂物质，平衡值 0～2 都是它，不会被另外三条抢走 */
    @GameTest(template = EMPTY)
    public static void radiatingChamberShadeProjection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item darkSoul = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("dark_soul_matter"));
        List<RadiatingChamberRecipe> candidates = RadiatingChamberBlockEntity.findCandidates(level.getRecipeManager(), level,
                emptyProjection(), new ItemStack(darkSoul));
        helper.assertTrue(candidates.size() == 1, "空白投影 + 暗黑灵魂物质应只对上 1 条配方，实际 " + candidates.size());
        Item shade = ECMaterials.SHADE_MATRIX_PROJECTION.get().asItem();
        for (float balance : new float[]{0F, 0.5F, 1F, 1.5F, 2F}) {
            expectPick(helper, candidates, balance, shade);
        }
        helper.assertTrue(candidates.get(0).getMRU() == 20000, "暗影投影要照 2 万刻，实际 " + candidates.get(0).getMRU());
        helper.succeed();
    }

    /** 三台机器、三个平衡值，同样放空白投影，分别做出冰冻、纯净（魔力）、混沌投影 */
    @GameTest(template = EMPTY)
    public static void radiatingChamberBalanceGivesProjection(GameTestHelper helper) {
        RadiatingChamberBlockEntity frozenBe = place(helper, new BlockPos(0, 1, 2), 0.2F);
        RadiatingChamberBlockEntity magicBe = place(helper, new BlockPos(2, 1, 2), 1F);
        RadiatingChamberBlockEntity chaosBe = place(helper, new BlockPos(4, 1, 2), 1.8F);
        List<RadiatingChamberBlockEntity> all = List.of(frozenBe, magicBe, chaosBe);
        all.forEach(be -> be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, emptyProjection()));

        helper.startSequence()
                .thenExecuteAfter(2, () -> all.forEach(be -> {
                    RadiatingChamberRecipe recipe = be.getDisplayRecipe();
                    helper.assertTrue(recipe != null && be.getProgress() > 0, "机器应已开始工作，状态 " + be.getStatus());
                    fastForward(be, recipe.getMRU() - 1);
                }))
                .thenWaitUntil(() -> all.forEach(be -> helper.assertTrue(
                        !be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty(), "等待产出")))
                .thenExecute(() -> {
                    expectOutput(helper, frozenBe, ECMaterials.FROZEN_MATRIX_PROJECTION.get().asItem());
                    expectOutput(helper, magicBe, ECMaterials.MAGIC_MATRIX_PROJECTION.get().asItem());
                    expectOutput(helper, chaosBe, ECMaterials.CHAOS_MATRIX_PROJECTION.get().asItem());
                    all.forEach(be -> helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).isEmpty(),
                            "原料应被消耗"));
                })
                .thenSucceed();
    }

    /**
     * 修原作 bug：做到一半平衡值从冰冻拉到混沌，进度必须清零，最后产出混沌投影
     * （原作进度照常累积，最后仍产出冰冻投影）。
     */
    @GameTest(template = EMPTY)
    public static void radiatingChamberSwitchResetsProgress(GameTestHelper helper) {
        RadiatingChamberBlockEntity be = place(helper, new BlockPos(2, 1, 2), 0.2F);
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, emptyProjection());
        Item frozen = ECMaterials.FROZEN_MATRIX_PROJECTION.get().asItem();
        Item chaos = ECMaterials.CHAOS_MATRIX_PROJECTION.get().asItem();

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(produces(be.getDisplayRecipe(), frozen), "平衡值 0.2 应在做冰冻投影");
                    helper.assertTrue(be.getProgress() >= 10, "20 tick 后进度应已累积，实际 " + be.getProgress());
                    be.getMRUStorage().setBalance(1.8F);
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(produces(be.getDisplayRecipe(), chaos), "平衡值改成 1.8 后应改做混沌投影");
                    // 换配方那一 tick 清零后又推进了 1，测试事件和方块实体 tick 的先后不定，最多 2
                    helper.assertTrue(be.getProgress() <= 2, "换配方后进度应清零，实际 " + be.getProgress());
                    fastForward(be, be.getDisplayRecipe().getMRU() - 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        !be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty(), "等待产出"))
                .thenExecute(() -> expectOutput(helper, be, chaos))
                .thenSucceed();
    }

    /** 石头 + 铁锭 → 魔力强化石 ×4，每份 10 tick、10 MRU；两份原料做两次 */
    @GameTest(template = EMPTY)
    public static void radiatingChamberFortifiedStone(GameTestHelper helper) {
        RadiatingChamberBlockEntity be = place(helper, new BlockPos(2, 1, 2), 1F);
        be.getMRUStorage().setMRU(100);
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, new ItemStack(Items.STONE, 2));
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM, new ItemStack(Items.IRON_INGOT, 2));
        Item fortifiedStone = ECMaterials.FORTIFIED_STONE.get().asItem();

        helper.succeedWhen(() -> {
            ItemStack out = be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(out.is(fortifiedStone) && out.getCount() == 8, "应产出 8 块魔力强化石，实际 " + out);
            helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).isEmpty()
                    && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM).isEmpty(), "原料应用完");
            helper.assertTrue(be.getMRUStorage().getMRU() == 80, "应消耗 20 MRU，剩余 " + be.getMRUStorage().getMRU());
        });
    }

    /** 有红石信号时停机：不推进、不耗 MRU */
    @GameTest(template = EMPTY)
    public static void radiatingChamberStopsWithRedstone(GameTestHelper helper) {
        RadiatingChamberBlockEntity be = place(helper, new BlockPos(2, 1, 2), 1F);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, new ItemStack(Items.STONE));
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM, new ItemStack(Items.IRON_INGOT));
        int before = be.getMRUStorage().getMRU();

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(be.getStatus() == RadiatingChamberBlockEntity.Status.POWERED, "状态应为 POWERED，实际 " + be.getStatus());
            helper.assertTrue(be.getProgress() == 0, "有红石信号时不应推进");
            helper.assertTrue(be.getMRUStorage().getMRU() == before, "有红石信号时不应耗 MRU");
            helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty(), "不应产出");
            helper.succeed();
        });
    }

    /** 漏斗、管道：0 号只收绑定宝石，1、2 号随意，3 号（输出）不能插入、只能从它取出 */
    @GameTest(template = EMPTY)
    public static void radiatingChamberAutomationRules(GameTestHelper helper) {
        RadiatingChamberBlockEntity be = place(helper, new BlockPos(2, 1, 2), 1F);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);

        helper.assertTrue(handler.insertItem(RadiatingChamberBlockEntity.SLOT_GEM, new ItemStack(Items.STONE), false).getCount() == 1,
                "0 号槽不应接受绑定宝石以外的物品");
        helper.assertTrue(handler.insertItem(RadiatingChamberBlockEntity.SLOT_TOP, new ItemStack(Items.STONE), false).isEmpty(),
                "1 号槽应接受配方里放在上格的石头");
        helper.assertTrue(handler.insertItem(RadiatingChamberBlockEntity.SLOT_OUTPUT, new ItemStack(Items.STONE), false).getCount() == 1,
                "输出槽不应能插入");
        helper.assertTrue(handler.extractItem(RadiatingChamberBlockEntity.SLOT_TOP, 1, false).isEmpty(), "原料槽不应能被取出");

        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT, new ItemStack(Items.DIAMOND));
        helper.assertTrue(handler.extractItem(RadiatingChamberBlockEntity.SLOT_OUTPUT, 1, false).is(Items.DIAMOND), "输出槽应能被取出");
        helper.succeed();
    }

    /**
     * 漏斗按配方表放位置：空白投影只进上格，第二个进不来（原作会进下格，配方要求下格为空，机器停在「空闲」）；
     * 没做过东西的辐射仓铁锭进下格（强化石、强化玻璃两条配方用下格，多于磁体的一条），之后石头进上格；下格有铁锭时投影进不来。
     */
    @GameTest(template = EMPTY)
    public static void radiatingChamberHopperPlacesByRecipe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        RadiatingChamberBlockEntity be = place(helper, pos, 1F);
        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);

        helper.assertTrue(ItemHandlerHelper.insertItem(handler, emptyProjection(), false).isEmpty(), "第一个空白投影应插得进");
        helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).is(ECMaterials.EMPTY_MATRIX_PROJECTION.get()),
                "空白投影应进上格");
        helper.assertTrue(ItemHandlerHelper.insertItem(handler, emptyProjection(), false).getCount() == 1,
                "第二个空白投影不应插进下格");
        helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM).isEmpty(), "下格应保持为空");

        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, ItemStack.EMPTY);
        helper.assertTrue(ItemHandlerHelper.insertItem(handler, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(), "铁锭应插得进");
        helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM).is(Items.IRON_INGOT),
                "铁锭应进下格（强化石配方的位置）");
        helper.assertTrue(ItemHandlerHelper.insertItem(handler, new ItemStack(Items.STONE, 2), false).isEmpty(), "石头应插得进");
        helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).is(Items.STONE), "石头应进上格");
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, ItemStack.EMPTY);
        helper.assertTrue(ItemHandlerHelper.insertItem(handler, emptyProjection(), false).getCount() == 1,
                "下格有铁锭时，空白投影没有配方能接，应插不进");
        helper.succeed();
    }

    /**
     * 漏斗记住上一次出产物用的配方（第四批 4b 审查 INT-2）：铁锭在强化石、强化玻璃里放下格，在 MRU 磁体里放上格。
     * 手放一份做出磁体以后，上下两格同时吃空、铁锭先到也进上格，精华随后进下格（以前每轮都会进下格，精华再也插不进，机器停着）；
     * 存盘读回后照样记得。之后手放一份做出强化石，铁锭又回到下格、石头进上格。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void radiatingChamberHopperRemembersLastRecipe(GameTestHelper helper) {
        RadiatingChamberBlockEntity be = place(helper, new BlockPos(2, 1, 2), 1F);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        ResourceLocation magnetId = ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "radiating_chamber/mru_magnet");
        ResourceLocation stoneId = ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "radiating_chamber/fortified_stone");
        Item magnet = ECTesla.MRU_MAGNET.get();
        Item fortifiedStone = ECMaterials.FORTIFIED_STONE.get().asItem();

        // 手放一份磁体原料：铁锭上格、魔力精华下格
        helper.assertTrue(be.getLastRecipe() == null, "新放的辐射仓不应记着配方");
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, new ItemStack(Items.IRON_INGOT));
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM, new ItemStack(ECAlloys.MAGICAL_ESSENCE.get()));

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    RadiatingChamberRecipe recipe = be.getDisplayRecipe();
                    helper.assertTrue(produces(recipe, magnet) && be.getProgress() > 0, "应在做 MRU 磁体，状态 " + be.getStatus());
                    fastForward(be, recipe.getMRU() - 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).is(magnet), "等待产出磁体"))
                .thenExecute(() -> {
                    helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).isEmpty()
                            && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM).isEmpty(), "上下两格应同时吃空");
                    helper.assertTrue(magnetId.equals(be.getLastRecipe()), "应记住磁体配方，实际 " + be.getLastRecipe());
                    // 存盘读回（和区块重新加载走同一条路）
                    be.load(be.saveWithoutMetadata());
                    helper.assertTrue(magnetId.equals(be.getLastRecipe()), "存盘读回后应仍记着磁体配方，实际 " + be.getLastRecipe());

                    helper.assertTrue(ItemHandlerHelper.insertItem(handler, new ItemStack(Items.IRON_INGOT), false).isEmpty(), "铁锭应插得进");
                    helper.assertTrue(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).is(Items.IRON_INGOT)
                                    && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM).isEmpty(),
                            "做过磁体后，两格吃空时铁锭应进上格");
                    helper.assertTrue(ItemHandlerHelper.insertItem(handler, new ItemStack(ECAlloys.MAGICAL_ESSENCE.get()), false).isEmpty()
                                    && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM).is(ECAlloys.MAGICAL_ESSENCE.get()),
                            "魔力精华应随后进下格");

                    // 换回强化石：手放一份（这时铁锭先到会进上格），清掉输出免得堵住
                    be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, new ItemStack(Items.STONE));
                    be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM, new ItemStack(Items.IRON_INGOT));
                    be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).is(fortifiedStone), "等待产出强化石"))
                .thenExecute(() -> {
                    helper.assertTrue(stoneId.equals(be.getLastRecipe()), "应改记强化石配方，实际 " + be.getLastRecipe());
                    helper.assertTrue(ItemHandlerHelper.insertItem(handler, new ItemStack(Items.IRON_INGOT), false).isEmpty()
                                    && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_BOTTOM).is(Items.IRON_INGOT)
                                    && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).isEmpty(),
                            "做过强化石后，两格吃空时铁锭应进下格");
                    helper.assertTrue(ItemHandlerHelper.insertItem(handler, new ItemStack(Items.STONE), false).isEmpty()
                                    && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP).is(Items.STONE),
                            "石头应随后进上格");
                })
                .thenSucceed();
    }

    private static void expectOutput(GameTestHelper helper, RadiatingChamberBlockEntity be, Item expected) {
        ItemStack out = be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(out.is(expected) && out.getCount() == 1, "应产出 1 个 " + expected + "，实际 " + out);
    }
}
