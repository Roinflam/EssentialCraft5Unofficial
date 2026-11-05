package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 岩浆法杖（原作 ItemMagmaticWand）：右键矿石方块，矿石直接变成岩浆合金掉在原地，每次 {@value #COST} MRU。
 * 掉落数 = 矿石加工配方（{@code essentialcraft5:ore_smelting}）的 drop_amount，{@value #DOUBLE_CHANCE} 的几率翻倍；不给方块经验（原作也不给）。
 * <p>
 * 写法照 policy §2.2「会产生掉落的破坏」：逻辑在 {@link #onItemUseFirst}，{@code useOn} 不覆盖（返回 PASS、什么都不改），
 * 这样挖方块不在 ForgeHooks 的放置捕获里——捕获只撤回方块、不收回掉落，写在 useOn 里被拒会刷出合金（Forge 只在 ItemStack#useOn 外面套捕获，
 * onItemUseFirst 不在里面；breakGuarded 自己也会暂停捕获）。
 * 不写在 {@code use} 里：use 要等方块自己的右键返回 PASS 才轮得到，红石矿（含深层）对手里不是方块物品的玩家一律返回 SUCCESS（顺带点亮），
 * 不潜行就永远炼不了。onItemUseFirst 在 RightClickBlock 事件之后、方块自己的右键之前调用（两端都是），潜行与否都走这里；
 * 已经亮着的红石矿也认（查配方看的是方块的物品，不看 LIT）。
 * 顺序：查配方 → 冒险模式 → 够不够 {@value #COST} MRU → {@link ECProtection#breakGuarded}（出生点保护、世界边界、以玩家真身发 BreakEvent，放行才删方块）→ 扣 MRU → 掉合金。
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>原作先扣 500 MRU 再发破坏事件，在别人领地里点一下白扣；现在放行才扣。MRU 不够、没有权限时在动作栏提示一句（原作什么都不说）。</li>
 *   <li>原作按方块的第一个矿辞查矿表；这里看方块的物品在不在某条配方的 ore（矿石）里，只认矿石，不认粗矿块之类；配方表按优先级取第一条（同冶炼炉）。</li>
 *   <li>挖掉时有破坏粒子和声音，含水的格子留下水（原作直接 setBlockToAir）。硬度小于 0 的方块（基岩等）不挖。</li>
 *   <li>假玩家（机械手之类）用不了：破坏要以真玩家身份问领地插件（policy §2.2）。</li>
 *   <li>红石矿：原作写在 onItemUse 里，不潜行时 1.12 的红石矿右键先把自己换成亮着的红石矿（没有物品形式、查不到矿表），
 *       只有潜行点没亮的才炼得了；现在不用潜行，亮着的也能炼（原作 bug 顺便修）。</li>
 * </ul>
 */
public class MagmaticStaffItem extends MRUStaffItem {

    /** 每次的花费（原作 playerUseMRU(…, 500)） */
    public static final int COST = 500;
    /** 掉落数翻倍的几率（原作 rand.nextFloat() &lt;= 0.33F） */
    public static final float DOUBLE_CHANCE = 0.33F;

    public MagmaticStaffItem(Properties properties) {
        super(properties);
    }

    /**
     * 右键方块（两端，在方块自己的右键之前）：点中的不是矿石返回 PASS，照常交给方块和别的逻辑；
     * 客户端只决定挥不挥手（够 MRU 返回 SUCCESS，不够 PASS）；服务端炼成返回 CONSUME，没炼成（MRU 不够、没权限、冒险模式）返回 FAIL，
     * 这一下到此为止，不会再去点亮红石矿或调 use。
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        OreSmeltingRecipe recipe = findOre(level.getRecipeManager(), state);
        if (recipe == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            // 客户端只决定挥不挥手；真正的判断和扣费在服务端
            return canAfford(player, stack, COST) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return smelt(level, player, stack, pos, state, recipe) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /**
     * 服务端：把 pos 上的矿石炼成岩浆合金。返回是否炼成（GameTest 也直接调它）。
     */
    public static boolean smelt(Level level, Player player, ItemStack stack, BlockPos pos, BlockState state, OreSmeltingRecipe recipe) {
        if (player instanceof ServerPlayer serverPlayer
                && player.blockActionRestricted(level, pos, serverPlayer.gameMode.getGameModeForPlayer())) {
            return false;   // 冒险模式、旁观模式
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        if (!canAfford(player, stack, COST)) {
            tellNoMRU(player, COST);
            return false;
        }
        if (!ECProtection.breakGuarded(player, level, pos, ItemStack.EMPTY, false)) {
            // 不是真玩家、出生点保护、领地插件拒绝：方块没动、不扣 MRU
            tellNoPermission(player);
            return false;
        }
        if (!MRUDrain.playerUseMRU(player, stack, COST)) {
            return false;   // 同一 tick 里刚确认过够，走不到这里；真走到了就不给合金
        }
        int count = Math.max(1, recipe.getDropAmount());
        if (level.random.nextFloat() <= DOUBLE_CHANCE) {
            count *= 2;
        }
        ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                MagmaticAlloyItem.create(recipe.getId(), count),
                (level.random.nextDouble() - 0.5D) * 0.2D, level.random.nextDouble() * 0.1D, (level.random.nextDouble() - 0.5D) * 0.2D);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        return true;
    }

    /**
     * 这个方块对应的矿石加工配方（只认配方的 ore，不认 raw），没有返回 null。配方类型按 id {@code essentialcraft5:ore_smelting} 取，
     * 同一个方块对上几条时按 {@link OreSmeltingRecipe#ORDER}（优先级高的在前）。
     */
    @Nullable
    public static OreSmeltingRecipe findOre(RecipeManager recipes, BlockState state) {
        ItemStack block = new ItemStack(state.getBlock());
        if (block.isEmpty()) {
            return null;
        }
        RecipeType<?> type = ForgeRegistries.RECIPE_TYPES.getValue(EssentialCraft5.id("ore_smelting"));
        if (type == null) {
            return null;
        }
        OreSmeltingRecipe best = null;
        for (Recipe<?> recipe : recipesOf(recipes, type)) {
            if (recipe instanceof OreSmeltingRecipe ore && ore.isOre(block) && (best == null || OreSmeltingRecipe.ORDER.compare(ore, best) < 0)) {
                best = ore;
            }
        }
        return best;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Recipe<?>> recipesOf(RecipeManager recipes, RecipeType<?> type) {
        return (List) recipes.getAllRecipesFor((RecipeType) type);
    }

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        tooltip.add(usage("tooltip.essentialcraft5.magmatic_staff.use", COST));
    }
}
