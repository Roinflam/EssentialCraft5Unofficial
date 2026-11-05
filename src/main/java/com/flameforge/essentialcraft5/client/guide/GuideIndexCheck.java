package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Block;
import com.flameforge.essentialcraft5.client.guide.GuideView.EntryView;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 条目 {@code items} 条件组的端到端自检：条件不成立时，组里的物品真的不进查阅索引、也不进搜索的物品名
 *
 * <p>gametest/GuideFormatTests 跑在服务器上，手册界面的类都带 {@code @OnlyIn(CLIENT)}，它只能守手册数据：
 * {@link GuideView} 的查阅索引或 {@link GuideSearch} 的物品名哪天改成不按条件过滤，那边照样通过。
 * 这里关掉海纳尔之门（{@link GuideClientConfig#setOverride}，不动真配置）建一份真视图，
 * 走的就是界面用的 {@link GuideView#find} 和 {@link GuideSearch#itemNames}。展示厅的手册部分每次都跑一遍
 * （dev/DevShowroom，{@code -PecShowroom} / {@code -PecShowroomGuide}），结果打到日志，前缀「[指南]」。</p>
 *
 * <p>判定：某个物品只在一篇可见条目的条件组里、组的条件不成立时 ——</p>
 * <ul>
 *   <li>查阅不能落到这一篇的篇首。篇首是条目 {@code items} 那一档的落点；这一篇有可见的块画着它时落到那一块，
 *       属于配方 / 物品块那两档，是正常的；</li>
 *   <li>这一篇的搜索物品名里不能有它的名字（这一篇可见的配方块画着它时除外）。</li>
 * </ul>
 * <p>门关着时这样的物品一个都没有也算问题：说明条件组被删了或者没解析出来，自检什么都没查。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GuideIndexCheck {

    private static final String PORTAL = "general.hoanna.portal_enabled";

    private GuideIndexCheck() {
    }

    /**
     * 关着海纳尔之门跑一遍，打日志；跑完撤掉覆盖值（{@code setOverride(null)}，调用方之前设的覆盖也一起撤掉）
     *
     * @return 问题列表，空表表示通过
     */
    @Nonnull
    public static List<String> runGateClosed() {
        GuideClientConfig.setOverride(Map.of(PORTAL, "false"));
        List<String> problems;
        try {
            problems = check(GuideView.current());
        } catch (RuntimeException e) {
            problems = List.of("自检出错：" + e);
        } finally {
            GuideClientConfig.setOverride(null);
        }
        if (problems.isEmpty()) {
            EssentialCraft5.LOGGER.info("[指南] 条目 items 条件组自检（海纳尔之门关）：通过");
        } else {
            EssentialCraft5.LOGGER.warn("[指南] 条目 items 条件组自检（海纳尔之门关）：{} 处问题", problems.size());
            for (String p : problems.subList(0, Math.min(20, problems.size()))) {
                EssentialCraft5.LOGGER.warn("[指南]   {}", p);
            }
        }
        return problems;
    }

    /**
     * 按当前视图的配置检查（判定见类注释）
     *
     * @param view 视图
     * @return 问题列表
     */
    @Nonnull
    static List<String> check(GuideView view) {
        List<String> problems = new ArrayList<>();
        int filtered = 0;
        for (EntryView ev : view.entryList) {
            Set<ResourceLocation> counted = new HashSet<>(ev.items());
            List<ResourceLocation> left = new ArrayList<>();
            // 条件全当成立时拿到的是这一篇写的全部物品；减去当前配置下算数的，就是被条件组拿掉的
            for (ResourceLocation id : ev.entry.items.visible(c -> true)) {
                if (!counted.contains(id)) {
                    left.add(id);
                }
            }
            if (left.isEmpty()) {
                continue;
            }
            String names = GuideSearch.itemNames(ev);
            for (ResourceLocation id : left) {
                Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
                if (item == null || item == Items.AIR) {
                    continue;
                }
                filtered++;
                GuideView.Target target = view.find(item);
                if (target != null && target.entryKey().equals(ev.key) && target.block() < 0) {
                    problems.add("查阅：" + id + " 只在 " + ev.key + " 的条件组里、条件不成立，查阅仍落到这一篇篇首");
                }
                String name = new ItemStack(item).getHoverName().getString();
                if (!name.isEmpty() && names.contains(name) && !drawnByRecipe(ev, id)) {
                    problems.add("搜索：" + ev.key + " 的物品名里仍有条件不成立的 " + id + "（" + name + "）");
                }
            }
        }
        if (filtered == 0) {
            problems.add("没有任何物品被条件组拿掉：条件组被删了或没解析出来，这项自检什么都没查");
        }
        return problems;
    }

    private static boolean drawnByRecipe(EntryView ev, ResourceLocation id) {
        for (Block b : ev.blocks()) {
            if (b.figure instanceof FigureSpec.Recipes recipes && recipes.items().contains(id)) {
                return true;
            }
        }
        return false;
    }
}
