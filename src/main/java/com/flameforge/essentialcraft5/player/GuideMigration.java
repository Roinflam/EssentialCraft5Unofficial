package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 研究手册的老存档迁移：主线加了新的必做步骤以后，已经走过那一段的老玩家登录时补上这些步骤的勾
 *
 * <h3>规则（batch10.md §2.6）</h3>
 * <ol>
 *   <li>旧进度的条件一个不改；改了完成条件的步骤换新的进度 id，旧的留着。</li>
 *   <li>新加的必做步骤靠登录时补勾：玩家已经有任一个排在它后面的「证明进度」，就把这一步的进度补全。
 *       <b>只补勾，不给物品</b>，也不改别的玩家数据（表里写了 {@code then} 的除外，见下）。</li>
 *   <li>整章新加的内容不当成「老存档没做完」，不进迁移表，已经毕业的人照常从首页看到它们。</li>
 * </ol>
 *
 * <h3>迁移表</h3>
 * <p>数据包里的 {@code data/essentialcraft5/guide_migration/v<N>.json}，一个版本一张：</p>
 * <pre>
 * [ {"grant": "essentialcraft5:guide/alloys/pale",
 *    "if_any": ["essentialcraft5:guide/enrichment/controller", …],
 *    "if_check": [检查名…],     // 可选
 *    "then": [动作名…]}, … ]    // 可选
 * </pre>
 * <p>{@code if_any} 里任一个进度完成了、或 {@code if_check} 里任一项检查成立，就补全 {@code grant} 的所有条件，
 * 补上之后依次做 {@code then} 里的动作。两样都没写的条目不认（报一次日志）—— 新玩家第一次登录也会跑一遍迁移，
 * 没条件的条目会把勾白送给他。</p>
 * <p>手册在客户端资源里、服务端读不到，所以表由 {@code tools/guide/gen_migration.py} 从 zh_cn 手册生成，生成结果进仓库
 * （用法见脚本开头）。版本号 {@link #CURRENT_VERSION} 加一时同时出一张新表。</p>
 *
 * <h3>什么时候跑</h3>
 * <p>登录时（排在 {@code PlayerDataEvents.login} 之后，玩家数据、风之好感都已读好）。玩家迁移到的版本存在
 * {@link Player#PERSISTED_NBT_TAG} 下的 {@link #TAG_VERSION}（死亡重生、从末地回来都跟着走），低于
 * {@link #CURRENT_VERSION} 时按版本顺序一张张跑，跑完写下版本号，以后不再跑。多人服里不在线的玩家下次登录时迁移。</p>
 * <ul>
 *   <li>某个版本的表读不到（数据包删了它）：停在它前一个版本，不往后跳 —— 下次登录再看，表补回来了照样补勾。</li>
 *   <li>表里的进度文件不存在（数据包删了某个步骤）：跳过那一条；证明进度不存在时当它没完成。</li>
 *   <li>已经完成的步骤不动（也不做它的 {@code then}）。</li>
 * </ul>
 *
 * <h3>检查与动作</h3>
 * <p>「证明进度」之外的补勾条件和补勾后要做的事按名字登记（{@link #registerCheck}、{@link #registerAction}），
 * 由拥有那份数据的模块登记，迁移本身不碰别人的数据结构。名字没登记时只报一次日志：检查按不成立，动作跳过。
 * v1 用到一个动作：补上风之真名（{@code guide/hoanna/true_name}）以后把心愿数记满（{@code WindWishes.LEGACY_ACTION}，ECWind 登记）。
 * 好感已到门槛的老玩家，心愿数由 {@code WindWishes.migrateLegacy}（登录时 NORMAL，排在这里之前）记满；风之真名的勾在这里之后
 * （LOWEST）才由代码补，免得登录这一刻刚发的勾被当成证明进度。</p>
 */
public final class GuideMigration extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "guide_migration";
    /** 现在的迁移版本：10b 出 v1（主线加长的 15 步），10c 出 v2（3 档五芒星只补给已经做出宅男套装的人），13a 出 v3（矩阵阶、凝结矩阵、契约之誉下限）；加了新的迁移表就加一 */
    public static final int CURRENT_VERSION = 3;
    /** {@link Player#PERSISTED_NBT_TAG} 里记「迁移到第几版」的键 */
    public static final String TAG_VERSION = EssentialCraft5.MODID + ":guide_migration";

    /** 迁移表的文件名：v1、v2…… */
    private static final Pattern FILE = Pattern.compile("v([1-9][0-9]{0,3})");
    private static final Map<String, Predicate<ServerPlayer>> CHECKS = new ConcurrentHashMap<>();
    private static final Map<String, Consumer<ServerPlayer>> ACTIONS = new ConcurrentHashMap<>();
    /** 同一句警告只报一次（没登记的名字、读不到的表） */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    /** 要写在 {@link #INSTANCE} 前面：静态字段按书写顺序初始化，构造器里要用到它 */
    private static final Gson GSON = new GsonBuilder().create();
    public static final GuideMigration INSTANCE = new GuideMigration();

    private static volatile Map<Integer, List<Step>> tables = Map.of();

    private GuideMigration() {
        super(GSON, DIRECTORY);
    }

    /**
     * 迁移表里的一条
     *
     * @param grant   要补勾的进度
     * @param ifAny   证明进度：任一个完成了就补
     * @param ifCheck 检查名：任一项成立也补
     * @param then    补上以后要做的动作名
     */
    public record Step(ResourceLocation grant, List<ResourceLocation> ifAny, List<String> ifCheck, List<String> then) {
        public Step {
            ifAny = List.copyOf(ifAny);
            ifCheck = List.copyOf(ifCheck);
            then = List.copyOf(then);
        }

        /** 只有证明进度的一条（测试、脚本生成的大多数条目） */
        public static Step proven(ResourceLocation grant, List<ResourceLocation> ifAny) {
            return new Step(grant, ifAny, List.of(), List.of());
        }
    }

    // ------------------------------------------------------------------
    // 读表
    // ------------------------------------------------------------------

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Integer, List<Step>> out = new TreeMap<>();
        int steps = 0;
        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            ResourceLocation id = file.getKey();
            // 版本号是本模组的：别的命名空间里同名目录不认（免得别的数据包的 v1 冒充这边的版本）
            Matcher m = FILE.matcher(id.getPath());
            if (!id.getNamespace().equals(EssentialCraft5.MODID) || !m.matches()) {
                EssentialCraft5.LOGGER.warn("[手册迁移] {}：只认 data/{}/{}/v<数字>.json，跳过", id, EssentialCraft5.MODID, DIRECTORY);
                continue;
            }
            List<Step> table = parse(file.getValue(), problem -> EssentialCraft5.LOGGER.warn("[手册迁移] {}：{}", id, problem));
            out.put(Integer.parseInt(m.group(1)), table);
            steps += table.size();
        }
        tables = Collections.unmodifiableMap(out);
        WARNED.clear();
        EssentialCraft5.LOGGER.debug("[手册迁移] 读到 {} 张迁移表、{} 条", out.size(), steps);
    }

    /**
     * 解析一张迁移表（纯函数，测试直接调）。坏条目只跳过它自己，问题交给 {@code warn}
     *
     * @param json 文件内容：条目数组
     * @param warn 有问题时调用
     * @return 条目（按文件顺序）
     */
    public static List<Step> parse(@Nullable JsonElement json, @Nonnull Consumer<String> warn) {
        if (json == null || !json.isJsonArray()) {
            warn.accept("文件必须是条目数组，整张跳过");
            return List.of();
        }
        List<Step> out = new ArrayList<>();
        JsonArray array = json.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String at = "第 " + (i + 1) + " 条";
            JsonElement el = array.get(i);
            if (!el.isJsonObject()) {
                warn.accept(at + "不是对象，跳过");
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            ResourceLocation grant = location(o.get("grant"));
            if (grant == null) {
                warn.accept(at + "的 grant 缺失或不是进度 id，跳过");
                continue;
            }
            List<ResourceLocation> ifAny = new ArrayList<>();
            for (String s : strings(o.get("if_any"), at + "的 if_any", warn)) {
                ResourceLocation rl = ResourceLocation.tryParse(s);
                if (rl == null) {
                    warn.accept(at + "的 if_any 里「" + s + "」不是进度 id，忽略");
                } else {
                    ifAny.add(rl);
                }
            }
            List<String> ifCheck = strings(o.get("if_check"), at + "的 if_check", warn);
            List<String> then = strings(o.get("then"), at + "的 then", warn);
            if (ifAny.isEmpty() && ifCheck.isEmpty()) {
                warn.accept(at + "（" + grant + "）没有 if_any 也没有 if_check，跳过（不然新玩家也会被补勾）");
                continue;
            }
            out.add(new Step(grant, ifAny, ifCheck, then));
        }
        return Collections.unmodifiableList(out);
    }

    @Nullable
    private static ResourceLocation location(@Nullable JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? ResourceLocation.tryParse(el.getAsString()) : null;
    }

    private static List<String> strings(@Nullable JsonElement el, String where, Consumer<String> warn) {
        List<String> out = new ArrayList<>();
        if (el == null || el.isJsonNull()) {
            return out;
        }
        if (!el.isJsonArray()) {
            warn.accept(where + "要写成字符串数组，忽略");
            return out;
        }
        for (JsonElement x : el.getAsJsonArray()) {
            if (x.isJsonPrimitive() && x.getAsJsonPrimitive().isString() && !x.getAsString().isBlank()) {
                out.add(x.getAsString().trim());
            } else {
                warn.accept(where + "里有一项不是非空字符串，忽略");
            }
        }
        return out;
    }

    /** @return 当前读到的迁移表：版本号 → 条目（只读；GameTest 核对随模组发的表） */
    public static Map<Integer, List<Step>> tables() {
        return tables;
    }

    // ------------------------------------------------------------------
    // 检查与动作
    // ------------------------------------------------------------------

    /**
     * 登记一项补勾条件。同名再登记会替换
     *
     * @param name  名字（带命名空间，例 {@code essentialcraft5:some_check}）
     * @param check 成立时补勾；只在服务端主线程登录时调用
     */
    public static void registerCheck(@Nonnull String name, @Nonnull Predicate<ServerPlayer> check) {
        CHECKS.put(name, check);
    }

    /**
     * 登记一个补勾后的动作。同名再登记会替换
     *
     * @param name   名字（带命名空间）
     * @param action 补上那一步之后调用；只在服务端主线程登录时调用
     */
    public static void registerAction(@Nonnull String name, @Nonnull Consumer<ServerPlayer> action) {
        ACTIONS.put(name, action);
    }

    public static boolean hasCheck(String name) {
        return CHECKS.containsKey(name);
    }

    public static boolean hasAction(String name) {
        return ACTIONS.containsKey(name);
    }

    // ------------------------------------------------------------------
    // 迁移
    // ------------------------------------------------------------------

    /** @return 这名玩家迁移到的版本（没迁移过为 0） */
    public static int version(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(TAG_VERSION);
    }

    /** 写下迁移到的版本（测试、排查用；正常只由 {@link #migrate} 写） */
    public static void setVersion(Player player, int version) {
        CompoundTag data = player.getPersistentData();
        // getCompound 在没有这个子标签时返回一个新的空标签，没挂在 data 上，改完要 put 回去
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putInt(TAG_VERSION, version);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /**
     * 按读到的迁移表把这名玩家迁到 {@link #CURRENT_VERSION}
     *
     * @return 补了几个步骤
     */
    public static int migrate(ServerPlayer player) {
        return migrate(player, tables, CURRENT_VERSION);
    }

    /**
     * 把玩家从现在的版本迁到 {@code target}（测试直接传表）
     *
     * @param player 玩家
     * @param tables 版本号 → 条目
     * @param target 目标版本
     * @return 补了几个步骤
     */
    public static int migrate(ServerPlayer player, Map<Integer, List<Step>> tables, int target) {
        int from = version(player);
        if (from >= target) {
            return 0;
        }
        int reached = from;
        int granted = 0;
        for (int v = from + 1; v <= target; v++) {
            List<Step> table = tables.get(v);
            if (table == null) {
                warnOnce("table:" + v, "没有第 " + v + " 版迁移表（data/" + EssentialCraft5.MODID + "/" + DIRECTORY + "/v" + v
                        + ".json），停在第 " + reached + " 版，表补回来以后下次登录接着迁");
                break;
            }
            granted += applyTable(player, table);
            reached = v;
        }
        if (reached != from) {
            setVersion(player, reached);
        }
        if (granted > 0) {
            EssentialCraft5.LOGGER.info("[手册迁移] {} 迁到第 {} 版：补了 {} 个手册步骤的勾", player.getGameProfile().getName(), reached, granted);
        }
        return granted;
    }

    private static int applyTable(ServerPlayer player, List<Step> table) {
        ServerAdvancementManager manager = player.server.getAdvancements();
        PlayerAdvancements advancements = player.getAdvancements();
        int granted = 0;
        for (Step step : table) {
            Advancement target = manager.getAdvancement(step.grant());
            if (target == null) {
                // 进度文件被数据包删掉了：这一步在这个世界里不存在，跳过
                continue;
            }
            AdvancementProgress progress = advancements.getOrStartProgress(target);
            if (progress.isDone() || !proven(player, manager, advancements, step)) {
                continue;
            }
            List<String> remaining = new ArrayList<>();
            progress.getRemainingCriteria().forEach(remaining::add);
            for (String criterion : remaining) {
                advancements.award(target, criterion);
            }
            granted++;
            for (String name : step.then()) {
                Consumer<ServerPlayer> action = ACTIONS.get(name);
                if (action == null) {
                    warnOnce("action:" + name, "迁移动作「" + name + "」没有登记，跳过（" + step.grant() + "）");
                    continue;
                }
                try {
                    action.accept(player);
                } catch (RuntimeException e) {
                    EssentialCraft5.LOGGER.error("[手册迁移] 动作「{}」出错（{}），跳过", name, step.grant(), e);
                }
            }
        }
        return granted;
    }

    private static boolean proven(ServerPlayer player, ServerAdvancementManager manager, PlayerAdvancements advancements, Step step) {
        for (ResourceLocation id : step.ifAny()) {
            Advancement proof = manager.getAdvancement(id);
            if (proof != null && advancements.getOrStartProgress(proof).isDone()) {
                return true;
            }
        }
        for (String name : step.ifCheck()) {
            Predicate<ServerPlayer> check = CHECKS.get(name);
            if (check == null) {
                warnOnce("check:" + name, "迁移检查「" + name + "」没有登记，按不成立处理（" + step.grant() + "）");
                continue;
            }
            try {
                if (check.test(player)) {
                    return true;
                }
            } catch (RuntimeException e) {
                EssentialCraft5.LOGGER.error("[手册迁移] 检查「{}」出错（{}），按不成立处理", name, step.grant(), e);
            }
        }
        return false;
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            EssentialCraft5.LOGGER.warn("[手册迁移] {}", message);
        }
    }

    /** 数据包重载时登记读取器；登录时迁移（Forge 总线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class GuideMigrationEvents {

        private GuideMigrationEvents() {
        }

        @SubscribeEvent
        public static void guideMigrationReload(AddReloadListenerEvent event) {
            event.addListener(INSTANCE);
        }

        /**
         * LOW：排在 PlayerDataEvents.login（NORMAL）之后，玩家数据已经读好、同步过；排在 WindWishes 登录时补风之真名（LOWEST）之前，
         * 那一刻代码发的勾不会被当成证明进度
         */
        @SubscribeEvent(priority = EventPriority.LOW)
        public static void guideMigrationLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                migrate(player);
            }
        }
    }
}
