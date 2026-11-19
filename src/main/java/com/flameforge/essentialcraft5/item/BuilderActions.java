package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端处理 {@link C2SBuilderActionPacket}：每人每 {@value #WINDOW_TICKS} 刻最多 {@value #MAX_PER_WINDOW} 个，超出的丢掉；
 * 手里那只手不是创世神之杖的不理。改设置的操作（尺寸、形状、模式、模块开关、混铺表、丢弃清单、选定方块、拆模块）施工中（做、暂停、排队、测量）一律不收，
 * 动作栏说一句「先停下才能改」；停下、撤销、要清单不受这条限制。面板的几个操作见 {@link #setShape}、{@link #setMode}、{@link #pickBlock}，清单见 {@link BuilderPanel}。拆模块交给 {@link BuilderModules#removeFromHand}（施工中不拆）；设混铺表、丢弃清单见 {@link #setMix}、{@link #setDiscard}（施工中不改）；远距离开工交给 {@link BuilderAim#start}（复核视线、照旧的开工间隔和确认）。尺寸按形状夹到这根魔杖档位的边长 / 格数（{@link BuilderTier#stats}）后写回手里那个物品对象的 NBT（不换对象，正在做的任务不受影响）。
 * 另外记着每人报上来的确认阈值（客户端配置 confirm_threshold；没报过按 {@value BuilderJobs#DEFAULT_CONFIRM_THRESHOLD}）。只在服务端线程上用。
 */
public final class BuilderActions {

    public static final int WINDOW_TICKS = 20;
    public static final int MAX_PER_WINDOW = 10;

    private static final Map<UUID, int[]> RATE = new HashMap<>();
    private static final Map<UUID, Integer> CONFIRM_THRESHOLD = new HashMap<>();

    private BuilderActions() {
    }

    /** 收到一个包（网络线程转到主线程后调；GameTest 直接调）；返回处理了没有（限频丢掉、不是魔杖、参数不对都返回 false） */
    public static boolean handle(C2SBuilderActionPacket packet, ServerPlayer player) {
        if (!ECProtection.isRealPlayer(player)) {
            return false;
        }
        if (packet.action() == C2SBuilderActionPacket.PANEL_SYNC) {
            // 要清单有自己的限频（BuilderPanel，每人 10 刻一次，超频的直接丢），不占操作包的名额：面板自己隔一会儿要一次，不该挤掉玩家真点的操作
            return BuilderPanel.request(player, packet.hand() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        }
        if (!allow(player)) {
            return false;
        }
        if (packet.action() == C2SBuilderActionPacket.CLIENT_PREFS) {
            CONFIRM_THRESHOLD.put(player.getUUID(), Mth.clamp(packet.a(), 0, 1 << 20));
            return true;
        }
        InteractionHand hand = packet.hand() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack wand = player.getItemInHand(hand);
        if (BuilderMode.of(wand) == null) {
            return false;
        }
        switch (packet.action()) {
            case C2SBuilderActionPacket.SET_SIZE -> {
                BuilderShape shape = BuilderSelection.shape(wand);
                if (!shape.resizable() || busy(player)) {
                    return false;
                }
                // 竖墙的第二个数是高（同样按边长、格数夹）
                BuilderTier.Stats stats = BuilderTier.stats(wand);
                BuilderShapes.Size size = BuilderShapes.clamp(packet.a(), packet.b(), stats.maxSide(), stats.maxVolume());
                BuilderSelection.setSize(wand, shape, size.width(), size.length());
                return true;
            }
            case C2SBuilderActionPacket.CYCLE_SHAPE -> {
                if (busy(player)) {
                    return false;
                }
                applyShape(player, wand, BuilderTier.of(wand).nextShape(BuilderSelection.shape(wand)));
                return true;
            }
            case C2SBuilderActionPacket.SET_SHAPE -> {
                return setShape(player, hand, packet.a()) == SettingResult.OK;
            }
            case C2SBuilderActionPacket.SET_MODE -> {
                return setMode(player, hand, packet.a()) == SettingResult.OK;
            }
            case C2SBuilderActionPacket.TOGGLE_MODULE -> {
                if (packet.b() != 0 && packet.b() != 1) {
                    return false;
                }
                return BuilderModules.toggleFromHand(player, hand, packet.a(), packet.b() == 1) == BuilderModules.RemoveResult.OK;
            }
            case C2SBuilderActionPacket.PICK_BLOCK -> {
                return packet.ids().size() == 1 && pickBlock(player, hand, packet.ids().get(0)) == SettingResult.OK;
            }
            case C2SBuilderActionPacket.UNDO -> {
                BuilderJobs.undo(player);
                return true;
            }
            case C2SBuilderActionPacket.STOP -> {
                BuilderJobs.stop(player);
                return true;
            }
            case C2SBuilderActionPacket.REMOVE_MODULE -> {
                return BuilderModules.removeFromHand(player, hand, packet.a()) == BuilderModules.RemoveResult.OK;
            }
            case C2SBuilderActionPacket.AIM_START -> {
                return BuilderAim.start(player, hand, BlockPos.of(packet.pos()), packet.face()).accepted();
            }
            case C2SBuilderActionPacket.SET_MIX -> {
                return setMix(player, hand, packet.ids(), packet.weights()) == SettingResult.OK;
            }
            case C2SBuilderActionPacket.SET_DISCARD -> {
                return setDiscard(player, hand, packet.ids()) == SettingResult.OK;
            }
            default -> {
                return false;
            }
        }
    }

    /** 设混铺表 / 丢弃清单的结果（GameTest 断言用） */
    public enum SettingResult {
        OK,
        /** 死了或是旁观者 */
        UNAVAILABLE,
        /** 那只手里不是创世神之杖 */
        NOT_WAND,
        /** 没装（或这一档不生效）对应的模块 */
        NO_MODULE,
        /** 有活在做、暂停或排队 */
        BUSY,
        /** 项数、注册名、权重有一项不对：整张不收，原来的不动 */
        INVALID
    }

    /**
     * 设混铺表（面板调；空表 = 清空）：活着、不是旁观、手里是装了混铺的魔杖、没在施工；不超过 {@code mix.max_kinds} 种，
     * 每种是能选定的方块（{@link BuilderMix#usableBlock}，含 {@code builder_blacklist}）、不重复（用同一种物品当材料的也算重复），权重 1～9。有一项不对整张不收（伪造的包不提示）。
     * 收下时换一个新种子（同一张表重设一次，花样也跟着换）。
     */
    public static SettingResult setMix(ServerPlayer player, InteractionHand hand, List<ResourceLocation> ids, List<Integer> weights) {
        ItemStack wand = player.getItemInHand(hand);
        SettingResult check = checkSetting(player, wand, BuilderModules.Kind.MIX);
        if (check != SettingResult.OK) {
            return check;
        }
        if (ids.size() != weights.size() || ids.size() > ECBuilder.Config.mixMaxKinds()) {
            return SettingResult.INVALID;
        }
        List<BuilderMix.Entry> entries = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            Block block = BuilderMix.usableBlock(ids.get(i));
            Integer weight = weights.get(i);
            if (block == null || weight == null || weight < BuilderMix.MIN_WEIGHT || weight > BuilderMix.MAX_WEIGHT) {
                return SettingResult.INVALID;
            }
            if (BuilderMix.sameItem(entries, block)) {
                return SettingResult.INVALID;
            }
            entries.add(new BuilderMix.Entry(block, weight));
        }
        BuilderMix.write(wand, entries, player.getRandom().nextInt());
        if (entries.isEmpty()) {
            MagicalBuilderItem.tell(player, Component.translatable("message.essentialcraft5.builder.mix_cleared"));
        } else {
            MutableComponent list = Component.empty();
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    list.append(Component.translatable("message.essentialcraft5.builder.list_sep"));
                }
                list.append(Component.translatable("message.essentialcraft5.builder.mix_weight", entries.get(i).block().getName(), entries.get(i).weight()));
            }
            MagicalBuilderItem.tell(player, Component.translatable("message.essentialcraft5.builder.mix_set", list));
        }
        return SettingResult.OK;
    }

    /**
     * 设回收的丢弃清单（面板调；空表 = 清空）：校验同 {@link #setMix}（换成回收模块），不超过 {@code salvage.discard_max} 项，
     * 每项是认得的物品、不重复。有一项不对整张不收。
     */
    public static SettingResult setDiscard(ServerPlayer player, InteractionHand hand, List<ResourceLocation> ids) {
        ItemStack wand = player.getItemInHand(hand);
        SettingResult check = checkSetting(player, wand, BuilderModules.Kind.SALVAGE);
        if (check != SettingResult.OK) {
            return check;
        }
        if (ids.size() > ECBuilder.Config.salvageDiscardMax()) {
            return SettingResult.INVALID;
        }
        List<Item> items = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Item item = BuilderSalvage.knownItem(id);
            if (item == null || items.contains(item)) {
                return SettingResult.INVALID;
            }
            items.add(item);
        }
        BuilderSalvage.writeDiscard(wand, items);
        MagicalBuilderItem.tell(player, items.isEmpty()
                ? Component.translatable("message.essentialcraft5.builder.discard_cleared")
                : Component.translatable("message.essentialcraft5.builder.discard_set", items.size()));
        return SettingResult.OK;
    }

    /**
     * 面板设形状：活着、不是旁观、手里是魔杖、没在施工（做、暂停、排队、测量都算），形状序号认得、这一档能用（一阶只有两点、铺面、直线）。
     * 和单按魔杖键切形状一样在动作栏说一句现在是什么形状
     */
    public static SettingResult setShape(ServerPlayer player, InteractionHand hand, int index) {
        ItemStack wand = player.getItemInHand(hand);
        SettingResult check = checkPanel(player, wand);
        if (check != SettingResult.OK) {
            return check;
        }
        BuilderShape[] shapes = BuilderShape.values();
        BuilderShape shape = index >= 0 && index < shapes.length ? shapes[index] : null;
        if (shape == null || !BuilderTier.of(wand).allows(shape)) {
            return SettingResult.INVALID;
        }
        applyShape(player, wand, shape);
        return SettingResult.OK;
    }

    private static void applyShape(ServerPlayer player, ItemStack wand, BuilderShape shape) {
        BuilderSelection.setShape(wand, shape);
        int[] raw = BuilderSelection.size(wand, shape);
        Component name = Component.translatable("message.essentialcraft5.builder.shape." + shape.id());
        MagicalBuilderItem.tell(player, shape.resizable()
                ? Component.translatable("message.essentialcraft5.builder.shape_sized", name, raw[0], raw[1])
                : Component.translatable("message.essentialcraft5.builder.shape_now", name));
    }

    /**
     * 面板换模式（tools/plan/builder_upgrade.md §2.3）：校验同 {@link #setShape}，模式序号认得、这一档有这个模式（铺设魔杖只有填充，没有别的可换）、
     * 不是现在这个；把手里那一格换成对应模式的魔杖，<b>整份 NBT 照抄</b>（物品标签和能力数据：MRU、选区、选定方块、档位、模块、混铺、丢弃清单都在），
     * 数量照旧。换的是同一格里的物品，不经过背包别处，也不掉落。
     */
    public static SettingResult setMode(ServerPlayer player, InteractionHand hand, int index) {
        ItemStack wand = player.getItemInHand(hand);
        SettingResult check = checkPanel(player, wand);
        if (check != SettingResult.OK) {
            return check;
        }
        BuilderMode[] modes = BuilderMode.values();
        BuilderMode mode = index >= 0 && index < modes.length ? modes[index] : null;
        if (mode == null || mode == BuilderMode.of(wand) || !BuilderTier.of(wand).allows(mode) || wand.getItem() instanceof PavingWandItem) {
            return SettingResult.INVALID;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(ECBuilder.item(mode));
        if (id == null) {
            return SettingResult.INVALID;
        }
        CompoundTag full = wand.serializeNBT();
        full.putString("id", id.toString());
        ItemStack swapped = ItemStack.of(full);
        if (BuilderMode.of(swapped) != mode) {
            return SettingResult.INVALID;
        }
        player.setItemInHand(hand, swapped);
        MagicalBuilderItem.tell(player, Component.translatable("message.essentialcraft5.builder.mode_now",
                Component.translatable("message.essentialcraft5.builder.mode." + mode.id())));
        return SettingResult.OK;
    }

    /**
     * 面板「从身上挑」：校验同 {@link #setShape}，方块要能选定、放下去就是它自己（{@link BuilderMix#usableBlock}），
     * 而且身上（背包、副手、随身容器）真有这种料（创造模式不要求）。写进魔杖的选定方块（{@code stored_block}）
     */
    public static SettingResult pickBlock(ServerPlayer player, InteractionHand hand, ResourceLocation blockId) {
        ItemStack wand = player.getItemInHand(hand);
        SettingResult check = checkPanel(player, wand);
        if (check != SettingResult.OK) {
            return check;
        }
        Block block = BuilderMix.usableBlock(blockId);
        if (block == null) {
            return SettingResult.INVALID;
        }
        if (!player.getAbilities().instabuild) {
            long[] counts = BuilderMaterials.count(player, block.asItem(), wand);
            if (counts[0] + counts[1] <= 0L) {
                return SettingResult.INVALID;
            }
        }
        BuilderSelection.setStoredBlock(wand, blockId);
        MagicalBuilderItem.tell(player, Component.translatable("message.essentialcraft5.builder.block", block.getName()));
        return SettingResult.OK;
    }

    /** 面板操作的公共校验：活着、不是旁观、手里是魔杖、没在施工 */
    private static SettingResult checkPanel(ServerPlayer player, ItemStack wand) {
        if (!player.isAlive() || player.isSpectator()) {
            return SettingResult.UNAVAILABLE;
        }
        if (BuilderMode.of(wand) == null) {
            return SettingResult.NOT_WAND;
        }
        return busy(player) ? SettingResult.BUSY : SettingResult.OK;
    }

    /** 在施工（做、暂停、排队、测量）：动作栏说一句「先停下才能改」，返回 true */
    private static boolean busy(ServerPlayer player) {
        if (!BuilderJobs.isRunning(player.getUUID())) {
            return false;
        }
        MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.panel_busy"));
        return true;
    }

    private static SettingResult checkSetting(ServerPlayer player, ItemStack wand, BuilderModules.Kind kind) {
        if (!player.isAlive() || player.isSpectator()) {
            return SettingResult.UNAVAILABLE;
        }
        if (BuilderMode.of(wand) == null) {
            return SettingResult.NOT_WAND;
        }
        if (!BuilderModules.has(wand, kind)) {
            return SettingResult.NO_MODULE;
        }
        if (BuilderJobs.isRunning(player.getUUID())) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.settings_busy"));
            return SettingResult.BUSY;
        }
        return SettingResult.OK;
    }

    /** 限频：每人每 {@value #WINDOW_TICKS} 刻最多 {@value #MAX_PER_WINDOW} 个 */
    private static boolean allow(ServerPlayer player) {
        long now = player.server.getTickCount();
        int window = (int) (now / WINDOW_TICKS);
        int[] state = RATE.computeIfAbsent(player.getUUID(), id -> new int[] {window, 0});
        if (state[0] != window) {
            state[0] = window;
            state[1] = 0;
        }
        if (state[1] >= MAX_PER_WINDOW) {
            return false;
        }
        state[1]++;
        return true;
    }

    /** 这名玩家的确认阈值：要动的格数超过它就要再点一次确认 */
    public static int confirmThreshold(UUID player) {
        return CONFIRM_THRESHOLD.getOrDefault(player, BuilderJobs.DEFAULT_CONFIRM_THRESHOLD);
    }

    static void forget(UUID player) {
        RATE.remove(player);
        CONFIRM_THRESHOLD.remove(player);
        BuilderStatus.forget(player);
        BuilderPanel.forget(player);
    }

    static void clearAll() {
        RATE.clear();
        CONFIRM_THRESHOLD.clear();
        BuilderStatus.clearAll();
        BuilderPanel.clearAll();
    }
}
