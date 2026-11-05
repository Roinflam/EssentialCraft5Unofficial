package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MagicianTableUpgrades;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.entity.MRUResistances;
import com.flameforge.essentialcraft5.gun.GunMaterials;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECWeaponMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 恶魔材料与恶魔护板（第七批 M3，batch7.md §4.3）的 GameTest：
 * <ul>
 *   <li>配方：恶魔板 = 魔术师台 魔力强化板 + 阿克罗尼特锭 ×4 → 4 个、2 000 MRU；恶魔核心 ISI / SES / ISI；恶魔护板 = 恶魔板 ×4 → 16 块。</li>
 *   <li>标签：恶魔核心进 cores/magic（枪的核心格也认）、恶魔板进 plates/magic、恶魔护板进富集仓外壳和线圈底座。</li>
 *   <li>按注册名预写的东西注册后生效：魔术师台升级 16、demonic 枪身与 16 倍狙击镜、恶魔护板 MRU 抗性 36。</li>
 *   <li>恶魔护板：硬度 3、抗爆 60、要镐、16 色、挖下来掉白色的自己。</li>
 * </ul>
 * 标签、掉落表要先跑 runData。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class DemonicMaterialTests {

    private static final String EMPTY = "empty";

    private DemonicMaterialTests() {
    }

    private static Recipe<?> recipe(GameTestHelper helper, String path) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id(path)).orElse(null);
        if (recipe == null) {
            helper.fail("没有配方 " + path);
        }
        return recipe;
    }

    /** 恶魔板：魔术师台，中间魔力强化板、四周 4 个阿克罗尼特锭 → 4 个，2 000 MRU（原作 RecipesCore L89） */
    @GameTest(template = EMPTY)
    public static void demonicPlateMagicianRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Recipe<?> recipe = recipe(helper, "demonic/demonic_plate");
        helper.assertTrue(recipe instanceof MagicianTableRecipe, "恶魔板应是魔术师台配方");
        MagicianTableRecipe table = (MagicianTableRecipe) recipe;
        ItemStack out = table.getResultItem(level.registryAccess());
        helper.assertTrue(out.is(ECDemonic.DEMONIC_PLATE.get()) && out.getCount() == 4, "一次出 4 个恶魔板，实际 " + out);
        helper.assertTrue(table.getMRU() == 2000, "花 2000 MRU，实际 " + table.getMRU());
        List<Ingredient> in = table.getIngredients();
        ItemStack fortified = new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("magic_fortified_plating")));
        helper.assertTrue(in.size() == 5 && in.get(0).test(fortified), "中间是魔力强化板");
        for (int i = 1; i < 5; i++) {
            helper.assertTrue(in.get(i).test(new ItemStack(ECDemonic.ACKRONITE_INGOT.get())), "四周是阿克罗尼特锭");
        }
        helper.succeed();
    }

    /** 恶魔核心 ISI / SES / ISI（恶魔板、元素晶石、MRU 共鸣水晶）；恶魔护板 = 恶魔板 ×4 → 16 块 */
    @GameTest(template = EMPTY)
    public static void demonicCoreAndPlatingRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Recipe<?> core = recipe(helper, "demonic/demonic_core");
        helper.assertTrue(core instanceof ShapedRecipe shaped && shaped.getWidth() == 3 && shaped.getHeight() == 3, "恶魔核心是 3×3 有序配方");
        List<Ingredient> in = core.getIngredients();
        ItemStack plate = new ItemStack(ECDemonic.DEMONIC_PLATE.get());
        ItemStack gem = new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("elemental_gem")));
        ItemStack resonating = new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("mru_resonating_crystal")));
        for (int i : new int[]{0, 2, 6, 8}) {
            helper.assertTrue(in.get(i).test(plate), "四角是恶魔板");
        }
        for (int i : new int[]{1, 3, 5, 7}) {
            helper.assertTrue(in.get(i).test(gem), "四边是元素晶石");
        }
        helper.assertTrue(in.get(4).test(resonating), "正中是 MRU 共鸣水晶");
        helper.assertTrue(core.getResultItem(level.registryAccess()).is(ECDemonic.DEMONIC_CORE.get()), "产出恶魔核心");

        Recipe<?> plating = recipe(helper, "demonic/demonic_plating");
        ItemStack out = plating.getResultItem(level.registryAccess());
        helper.assertTrue(plating instanceof ShapelessRecipe && plating.getIngredients().size() == 4
                && plating.getIngredients().stream().allMatch(i -> i.test(plate)), "恶魔护板：4 个恶魔板，无序");
        helper.assertTrue(out.is(ECDemonic.DEMONIC_PLATING.get().asItem()) && out.getCount() == 16, "一次出 16 块，实际 " + out);
        helper.assertTrue(level.getServer().getAdvancements().getAdvancement(EssentialCraft5.id("guide/demons/ackronite")) != null,
                "应有手册打勾进度 guide/demons/ackronite");
        helper.succeed();
    }

    /** 标签与按注册名预写的数值：cores/magic、plates/magic、外壳、线圈底座；魔术师台升级 16、demonic 枪材与 16 倍镜、MRU 抗性 36 */
    @GameTest(template = EMPTY)
    public static void demonicTagsAndPresets(GameTestHelper helper) {
        ItemStack core = new ItemStack(ECDemonic.DEMONIC_CORE.get());
        ItemStack plate = new ItemStack(ECDemonic.DEMONIC_PLATE.get());
        helper.assertTrue(core.is(ECTags.modItem("cores/magic")) && core.is(ECWeaponMaker.MAGIC_CORES), "恶魔核心应在 cores/magic 里（枪的核心格也认）");
        helper.assertTrue(plate.is(ECTags.modItem("plates/magic")), "恶魔板应在 plates/magic 里");
        BlockState plating = ECDemonic.DEMONIC_PLATING.get().defaultBlockState();
        helper.assertTrue(plating.is(ECTags.modBlock("mrucuec_casing")) && plating.is(ECTags.modBlock("mru_coil_base")),
                "恶魔护板应在富集仓外壳、线圈底座标签里");

        helper.assertTrue(MagicianTableUpgrades.efficiency(core) == 16D, "恶魔核心当魔术师台升级 16，实际 " + MagicianTableUpgrades.efficiency(core));
        helper.assertTrue(GunMaterials.body("demonic").map(GunMaterials.Body::available).orElse(false)
                && GunMaterials.bodyByItem(new ItemStack(ECDemonic.ACKRONITE_INGOT.get())).isPresent(), "demonic 枪身注册后开放");
        helper.assertTrue(GunMaterials.scope(GunType.SNIPER, "demonic").filter(GunMaterials.Scope::available).map(GunMaterials.Scope::zoom).orElse(0F) == 16F,
                "阿克罗尼特狙击镜 16 倍");
        helper.assertTrue(MRUResistances.get(plating) == 36F, "恶魔护板 MRU 抗性 36，实际 " + MRUResistances.get(plating));
        helper.succeed();
    }

    /** 恶魔护板：硬度 3、抗爆 60、要镐、16 色；挖下来掉白色的自己 */
    @GameTest(template = EMPTY)
    public static void demonicPlatingBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block block = ECDemonic.DEMONIC_PLATING.get();
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block.defaultBlockState().setValue(DyeableBlock.COLOR, DyeColor.RED));
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = level.getBlockState(abs);
        helper.assertTrue(block instanceof DyeableBlock && state.getValue(DyeableBlock.COLOR) == DyeColor.RED, "能染成 16 色（方块状态）");
        helper.assertTrue(state.getDestroySpeed(level, abs) == 3F, "硬度 3");
        helper.assertTrue(Math.abs(state.getExplosionResistance(level, abs, null) - 60F) < 1e-4F, "抗爆 60（原作先硬度后抗爆 3 / 100）");
        helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.requiresCorrectToolForDrops(), "要镐");
        List<ItemStack> drops = Block.getDrops(state, level, abs, null, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1, "掉 1 个自己（要先跑 runData），实际 " + drops);
        helper.succeed();
    }
}
