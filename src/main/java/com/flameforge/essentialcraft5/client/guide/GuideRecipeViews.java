package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.recipe.AttunementTransferRecipe;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 研究手册配方页的扩展点：模块自己的配方类型登记一个「怎么画」（配方 → {@link RecipeView}），
 * 配方页（按产物查、按类型列）、Ctrl 查阅、「点物品跳转」就都认这个类型了，不用改手册的代码。
 * <p>
 * 在模块的客户端类里、{@code FMLClientSetupEvent} 中登记（这时配方类型已经注册好，{@code RegistryObject#get} 取得到）：
 * <pre>{@code
 * @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
 * public final class MithrilineClient {
 *     @SubscribeEvent
 *     public static void setup(FMLClientSetupEvent event) {
 *         GuideRecipeViews.register(ECMithriline.MITHRILINE_FURNACE_TYPE.get(), (MithrilineFurnaceRecipe recipe) -> RecipeView.builder()
 *                 .station(ECMithriline.MITHRILINE_FURNACE.get())
 *                 .input(recipe.getInput(), recipe.getCount())
 *                 .output(recipe.getResultItem(GuideRecipeViews.registryAccess()))
 *                 .note(Component.translatable("gui.essentialcraft5.espe", Figures.number(recipe.getEspe())))
 *                 .build());
 *     }
 * }
 * }</pre>
 * 书里写 {@code {"type": "recipe", "item": "essentialcraft5:mithriline_ingot"}} 就会画出米斯莱熔炉的配方
 * （排在工作台、魔力工作台、辐射仓、熔炉之后）；整张转化表写 {@code {"type": "recipe", "recipe_type": "essentialcraft5:mithriline_furnace", "compact": true}}。
 * <p>
 * 按产物查配方靠配方自己的 {@code getResultItem}，配方类要照常实现它（RecipeView 里的产物只管怎么画）。
 * 登记的函数抛异常时这条配方不画，日志里记一次「[指南]」。同一个类型登记两次，后登记的覆盖前面的（也打一行警告）。
 * <p>
 * 同调升阶、同调转移（第十三批，batch13.md §2.7）和普通魔力工作台配方同属一个配方类型，不走上面的登记，
 * 由魔力工作台的配方图（{@code Figures.Magician}）和 JEI 的魔力工作台分类按 {@link #magicianExtras} 画：
 * 中心格画「n 阶 · 满」的同调装备、四角带数量、产物随中心那件轮换，下面多一行矩阵阶要求。书里写
 * {@code {"type": "recipe", "recipe": "essentialcraft5:attunement/transfer_armor"}} 按 id 画一条。
 * 同一件升一阶的配方、转移配方没有固定产物，不出现在「按产物查」里（物品页不会多出五张升阶图）；升格配方（裁决之刃）有产物，物品页查得到。
 */
public final class GuideRecipeViews {

    private record Registration(int order, Function<Recipe<?>, RecipeView> view) {
    }

    private static final Map<RecipeType<?>, Registration> VIEWS = new ConcurrentHashMap<>();
    private static final AtomicInteger ORDER = new AtomicInteger();
    /** 出过错的配方只记一次日志（排版在窗口大小变化时会重做） */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private GuideRecipeViews() {
    }

    /**
     * 登记一个配方类型的画法。
     *
     * @param view 配方 → RecipeView；传进来的一定是这个类型的配方（按 getType 分派），可以直接用具体的配方类当参数类型
     */
    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> void register(RecipeType<T> type, Function<? super T, RecipeView> view) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(view);
        Function<Recipe<?>, RecipeView> erased = recipe -> ((Function<Recipe<?>, RecipeView>) (Function<?, ?>) view).apply(recipe);
        Registration old = VIEWS.put(type, new Registration(ORDER.getAndIncrement(), erased));
        if (old != null) {
            EssentialCraft5.LOGGER.warn("[指南] 配方类型 {} 的画法登记了两次，用后登记的", type);
        }
    }

    public static boolean has(RecipeType<?> type) {
        return VIEWS.containsKey(type);
    }

    /** 登记顺序（同一物品有多个登记类型的配方时按它排），没登记返回 Integer.MAX_VALUE */
    static int order(RecipeType<?> type) {
        Registration registration = VIEWS.get(type);
        return registration == null ? Integer.MAX_VALUE : registration.order();
    }

    /** 这条配方的画法；没登记或登记的函数出错时返回 null（JEI 接入也用它，两边的说明文字一致） */
    @Nullable
    public static RecipeView view(Recipe<?> recipe) {
        Registration registration = VIEWS.get(recipe.getType());
        if (registration == null) {
            return null;
        }
        try {
            return registration.view().apply(recipe);
        } catch (RuntimeException e) {
            if (REPORTED.add(String.valueOf(recipe.getId()))) {
                EssentialCraft5.LOGGER.warn("[指南] 配方 {}（类型 {}）的画法出错，跳过：{}", recipe.getId(), recipe.getType(), e.toString());
            }
            return null;
        }
    }

    /**
     * 魔力工作台配方图要多画的东西（同调升阶 / 转移）。
     *
     * @param inputs  五格的候选（中心、左上、右上、左下、右下），数量照配方要的写好
     * @param results 产物候选，和中心格的候选一一对应（同一个下标一起轮换）
     * @param badge   中心格旁边的小字（「2 阶 · 满」「0 阶」），没有为 null
     * @param note    图下面多的一行（矩阵阶要求、转移规则），没有为 null
     */
    public record MagicianExtras(ItemStack[][] inputs, ItemStack[] results, @Nullable Component badge, @Nullable Component note) {
    }

    /** 同调升阶 / 转移配方的画法；普通魔力工作台配方返回 null（照原样画） */
    @Nullable
    public static MagicianExtras magicianExtras(MagicianTableRecipe recipe) {
        if (recipe instanceof AttunementAscensionRecipe ascension) {
            ItemStack[][] inputs = new ItemStack[MagicianTableRecipe.INPUTS][];
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = ascension.displayInputs(i);
            }
            ItemStack[] results = new ItemStack[inputs[0].length];
            for (int i = 0; i < results.length; i++) {
                results[i] = ascension.displayResult(inputs[0][i]);
            }
            return new MagicianExtras(inputs, results,
                    Component.translatable("guide.essentialcraft5.recipe.attunement.badge_full", ascension.fromStage()), ascension.rankNote());
        }
        if (recipe instanceof AttunementTransferRecipe transfer) {
            ItemStack[][] inputs = new ItemStack[MagicianTableRecipe.INPUTS][];
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = transfer.displayInputs(i);
            }
            List<ItemStack[]> pairs = transfer.displayPairs();
            ItemStack[] results = new ItemStack[pairs.size()];
            for (int i = 0; i < results.length; i++) {
                results[i] = transfer.displayResult(pairs.get(i)[0], pairs.get(i)[1]);
            }
            return new MagicianExtras(inputs, results, Component.translatable("guide.essentialcraft5.recipe.attunement.badge_new"),
                    Component.translatable("guide.essentialcraft5.recipe.attunement.transfer"));
        }
        return null;
    }

    /** 当前世界的 RegistryAccess（取配方产物 getResultItem 要用）；没进世界时是 EMPTY */
    public static RegistryAccess registryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? RegistryAccess.EMPTY : minecraft.level.registryAccess();
    }
}
