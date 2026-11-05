package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 进服收到配方后的自检（从旧手册的 GuideValidator.checkRecipes 搬过来）：配方块的产物有没有找到任何配方、
 * 按配方类型列的块有没有能画的配方（写了 optional 的不查）。结果打到日志，前缀「[指南]」。
 * <p>
 * 找不到配方的块在界面上整块隐藏（不画一个做不出来的东西），所以只能靠这里的日志发现。
 * 内容本身的格式问题由离线脚本 {@code tools/guide/validate_guide.py} 查，这里不重复。
 * <p>
 * 另外管一个「配方表代数」：每次客户端收到配方（进服、/reload）+1，{@link GuideView} 据此重建（查阅索引、打开着的配方图都跟着变）。
 */
@OnlyIn(Dist.CLIENT)
final class GuideChecks {

    /** 同一批问题最多逐条打多少行，再多只报数量 */
    private static final int MAX_LINES = 200;

    private static volatile int recipeGeneration;

    private GuideChecks() {
    }

    /** 配方表代数：每次客户端收到配方 +1 */
    static int recipeGeneration() {
        return recipeGeneration;
    }

    /**
     * 客户端收到配方（RecipesUpdatedEvent，主线程）
     *
     * @param manager 新的配方表
     */
    static void onRecipesUpdated(RecipeManager manager) {
        recipeGeneration++;
        try {
            checkRecipes(manager);
        } catch (RuntimeException e) {
            // 自检出错不能影响进服
            EssentialCraft5.LOGGER.warn("[指南] 配方自检出错：{}", e.toString());
        }
    }

    private static void checkRecipes(RecipeManager manager) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        RegistryAccess access = connection == null ? RegistryAccess.EMPTY : connection.registryAccess();
        GuideView view = GuideView.current();
        List<String> problems = new ArrayList<>();
        for (GuideView.EntryView ev : view.entryList) {
            List<Block> blocks = ev.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                if (!(blocks.get(i).figure instanceof FigureSpec.Recipes recipes) || recipes.optional()) {
                    continue;
                }
                String where = ev.key + " 第 " + (i + 1) + " 块";
                if (recipes.recipe() != null) {
                    if (RecipeFinder.forPage(manager, access, recipes).isEmpty()) {
                        problems.add(where + "：配方 " + recipes.recipe() + " 不存在，或者这种配方没有登记画法");
                    }
                    continue;
                }
                if (recipes.recipeType() != null) {
                    if (!ForgeRegistries.RECIPE_TYPES.containsKey(recipes.recipeType())) {
                        problems.add(where + "：配方类型 " + recipes.recipeType() + " 没有注册");
                    } else if (RecipeFinder.forPage(manager, access, recipes).isEmpty()) {
                        problems.add(where + "：配方类型 " + recipes.recipeType()
                                + " 下没有能画的配方（没有配方，或者模块没用 GuideRecipeViews.register 登记画法）");
                    }
                    continue;
                }
                for (ResourceLocation id : recipes.items()) {
                    Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
                    if (item != null && RecipeFinder.find(manager, access, item).isEmpty()) {
                        problems.add(where + "：配方块找不到 " + id + " 的配方（这一项不画）");
                    }
                }
            }
        }
        if (problems.isEmpty()) {
            EssentialCraft5.LOGGER.info("[指南] 配方检查通过");
            return;
        }
        EssentialCraft5.LOGGER.warn("[指南] 配方检查发现 {} 个问题：", problems.size());
        for (int i = 0; i < Math.min(MAX_LINES, problems.size()); i++) {
            EssentialCraft5.LOGGER.warn("[指南]   {}", problems.get(i));
        }
        if (problems.size() > MAX_LINES) {
            EssentialCraft5.LOGGER.warn("[指南]   ……还有 {} 个", problems.size() - MAX_LINES);
        }
    }
}
