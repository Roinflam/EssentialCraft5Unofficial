package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IColdBlock;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 材料模块的 GameTest：染色、魔晶簇掉落、霜冻石冷值、魔力水还瓶子。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MaterialsTests {

    private static final String EMPTY = "empty";

    private MaterialsTests() {
    }

    /**
     * 拿染料右键只染这一块、不消耗染料；潜行右键把周围的同种方块一起染（范围染色只给真玩家，用 realPlayer）；
     * 假玩家（机械手、方块激活器）潜行也只染点中的那一块（第三批审查 RB-5：混合端不转假玩家的放置事件，连带的格子没人管）
     */
    @GameTest(template = EMPTY)
    public static void fortifiedStoneDyeing(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(2, 1, 1);
        helper.setBlock(a, ECMaterials.FORTIFIED_STONE.get());
        helper.setBlock(b, ECMaterials.FORTIFIED_STONE.get());
        helper.assertBlockProperty(a, DyeableBlock.COLOR, DyeColor.WHITE);

        Player player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE));
        helper.useBlock(a, player);
        helper.assertBlockProperty(a, DyeableBlock.COLOR, DyeColor.RED);
        helper.assertBlockProperty(b, DyeableBlock.COLOR, DyeColor.WHITE);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "染色不应消耗染料");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLUE_DYE));
        player.setShiftKeyDown(true);
        helper.useBlock(a, player);
        helper.assertBlockProperty(a, DyeableBlock.COLOR, DyeColor.BLUE);
        helper.assertBlockProperty(b, DyeableBlock.COLOR, DyeColor.BLUE);

        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        ItemStack fakeHeld = fake.getItemInHand(InteractionHand.MAIN_HAND);
        boolean fakeSneaking = fake.isShiftKeyDown();
        try {
            fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GREEN_DYE));
            fake.setShiftKeyDown(true);
            helper.useBlock(a, fake);
        } finally {
            // FakePlayerFactory 按 GameProfile 缓存同一个实例，别的测试还会拿到它
            fake.setItemInHand(InteractionHand.MAIN_HAND, fakeHeld);
            fake.setShiftKeyDown(fakeSneaking);
        }
        helper.assertBlockProperty(a, DyeableBlock.COLOR, DyeColor.GREEN);
        helper.assertBlockProperty(b, DyeableBlock.COLOR, DyeColor.BLUE);
        helper.succeed();
    }

    /** 火之魔晶簇敲碎掉 1~6 颗火之魔晶（多试几次，覆盖随机） */
    @GameTest(template = EMPTY)
    public static void dropsClusterDrops(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ECMaterials.FIRE_DROPS_CLUSTER.get());
        BlockState state = helper.getBlockState(pos);
        for (int i = 0; i < 32; i++) {
            List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), null);
            int count = 0;
            for (ItemStack stack : drops) {
                helper.assertTrue(stack.is(ECMaterials.FIRE_DROPS.get()), "只应掉火之魔晶，实际 " + stack);
                count += stack.getCount();
            }
            helper.assertTrue(count >= 1 && count <= 6, "应掉 1~6 颗，实际 " + count);
        }
        helper.succeed();
    }

    /** 霜冻石冷值 0.5，普通方块 0 */
    @GameTest(template = EMPTY)
    public static void coldStoneColdModifier(GameTestHelper helper) {
        BlockPos cold = new BlockPos(1, 1, 1);
        BlockPos stone = new BlockPos(2, 1, 1);
        helper.setBlock(cold, ECMaterials.COLD_STONE.get());
        helper.setBlock(stone, Blocks.STONE);
        helper.assertTrue(coldModifier(helper, cold) == 0.5F, "霜冻石冷值应为 0.5");
        helper.assertTrue(coldModifier(helper, stone) == 0F, "石头不是 IColdBlock，冷值应为 0");
        helper.succeed();
    }

    private static float coldModifier(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(pos);
        return state.getBlock() instanceof IColdBlock cold ? cold.getColdModifier(helper.getLevel(), pos, state) : 0F;
    }

    /** 魔力水：一组没喝完时瓶子进物品栏（原作会丢掉），喝完最后一瓶直接变成玻璃瓶 */
    @GameTest(template = EMPTY)
    public static void magicalWaterReturnsBottle(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack water = new ItemStack(ECMaterials.MAGICAL_WATER.get(), 2);
        ItemStack left = water.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(left.is(ECMaterials.MAGICAL_WATER.get()) && left.getCount() == 1, "应剩 1 瓶魔力水，实际 " + left);
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "玻璃瓶应进物品栏");

        ItemStack last = left.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(last.is(Items.GLASS_BOTTLE), "喝完最后一瓶应返回玻璃瓶，实际 " + last);
        helper.succeed();
    }

    /** 魔晶簇挡液体（原作 Material.CLOTH；不挡的话岩浆流过来会把它烧掉、一颗魔晶都不掉）；霜冻石不导红石、不刷怪 */
    @GameTest(template = EMPTY)
    public static void clusterBlocksFluidsAndColdStoneIsIce(GameTestHelper helper) {
        BlockState cluster = ECMaterials.FIRE_DROPS_CLUSTER.get().defaultBlockState();
        helper.assertTrue(!cluster.canBeReplaced(Fluids.LAVA) && !cluster.canBeReplaced(Fluids.WATER), "液体不应能冲掉魔晶簇");
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECMaterials.COLD_STONE.get());
        BlockState cold = helper.getBlockState(pos);
        BlockPos absolute = helper.absolutePos(pos);
        helper.assertTrue(!cold.isRedstoneConductor(helper.getLevel(), absolute), "霜冻石不应导红石");
        helper.assertTrue(!cold.isValidSpawn(helper.getLevel(), absolute, EntityType.ZOMBIE), "怪物不应能在霜冻石上生成");
        helper.succeed();
    }
}
