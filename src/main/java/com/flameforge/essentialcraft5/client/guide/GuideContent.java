package com.flameforge.essentialcraft5.client.guide;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * 冒险指南的内容模型：一本书 = 若干分类，分类 = 若干条目，条目 = 若干块
 *
 * <p>这里只存 JSON 里的<b>原文</b>（带标签、占位符、链接的字符串），不做任何解析。
 * 同一份内容要在不同的配置快照下渲染出不同的数值 —— 服务端热重载配置后书要跟着变，
 * 所以解析放在界面那一层按快照版本做，模型本身在资源重载之前一直不变。</p>
 *
 * <p>所有集合都是不可变的，加载器构造完就交出去，界面只读。</p>
 *
 * <p>EC5 在通用引擎之外多出来的：{@code structure / ritual / image} 三种块和扩过的 {@code recipe} 块
 * （数据在 {@link Block#figure}，见 {@link FigureSpec}）、{@code items} 块的 {@code per_row}、条目的
 * {@code complete / optional / advancement / items}（打勾、下一步、进度锁、查阅索引；{@code optional} 可以按条件可选，见 {@link GuideSteps}；
 * {@code items} 可带条件组，见 {@link EntryItems}）、
 * 分类的 {@code group / advancement / _draft}，以及分类、条目的 {@code locked_hint}（被进度锁住时给玩家看的解锁提示，第十二批 batch12.md §4.1.9）。
 * 格式见 tools/guide/FORMAT.md。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GuideContent {

    private GuideContent() {}

    /** 块类型，对应内容格式规范 §1.2 */
    public enum BlockType {
        H, P, LIST, STEPS, KV, TABLE, TIP, FORMULA, ITEMS, RECIPE, ENTITY, DIVIDER,
        /** 多方块结构图（EC5） */
        STRUCTURE,
        /** 地面仪式图（EC5） */
        RITUAL,
        /** 插图（EC5） */
        IMAGE,
        /** 题记：章首引一段先辈的残卷 / 碑文，出处写在 by 字段（EC5） */
        QUOTE;

        /**
         * 按 JSON 里的 type 字段找类型
         *
         * @param name type 字段
         * @return 类型；不认识时为 null
         */
        @Nullable
        public static BlockType of(@Nullable String name) {
            if (name == null) {
                return null;
            }
            return switch (name) {
                case "h" -> H;
                case "p" -> P;
                case "list" -> LIST;
                case "steps" -> STEPS;
                case "kv" -> KV;
                case "table" -> TABLE;
                case "tip" -> TIP;
                case "formula" -> FORMULA;
                case "items" -> ITEMS;
                case "recipe" -> RECIPE;
                case "entity" -> ENTITY;
                case "divider" -> DIVIDER;
                case "structure" -> STRUCTURE;
                case "ritual" -> RITUAL;
                case "image" -> IMAGE;
                case "quote" -> QUOTE;
                default -> null;
            };
        }
    }

    /** 提示框样式 */
    public enum TipStyle {
        INFO, WARN, GOOD, BAD,
        /** 源质小知识：讲原作里的来历，不承载玩法信息，没玩过原作的玩家可以跳过 */
        LORE;

        @Nonnull
        public static TipStyle of(@Nullable String name) {
            if ("warn".equals(name)) {
                return WARN;
            }
            if ("good".equals(name)) {
                return GOOD;
            }
            if ("bad".equals(name)) {
                return BAD;
            }
            if ("lore".equals(name)) {
                return LORE;
            }
            return INFO;
        }

        public int color() {
            return switch (this) {
                case INFO -> GuideTheme.TIP_INFO;
                case WARN -> GuideTheme.TIP_WARN;
                case GOOD -> GuideTheme.TIP_GOOD;
                case BAD -> GuideTheme.TIP_BAD;
                case LORE -> GuideTheme.TIP_LORE;
            };
        }
    }

    /**
     * 一本书：按 order 排好序的分类
     */
    public static final class Book {
        public final List<Category> categories;
        public final Map<String, Category> byId;
        /** 实际读的是哪个语言目录（回退之后），只用于日志 */
        public final String language;

        public Book(@Nonnull List<Category> categories, @Nonnull String language) {
            this.categories = List.copyOf(categories);
            Map<String, Category> map = new LinkedHashMap<>();
            for (Category c : this.categories) {
                map.put(c.id, c);
            }
            this.byId = Collections.unmodifiableMap(map);
            this.language = language;
        }

        public static Book empty(String language) {
            return new Book(List.of(), language);
        }
    }

    /** 首页 / 侧栏的分区：入门、主线、支线 */
    public enum Group {
        START, MAIN, SIDE;

        /**
         * @param name JSON 里的 group 字段
         * @return 分区；不认识或没写时归到支线（排在最后，不会冒充主线的章号）
         */
        @Nonnull
        public static Group of(@Nullable String name) {
            if ("start".equals(name)) {
                return START;
            }
            if ("main".equals(name)) {
                return MAIN;
            }
            return SIDE;
        }
    }

    /**
     * 分类：首页的一个格子
     */
    public static final class Category {
        public final String id;
        public final int order;
        public final Group group;
        public final String title;
        public final String subtitle;
        @Nullable
        public final String icon;
        /** 解锁进度：完成前看得到标题、点不开（给数据包作者用） */
        @Nullable
        public final ResourceLocation advancement;
        /** 被 {@link #advancement} 锁住时的解锁提示原文（可带占位符和行内标记）；null = 用默认的「完成进度「X」后解锁」 */
        @Nullable
        public final String lockedHint;
        /** 机械转换出来的草稿（只用于日志与校验，界面不区分） */
        public final boolean draft;
        /** 全部成立才显示；空表示无条件 */
        public final List<String> cond;
        public final List<Entry> entries;

        public Category(String id, int order, Group group, String title, String subtitle, @Nullable String icon,
                        @Nullable ResourceLocation advancement, @Nullable String lockedHint, boolean draft, List<String> cond,
                        List<Entry> entries) {
            this.id = id;
            this.order = order;
            this.group = group;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.advancement = advancement;
            this.lockedHint = lockedHint;
            this.draft = draft;
            this.cond = List.copyOf(cond);
            this.entries = List.copyOf(entries);
        }
    }

    /**
     * 条目：阅读页的一篇
     */
    public static final class Entry {
        public final String id;
        public final String title;
        @Nullable
        public final String icon;
        public final String summary;
        public final List<String> cond;
        public final List<Block> blocks;
        /** 完成进度：写了就是流程里的一「步」，完成后打勾（不锁任何东西）；null 表示不是步骤 */
        @Nullable
        public final ResourceLocation complete;
        /**
         * 可选步骤：做完照样打勾，但不算进 x/y、不当「下一步」。可以按条件可选（{@link GuideSteps.OptionalRule}），
         * 所以「是不是可选」要按当前配置求值（界面里是 {@code GuideView.EntryView#optional}），这里只存规则
         */
        public final GuideSteps.OptionalRule optional;
        /** 解锁进度：完成前点不开 */
        @Nullable
        public final ResourceLocation advancement;
        /**
         * 被 {@link #advancement} 锁住时的解锁提示原文（可带占位符和行内标记），代替默认的「完成进度「X」后解锁」。
         * 隐藏进度（没有 display）默认只能说「完成某个进度」，玩家不知道去哪做，所以给它们写一句「去哪找」（残卷各卷就是这样锁的）
         */
        @Nullable
        public final String lockedHint;
        /**
         * 这个条目「负责介绍」的物品：查阅、点物品跳转都优先来这里。可以带条件组（{@link EntryItems}），
         * 用的时候按当前配置过滤（{@code GuideView.EntryView#items()}；{@link EntryItems} 只提供过滤后的取法）
         */
        public final EntryItems items;

        public Entry(String id, String title, @Nullable String icon, String summary, List<String> cond, List<Block> blocks,
                     @Nullable ResourceLocation complete, GuideSteps.OptionalRule optional, @Nullable ResourceLocation advancement,
                     @Nullable String lockedHint, EntryItems items) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.summary = summary;
            this.cond = List.copyOf(cond);
            this.blocks = List.copyOf(blocks);
            this.complete = complete;
            this.optional = optional;
            this.advancement = advancement;
            this.lockedHint = lockedHint;
            this.items = items;
        }

        /**
         * 是不是流程里的一步（写了 complete）。是不是<b>必做</b>的一步跟配置有关（按条件可选），
         * 看 {@code GuideView.EntryView#requiredStep}
         */
        public boolean isStep() {
            return complete != null;
        }
    }

    /**
     * 块：一个类型加上它用得到的字段，其余字段为空
     *
     * <p>用一个类装全部块类型而不是一种块一个子类：块只是数据，真正按类型分派的是排版
     * （{@link GuideBlocks}），在那边 switch 一次比在这里维护十二个几乎空的子类清楚。</p>
     */
    public static final class Block {
        public final BlockType type;
        public final List<String> cond;
        /** h / p / formula / tip 的正文 */
        public final String text;
        /** tip 的标题（可空） */
        @Nullable
        public final String title;
        public final TipStyle style;
        /** list / steps 的文本项；items 块的物品 id */
        public final List<String> items;
        /** 与 items 等长：每一项自己的 if（空表 = 总是显示） */
        public final List<List<String>> itemConds;
        public final boolean ordered;
        /** kv / table 的行 */
        public final List<List<String>> rows;
        /** 与 rows 等长：每一行自己的 if（空表 = 总是显示） */
        public final List<List<String>> rowConds;
        /** table 表头 */
        public final List<String> header;
        /** table 列宽权重，可空 */
        @Nullable
        public final float[] widths;
        /** items / recipe / structure / ritual / image 块的说明；quote 块的出处（可空） */
        @Nullable
        public final String caption;
        /** items 块每行几个（0 = 按宽度排） */
        public final int perRow;
        /**
         * recipe / structure / ritual / image 块的数据：{@link FigureSpec.Recipes}、{@link FigureSpec.Structure}、
         * {@link FigureSpec.Image} 之一；其它块为 null
         */
        @Nullable
        public final Object figure;
        /** entity：实体类型 id */
        @Nullable
        public final String entity;
        /** entity：预览高度 */
        public final int height;
        /** entity：合并进预览实体的 SNBT（精英变体、颜色），可空；见 {@link EntityPreviewSpec} */
        @Nullable
        public final String entityNbt;
        /** entity：预览缩放（大首领缩小），默认 1 */
        public final float entityScale;
        /**
         * entity：框下名字的语言键，可空（不写就用实体类型的名字）。nbt 摆出来的变体自己换了名字时写（风暴术士、精英），
         * 不然框下还是类型名，和页面标题对不上
         */
        @Nullable
        public final String entityName;

        private Block(Builder b) {
            this.type = b.type;
            this.cond = List.copyOf(b.cond);
            this.text = b.text == null ? "" : b.text;
            this.title = b.title;
            this.style = b.style;
            this.items = List.copyOf(b.items);
            this.itemConds = pad(b.itemConds, this.items.size());
            this.ordered = b.ordered;
            this.rows = b.rows.stream().map(List::copyOf).toList();
            this.rowConds = pad(b.rowConds, this.rows.size());
            this.header = List.copyOf(b.header);
            this.widths = b.widths;
            this.caption = b.caption;
            this.perRow = b.perRow;
            this.figure = b.figure;
            this.entity = b.entity;
            this.height = b.height;
            this.entityNbt = b.entityNbt;
            this.entityScale = b.entityScale;
            this.entityName = b.entityName;
        }

        private static List<List<String>> pad(List<List<String>> conds, int size) {
            List<List<String>> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                out.add(i < conds.size() && conds.get(i) != null ? List.copyOf(conds.get(i)) : List.of());
            }
            return List.copyOf(out);
        }

        /**
         * 去掉条件不成立的列表项 / 表格行
         *
         * <p>让「服务端开了才有」的玩法能细到一行：例如盔甲槽模组关着时，槽位表里就没有头盔那一行。
         * 没有任何行级条件时直接返回自己，不复制。</p>
         *
         * @param test 条件求值（同块级 if 的规则）
         * @return 过滤后的块
         */
        public Block visible(Predicate<List<String>> test) {
            boolean any = false;
            for (List<String> c : itemConds) {
                any |= !c.isEmpty();
            }
            for (List<String> c : rowConds) {
                any |= !c.isEmpty();
            }
            if (!any) {
                return this;
            }
            Builder b = toBuilder();
            List<String> its = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (itemConds.get(i).isEmpty() || test.test(itemConds.get(i))) {
                    its.add(items.get(i));
                }
            }
            List<List<String>> rs = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                if (rowConds.get(i).isEmpty() || test.test(rowConds.get(i))) {
                    rs.add(rows.get(i));
                }
            }
            b.items = its;
            b.itemConds = List.of();
            b.rows = rs;
            b.rowConds = List.of();
            return b.build();
        }

        /**
         * 行级条件过滤后，列表 / 表格是否一行不剩（整块应当隐藏）
         *
         * @return true 表示该隐藏
         */
        public boolean isHollow() {
            return switch (type) {
                case LIST, STEPS, ITEMS -> items.isEmpty();
                case KV, TABLE -> rows.isEmpty();
                default -> false;
            };
        }

        private Builder toBuilder() {
            Builder b = new Builder(type);
            b.cond = cond;
            b.text = text;
            b.title = title;
            b.style = style;
            b.items = items;
            b.itemConds = itemConds;
            b.ordered = ordered;
            b.rows = rows;
            b.rowConds = rowConds;
            b.header = header;
            b.widths = widths;
            b.caption = caption;
            b.perRow = perRow;
            b.figure = figure;
            b.entity = entity;
            b.height = height;
            b.entityNbt = entityNbt;
            b.entityScale = entityScale;
            b.entityName = entityName;
            return b;
        }

        /** 块的构造器：加载器边解析边填 */
        public static final class Builder {
            public BlockType type;
            public List<String> cond = List.of();
            public String text;
            public String title;
            public TipStyle style = TipStyle.INFO;
            public List<String> items = List.of();
            public List<List<String>> itemConds = List.of();
            public boolean ordered;
            public List<List<String>> rows = List.of();
            public List<List<String>> rowConds = List.of();
            public List<String> header = List.of();
            public float[] widths;
            public String caption;
            public int perRow;
            public Object figure;
            public String entity;
            public int height = 64;
            public String entityNbt;
            public float entityScale = 1F;
            public String entityName;

            public Builder(BlockType type) {
                this.type = type;
            }

            public Block build() {
                return new Block(this);
            }
        }
    }
}
