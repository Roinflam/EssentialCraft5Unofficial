package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MagicianTableUpgrades;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.block.LevitatorBlock;
import com.flameforge.essentialcraft5.block.ParticleCooldown;
import com.flameforge.essentialcraft5.block.SpreaderBlock;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.entity.MRUResistances;
import com.flameforge.essentialcraft5.gun.GunMaterials;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.item.bauble.MysticalAccessoryItem;
import com.flameforge.essentialcraft5.loot.HoannaLoot;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.registry.ECHoannaSurvey;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 虚空材料与海纳尔遗物（ECVoid，batch6a §5.7）的 GameTest（掉落表、标签、配方都要先跑 runData）：
 * <ul>
 *   <li>方块数值（硬度 / 抗爆换算、要不要工具、栅栏标签、悬浮器 / 散射器不开随机刻、散射器亮度 15）、MRU 抗性 18 / 15；
 *       虚空玻璃空手也掉、镐挖得快（挖掘时间）；</li>
 *   <li>三条工作台配方（16 / 32 / 16，中心换别的不匹配）、魔术师台做虚空板（中心虚空石、四角空、1 000 MRU）；</li>
 *   <li>注册即生效：魔术师台升级 ×6 / ×7.5 / ×10、虚空枪身与 12 倍狙击镜开放；</li>
 *   <li>虚空石染色（不耗染料、挖下来白色）、潜行范围染色：被模拟领地拒绝的格子不变、没有领地时整片都染；走真右键路径时不把别人设的 useBlock DENY 改回 ALLOW；</li>
 *   <li>虚空石当特斯拉线圈底座、虚空石 + 虚空玻璃当富集仓外壳；</li>
 *   <li>两张遗迹箱子表（结构对原作、开箱产物都已注册、数量在范围、神秘饰品开出来就有属性）；</li>
 *   <li>神秘饰品：只能戴进形态对应的槽（canEquip 与 Curios 自己的放入校验 / 右键戴上都测）、抗性按 NBT 进易伤、没抽过的进背包就抽、被禁用时全算 0、
 *       多件合计最多 0.4（加出来的饰品栏也一样，别的饰品不受影响）；余烬 8 种、一组 16、只进本模组标签；</li>
 *   <li>ε 腰带：真玩家亲手击杀按 5% 掉余烬，假玩家、没戴、doMobLoot 关掉都不掉；掉落事件里真的加进掉落列表；</li>
 *   <li>悬浮器 / 散射器粒子的每格冷却与预算、悬浮器被挡住的面不冒（埋着的一颗都不冒）、粉尘大小。</li>
 * </ul>
 * 真玩家用 {@link ProtectionFrameworkTests#realPlayer}（不在世界里），模拟领地用 {@link ProtectionFrameworkTests.Claims}；随机种子乘大奇数打散。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class VoidTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 打散候选种子（坑：挨着的小种子第一次 nextFloat 几乎一样） */
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;

    private VoidTests() {
    }

    // ------------------------------------------------------------------ 工具

    private static ItemStack stack(RegistryObject<? extends Item> item) {
        return new ItemStack(item.get());
    }

    private static ItemStack blockItem(RegistryObject<? extends Block> block) {
        return new ItemStack(block.get());
    }

    private static void near(GameTestHelper helper, float actual, float expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < 1e-4F, what + " 应是 " + expected + "，实际 " + actual);
    }

    /** 直接放进饰品槽（不走 canEquip），并让缓存作废 */
    private static void wear(GameTestHelper helper, Player player, String slot, ItemStack stack) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player).resolve();
        if (inventory.isEmpty()) {
            helper.fail("玩家没有 Curios 饰品栏");
            return;
        }
        ICurioStacksHandler handler = inventory.get().getCurios().get(slot);
        if (handler == null || handler.getSlots() < 1) {
            helper.fail("玩家没有 Curios 槽位 " + slot);
            return;
        }
        handler.getStacks().setStackInSlot(0, stack);
        CuriosCompat.invalidate(player);
    }

    private static CraftingContainer grid(String[] pattern, Map<Character, ItemStack> key) {
        CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        for (int row = 0; row < pattern.length; row++) {
            for (int col = 0; col < pattern[row].length(); col++) {
                ItemStack item = key.get(pattern[row].charAt(col));
                if (item != null) {
                    grid.setItem(row * 3 + col, item.copy());
                }
            }
        }
        return grid;
    }

    @Nullable
    private static ItemStack craft(ServerLevel level, CraftingContainer grid) {
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        return recipe.map(r -> r.assemble(grid, level.registryAccess())).orElse(null);
    }

    @Nullable
    private static JsonObject readJson(GameTestHelper helper, ResourceLocation file) {
        Optional<Resource> resource = helper.getLevel().getServer().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            helper.fail("找不到 " + file);
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            helper.fail(file + " 读不出来：" + e);
            return null;
        }
    }

    // ------------------------------------------------------------------ 方块

    /**
     * 硬度 8 / 8 / 8 / 1 / 1 / 1、抗爆 90 / 90 / 90 / 1 / 60 / 60；虚空玻璃空手也掉、不刷怪，但也在 mineable/pickaxe（镐挖得快，挖掘时间见 glassMiningTime）；
     * 栅栏标签；悬浮器 / 散射器不开随机刻、散射器亮度 15
     */
    @GameTest(template = EMPTY)
    public static void voidBlocksRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
        // 方块、硬度、抗爆、要不要镐才掉、在不在 mineable/pickaxe
        Object[][] table = {
                {ECVoid.VOID_STONE, 8F, 90F, true, true},
                {ECVoid.VOID_GLASS, 8F, 90F, false, true},
                {ECVoid.VOID_FENCE, 8F, 90F, true, true},
                {ECVoid.CONCRETE, 1F, 1F, true, true},
                {ECVoid.LEVITATOR, 1F, 60F, true, true},
                {ECVoid.SPREADER, 1F, 60F, true, true},
        };
        ItemStack pick = new ItemStack(Items.WOODEN_PICKAXE);
        for (Object[] row : table) {
            @SuppressWarnings("unchecked")
            RegistryObject<? extends Block> block = (RegistryObject<? extends Block>) row[0];
            BlockState state = block.get().defaultBlockState();
            String name = block.getId().toString();
            near(helper, state.getDestroySpeed(level, abs), (Float) row[1], name + " 的硬度");
            near(helper, state.getExplosionResistance(level, abs, null), (Float) row[2], name + " 的抗爆");
            boolean tool = (Boolean) row[3];
            boolean mineable = (Boolean) row[4];
            helper.assertTrue(state.requiresCorrectToolForDrops() == tool, name + (tool ? " 应要镐才掉" : " 应空手可挖"));
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE) == mineable,
                    name + (mineable ? " 应在 mineable/pickaxe（要先跑 runData）" : " 不应在 mineable/pickaxe"));
            if (tool) {
                helper.assertTrue(pick.isCorrectToolForDrops(state), name + " 木镐应能挖（原作 Material.ROCK 0 级）");
            }
            helper.assertFalse(state.isRandomlyTicking(), name + " 不应开随机刻");
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(block.getId()), name + " 应有方块物品");
        }
        BlockState glass = ECVoid.VOID_GLASS.get().defaultBlockState();
        List<ItemStack> glassDrops = Block.getDrops(glass, level, abs, null, null, ItemStack.EMPTY);
        helper.assertTrue(glassDrops.size() == 1 && glassDrops.get(0).is(ECVoid.VOID_GLASS.get().asItem()), "虚空玻璃空手挖应掉自己，实际 " + glassDrops);
        helper.assertFalse(glass.isValidSpawn(level, abs, EntityType.ZOMBIE), "虚空玻璃上面不应刷怪");
        helper.assertFalse(glass.isRedstoneConductor(level, abs), "虚空玻璃不导红石");
        helper.assertTrue(ECVoid.VOID_FENCE.get().defaultBlockState().is(BlockTags.FENCES)
                && blockItem(ECVoid.VOID_FENCE).is(ItemTags.FENCES), "虚空栅栏应在 minecraft:fences（方块和物品）");
        int light = ECVoid.SPREADER.get().defaultBlockState().getLightEmission(level, abs);
        helper.assertTrue(light == SpreaderBlock.LIGHT && light == 15, "散射器亮度应是 15，实际 " + light);
        helper.assertTrue(ECVoid.LEVITATOR.get().defaultBlockState().getLightEmission(level, abs) == 0, "悬浮器不发光");
        helper.assertFalse(ECVoid.CONCRETE.get().defaultBlockState().propagatesSkylightDown(level, abs), "混凝土是普通不透光方块");
        helper.succeed();
    }

    /** 挖下来：虚空石（染成什么色都）掉 1 个普通虚空石（物品没有颜色，放下是白色）；栅栏、混凝土、悬浮器、散射器用镐掉自己 */
    @GameTest(template = EMPTY)
    public static void voidBlocksDropThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        BlockState red = ECVoid.VOID_STONE.get().defaultBlockState().setValue(DyeableBlock.COLOR, DyeColor.RED);
        List<ItemStack> drops = Block.getDrops(red, level, abs, null, null, pick);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ECVoid.VOID_STONE.get().asItem()) && drops.get(0).getCount() == 1
                && drops.get(0).getTag() == null, "红色虚空石应掉 1 个普通虚空石，实际 " + drops + "（要先跑 runData）");
        for (RegistryObject<? extends Block> block : List.of(ECVoid.VOID_FENCE, ECVoid.CONCRETE, ECVoid.LEVITATOR, ECVoid.SPREADER)) {
            List<ItemStack> own = Block.getDrops(block.get().defaultBlockState(), level, abs, null, null, pick);
            helper.assertTrue(own.size() == 1 && own.get(0).is(block.get().asItem()), block.getId() + " 用镐挖应掉自己，实际 " + own);
        }
        helper.succeed();
    }

    /**
     * 虚空玻璃的挖掘时间（站在地上一直按着挖）：空手约 12 秒（240 刻）、钻石镐约 1.5 秒（30 刻）。
     * 不要求工具（空手也掉自己），但在 mineable/pickaxe 里：不在的话镐不加速，钻石镐也要 240 刻。
     * 强化玻璃照原作不进 mineable（硬度 3，拿什么挖都约 4.5 秒），这里不测。
     */
    @GameTest(template = EMPTY)
    public static void glassMiningTime(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        // 不在地上时挖掘速度再 ÷5
        player.setOnGround(true);
        BlockState voidGlass = ECVoid.VOID_GLASS.get().defaultBlockState();

        int voidBare = ticksToMine(helper, player, voidGlass);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        int voidPick = ticksToMine(helper, player, voidGlass);
        helper.assertTrue(voidBare >= 230 && voidBare <= 250, "空手挖虚空玻璃应约 240 刻（12 秒），实际 " + voidBare);
        helper.assertTrue(voidPick >= 28 && voidPick <= 33, "钻石镐挖虚空玻璃应约 30 刻（1.5 秒），实际 " + voidPick);
        helper.succeed();
    }

    /** 一直按着挖这种方块要几刻（照客户端 MultiPlayerGameMode：每刻累加 getDestroyProgress，到 1 挖掉） */
    private static int ticksToMine(GameTestHelper helper, ServerPlayer player, BlockState state) {
        float perTick = state.getDestroyProgress(player, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)));
        if (perTick <= 0F) {
            return Integer.MAX_VALUE;
        }
        float progress = 0F;
        int ticks = 0;
        while (progress < 1F && ticks < 100_000) {
            progress += perTick;
            ticks++;
        }
        return ticks;
    }

    /** 方块的 MRU 抗性（聚集体矩阵损伤的「挡墙」）：虚空石 18、虚空玻璃 15，染成什么颜色都一样 */
    @GameTest(template = EMPTY)
    public static void voidMRUResistance(GameTestHelper helper) {
        for (DyeColor color : new DyeColor[] {DyeColor.WHITE, DyeColor.PURPLE}) {
            near(helper, MRUResistances.get(ECVoid.VOID_STONE.get().defaultBlockState().setValue(DyeableBlock.COLOR, color)), 18F,
                    "虚空石（" + color + "）的 MRU 抗性");
            near(helper, MRUResistances.get(ECVoid.VOID_GLASS.get().defaultBlockState().setValue(DyeableBlock.COLOR, color)), 15F,
                    "虚空玻璃（" + color + "）的 MRU 抗性");
        }
        near(helper, MRUResistances.get(ECVoid.CONCRETE.get().defaultBlockState()), MRUResistances.DEFAULT, "混凝土的 MRU 抗性");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 配方

    /** 虚空石 ISI / STS / ISI → 16，虚空玻璃（S 换强化框架）→ 32，栅栏 DDD / DDD → 16；中心换成榍石都不匹配（原作矿辞笔误，这里直接引用物品、能做） */
    @GameTest(template = EMPTY)
    public static void voidCraftingRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack titanite = stack(ECVoid.TITANITE);
        ItemStack twinkling = stack(ECVoid.TWINKLING_TITANITE);
        ItemStack obsidianPlate = new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("obsidian_plate")));
        ItemStack frame = new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("fortified_frame")));
        String[] shape = {"ISI", "STS", "ISI"};

        ItemStack stone = craft(level, grid(shape, Map.of('I', titanite, 'S', obsidianPlate, 'T', twinkling)));
        helper.assertTrue(stone != null && stone.is(ECVoid.VOID_STONE.get().asItem()) && stone.getCount() == 16, "虚空石配方应出 16 个，实际 " + stone);
        ItemStack glass = craft(level, grid(shape, Map.of('I', titanite, 'S', frame, 'T', twinkling)));
        helper.assertTrue(glass != null && glass.is(ECVoid.VOID_GLASS.get().asItem()) && glass.getCount() == 32, "虚空玻璃配方应出 32 个，实际 " + glass);
        ItemStack fence = craft(level, grid(new String[] {"DDD", "DDD"}, Map.of('D', blockItem(ECVoid.VOID_STONE))));
        helper.assertTrue(fence != null && fence.is(ECVoid.VOID_FENCE.get().asItem()) && fence.getCount() == 16, "虚空栅栏配方应出 16 个，实际 " + fence);

        helper.assertTrue(craft(level, grid(shape, Map.of('I', titanite, 'S', obsidianPlate, 'T', titanite))) == null, "中心不是闪耀榍石时不应匹配（虚空石）");
        helper.assertTrue(craft(level, grid(shape, Map.of('I', titanite, 'S', frame, 'T', titanite))) == null, "中心不是闪耀榍石时不应匹配（虚空玻璃）");
        helper.assertTrue(craft(level, grid(shape, Map.of('I', twinkling, 'S', obsidianPlate, 'T', twinkling))) == null, "四角不是榍石时不应匹配");
        helper.succeed();
    }

    /** 魔术师台：中心虚空石、四角空 → 虚空板、1 000 MRU；四角任一格有东西就不匹配 */
    @GameTest(template = EMPTY)
    public static void voidPlatingMagicianTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack voidStone = blockItem(ECVoid.VOID_STONE);
        Optional<MagicianTableRecipe> recipe = level.getRecipeManager().getRecipeFor(ECRecipes.MAGICIAN_TABLE.get(),
                new SimpleContainer(voidStone.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), level);
        helper.assertTrue(recipe.isPresent(), "中心放虚空石、四角空应匹配魔术师台配方");
        helper.assertTrue(recipe.get().getResultItem(level.registryAccess()).is(ECVoid.VOID_PLATING.get()) && recipe.get().getMRU() == 1000,
                "应做出虚空板、要 1 000 MRU，实际 " + recipe.get().getResultItem(level.registryAccess()) + " / " + recipe.get().getMRU());
        for (int corner = 1; corner <= 4; corner++) {
            ItemStack[] slots = {voidStone.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
            slots[corner] = stack(ECVoid.TITANITE);
            helper.assertTrue(level.getRecipeManager().getRecipeFor(ECRecipes.MAGICIAN_TABLE.get(), new SimpleContainer(slots), level).isEmpty(),
                    "第 " + corner + " 个角放了东西时不应匹配");
        }
        helper.succeed();
    }

    /** 注册即生效：魔术师台升级 ×6 / ×7.5 / ×10（api/MagicianTableUpgrades 早就按注册名登记了）；虚空枪身与 12 倍狙击镜开放（gun/GunMaterials 按「物品已注册」） */
    @GameTest(template = EMPTY)
    public static void voidUpgradesAndGunTier(GameTestHelper helper) {
        helper.assertTrue(MagicianTableUpgrades.efficiency(stack(ECVoid.VOID_PLATING)) == 6D, "虚空板应是 ×6 升级");
        helper.assertTrue(MagicianTableUpgrades.efficiency(stack(ECVoid.VOID_CORE)) == 7.5D, "虚空核心应是 ×7.5 升级");
        helper.assertTrue(MagicianTableUpgrades.efficiency(stack(ECVoid.VOID_MRU_REACTOR)) == 10D, "MRU 反应核应是 ×10 升级");

        GunMaterials.Body body = GunMaterials.body("void").orElse(null);
        helper.assertTrue(body != null && body.available(), "虚空枪身应已开放");
        helper.assertTrue(GunMaterials.bodyByItem(stack(ECVoid.VOID_PLATING)).orElse(null) == body, "虚空板应认成虚空枪身材料");
        helper.assertTrue(GunMaterials.availableScopes(GunType.SNIPER).stream().anyMatch(s -> s.id().equals("void") && s.zoom() == 12F),
                "12 倍虚空狙击镜应已开放");
        helper.assertTrue(GunMaterials.scopeByItem(GunType.SNIPER, stack(ECVoid.VOID_PLATING)).map(GunMaterials.Scope::zoom).orElse(0F) == 12F,
                "虚空板应是 12 倍狙击镜材");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 染色（复用 DyeableBlock）

    /** 红染料右键 → 红色、染料不少；再用紫染料 → 紫色 */
    @GameTest(template = EMPTY)
    public static void voidStoneDyes(GameTestHelper helper) {
        BlockPos stonePos = new BlockPos(1, 1, 2);
        BlockPos glassPos = new BlockPos(3, 1, 2);
        helper.setBlock(stonePos, ECVoid.VOID_STONE.get());
        helper.setBlock(glassPos, ECVoid.VOID_GLASS.get());
        helper.assertBlockProperty(stonePos, DyeableBlock.COLOR, DyeColor.WHITE);
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE));
        helper.useBlock(stonePos, player);
        helper.assertBlockProperty(stonePos, DyeableBlock.COLOR, DyeColor.RED);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "染色不应消耗染料");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PURPLE_DYE));
        helper.useBlock(glassPos, player);
        helper.assertBlockProperty(glassPos, DyeableBlock.COLOR, DyeColor.PURPLE);
        helper.assertBlockProperty(stonePos, DyeableBlock.COLOR, DyeColor.RED);
        helper.succeed();
    }

    /** 潜行范围染色（限制：连带的格子逐格以玩家发放置事件）：被模拟领地拒绝的那格不变色，点中的那格和其余同种方块照染；不同种的虚空玻璃不被连带 */
    @GameTest(template = EMPTY)
    public static void voidStoneAreaDyeDeniedCellUnchanged(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(1, 1, 2);
        BlockPos free = new BlockPos(2, 1, 2);
        BlockPos claimed = new BlockPos(3, 1, 2);
        BlockPos glass = new BlockPos(2, 2, 2);
        helper.setBlock(clicked, ECVoid.VOID_STONE.get());
        helper.setBlock(free, ECVoid.VOID_STONE.get());
        helper.setBlock(claimed, ECVoid.VOID_STONE.get());
        helper.setBlock(glass, ECVoid.VOID_GLASS.get());
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE));
        player.setShiftKeyDown(true);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(claimed)) {
            helper.useBlock(clicked, player);
            helper.assertBlockProperty(clicked, DyeableBlock.COLOR, DyeColor.RED);
            helper.assertBlockProperty(free, DyeableBlock.COLOR, DyeColor.RED);
            helper.assertBlockProperty(claimed, DyeableBlock.COLOR, DyeColor.WHITE);
            helper.assertBlockProperty(glass, DyeableBlock.COLOR, DyeColor.WHITE);
            helper.assertTrue(claims.places(claimed) == 1 && claims.places(free) == 1, "连带的两格各发一次放置事件，实际 "
                    + claims.places(free) + " / " + claims.places(claimed));
            helper.assertTrue(claims.places(clicked) == 0 && claims.places(glass) == 0, "点中的那格、不同种的方块不发放置事件");
        }
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "染料不少");
        helper.succeed();
    }

    /**
     * 走真的右键路径（ServerPlayerGameMode.useItemOn → RightClickBlock 事件 → SneakDyeing 放行 → use）。上面几条用 helper.useBlock 直接调 use，
     * 测不到事件这一环。审查 M7-5：别的模组在更高优先级只把 useBlock 设成 DENY（没取消事件）时，SneakDyeing 不许改回 ALLOW——
     * 点中的那格和它连带的格子都不变色；对照组没人拦时照常整片染。
     */
    @GameTest(template = EMPTY)
    public static void voidStoneSneakDyeKeepsDeny(GameTestHelper helper) {
        // 两组对角摆开：潜行染色是点中那格 ±2 的方盒，对照组染色时不能把被拦的那组连带染掉
        BlockPos openClicked = new BlockPos(0, 1, 0);
        BlockPos openNeighbour = new BlockPos(1, 1, 0);
        BlockPos deniedClicked = new BlockPos(4, 1, 4);
        BlockPos deniedNeighbour = new BlockPos(3, 1, 4);
        for (BlockPos pos : List.of(openClicked, openNeighbour, deniedClicked, deniedNeighbour)) {
            helper.setBlock(pos, ECVoid.VOID_STONE.get());
        }
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE));
        player.setShiftKeyDown(true);
        BlockPos deniedAbs = helper.absolutePos(deniedClicked);
        Consumer<PlayerInteractEvent.RightClickBlock> otherMod = event -> {
            if (event.getPos().equals(deniedAbs)) {
                event.setUseBlock(Event.Result.DENY);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.RightClickBlock.class, otherMod);
        try {
            rightClick(helper, player, openClicked);
            helper.assertBlockProperty(openClicked, DyeableBlock.COLOR, DyeColor.RED);
            helper.assertBlockProperty(openNeighbour, DyeableBlock.COLOR, DyeColor.RED);
            rightClick(helper, player, deniedClicked);
            helper.assertBlockProperty(deniedClicked, DyeableBlock.COLOR, DyeColor.WHITE);
            helper.assertBlockProperty(deniedNeighbour, DyeableBlock.COLOR, DyeColor.WHITE);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(otherMod);
        }
        helper.assertTrue(player.getMainHandItem().is(Items.RED_DYE) && player.getMainHandItem().getCount() == 1, "染料不少");
        helper.succeed();
    }

    /** 以玩家身份右键方块顶面（服务端真的交互路径，会发 RightClickBlock） */
    private static void rightClick(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    /** 同上，没有领地：5 × 5 × 5 范围里的虚空石全染（这里摆一横排 + 一竖列） */
    @GameTest(template = EMPTY)
    public static void voidStoneAreaDyeWithoutClaims(GameTestHelper helper) {
        List<BlockPos> stones = List.of(new BlockPos(0, 1, 2), new BlockPos(1, 1, 2), new BlockPos(2, 1, 2), new BlockPos(3, 1, 2),
                new BlockPos(4, 1, 2), new BlockPos(2, 2, 2), new BlockPos(2, 3, 2), new BlockPos(2, 1, 0), new BlockPos(2, 1, 4));
        for (BlockPos pos : stones) {
            helper.setBlock(pos, ECVoid.VOID_STONE.get());
        }
        BlockPos outside = new BlockPos(2, 4, 2);   // 点中 (2, 1, 2)：往上 3 格已出 ±2 的范围
        helper.setBlock(outside, ECVoid.VOID_STONE.get());
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LIME_DYE));
        player.setShiftKeyDown(true);
        helper.useBlock(new BlockPos(2, 1, 2), player);
        for (BlockPos pos : stones) {
            helper.assertBlockProperty(pos, DyeableBlock.COLOR, DyeColor.LIME);
        }
        helper.assertBlockProperty(outside, DyeableBlock.COLOR, DyeColor.WHITE);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 结构标签

    /** 25 块虚空石当特斯拉线圈的底座（mru_coil_base，原作 StructureRegistry L55），0 层基座 → 成形、射程 3 */
    @GameTest(template = EMPTY_LARGE)
    public static void coilBaseAcceptsVoidStone(GameTestHelper helper) {
        BlockPos base = new BlockPos(5, 1, 5);
        for (BlockPos offset : ECTesla.COIL_FOUNDATION.rules().keySet()) {
            helper.setBlock(base.offset(offset), ECVoid.VOID_STONE.get().defaultBlockState().setValue(DyeableBlock.COLOR, DyeColor.CYAN));
        }
        BlockPos coilPos = base.above();
        helper.setBlock(coilPos, ECTesla.MRU_COIL.get().defaultBlockState());
        if (!(helper.getBlockEntity(coilPos) instanceof MRUCoilBlockEntity coil)) {
            helper.fail("线圈没有方块实体");
            return;
        }
        coil.limitSearchForTest(new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(10, 7, 10))));
        coil.setSpawnSafeRadiusForTest(0);
        coil.refreshNowForTest();
        helper.assertTrue(helper.getBlockState(base).is(ECTesla.MRU_COIL_BASE), "虚空石应在 essentialcraft5:mru_coil_base 里（要先跑 runData）");
        helper.assertTrue(coil.formed() && coil.radius() == 3, "虚空石底座应成形、射程 3，实际 formed=" + coil.formed() + " r=" + coil.radius());
        helper.succeed();
    }

    /** 虚空石砌 3 × 3 × 3 外壳、东面整面换成虚空玻璃，角上放控制器 → 富集仓成形（mrucuec_casing，原作 StructureRegistry L38~43） */
    @GameTest(template = EMPTY_LARGE)
    public static void casingAcceptsVoid(GameTestHelper helper) {
        BlockPos min = new BlockPos(2, 1, 2);
        BlockPos max = new BlockPos(4, 3, 4);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    boolean shell = x == min.getX() || x == max.getX() || y == min.getY() || y == max.getY() || z == min.getZ() || z == max.getZ();
                    if (shell) {
                        helper.setBlock(new BlockPos(x, y, z), x == max.getX() ? ECVoid.VOID_GLASS.get() : ECVoid.VOID_STONE.get());
                    }
                }
            }
        }
        helper.setBlock(min, ECEnrichment.EC_CONTROLLER.get());
        if (!(helper.getBlockEntity(min) instanceof EnrichmentControllerBlockEntity controller)) {
            helper.fail("控制器没有方块实体");
            return;
        }
        StructureResult result = controller.checkNow();
        helper.assertTrue(result == StructureResult.MATCH, "虚空石 + 虚空玻璃的外壳应成形，实际 " + result + " / " + controller.failure()
                + "（要先跑 runData 生成标签）");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 遗迹箱子

    /**
     * 两张表的遗物池照原作：抽 6~17、24 条、总权重 343、两张逐项相同；神秘饰品带 randomize_accessory 与 not_disabled。
     * 第十二批起遗物池后面各多一池源质残卷（几率、卷号两张不同，ScrollTests 核），这里只认出它、不算进遗物；
     * 第十三批 13d 再多一池残破勘测图（rolls 1、random_chance 0.12，HoannaSurveyTests 核几率），同样只认出它。
     * 各开 200 箱（种子打散）：产物都已注册、每堆数量在该条目范围里、神秘饰品开出来就有形态和三项抗性。
     */
    @GameTest(template = EMPTY)
    public static void hoannaChestTables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        JsonObject tower = readJson(helper, EssentialCraft5.id("loot_tables/" + HoannaLoot.TOWN_TOWER.getPath() + ".json"));
        JsonObject catacombs = readJson(helper, EssentialCraft5.id("loot_tables/" + HoannaLoot.CATACOMBS.getPath() + ".json"));
        if (tower == null || catacombs == null) {
            return;
        }
        JsonArray pools = tower.getAsJsonArray("pools");
        helper.assertTrue(pools.size() == 3 && catacombs.getAsJsonArray("pools").size() == 3, "应是遗物池 + 残卷池 + 勘测图池，实际 " + pools.size());
        helper.assertTrue(pools.get(0).equals(catacombs.getAsJsonArray("pools").get(0)), "两张表的遗物池应逐项相同（原作如此）");
        for (JsonObject table : List.of(tower, catacombs)) {
            JsonObject scroll = table.getAsJsonArray("pools").get(1).getAsJsonObject();
            helper.assertTrue("ancient_scroll".equals(scroll.get("name").getAsString())
                    && scroll.toString().contains("essentialcraft5:scroll_volume"), "第二个池应是源质残卷，实际 " + scroll);
            JsonObject survey = table.getAsJsonArray("pools").get(2).getAsJsonObject();
            helper.assertTrue("torn_survey_map".equals(survey.get("name").getAsString())
                    && survey.toString().contains("essentialcraft5:torn_survey_map"), "第三个池应是残破勘测图，实际 " + survey);
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonObject rolls = pool.getAsJsonObject("rolls");
        helper.assertTrue(rolls.get("min").getAsInt() == 6 && rolls.get("max").getAsInt() == 17, "每箱抽 6~17 次，实际 " + rolls);
        JsonArray entries = pool.getAsJsonArray("entries");
        helper.assertTrue(entries.size() == 24, "应有 24 条，实际 " + entries.size());
        Map<Item, int[]> ranges = new HashMap<>();
        int total = 0;
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            ResourceLocation id = ResourceLocation.parse(entry.get("name").getAsString());
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id), "表里的 " + id + " 应已注册（未知物品会让整张表加载失败）");
            total += entry.get("weight").getAsInt();
            int[] range = {1, 1};
            if (entry.has("functions")) {
                for (JsonElement function : entry.getAsJsonArray("functions")) {
                    JsonObject f = function.getAsJsonObject();
                    if (f.get("function").getAsString().equals("minecraft:set_count")) {
                        JsonObject count = f.getAsJsonObject("count");
                        range = new int[] {count.get("min").getAsInt(), count.get("max").getAsInt()};
                    }
                }
            }
            ranges.put(ForgeRegistries.ITEMS.getValue(id), range);
            if (id.equals(ECVoid.MYSTICAL_ACCESSORY.getId())) {
                helper.assertTrue(entry.toString().contains("essentialcraft5:randomize_accessory") && entry.toString().contains("essentialcraft5:not_disabled"),
                        "神秘饰品应带 randomize_accessory 与 not_disabled，实际 " + entry);
            }
        }
        helper.assertTrue(total == 343, "总权重应是 343，实际 " + total);

        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)))
                .create(LootContextParamSets.CHEST);
        int accessories = 0;
        int voidCores = 0;
        for (ResourceLocation id : List.of(HoannaLoot.TOWN_TOWER, HoannaLoot.CATACOMBS)) {
            LootTable table = level.getServer().getLootData().getLootTable(id);
            helper.assertTrue(table != LootTable.EMPTY, id + " 应能加载");
            for (int i = 0; i < 200; i++) {
                for (ItemStack stack : table.getRandomItems(params, (i + 1) * SEED_MIX)) {
                    if (stack.is(ECScrolls.ANCIENT_SCROLL.get()) || stack.is(ECHoannaSurvey.TORN_SURVEY_MAP.get())) {
                        continue;
                    }
                    int[] range = ranges.get(stack.getItem());
                    helper.assertTrue(range != null, id + " 开出了表外的 " + ForgeRegistries.ITEMS.getKey(stack.getItem()));
                    helper.assertTrue(stack.getCount() >= range[0] && stack.getCount() <= range[1],
                            ForgeRegistries.ITEMS.getKey(stack.getItem()) + " 一堆应在 " + range[0] + "~" + range[1] + "，实际 " + stack.getCount());
                    if (stack.is(ECVoid.VOID_CORE.get())) {
                        voidCores++;
                    }
                    if (stack.getItem() instanceof MysticalAccessoryItem) {
                        accessories++;
                        checkAccessory(helper, stack);
                    }
                }
            }
        }
        helper.assertTrue(accessories > 100 && accessories < 300, "400 箱应开出约 200 件神秘饰品（每箱约 0.5），实际 " + accessories);
        helper.assertTrue(voidCores > 40 && voidCores < 140, "400 箱应开出约 80 个虚空核心（每箱约 0.2），实际 " + voidCores);
        helper.succeed();
    }

    private static void checkAccessory(GameTestHelper helper, ItemStack stack) {
        helper.assertTrue(MysticalAccessoryItem.isIdentified(stack), "箱子里开出来的神秘饰品应已定形态");
        int type = MysticalAccessoryItem.type(stack);
        int bottom = stack.getTag().getInt(MysticalAccessoryItem.TAG_BOTTOM);
        int top = stack.getTag().getInt(MysticalAccessoryItem.TAG_TOP);
        helper.assertTrue(type >= 0 && type < 3 && bottom >= 0 && bottom < 6 && top >= 0 && top < 6,
                "形态 / 外观越界：" + type + " / " + bottom + " / " + top);
        for (MRUResistance.Type kind : MRUResistance.Type.values()) {
            float value = stack.getTag().getFloat(MysticalAccessoryItem.key(kind));
            helper.assertTrue(value >= 0F && value < 0.1F, kind + " 抗性应在 [0, 0.1)，实际 " + value);
        }
    }

    // ------------------------------------------------------------------ 神秘饰品

    /**
     * 形态 2（戒指）只能戴进戒指槽；没定形态的哪都戴不上。只戴它时海纳尔辐射易伤 = 1 − car（MRU 腐化同理）；
     * 没定形态的放进背包 inventoryTick 一次就定；被 general.baubles.disabled 禁用时抗性全算 0。
     */
    @GameTest(template = EMPTY)
    public static void mysticalAccessoryCurios(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        MysticalAccessoryItem item = ECVoid.MYSTICAL_ACCESSORY.get();
        ItemStack ring = new ItemStack(item);
        ring.getOrCreateTag().putInt(MysticalAccessoryItem.TAG_TYPE, 2);
        ring.getTag().putInt(MysticalAccessoryItem.TAG_BOTTOM, 3);
        ring.getTag().putInt(MysticalAccessoryItem.TAG_TOP, 4);
        ring.getTag().putFloat(MysticalAccessoryItem.TAG_MRU_CORRUPTION, 0.04F);
        ring.getTag().putFloat(MysticalAccessoryItem.TAG_CORRUPTION_RADIATION, 0.02F);
        ring.getTag().putFloat(MysticalAccessoryItem.TAG_HOANNA_RADIATION, 0.075F);

        helper.assertTrue(item.canEquip(new SlotContext(CuriosCompat.RING, player, 0, false, true), ring), "戒指形态应能戴进戒指槽");
        helper.assertFalse(item.canEquip(new SlotContext(CuriosCompat.NECKLACE, player, 0, false, true), ring), "戒指形态不应戴进项链槽");
        helper.assertFalse(item.canEquip(new SlotContext(CuriosCompat.BELT, player, 0, false, true), ring), "戒指形态不应戴进腰带槽");
        ItemStack blank = new ItemStack(item);
        for (String slot : new String[] {CuriosCompat.RING, CuriosCompat.NECKLACE, CuriosCompat.BELT}) {
            helper.assertFalse(item.canEquip(new SlotContext(slot, player, 0, false, true), blank), "没定形态的不应戴进 " + slot);
        }
        // 审查 M7-6：再走一遍 Curios 自己的放入校验（界面里放、漏斗、别的模组往槽里塞都走它）：
        // DynamicStackHandler.isItemValid = CurioEquipEvent → CuriosApi.isStackValid（curios:<槽> 标签与槽位验证器）→ canEquip。
        // 标签漏了（例：ItemSpec 少了 CURIOS_BELT）或验证器被改，上面只调 canEquip 的断言照样过，这里会挂
        ICuriosItemHandler curios = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (curios == null) {
            helper.fail("玩家没有 Curios 饰品栏");
            return;
        }
        String[] slotByType = {CuriosCompat.NECKLACE, CuriosCompat.BELT, CuriosCompat.RING};
        for (String slot : slotByType) {
            ICurioStacksHandler handler = curios.getCurios().get(slot);
            if (handler == null || handler.getSlots() < 1) {
                helper.fail("玩家没有 Curios 槽位 " + slot);
                return;
            }
            for (int type = 0; type < MysticalAccessoryItem.TYPES; type++) {
                ItemStack formed = new ItemStack(item);
                formed.getOrCreateTag().putInt(MysticalAccessoryItem.TAG_TYPE, type);
                boolean expected = slot.equals(slotByType[type]);
                helper.assertTrue(handler.getStacks().isItemValid(0, formed) == expected,
                        "形态 " + type + "（" + MysticalAccessoryItem.KINDS[type] + "）放进 " + slot + " 槽应" + (expected ? "被放行" : "被拒"));
                // 右键戴上（CuriosEventHandler）= isItemValid && canEquipFromUse
                boolean fromUse = handler.getStacks().isItemValid(0, formed)
                        && item.canEquipFromUse(new SlotContext(slot, player, 0, false, true), formed);
                helper.assertTrue(fromUse == expected, "形态 " + type + " 右键戴进 " + slot + " 槽应" + (expected ? "被放行" : "被拒"));
            }
            helper.assertFalse(handler.getStacks().isItemValid(0, new ItemStack(item)), "没定形态的不应放进 " + slot + " 槽");
        }

        helper.assertTrue(MysticalAccessoryItem.modelIndex(ring) == 2 * 36 + 3 * 6 + 4, "模型属性应是 形态 × 36 + 底 × 6 + 面");
        helper.assertTrue(MysticalAccessoryItem.modelIndex(2, 5, 5) == 107F && MysticalAccessoryItem.modelIndex(blank) == 0F, "模型属性范围 0~107");

        wear(helper, player, CuriosCompat.RING, ring);
        near(helper, MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION), 1F - 0.075F, "只戴它时的海纳尔辐射易伤");
        near(helper, MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION), 1F - 0.04F, "只戴它时的 MRU 腐化易伤");
        try {
            CuriosCompat.Config.setDisabledForTest(List.of(ECVoid.MYSTICAL_ACCESSORY.getId().toString()));
            near(helper, MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION), 1F, "被禁用后的海纳尔辐射易伤");
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
        }

        // 指令改出来的越界数值读出来夹到 0.1
        ItemStack hacked = ring.copy();
        hacked.getTag().putFloat(MysticalAccessoryItem.TAG_HOANNA_RADIATION, 5F);
        near(helper, item.getResistance(hacked, MRUResistance.Type.HOANNA_RADIATION), MysticalAccessoryItem.MAX_RESISTANCE, "越界的抗性读数");

        helper.assertFalse(MysticalAccessoryItem.isIdentified(blank), "新物品没有形态");
        blank.inventoryTick(helper.getLevel(), player, 0, false);
        helper.assertTrue(MysticalAccessoryItem.isIdentified(blank), "放进背包 inventoryTick 一次就应定形态");
        checkAccessory(helper, blank);
        helper.succeed();
    }

    /** 定好形态、三项抗性都是 each 的神秘饰品 */
    private static ItemStack accessory(int type, float each) {
        ItemStack stack = new ItemStack(ECVoid.MYSTICAL_ACCESSORY.get());
        stack.getOrCreateTag().putInt(MysticalAccessoryItem.TAG_TYPE, type);
        for (MRUResistance.Type resistance : MRUResistance.Type.values()) {
            stack.getTag().putFloat(MysticalAccessoryItem.key(resistance), each);
        }
        return stack;
    }

    /**
     * 神秘饰品每项抗性合计最多 {@link MysticalAccessoryItem#MAX_TOTAL}（原作能戴神秘饰品的 4 格 × 0.1：只抽项链、腰带、戒指三种形态，Baubles 里对应 1 + 1 + 2 格）：戒指槽用属性修饰加到 6 格（别的模组、数据包加槽位走的也是它），
     * 戴 6 件各 0.09 的，海纳尔辐射 / MRU 腐化易伤都是 0.6（不设上限是 0.46）；摘到 4 件（0.36）没到上限照加；
     * 别的饰品不算进这个上限（拿魔力防护头盔塞进项链槽，代表别的模组实现 IMRUResistanceItem 的饰品：0.6 − 0.10 = 0.5）。
     */
    @GameTest(template = EMPTY)
    public static void mysticalAccessoryTotalCap(GameTestHelper helper) {
        near(helper, MysticalAccessoryItem.MAX_TOTAL, 4 * MysticalAccessoryItem.MAX_RESISTANCE, "合计上限（原作能戴神秘饰品的 4 格）");
        near(helper, MRUResistance.vulnerability(0F, 0F, 0.54F), 0.6F, "公式：神秘饰品合计 0.54 夹到 0.4");
        near(helper, MRUResistance.vulnerability(0F, 0.1F, 0.54F), 0.5F, "公式：别的饰品 0.1 不进上限");
        near(helper, MRUResistance.vulnerability(1F, 0F, 0.3F), 0.45F, "公式：没到上限照加");

        Player player = helper.makeMockPlayer();
        ICuriosItemHandler curios = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        ICurioStacksHandler rings = curios == null ? null : curios.getCurios().get(CuriosCompat.RING);
        if (rings == null) {
            helper.fail("玩家没有 Curios 戒指槽");
            return;
        }
        int extra = Math.max(0, 6 - rings.getSlots());
        rings.addTransientModifier(new AttributeModifier(UUID.fromString("6c1f3a52-0e5b-4f0e-9d1c-2b7a9e4f5a61"), "ec5 test extra rings",
                extra, AttributeModifier.Operation.ADDITION));
        if (rings.getSlots() < 6) {
            helper.fail("戒指槽没加到 6 格，实际 " + rings.getSlots());
            return;
        }
        for (int i = 0; i < 6; i++) {
            rings.getStacks().setStackInSlot(i, accessory(2, 0.09F));
        }
        CuriosCompat.invalidate(player);
        near(helper, MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION), 0.6F, "戴 6 件各 0.09 时的海纳尔辐射易伤");
        near(helper, MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION), 0.6F, "戴 6 件各 0.09 时的 MRU 腐化易伤");

        rings.getStacks().setStackInSlot(4, ItemStack.EMPTY);
        rings.getStacks().setStackInSlot(5, ItemStack.EMPTY);
        CuriosCompat.invalidate(player);
        near(helper, MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION), 1F - 4 * 0.09F, "戴 4 件（0.36，没到上限）时的海纳尔辐射易伤");

        rings.getStacks().setStackInSlot(4, accessory(2, 0.09F));
        rings.getStacks().setStackInSlot(5, accessory(2, 0.09F));
        wear(helper, player, CuriosCompat.NECKLACE, new ItemStack(ECGear.MHS_HELMET.get()));
        near(helper, MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION), 0.5F, "6 件神秘饰品 + 别的饰品 0.10 时的海纳尔辐射易伤");
        near(helper, MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION), 0.35F, "6 件神秘饰品 + 别的饰品 0.25 时的 MRU 腐化易伤");
        helper.succeed();
    }

    /** 余烬 8 种、一组 16、都在 essentialcraft5:embers 里、不进任何 forge 标签；神秘饰品进三种 Curios 槽位标签、不进特殊饰品标签 */
    @GameTest(template = EMPTY)
    public static void embersAndAccessoryTags(GameTestHelper helper) {
        List<RegistryObject<Item>> embers = ECVoid.embers();
        helper.assertTrue(embers.size() == 8, "应有 8 种余烬");
        for (RegistryObject<Item> ember : embers) {
            ItemStack stack = stack(ember);
            helper.assertTrue(stack.getMaxStackSize() == ECVoid.EMBER_STACK && ECVoid.EMBER_STACK == 16, ember.getId() + " 一组应是 16（原作）");
            helper.assertTrue(stack.is(ECVoid.EMBERS), ember.getId() + " 应在 essentialcraft5:embers 里（要先跑 runData）");
            helper.assertTrue(stack.getTags().noneMatch(tag -> tag.location().getNamespace().equals("forge")), ember.getId() + " 不应进 forge 标签");
        }
        ItemStack accessory = stack(ECVoid.MYSTICAL_ACCESSORY);
        for (TagKey<Item> tag : List.of(ECTags.CURIOS_RING, ECTags.CURIOS_NECKLACE, ECTags.CURIOS_BELT)) {
            helper.assertTrue(accessory.is(tag), "神秘饰品应在 " + tag.location() + " 里");
        }
        helper.assertFalse(accessory.is(ECBaubles.SPECIAL_BAUBLES), "神秘饰品不是 32 件特殊饰品之一");
        helper.assertTrue(accessory.getMaxStackSize() == 1, "神秘饰品不叠加");
        helper.succeed();
    }

    // ------------------------------------------------------------------ ε 腰带

    /** 找一个第一次 nextFloat 落在 [lo, hi) 的打散种子 */
    private static long seedWithFirstFloat(float lo, float hi) {
        for (long i = 1; i < 100_000; i++) {
            long seed = i * SEED_MIX;
            float f = RandomSource.create(seed).nextFloat();
            if (f >= lo && f < hi) {
                return seed;
            }
        }
        throw new IllegalStateException("找不到合适的种子");
    }

    private static Zombie zombie(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) {
            throw new IllegalStateException("建不出僵尸");
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        zombie.moveTo(at.x, at.y, at.z, 0F, 0F);
        return zombie;
    }

    /**
     * 真玩家戴 ε、亲手打死：掷中（< 5%）掉一个 embers 里的余烬，没掷中不掉；假玩家、没戴、doMobLoot 关掉、被禁用都不掉。
     * 再走一遍真的掉落事件（400 次，平均 20 次掉；只断言在 5~40 之间）：余烬确实加进了掉落列表。
     */
    @GameTest(template = EMPTY)
    public static void epsilonEmberDrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Zombie victim = zombie(helper);
        DamageSource byPlayer = level.damageSources().playerAttack(player);
        long hit = seedWithFirstFloat(0F, 0.04F);
        long miss = seedWithFirstFloat(0.2F, 1F);

        helper.assertTrue(ECVoid.VoidEmberEvents.rollEmber(victim, byPlayer, RandomSource.create(hit)).isEmpty(), "没戴 ε 不应掉余烬");
        wear(helper, player, CuriosCompat.BELT, new ItemStack(ECBaubles.EPSILON_BELT.get()));
        ItemStack ember = ECVoid.VoidEmberEvents.rollEmber(victim, byPlayer, RandomSource.create(hit));
        helper.assertTrue(!ember.isEmpty() && ember.is(ECVoid.EMBERS) && ember.getCount() == 1, "戴着 ε、掷中时应掉 1 个余烬，实际 " + ember);
        helper.assertTrue(ECVoid.VoidEmberEvents.rollEmber(victim, byPlayer, RandomSource.create(miss)).isEmpty(), "没掷中不应掉");

        // 假玩家（机器、自动化）：第一道就拦下（不给共用的假玩家戴饰品，免得影响别的测试）
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        helper.assertTrue(ECVoid.VoidEmberEvents.rollEmber(victim, level.damageSources().playerAttack(fake), RandomSource.create(hit)).isEmpty(),
                "假玩家击杀不应掉余烬");
        helper.assertTrue(ECVoid.VoidEmberEvents.rollEmber(victim, level.damageSources().generic(), RandomSource.create(hit)).isEmpty(),
                "没有来源的死亡不应掉余烬");

        GameRules.BooleanValue mobLoot = level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean before = mobLoot.get();
        try {
            mobLoot.set(false, level.getServer());
            helper.assertTrue(ECVoid.VoidEmberEvents.rollEmber(victim, byPlayer, RandomSource.create(hit)).isEmpty(), "doMobLoot 关掉时不应掉余烬");
        } finally {
            mobLoot.set(before, level.getServer());
        }
        try {
            CuriosCompat.Config.setDisabledForTest(List.of(ECBaubles.EPSILON_BELT.getId().toString()));
            helper.assertTrue(ECVoid.VoidEmberEvents.rollEmber(victim, byPlayer, RandomSource.create(hit)).isEmpty(), "ε 被禁用时不应掉余烬");
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
        }

        int dropped = 0;
        for (int i = 0; i < 400; i++) {
            Zombie target = zombie(helper);
            List<ItemEntity> drops = new ArrayList<>();
            MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(target, byPlayer, drops, 0, true));
            for (ItemEntity drop : drops) {
                helper.assertTrue(drop.getItem().is(ECVoid.EMBERS), "掉落事件里只该多出余烬，实际 " + drop.getItem());
                dropped++;
            }
        }
        helper.assertTrue(dropped >= 5 && dropped <= 40, "400 次击杀应掉约 20 个余烬（5%），实际 " + dropped);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 粒子预算

    /** 每次至多 12 / 10 个（原作 48 / 50）；同一格 20 tick 内只冒一次，别的格不受影响，过了冷却再冒；表长到上限会清理 */
    @GameTest(template = EMPTY)
    public static void decorParticleBudget(GameTestHelper helper) {
        helper.assertTrue(LevitatorBlock.MAX_PARTICLES == 12 && SpreaderBlock.MAX_PARTICLES == 10 && LevitatorBlock.MAX_CAMERA_DISTANCE == 24,
                "预算应是 12 / 10 个、24 格");
        ParticleCooldown cooldown = new ParticleCooldown();
        long a = BlockPos.asLong(1, 2, 3);
        long b = BlockPos.asLong(4, 5, 6);
        helper.assertTrue(cooldown.tryFire(a, 100L), "第一次应能冒");
        helper.assertFalse(cooldown.tryFire(a, 119L), "19 tick 后还在冷却");
        helper.assertTrue(cooldown.tryFire(b, 119L), "别的格不受影响");
        helper.assertTrue(cooldown.tryFire(a, 120L), "20 tick 后应能再冒");
        helper.assertTrue(cooldown.tryFire(a, 50L), "游戏时间倒退（换了存档）当作过了冷却");
        for (int i = 0; i < ParticleCooldown.PRUNE_SIZE * 3; i++) {
            cooldown.tryFire(BlockPos.asLong(i, 64, 0), 1000L + i);
        }
        helper.assertTrue(cooldown.size() <= ParticleCooldown.PRUNE_SIZE + ParticleCooldown.COOLDOWN_TICKS,
                "冷却表应被清理，实际 " + cooldown.size());
        helper.assertFalse(ECVoid.LEVITATOR.get().defaultBlockState().isRandomlyTicking(), "悬浮器不开随机刻");
        helper.succeed();
    }

    private static void checkSplit(GameTestHelper helper, int open, int column, int ring, String what) {
        int actualColumn = LevitatorBlock.columnParticles(open);
        int actualRing = LevitatorBlock.ringParticles(open);
        helper.assertTrue(actualColumn == column && actualRing == ring,
                what + "：竖线 / 腰圈应是 " + column + " / " + ring + "，实际 " + actualColumn + " / " + actualRing);
    }

    /**
     * 悬浮器粒子往哪冒（{@link LevitatorBlock#openFaces}，客户端 VoidClient 照它摆粒子）：五面都露着时竖线 8 + 腰圈 4；顶上压着强化石（城镇里墙角正下方）
     * 没有竖线、12 颗全给腰圈；顶上是玻璃不算挡；四周全被挡、只露顶时全给竖线；五面全被挡（埋着）一颗都不冒；只露一面时腰圈只在那一面。
     * 粉尘放大到 1.2~1.5（原来 0.7 / 0.8 几格外就看不见）。
     */
    @GameTest(template = EMPTY)
    public static void levitatorParticleFaces(GameTestHelper helper) {
        helper.assertTrue(LevitatorBlock.DUST_SCALE >= 1.2F && LevitatorBlock.DUST_SCALE <= 1.5F
                && SpreaderBlock.DUST_SCALE >= 1.2F && SpreaderBlock.DUST_SCALE <= 1.5F, "悬浮器 / 散射器的粉尘大小应在 1.2~1.5");
        helper.assertTrue(LevitatorBlock.columnParticles(LevitatorBlock.FACE_UP | LevitatorBlock.FACE_SIDES)
                + LevitatorBlock.ringParticles(LevitatorBlock.FACE_UP | LevitatorBlock.FACE_SIDES) == LevitatorBlock.MAX_PARTICLES, "总数仍是 12");
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, ECVoid.LEVITATOR.get());
        int all = LevitatorBlock.FACE_UP | LevitatorBlock.FACE_SIDES;

        int open = LevitatorBlock.openFaces(level, abs);
        helper.assertTrue(open == all, "五面都露着，实际 " + Integer.toBinaryString(open));
        checkSplit(helper, open, LevitatorBlock.COLUMN_PARTICLES, LevitatorBlock.MAX_PARTICLES - LevitatorBlock.COLUMN_PARTICLES, "五面都露着");

        helper.setBlock(rel.above(), ECMaterials.FORTIFIED_STONE.get());
        open = LevitatorBlock.openFaces(level, abs);
        helper.assertTrue(open == LevitatorBlock.FACE_SIDES, "顶上压着强化石应只剩四周，实际 " + Integer.toBinaryString(open));
        checkSplit(helper, open, 0, LevitatorBlock.MAX_PARTICLES, "顶上压着强化石");

        helper.setBlock(rel.above(), Blocks.GLASS);
        helper.assertTrue(LevitatorBlock.openFaces(level, abs) == all, "顶上是玻璃不算挡");

        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(rel.relative(side), Blocks.STONE);
        }
        open = LevitatorBlock.openFaces(level, abs);
        helper.assertTrue(open == LevitatorBlock.FACE_UP, "四周全被挡应只剩顶上，实际 " + Integer.toBinaryString(open));
        checkSplit(helper, open, LevitatorBlock.MAX_PARTICLES, 0, "只露顶上");

        helper.setBlock(rel.above(), Blocks.STONE);
        open = LevitatorBlock.openFaces(level, abs);
        helper.assertTrue(open == 0, "埋着应五面全被挡，实际 " + Integer.toBinaryString(open));
        checkSplit(helper, open, 0, 0, "埋着");

        helper.setBlock(rel.north(), Blocks.AIR);
        open = LevitatorBlock.openFaces(level, abs);
        helper.assertTrue(open == LevitatorBlock.faceBit(Direction.NORTH), "只露北面，实际 " + Integer.toBinaryString(open));
        checkSplit(helper, open, 0, LevitatorBlock.MAX_PARTICLES, "只露北面");
        helper.succeed();
    }

    /** TransientCraftingContainer 要一个菜单通知格子变化，测试里用个空的 */
    private static final class DummyMenu extends AbstractContainerMenu {
        DummyMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
