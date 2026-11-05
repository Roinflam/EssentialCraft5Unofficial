package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.client.guide.GuideContent.Block;
import com.flameforge.essentialcraft5.client.guide.GuideContent.BlockType;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Book;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Category;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Entry;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Group;
import com.flameforge.essentialcraft5.client.guide.GuideMarkup.LinkTarget;
import com.flameforge.essentialcraft5.client.guide.GuideMarkup.Rich;
import com.flameforge.essentialcraft5.client.guide.data.GuideUnlocks;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「这本书在当前世界里长什么样」：内容 × 配置快照 × 语言
 *
 * <p>内容模型（{@link GuideContent}）是原文，本类在它上面套一层条件求值：哪些分类 / 条目 / 块在当前配置下存在、
 * 标题和摘要解析成什么纯文本、链接指向哪里。界面只和这一层打交道。</p>
 *
 * <h3>全局只有一份（{@link #current()}）</h3>
 * <p>手册界面、物品提示上的「按住 Ctrl 查阅」、手持手册潜行右键、查阅键共用同一个实例 —— 手册关着的时候也要能查
 * 「这个物品在哪一篇」。五样东西任何一样变了就整个重建（{@link #isStale()}）：资源重载换了内容、
 * 配置变了（{@link GuideClientConfig#revision()}，由配置事件驱动，不轮询）、玩家切了语言、配方表换了、
 * 玩家进出创造模式（条件 {@code gamemode:creative}，每次取视图时比一个布尔）。
 * 重建很便宜 —— 一百多个条目的条件求值，远不到一毫秒；标题、正文、索引都等真正用到时才算。</p>
 *
 * <h3>EC5 自己的几样东西</h3>
 * <ul>
 *   <li><b>物品 → (条目, 块) 索引</b>（{@link #find}）：查阅、点物品跳转共用，优先级照旧手册：
 *       ① 条目 {@code items} 里写了的（条件组不成立的不算）；② {@code recipe} 块写明的产物；③ {@code items} 块、{@code ritual} 的产物。
 *       同一优先级按全书顺序取先出现的。<b>只收可见的条目和块</b>；块序号是过滤掉 {@code if} 之后的编号，
 *       和阅读页用的是同一个 {@link EntryView#blocks()}，所以对得上。</li>
 *   <li><b>打勾与「下一步」</b>（{@link #progress()}）：条目的 {@code complete} 进度完成了就打勾；
 *       全书第一个没完成、不是可选的步骤是「下一步」。被 {@code if} 藏掉的条目不算步骤。
 *       {@code optional} 可以按条件可选，建视图时按配置快照求一次（{@link EntryView#optional}）；x/y 怎么数、
 *       谁当「下一步」的规则在 {@link GuideSteps}（GameTest 用的也是它）。
 *       进度是反射读客户端进度表（{@link GuideUnlocks}），结果缓存半秒，界面每帧来取也不会每帧都查。</li>
 *   <li><b>进度锁</b>（分类 / 条目的 {@code advancement}，给数据包作者用）：看得到标题、点不开。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
final class GuideView implements GuideMarkup.Resolver {

    /** 打勾 / 锁定状态的缓存时长：进度完成后最多半秒书里就跟上 */
    private static final long PROGRESS_TTL_MS = 500L;

    @Nullable
    private static GuideView current;

    final Book book;
    final GuideValues values;
    final int revision;
    final int generation;
    final int recipes;
    final String language;
    /** 建视图时本地玩家是不是创造模式 */
    final boolean creative;

    /** 可见、且至少有一个可见条目的分类，按全书顺序（入门 → 主线 → 支线，区内按 order） */
    final List<CatView> categories;
    final Map<String, CatView> categoriesById;
    /** 可见条目，键 = 分类id/条目id */
    final Map<String, EntryView> entries;
    /** 可见条目按书中顺序 */
    final List<EntryView> entryList;

    private final Map<String, ItemStack> icons = new HashMap<>();
    @Nullable
    private Map<Item, Target> index;
    private final Progress progress;

    /**
     * 查阅的目标
     *
     * @param entryKey 条目键（分类/条目）
     * @param block    块序号（{@link EntryView#blocks()} 里的下标）；-1 表示篇首
     */
    record Target(String entryKey, int block) {}

    private GuideView(Book book, GuideValues values, int revision, int generation, int recipes, String language, boolean creative) {
        this.book = book;
        this.values = values;
        this.revision = revision;
        this.generation = generation;
        this.recipes = recipes;
        this.language = language;
        this.creative = creative;

        List<CatView> cats = new ArrayList<>();
        Map<String, CatView> catMap = new LinkedHashMap<>();
        Map<String, EntryView> entryMap = new LinkedHashMap<>();
        List<EntryView> all = new ArrayList<>();
        int chapter = 0;
        for (Category c : book.categories) {
            if (!values.test(c.cond)) {
                continue;
            }
            CatView cv = new CatView(this, c, cats.size());
            for (Entry e : c.entries) {
                if (!values.test(e.cond)) {
                    continue;
                }
                EntryView ev = new EntryView(this, cv, e, cv.entries.size(), all.size());
                cv.entries.add(ev);
                all.add(ev);
                entryMap.put(ev.key, ev);
            }
            if (cv.entries.isEmpty()) {
                // 条目全被条件藏掉的分类不显示空格子（点进去什么都没有）
                continue;
            }
            if (c.group == Group.MAIN) {
                cv.chapter = ++chapter;
            }
            cats.add(cv);
            catMap.put(c.id, cv);
        }
        this.categories = Collections.unmodifiableList(cats);
        this.categoriesById = Collections.unmodifiableMap(catMap);
        this.entries = Collections.unmodifiableMap(entryMap);
        this.entryList = Collections.unmodifiableList(all);
        this.progress = new Progress(all.size(), cats.size());
    }

    /**
     * 当前的视图（全局共用一份；内容、配置、语言、配方表任何一样变了就重建）
     *
     * @return 视图
     */
    @Nonnull
    static GuideView current() {
        GuideView view = current;
        if (view == null || view.isStale()) {
            view = build();
            current = view;
        }
        return view;
    }

    /**
     * 按当前内容、配置快照、语言建一个视图
     *
     * @return 视图
     */
    private static GuideView build() {
        Book book = GuideContentLoader.get();
        int generation = GuideContentLoader.generation();
        int revision = GuideClientConfig.revision();
        boolean creative = playerCreative();
        GuideValues values = new GuideValues(GuideClientConfig.snapshot(), GuideClientConfig.isSynced(), creative);
        return new GuideView(book, values, revision, generation, GuideChecks.recipeGeneration(), GuideContentLoader.currentLanguage(), creative);
    }

    /**
     * 内容、配置、语言、配方表、创造模式之一变了
     *
     * @return 是否需要重建
     */
    boolean isStale() {
        return revision != GuideClientConfig.revision()
                || generation != GuideContentLoader.generation()
                || recipes != GuideChecks.recipeGeneration()
                || !language.equals(GuideContentLoader.currentLanguage())
                || creative != playerCreative();
    }

    /** 本地玩家现在是不是创造模式（instabuild）；不在世界里算否 */
    private static boolean playerCreative() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getAbilities().instabuild;
    }

    // ==================== 解析 ====================

    /**
     * 解析一段原文
     *
     * @param raw   原文
     * @param color 默认色
     * @param bold  默认加粗
     * @return 片段序列
     */
    Rich rich(@Nullable String raw, int color, boolean bold) {
        return GuideMarkup.parse(raw, this, color, bold);
    }

    /**
     * 原文 → 纯文本（标题、摘要、搜索）
     */
    String plain(@Nullable String raw) {
        return rich(raw, GuideTheme.BODY, false).plain();
    }

    @Override
    public GuideValues.Resolved placeholder(String token) {
        return values.placeholder(token);
    }

    /**
     * 正在解析的链接标题层数。链接不写显示文字时要取目标的标题，而标题本身也是带标记的原文 ——
     * 标题里再链回自己（或 A 的标题链 B、B 的标题链 A）会无限递归直到栈溢出，
     * 而 StackOverflowError 不是 RuntimeException，哪一层的兜底都接不住，客户端直接崩。
     * 超过一层就不再取标题，退回原始 id。
     */
    private int linkDepth;

    /**
     * 是否正在替某条链接取目标标题。这时算出的标题里，再往下一层的链接退回了原始 id，
     * 不是这个标题「真正的样子」—— 不能写进缓存，也不能读缓存（缓存里那份又嵌着再下一层）。
     */
    boolean isResolvingLink() {
        return linkDepth > 0;
    }

    @Override
    public LinkTarget link(String target) {
        if (linkDepth > 0) {
            EntryView ev = target.indexOf('/') < 0 ? null : entries.get(target);
            CatView cv = ev == null && target.indexOf('/') < 0 ? categoriesById.get(target) : null;
            String key = ev != null ? ev.key : cv != null ? cv.entries.get(0).key : null;
            return new LinkTarget(key, target);
        }
        linkDepth++;
        try {
            return resolveLink(target);
        } finally {
            linkDepth--;
        }
    }

    private LinkTarget resolveLink(String target) {
        int slash = target.indexOf('/');
        String catId = slash < 0 ? target : target.substring(0, slash);
        if (slash < 0) {
            CatView cv = categoriesById.get(catId);
            if (cv != null) {
                return new LinkTarget(cv.entries.get(0).key, cv.title());
            }
            Category c = book.byId.get(catId);
            if (c == null) {
                GuideLog.warnOnce("link:" + target, "链接目标分类不存在: [[" + target + "]]");
            }
            return new LinkTarget(null, c != null ? plain(c.title) : target);
        }
        EntryView ev = entries.get(target);
        if (ev != null) {
            return new LinkTarget(ev.key, ev.title());
        }
        // 目标存在但被条件藏掉了：返回它的标题、不给键。GuideMarkup 对这种死链不生成 Link，
        // 按外层正文原样输出（和正文同色、不可点）—— 读者看不出这里原本是个链接，又知道原文提到了什么
        Category c = book.byId.get(catId);
        if (c != null) {
            String entryId = target.substring(slash + 1);
            for (Entry e : c.entries) {
                if (e.id.equals(entryId)) {
                    return new LinkTarget(null, plain(e.title));
                }
            }
        }
        GuideLog.warnOnce("link:" + target, "链接目标条目不存在: [[" + target + "]]");
        return new LinkTarget(null, target);
    }

    // ==================== 图标 ====================

    /**
     * 物品 id → 图标；不存在时用 fallback
     */
    ItemStack icon(@Nullable String id, @Nonnull ItemStack fallback) {
        if (id == null || id.isEmpty()) {
            return fallback;
        }
        ItemStack cached = icons.get(id);
        if (cached == null) {
            // 图标画不出来的物品（元素水晶的默认栈是全透明贴图）换成显示用的替身，见 GuideStacks
            cached = GuideStacks.display(stack(id));
            icons.put(id, cached);
        }
        return cached.isEmpty() ? fallback : cached;
    }

    /**
     * 物品 id → ItemStack；不存在时为空栈（并报一次）
     */
    static ItemStack stack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
            GuideLog.warnOnce("icon:" + id, "物品不存在: " + id);
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private ItemStack bookIcon;

    /** 书本身的图标：顶栏徽记、所有图标的最终兜底（顶栏每帧都画，所以缓存） */
    ItemStack bookIcon() {
        if (bookIcon == null) {
            bookIcon = icon("essentialcraft5:research_book", new ItemStack(Items.BOOK));
        }
        return bookIcon;
    }

    // ==================== 查阅索引 ====================

    /**
     * 介绍这个物品的条目和块（优先级见类注释）
     *
     * @param item 物品
     * @return 目标；书里没有（或只在被藏掉的条目 / 块里）时为 null
     */
    @Nullable
    Target find(Item item) {
        if (index == null) {
            index = buildIndex();
        }
        return index.get(item);
    }

    private Map<Item, Target> buildIndex() {
        Map<Item, Target> map = new HashMap<>();
        // 1. 条目明确声明的物品（条件组不成立的不算）：跳到这篇里第一块展示它的配方 / 物品 / 仪式，都没有就是篇首
        for (EntryView ev : entryList) {
            for (ResourceLocation id : ev.items()) {
                Item item = item(id);
                if (item != null && !map.containsKey(item)) {
                    map.put(item, new Target(ev.key, firstBlockShowing(ev, id)));
                }
            }
        }
        // 2. 配方块写明的产物
        for (EntryView ev : entryList) {
            List<Block> blocks = ev.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                if (blocks.get(i).figure instanceof FigureSpec.Recipes recipes) {
                    for (ResourceLocation id : recipes.items()) {
                        Item item = item(id);
                        if (item != null) {
                            map.putIfAbsent(item, new Target(ev.key, i));
                        }
                    }
                }
            }
        }
        // 3. 物品块、仪式产物
        for (EntryView ev : entryList) {
            List<Block> blocks = ev.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                Block b = blocks.get(i);
                if (b.type == BlockType.ITEMS) {
                    for (String raw : b.items) {
                        ResourceLocation id = ResourceLocation.tryParse(raw);
                        Item item = id == null ? null : item(id);
                        if (item != null) {
                            map.putIfAbsent(item, new Target(ev.key, i));
                        }
                    }
                } else if (b.figure instanceof FigureSpec.Structure s && s.result() != null) {
                    Item item = item(s.result());
                    if (item != null) {
                        map.putIfAbsent(item, new Target(ev.key, i));
                    }
                }
            }
        }
        return map;
    }

    /** 这一篇里第一块展示这个物品的块：配方块优先，其次物品块 / 仪式；都没有就是篇首（-1） */
    private static int firstBlockShowing(EntryView ev, ResourceLocation id) {
        List<Block> blocks = ev.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).figure instanceof FigureSpec.Recipes recipes && recipes.items().contains(id)) {
                return i;
            }
        }
        String raw = id.toString();
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.type == BlockType.ITEMS && b.items.contains(raw)
                    || b.figure instanceof FigureSpec.Structure s && id.equals(s.result())) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static Item item(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        return item == null || item == Items.AIR ? null : item;
    }

    // ==================== 打勾、下一步、锁 ====================

    /**
     * 各条目的完成 / 锁定状态与「下一步」（缓存半秒，见类注释）
     *
     * @return 当前状态
     */
    Progress progress() {
        progress.refresh(this);
        return progress;
    }

    /** 让打勾状态马上重查（刚打开界面时） */
    void refreshProgress() {
        progress.checkedAt = 0L;
    }

    /**
     * 打勾、锁定、下一步的状态表（按条目在全书里的序号存，每半秒整体重算一次）
     */
    static final class Progress {
        private final boolean[] done;
        private final boolean[] locked;
        /** 各分类的 x/y（规则在 {@link GuideSteps.Tally}） */
        private final GuideSteps.Tally[] cats;
        /** 全书的必做步骤计数（首页「主线已完成」、底栏进度） */
        private final GuideSteps.Tally book = new GuideSteps.Tally();
        private final boolean[] catLocked;
        private long checkedAt;
        @Nullable
        private EntryView next;

        Progress(int entries, int categories) {
            done = new boolean[entries];
            locked = new boolean[entries];
            cats = new GuideSteps.Tally[categories];
            for (int c = 0; c < categories; c++) {
                cats[c] = new GuideSteps.Tally();
            }
            catLocked = new boolean[categories];
        }

        void refresh(GuideView view) {
            long now = Util.getMillis();
            if (checkedAt != 0L && now - checkedAt < PROGRESS_TTL_MS) {
                return;
            }
            checkedAt = now;
            next = null;
            book.reset();
            for (CatView cv : view.categories) {
                int c = cv.index;
                GuideSteps.Tally tally = cats[c];
                tally.reset();
                catLocked[c] = !GuideUnlocks.isUnlocked(cv.category.advancement);
                for (EntryView ev : cv.entries) {
                    int i = ev.globalIndex;
                    Entry e = ev.entry;
                    locked[i] = catLocked[c] || !GuideUnlocks.isUnlocked(e.advancement);
                    done[i] = e.isStep() && GuideUnlocks.isDone(e.complete);
                    book.add(e.isStep(), ev.requiredStep, done[i]);
                    if (tally.add(e.isStep(), ev.requiredStep, done[i]) && next == null) {
                        next = ev;
                    }
                }
            }
        }

        /** 这一步做完了没有（不是步骤的条目恒为 false） */
        boolean done(EntryView ev) {
            return done[ev.globalIndex];
        }

        /** 被进度锁着（分类或条目的 advancement 还没完成） */
        boolean locked(EntryView ev) {
            return locked[ev.globalIndex];
        }

        boolean locked(CatView cv) {
            return catLocked[cv.index];
        }

        /** 全书第一个没完成的必做步骤；全做完了（或没有步骤）为 null */
        @Nullable
        EntryView next() {
            return next;
        }

        int required() {
            return book.required();
        }

        int requiredDone() {
            return book.requiredDone();
        }

        /**
         * 分类格子上的 x/y：有必做步骤时数必做步骤；全是可选步骤的支线数全部步骤；没有步骤为 -1
         */
        int total(CatView cv) {
            return cats[cv.index].total();
        }

        int doneCount(CatView cv) {
            return cats[cv.index].doneCount();
        }
    }

    /**
     * 锁住这个条目的进度（先看分类、再看条目），没锁返回 null。用来给「怎么解锁」的提示
     */
    @Nullable
    ResourceLocation lockedBy(EntryView ev) {
        ResourceLocation cat = ev.cat.category.advancement;
        if (cat != null && !GuideUnlocks.isUnlocked(cat)) {
            return cat;
        }
        ResourceLocation own = ev.entry.advancement;
        return own != null && !GuideUnlocks.isUnlocked(own) ? own : null;
    }

    /**
     * 锁住这个条目的那一层（分类或条目，同 {@link #lockedBy}）写的 {@code locked_hint} 原文；没锁、或那一层没写，返回 null
     */
    @Nullable
    String lockedHint(EntryView ev) {
        ResourceLocation cat = ev.cat.category.advancement;
        if (cat != null && !GuideUnlocks.isUnlocked(cat)) {
            return ev.cat.category.lockedHint;
        }
        ResourceLocation own = ev.entry.advancement;
        return own != null && !GuideUnlocks.isUnlocked(own) ? ev.entry.lockedHint : null;
    }

    /**
     * 悬停、动作栏里的解锁提示（纯文字，颜色由调用方给）：锁住它的那一层写了 {@code locked_hint} 就用它（占位符展开、标签去掉），
     * 否则是「完成进度「X」后解锁」
     *
     * @param lock {@link #lockedBy} 的结果
     */
    Component lockHint(EntryView ev, ResourceLocation lock) {
        String raw = lockedHint(ev);
        return GuideUnlocks.hint(lock, raw == null ? null : Component.literal(plain(raw)));
    }

    /** 首页格子的解锁提示（分类被自己的 advancement 锁着时调） */
    Component lockHint(CatView cv) {
        String raw = cv.category.lockedHint;
        return GuideUnlocks.hint(cv.category.advancement, raw == null ? null : Component.literal(plain(raw)));
    }

    // ==================== 视图节点 ====================

    /**
     * 一个可见分类
     */
    static final class CatView {
        final GuideView view;
        final Category category;
        final int index;
        final List<EntryView> entries = new ArrayList<>();
        /** 主线的章号（1 起）；不是主线为 0 */
        int chapter;
        private String title;
        private String shortTitle;
        private String prefix;
        private String subtitle;
        private ItemStack icon;

        CatView(GuideView view, Category category, int index) {
            this.view = view;
            this.category = category;
            this.index = index;
        }

        String id() {
            return category.id;
        }

        Group group() {
            return category.group;
        }

        String title() {
            if (view.isResolvingLink()) {
                // 见 isResolvingLink：链接取标题时现算、不碰缓存
                return view.plain(category.title);
            }
            if (title == null) {
                title = view.plain(category.title);
            }
            return title;
        }

        /**
         * 标题去掉「第一章 · 」「支线 · 」这类前缀后的部分（首页小格子、侧栏窄行用）；没有前缀时就是整个标题
         */
        String shortTitle() {
            if (shortTitle == null) {
                String t = title();
                int at = t.lastIndexOf(" · ");
                shortTitle = at > 0 && at + 3 < t.length() ? t.substring(at + 3) : t;
            }
            return shortTitle;
        }

        /**
         * 标题的前缀（「第一章」「Chapter 1」「支线」）；没有前缀时为空串
         */
        String prefix() {
            if (prefix == null) {
                String t = title();
                int at = t.lastIndexOf(" · ");
                prefix = at > 0 ? t.substring(0, at) : "";
            }
            return prefix;
        }

        String subtitle() {
            if (subtitle == null) {
                subtitle = view.plain(category.subtitle);
            }
            return subtitle;
        }

        ItemStack icon() {
            if (icon == null) {
                icon = view.icon(category.icon, view.bookIcon());
            }
            return icon;
        }
    }

    /**
     * 一个可见条目
     */
    static final class EntryView {
        final GuideView view;
        final CatView cat;
        final Entry entry;
        /** 分类id/条目id */
        final String key;
        /** 在分类里的序号 */
        final int index;
        /** 在全书里的序号 */
        final int globalIndex;
        /** 当前配置下是不是可选（{@code optional} 可以按条件可选，建视图时求一次；配置变了整个视图重建） */
        final boolean optional;
        /** 当前配置下是不是必做的一步：写了 complete、且不是可选的 */
        final boolean requiredStep;
        private String title;
        private String summary;
        private ItemStack icon;
        @Nullable
        private List<Block> blocks;
        @Nullable
        private List<ResourceLocation> items;

        EntryView(GuideView view, CatView cat, Entry entry, int index, int globalIndex) {
            this.view = view;
            this.cat = cat;
            this.entry = entry;
            this.key = cat.category.id + "/" + entry.id;
            this.index = index;
            this.globalIndex = globalIndex;
            this.optional = entry.optional.test(view.values::test);
            this.requiredStep = entry.isStep() && !optional;
        }

        String title() {
            if (view.isResolvingLink()) {
                // 见 isResolvingLink：链接取标题时现算、不碰缓存（标题一般很短，现算的开销可以忽略）
                return view.plain(entry.title);
            }
            if (title == null) {
                title = view.plain(entry.title);
            }
            return title;
        }

        String summary() {
            if (summary == null) {
                summary = view.plain(entry.summary);
            }
            return summary;
        }

        ItemStack icon() {
            if (icon == null) {
                icon = view.icon(entry.icon, cat.icon());
            }
            return icon;
        }

        /**
         * 当前配置下可见的块：块级 {@code if} 成立、行级条件过滤后没被掏空的。
         * 阅读页、查阅索引、搜索都用这一份，所以「块序号」在三处是同一个意思。
         */
        List<Block> blocks() {
            if (blocks == null) {
                List<Block> out = new ArrayList<>();
                for (Block b : entry.blocks) {
                    if (view.values.test(b.cond)) {
                        Block vb = b.visible(view.values::test);
                        if (!vb.isHollow()) {
                            out.add(vb);
                        }
                    }
                }
                blocks = Collections.unmodifiableList(out);
            }
            return blocks;
        }

        /**
         * 当前配置下条目 {@code items} 里算数的物品：条件组（{@link EntryItems}）不成立的不算。
         * 查阅索引和搜索的物品名都用这一份
         */
        List<ResourceLocation> items() {
            if (items == null) {
                items = entry.items.visible(view.values::test);
            }
            return items;
        }
    }
}
