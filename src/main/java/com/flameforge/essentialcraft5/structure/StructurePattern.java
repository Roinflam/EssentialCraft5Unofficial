package com.flameforge.essentialcraft5.structure;

import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 多方块结构的定义：以机器自己（原点）为准的相对偏移 → 规则。判定、手册画图、找附属方块的位置都用这一份数据。
 * <p>
 * 原作每台机器各写一串 {@code getBlockState(pos.add(…)).getBlock() == …}（米斯莱熔炉 24 行、太阳棱镜 12 格），
 * 研究书里的结构图又另画一份，两边常常对不上；这里写一次，判定和手册共用（手册按 id 引用，见 {@link StructurePatterns}）。
 *
 * <h2>坐标</h2>
 * 按机器<b>朝北</b>时写：x 向东、y 向上、z 向南（北 = z 负方向）。判定时给出机器的水平朝向，偏移自动旋转
 * （东 = 顺时针 90°，南 = 180°，西 = 逆时针 90°；朝上 / 朝下按北处理）。四面对称的结构随便传一个朝向（传 NORTH）。
 *
 * <h2>写法（字符网格，推荐）</h2>
 * 每层一个 {@code layer(...)}，<b>从下往上</b>依次写；每层里一行一个字符串，<b>从北往南</b>，行内从西往东；空格 = 不管。
 * {@code anchor} 指定机器自己所在的字符（全部层里恰好出现一次），它所在的层是 y = 0。所有层的行数、每行长度必须一样。
 * <pre>{@code
 * // 米斯莱熔炉（原作 TileMithrilineFurnace.isStructureCorrect + possiblePowerSources）
 * public static final StructurePattern.Rule PILLAR = StructurePattern.Rule.block(MITHRILINE_CRYSTAL_PILLAR,
 *         "structure.essentialcraft5.mithriline_furnace.pillar").optional();
 * public static final StructurePattern FURNACE_STRUCTURE = StructurePatterns.register(EssentialCraft5.id("mithriline_furnace"),
 *         StructurePattern.builder()
 *                 .where('P', StructurePattern.Rule.block(MITHRILINE_PLATING))
 *                 .where('C', PILLAR)
 *                 .where('F', StructurePattern.Rule.block(MITHRILINE_FURNACE))
 *                 .anchor('F')
 *                 .layer("  P  ", " PPP ", "PP PP", " PPP ", "  P  ")   // y = -1：12 块
 *                 .layer("P P P", " C C ", "P F P", " C C ", "P P P")   // y = 0：外圈 8 块，同层四斜角放水晶柱
 *                 .layer("P C P", "     ", "C C C", "     ", "P C P")   // y = 1：四角 4 块
 *                 .layer("C   C", "     ", "     ", "     ", "C   C")   // y = 2：外圈四角的水晶柱
 *                 .build());
 * }</pre>
 * 也可以直接按偏移写：{@code .at(x, y, z, rule)}（北向坐标，原点就是机器），能和 layer 混用。
 *
 * <h2>规则</h2>
 * 见 {@link Rule}：方块、几种方块之一、方块标签、必须是空气、任意非空气方块、自定义谓词；{@code .optional()} 的规则不参与判定
 * （只画进手册、只用来列位置，比如熔炉的 13 个水晶柱位置：{@code FURNACE_STRUCTURE.positions(PILLAR, worldPosition, Direction.NORTH)}）。
 * 每条规则都有画图用的代表方块和图例文字（翻译键；方块规则默认用方块名）。
 *
 * <h2>判定</h2>
 * 机器里用 {@link StructureCache}（带节流和「区块没加载就保留上次结果」）；要立即判一次用 {@link #match}。
 * 只读已经完全加载的区块（{@link ECLevels#isFullyLoaded}），绝不触发区块加载。
 * <p>
 * 定义在类加载时构造（静态字段），这时方块还没注册，所以规则里的方块一律传 Supplier（RegistryObject 本身就是），用到时才取。
 * 结构不对（字符没定义、原点不唯一、层大小不一）在 build() 时直接抛异常，模块一加载就会发现。
 */
public final class StructurePattern {

    /** 图例：必须是空气（判定用 {@link BlockState#isAir()}，空气、洞穴空气、虚空空气都算） */
    public static final String KEY_AIR = "structure.essentialcraft5.rule.air";
    /** 图例：任意方块（只要不是空气） */
    public static final String KEY_ANY_BLOCK = "structure.essentialcraft5.rule.any_block";
    /** 判定失败的提示：「%2$s, %3$s, %4$s 处应为%1$s」 */
    public static final String KEY_MISMATCH = "structure.essentialcraft5.mismatch";

    private static final Comparator<BlockPos> ORDER = Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX);

    /** 北向偏移，按 y、z、x 排序（「第一个不符」就是最下层、最北、最西的那格，和玩家从下往上搭的顺序一致） */
    private final BlockPos[] offsets;
    private final Rule[] rules;
    /** 四个 Rotation（按 ordinal）下旋转好的偏移 */
    private final BlockPos[][] rotated = new BlockPos[Rotation.values().length][];
    private final Map<BlockPos, Rule> ruleMap;
    private final List<Layer> layers;
    private final int requiredCount;

    private StructurePattern(Map<BlockPos, Rule> entries) {
        List<Map.Entry<BlockPos, Rule>> sorted = new ArrayList<>(entries.entrySet());
        sorted.sort(Map.Entry.comparingByKey(ORDER));
        offsets = new BlockPos[sorted.size()];
        rules = new Rule[sorted.size()];
        Map<BlockPos, Rule> map = new LinkedHashMap<>();
        int required = 0;
        for (int i = 0; i < sorted.size(); i++) {
            offsets[i] = sorted.get(i).getKey();
            rules[i] = sorted.get(i).getValue();
            map.put(offsets[i], rules[i]);
            if (!rules[i].isOptional()) {
                required++;
            }
        }
        for (Rotation rotation : Rotation.values()) {
            BlockPos[] out = new BlockPos[offsets.length];
            for (int i = 0; i < offsets.length; i++) {
                out[i] = offsets[i].rotate(rotation);
            }
            rotated[rotation.ordinal()] = out;
        }
        ruleMap = Collections.unmodifiableMap(map);
        requiredCount = required;
        layers = buildLayers();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 机器朝向对应的旋转：北不转、东顺时针 90°、南 180°、西逆时针 90°；朝上 / 朝下按北 */
    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    // ================================================================ 查询

    /** 全部规则：北向偏移 → 规则，按 y、z、x 排好序，只读 */
    public Map<BlockPos, Rule> rules() {
        return ruleMap;
    }

    /** 规则总数（含可选） */
    public int size() {
        return rules.length;
    }

    /** 参与判定的规则数（不含 optional） */
    public int requiredCount() {
        return requiredCount;
    }

    /** 偏移换成世界坐标 */
    public static BlockPos toWorld(BlockPos origin, BlockPos offset, Direction facing) {
        return origin.offset(offset.rotate(rotation(facing)));
    }

    /** 用了这条规则（按对象判断，同一个 Rule 实例）的北向偏移，按 y、z、x 排序 */
    public List<BlockPos> offsets(Rule rule) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < rules.length; i++) {
            if (rules[i] == rule) {
                out.add(offsets[i]);
            }
        }
        return out;
    }

    /**
     * 用了这条规则的格子在世界里的位置（已按朝向旋转）。例：熔炉找 13 个水晶柱位置，
     * 然后逐个 {@code ECLevels.loadedBlockEntity(level, pos)} 取方块实体（别用 level.getBlockEntity，会触发加载）。
     */
    public List<BlockPos> positions(Rule rule, BlockPos origin, Direction facing) {
        BlockPos[] offs = rotated[rotation(facing).ordinal()];
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < rules.length; i++) {
            if (rules[i] == rule) {
                out.add(origin.offset(offs[i]));
            }
        }
        return out;
    }

    /** 按 y 分层（从下到上），给手册画图。所有层网格一样大（覆盖全部规则和原点），中间没有规则的层也列出来（isEmpty） */
    public List<Layer> layers() {
        return layers;
    }

    /**
     * 立即判定一次（不缓存；机器里请用 {@link StructureCache}）。
     * <ul>
     *   <li>已加载的格子里有一格不符 → MISMATCH，附第一个不符的格子（按 y、z、x 的顺序）；</li>
     *   <li>否则有格子落在没完全加载的区块里 → UNKNOWN；</li>
     *   <li>否则 → MATCH。</li>
     * </ul>
     * 超出世界高度的格子按虚空空气算（和 Level#getBlockState 一样），不算「没加载」。optional 规则不参与。
     */
    public Match match(Level level, BlockPos origin, Direction facing) {
        BlockPos[] offs = rotated[rotation(facing).ordinal()];
        boolean unknown = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < rules.length; i++) {
            Rule rule = rules[i];
            if (rule.isOptional()) {
                continue;
            }
            pos.setWithOffset(origin, offs[i]);
            BlockState state;
            if (level.isOutsideBuildHeight(pos)) {
                state = Blocks.VOID_AIR.defaultBlockState();
            } else if (!ECLevels.isFullyLoaded(level, pos)) {
                // 先别下结论：后面已加载的格子里若有确定不符的，结果仍是 MISMATCH
                unknown = true;
                continue;
            } else {
                state = level.getBlockState(pos);
            }
            if (!rule.test(level, pos, state)) {
                return new Match(StructureResult.MISMATCH, new Mismatch(pos.immutable(), offsets[i], rule, state));
            }
        }
        return unknown ? Match.UNKNOWN : Match.MATCHED;
    }

    private List<Layer> buildLayers() {
        // 网格包含原点（机器自己），即使原点没写规则，手册也要画出机器在哪
        int minX = 0;
        int maxX = 0;
        int minY = 0;
        int maxY = 0;
        int minZ = 0;
        int maxZ = 0;
        for (BlockPos offset : offsets) {
            minX = Math.min(minX, offset.getX());
            maxX = Math.max(maxX, offset.getX());
            minY = Math.min(minY, offset.getY());
            maxY = Math.max(maxY, offset.getY());
            minZ = Math.min(minZ, offset.getZ());
            maxZ = Math.max(maxZ, offset.getZ());
        }
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        List<Layer> out = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            Rule[] cells = new Rule[width * depth];
            for (int i = 0; i < offsets.length; i++) {
                if (offsets[i].getY() == y) {
                    cells[(offsets[i].getZ() - minZ) * width + (offsets[i].getX() - minX)] = rules[i];
                }
            }
            out.add(new Layer(y, minX, minZ, width, depth, Collections.unmodifiableList(Arrays.asList(cells))));
        }
        return List.copyOf(out);
    }

    // ================================================================ 结果

    /**
     * 一次判定的结果。
     *
     * @param mismatch 结果是 MISMATCH 时第一个不符的格子，否则为 null
     */
    public record Match(StructureResult result, @Nullable Mismatch mismatch) {

        static final Match MATCHED = new Match(StructureResult.MATCH, null);
        static final Match UNKNOWN = new Match(StructureResult.UNKNOWN, null);

        public boolean matches() {
            return result == StructureResult.MATCH;
        }
    }

    /**
     * 第一个不符的格子（给界面提示：「x, y, z 处应为米斯莱护板」）。
     *
     * @param pos    世界坐标
     * @param offset 北向偏移（结构定义里的坐标）
     * @param rule   这一格的要求
     * @param found  实际的方块
     */
    public record Mismatch(BlockPos pos, BlockPos offset, Rule rule, BlockState found) {

        /** 「x, y, z 处应为<图例>」（键 structure.essentialcraft5.mismatch） */
        public Component message() {
            return Component.translatable(KEY_MISMATCH, rule.legend(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /**
     * 一层（手册画图用）。网格按北向坐标：行 = z（从北往南），列 = x（从西往东）；没有要求的格子为 null。
     *
     * @param y     这一层相对机器的高度（机器所在层为 0）
     * @param minX  第 0 列对应的 x 偏移
     * @param minZ  第 0 行对应的 z 偏移
     * @param width 列数
     * @param depth 行数
     * @param cells 行优先，长度 width × depth，元素可为 null
     */
    public record Layer(int y, int minX, int minZ, int width, int depth, List<Rule> cells) {

        @Nullable
        public Rule at(int column, int row) {
            return cells.get(row * width + column);
        }

        /** 机器（原点）在这一层网格里的列、行；原点列 = -minX、原点行 = -minZ（每层都一样，用来在上下层标出机器那一竖列） */
        public int anchorColumn() {
            return -minX;
        }

        public int anchorRow() {
            return -minZ;
        }

        /** 这一层用到的规则（去重，按从北到南、从西到东第一次出现的顺序），给图例用 */
        public List<Rule> distinctRules() {
            Set<Rule> seen = new LinkedHashSet<>();
            for (Rule rule : cells) {
                if (rule != null) {
                    seen.add(rule);
                }
            }
            return List.copyOf(seen);
        }

        public boolean isEmpty() {
            for (Rule rule : cells) {
                if (rule != null) {
                    return false;
                }
            }
            return true;
        }
    }

    // ================================================================ 规则

    /** 自定义谓词：这一格的方块是否满足要求。只该看传进来的 state（要看别的格子自己保证区块已加载） */
    @FunctionalInterface
    public interface Test {

        boolean test(BlockGetter level, BlockPos pos, BlockState state);
    }

    /**
     * 一格的要求。用工厂方法创建，不可变；{@link #optional()}、{@link #legend(String)} 返回新对象。
     * <p>
     * 同一个 Rule 实例可以用在很多格上；{@link StructurePattern#positions} 按实例找格子，所以要列位置的规则存成静态字段复用。
     */
    public static final class Rule {

        private enum Kind {
            BLOCK, TAG, AIR, ANY_BLOCK, PREDICATE
        }

        private final Kind kind;
        private final List<Supplier<? extends Block>> blocks;
        @Nullable
        private final TagKey<Block> tag;
        @Nullable
        private final Test test;
        @Nullable
        private final Supplier<BlockState> display;
        @Nullable
        private final String legendKey;
        private final boolean optional;

        private Rule(Kind kind, List<Supplier<? extends Block>> blocks, @Nullable TagKey<Block> tag, @Nullable Test test,
                     @Nullable Supplier<BlockState> display, @Nullable String legendKey, boolean optional) {
            this.kind = kind;
            this.blocks = blocks;
            this.tag = tag;
            this.test = test;
            this.display = display;
            this.legendKey = legendKey;
            this.optional = optional;
        }

        /** 必须是这个方块（任何状态）。图例 = 方块名 */
        public static Rule block(Supplier<? extends Block> block) {
            return new Rule(Kind.BLOCK, List.of(Objects.requireNonNull(block)), null, null, null, null, false);
        }

        /** 必须是这个方块，图例用给定的翻译键（比如「米斯莱护板（可以染色）」） */
        public static Rule block(Supplier<? extends Block> block, String legendKey) {
            return new Rule(Kind.BLOCK, List.of(Objects.requireNonNull(block)), null, null, null, Objects.requireNonNull(legendKey), false);
        }

        /** 几种方块之一（手册里轮换显示）；画图的代表方块是第一个 */
        @SafeVarargs
        public static Rule anyOf(String legendKey, Supplier<? extends Block>... blocks) {
            if (blocks.length == 0) {
                throw new IllegalArgumentException("anyOf 至少要一个方块");
            }
            return new Rule(Kind.BLOCK, List.of(blocks), null, null, null, Objects.requireNonNull(legendKey), false);
        }

        /**
         * 方块标签里的任意方块。标签成员由服务端同步，手册里按成员轮换显示；标签还没同步（主菜单）时画代表方块。
         *
         * @param display 代表方块状态（比如 {@code () -> Blocks.OAK_LOG.defaultBlockState()}）
         */
        public static Rule tag(TagKey<Block> tag, Supplier<BlockState> display, String legendKey) {
            return new Rule(Kind.TAG, List.of(), Objects.requireNonNull(tag), null, Objects.requireNonNull(display), Objects.requireNonNull(legendKey), false);
        }

        /** 必须是空气（原作 isAirBlock；太阳棱镜紧挨着的 8 格）。图例「空气」 */
        public static Rule air() {
            return new Rule(Kind.AIR, List.of(), null, null, null, null, false);
        }

        /** 任意方块，只要不是空气（太阳棱镜四个方向隔一格的方块）。图例「任意方块」，手册里画石头 */
        public static Rule anyBlock() {
            return new Rule(Kind.ANY_BLOCK, List.of(), null, null, null, null, false);
        }

        /**
         * 自定义谓词（方块状态的某个属性、方块实体……）。
         *
         * @param display   手册里画的代表方块状态
         * @param legendKey 图例文字的翻译键
         */
        public static Rule predicate(Test test, Supplier<BlockState> display, String legendKey) {
            return new Rule(Kind.PREDICATE, List.of(), null, Objects.requireNonNull(test), Objects.requireNonNull(display), Objects.requireNonNull(legendKey), false);
        }

        /** 同样的规则，但不参与判定：只画进手册（半透明、提示「可选」），以及给 positions 列位置 */
        public Rule optional() {
            return new Rule(kind, blocks, tag, test, display, legendKey, true);
        }

        /** 同样的规则，换一个图例翻译键 */
        public Rule legend(String legendKey) {
            return new Rule(kind, blocks, tag, test, display, Objects.requireNonNull(legendKey), optional);
        }

        public boolean test(BlockGetter level, BlockPos pos, BlockState state) {
            return switch (kind) {
                case BLOCK -> {
                    for (Supplier<? extends Block> block : blocks) {
                        if (state.is(block.get())) {
                            yield true;
                        }
                    }
                    yield false;
                }
                case TAG -> state.is(Objects.requireNonNull(tag));
                case AIR -> state.isAir();
                case ANY_BLOCK -> !state.isAir();
                case PREDICATE -> Objects.requireNonNull(test).test(level, pos, state);
            };
        }

        public boolean isOptional() {
            return optional;
        }

        /** 「必须是空气」：手册里画虚线框 */
        public boolean isAir() {
            return kind == Kind.AIR;
        }

        /** 图例就是方块自己的名字（单个方块、没指定图例键）；手册悬停时就不再重复写一遍 */
        public boolean usesBlockName() {
            return kind == Kind.BLOCK && legendKey == null;
        }

        /** 标签规则的标签，别的规则为 null */
        @Nullable
        public TagKey<Block> tag() {
            return tag;
        }

        /** 图例文字的翻译键；没指定时为 null（方块规则用方块名、空气 / 任意方块用各自的默认键） */
        @Nullable
        public String legendKey() {
            return legendKey;
        }

        /** 画图用的代表方块状态（空气规则是空气） */
        public BlockState displayState() {
            return switch (kind) {
                case BLOCK -> blocks.get(0).get().defaultBlockState();
                case AIR -> Blocks.AIR.defaultBlockState();
                case ANY_BLOCK -> display != null ? display.get() : Blocks.STONE.defaultBlockState();
                case TAG, PREDICATE -> Objects.requireNonNull(display).get();
            };
        }

        /**
         * 手册里轮换显示的方块：几种方块之一 → 全部；标签 → 标签成员（还没同步时退回代表方块）；空气 → 空表；其余 → 代表方块。
         */
        public List<Block> displayBlocks() {
            switch (kind) {
                case AIR -> {
                    return List.of();
                }
                case BLOCK -> {
                    List<Block> out = new ArrayList<>();
                    for (Supplier<? extends Block> block : blocks) {
                        out.add(block.get());
                    }
                    return out;
                }
                case TAG -> {
                    List<Block> out = new ArrayList<>();
                    for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(Objects.requireNonNull(tag))) {
                        out.add(holder.value());
                    }
                    return out.isEmpty() ? List.of(displayState().getBlock()) : out;
                }
                default -> {
                    return List.of(displayState().getBlock());
                }
            }
        }

        /** 图例文字：指定了键用键；否则方块规则 = 方块名，空气 =「空气」，任意方块 =「任意方块」 */
        public Component legend() {
            if (legendKey != null) {
                return Component.translatable(legendKey);
            }
            return switch (kind) {
                case AIR -> Component.translatable(KEY_AIR);
                case ANY_BLOCK -> Component.translatable(KEY_ANY_BLOCK);
                default -> displayState().getBlock().getName();
            };
        }

        @Override
        public String toString() {
            return "Rule[" + kind + (legendKey != null ? " " + legendKey : "") + (optional ? " optional" : "") + "]";
        }
    }

    // ================================================================ 构造

    /** 见类注释的写法。字符网格和 at() 可以混用，同一格不能写两次 */
    public static final class Builder {

        private final Map<Character, Rule> symbols = new LinkedHashMap<>();
        private final List<String[]> layers = new ArrayList<>();
        private final Map<BlockPos, Rule> explicit = new LinkedHashMap<>();
        @Nullable
        private Character anchor;

        private Builder() {
        }

        /** 字符 → 规则。空格保留给「不管」 */
        public Builder where(char symbol, Rule rule) {
            if (symbol == ' ') {
                throw new IllegalArgumentException("空格表示「不管」，不能定义成规则");
            }
            if (symbols.put(symbol, Objects.requireNonNull(rule)) != null) {
                throw new IllegalArgumentException("字符「" + symbol + "」定义了两次");
            }
            return this;
        }

        /** 一层，从下往上依次调用；每个字符串一行，从北往南，行内从西往东 */
        public Builder layer(String... rows) {
            if (rows.length == 0) {
                throw new IllegalArgumentException("一层至少要一行");
            }
            layers.add(rows.clone());
            return this;
        }

        /** 机器自己所在的字符（原点）。可以同时用 where 给它定义规则（那样原点也参与判定），也可以不定义 */
        public Builder anchor(char symbol) {
            if (symbol == ' ') {
                throw new IllegalArgumentException("原点字符不能是空格");
            }
            anchor = symbol;
            return this;
        }

        /** 直接按北向偏移写一格（原点 = 机器） */
        public Builder at(int x, int y, int z, Rule rule) {
            if (explicit.put(new BlockPos(x, y, z), Objects.requireNonNull(rule)) != null) {
                throw new IllegalArgumentException("偏移 " + x + ", " + y + ", " + z + " 写了两次");
            }
            return this;
        }

        public StructurePattern build() {
            Map<BlockPos, Rule> entries = new LinkedHashMap<>();
            if (!layers.isEmpty()) {
                addLayers(entries);
            }
            for (Map.Entry<BlockPos, Rule> entry : explicit.entrySet()) {
                if (entries.put(entry.getKey(), entry.getValue()) != null) {
                    throw new IllegalStateException("偏移 " + entry.getKey().toShortString() + " 在字符网格和 at() 里都写了");
                }
            }
            if (entries.isEmpty()) {
                throw new IllegalStateException("结构里一格规则都没有");
            }
            return new StructurePattern(entries);
        }

        private void addLayers(Map<BlockPos, Rule> entries) {
            if (anchor == null) {
                throw new IllegalStateException("用了 layer() 就要用 anchor() 指定机器所在的字符");
            }
            int rows = layers.get(0).length;
            int columns = layers.get(0)[0].length();
            int anchorLayer = -1;
            int anchorRow = -1;
            int anchorColumn = -1;
            for (int l = 0; l < layers.size(); l++) {
                String[] layer = layers.get(l);
                if (layer.length != rows) {
                    throw new IllegalStateException("第 " + (l + 1) + " 层有 " + layer.length + " 行，第 1 层有 " + rows + " 行（各层必须一样大，否则上下对不齐）");
                }
                for (int r = 0; r < rows; r++) {
                    if (layer[r].length() != columns) {
                        throw new IllegalStateException("第 " + (l + 1) + " 层第 " + (r + 1) + " 行长 " + layer[r].length() + "，应为 " + columns + "（空位用空格补齐）");
                    }
                    for (int c = 0; c < columns; c++) {
                        char ch = layer[r].charAt(c);
                        if (ch == anchor) {
                            if (anchorLayer >= 0) {
                                throw new IllegalStateException("原点字符「" + ch + "」出现了不止一次");
                            }
                            anchorLayer = l;
                            anchorRow = r;
                            anchorColumn = c;
                        } else if (ch != ' ' && !symbols.containsKey(ch)) {
                            throw new IllegalStateException("第 " + (l + 1) + " 层的字符「" + ch + "」没有用 where 定义");
                        }
                    }
                }
            }
            if (anchorLayer < 0) {
                throw new IllegalStateException("原点字符「" + anchor + "」在各层里都没有出现");
            }
            for (int l = 0; l < layers.size(); l++) {
                String[] layer = layers.get(l);
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < columns; c++) {
                        char ch = layer[r].charAt(c);
                        Rule rule = ch == ' ' ? null : symbols.get(ch);
                        if (rule != null) {
                            entries.put(new BlockPos(c - anchorColumn, l - anchorLayer, r - anchorRow), rule);
                        }
                    }
                }
            }
        }
    }
}
