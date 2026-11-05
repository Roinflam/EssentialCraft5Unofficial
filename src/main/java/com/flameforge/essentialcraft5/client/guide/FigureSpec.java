package com.flameforge.essentialcraft5.client.guide;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 手册里「图」的数据：配方、结构 / 仪式、插图（只有数据，不含绘制）。
 * <p>
 * 由 {@link FigureSpecParser} 从内容 JSON 的 {@code recipe / structure / ritual / image} 块解析出来，存在
 * {@link GuideContent.Block#figure} 里；排版由 {@link FigureBlocks} 交给 {@link Figures} 画。
 * 这几个 record 原本是旧手册数据层 {@code GuidePage} 的页类型，搬过来时去掉了 title / text（标题和正文现在是单独的块）。
 * <p>
 * 所有文字字段（层标签、图例说明）是<b>文字本身</b>，或者原版 / EC5 的非手册语言键：界面用 {@code Language.getOrDefault} 取，
 * 是键就翻译、不是键就原样显示。
 */
public final class FigureSpec {

    private FigureSpec() {
    }

    /**
     * 配方图：给出物品，界面从客户端 RecipeManager 找出所有产出它的配方（工作台、魔力工作台、辐射仓、熔炉，
     * 以及模块用 {@code GuideRecipeViews.register} 登记过画法的配方类型）画出来。配方改了（数据包、KubeJS），书里自动跟着变。
     * <p>
     * 也可以给一个配方类型（recipeType），列出这个类型的全部配方（转化表之类），按配方 id 排序；同时给了物品时只列产出这些物品的。
     * 还可以直接给配方 id（recipe），只画那一条。
     *
     * @param items      产物（按产物查配方）；给了 recipeType 时可以为空（为空 = 不按产物筛）
     * @param recipe     按配方 id 画这一条，可为 null
     * @param recipeType 列出这个类型的全部配方，可为 null
     * @param compact    登记过画法的类型画成一行一条（原料 → 产物 + 第一行说明），适合几十条的表；内置四种不受影响
     * @param optional   找不到配方时整块不显示、也不报日志（给以后批次才加配方的东西用，比如月亮井）
     */
    public record Recipes(List<ResourceLocation> items, @Nullable ResourceLocation recipe, @Nullable ResourceLocation recipeType,
                          boolean compact, boolean optional) {
    }

    /**
     * 摆法（地面仪式、产能器摆法、多方块结构）：俯视网格，每层一个 pattern（每行一个字符串，从北往南），字符在 key 里查；
     * 空格表示「不用管」。多层时从下到上。
     * <p>
     * 三种写法：单层 {@code pattern + key}（仪式也用它）；多层 {@code layers: [{pattern, key, label}]}；
     * 或者 {@code structure: id} 直接画代码里登记的 StructurePattern（书和机器判定用同一份数据），这时 layers 为空。
     *
     * @param layers    从下到上的各层；structure 不为 null 时为空表
     * @param structure 代码里登记的结构 id（StructurePatterns），可为 null
     * @param anchor    机器所在的字符（四角品红光；其它层在同一竖列画小标记，标题写「高度：±n」），可为 null
     * @param thrown    仪式要丢在中心方块上方的物品，可为 null（不是仪式；只对单层有效）
     * @param result    仪式产物，可为 null
     * @param chance    成功率 0~1，负数表示不显示
     */
    public record Structure(List<StructureLayer> layers, @Nullable ResourceLocation structure, @Nullable Character anchor,
                            @Nullable ResourceLocation thrown, @Nullable ResourceLocation result, float chance) {

        /** 各层 key 里的全部定义（给检查用） */
        public List<StructureKey> allKeys() {
            List<StructureKey> out = new ArrayList<>();
            for (StructureLayer layer : layers) {
                out.addAll(layer.key().values());
            }
            return out;
        }
    }

    /**
     * 摆法的一层。
     *
     * @param pattern 每行一个字符串，从北往南
     * @param key     这一层的字符定义（已经并入块级的公共 key，层里写的优先）
     * @param label   这一层的名字（文字或非手册语言键），可为 null；画在层标题后面，比如「棱镜层」
     */
    public record StructureLayer(List<String> pattern, Map<Character, StructureKey> key, @Nullable String label) {
    }

    /**
     * 摆法里一个字符代表什么。
     *
     * @param blocks 候选方块（多个时每秒轮换显示，比如沙子和红沙）
     * @param tag    方块标签（与 blocks 二选一，界面按标签成员轮换）
     * @param air    这一格必须是空气
     * @param label  悬停提示里额外显示的说明（文字或非手册语言键），比如「流动的也算」
     */
    public record StructureKey(List<ResourceLocation> blocks, @Nullable ResourceLocation tag, boolean air, @Nullable String label) {
    }

    /** 插图：一张贴图（按阅读宽度等比缩小，不放大） */
    public record Image(ResourceLocation texture, int width, int height) {
    }
}
