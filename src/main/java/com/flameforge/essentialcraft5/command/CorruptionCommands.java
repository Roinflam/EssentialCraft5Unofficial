package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.corruption.Afflictions;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@code /ec5 player <targets> corruption …}：查看、增删玩家身上的腐化效果（原作 /corruptioneffect &lt;player&gt; &lt;序号|clear&gt;，权限 3）。
 * <ul>
 *   <li>{@code list}：每个目标一行，列出效果的名字和 id（没有时说「没有腐化效果」）。</li>
 *   <li>{@code add <effect>}：按 id 加（{@code body_0} … {@code matrix_24}，可补全；原作按序号）。已经有的不重复加；配置关掉的效果也能加，只是不触发。</li>
 *   <li>{@code remove <effect>}：按 id 去掉。</li>
 *   <li>{@code clear}：全部去掉（原作同名）。</li>
 *   <li>{@code tier [<档位>]}：查看 / 改玩家「打开过的最高档五芒星」（−1 ~ 3；第十批「治得了才抽」按它判，见 corruption/CorruptionGate）。
 *       给老存档里的人补档位、排查「为什么没抽到 / 为什么潜伏」用；改成更低的档位也行（这条指令不受「只升不降」限制）。</li>
 *   <li>{@code afflictions clear}：清掉身上的矩阵后遗症（双手迟钝、心智悖论、MRU 腐化……，corruption/Afflictions）。
 *       它们不是药水效果，{@code /effect clear} 和牛奶都清不掉，卡住了用这个。</li>
 * </ul>
 * 只改玩家数据（自带 markDirty，最多 10 tick 内同步给本人），不碰矩阵损伤（那个用 {@code matrix_damage set}）。
 * 失败规则照原版 /effect：一个人都没改成时报错（add 都已经有了、remove / clear 都没有）。
 * <p>
 * 返回值（{@code execute store} 用）：list 只选中一个玩家时返回他的效果个数，多个时返回人数；add / remove / clear 返回改了几个人。
 * 逻辑写成静态方法、参数是 {@code Collection<? extends Player>}，GameTest 用模拟玩家直接调（同 {@link PlayerCommands}）。
 * <p>
 * 挂载：{@link #build} 符合 {@link ECCommands.Subcommand}，由 player/PlayerSystems.init() 用 {@code ECCommands.addPlayerSubcommand(CorruptionCommands::build)} 挂上。
 */
public final class CorruptionCommands {

    /** 效果参数名 */
    public static final String EFFECT = "effect";
    /** 五芒星档位参数名 */
    public static final String TIER = "tier";

    private static final SimpleCommandExceptionType NO_DATA = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.no_player_data"));
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.essentialcraft5.corruption.unknown", id));
    private static final SimpleCommandExceptionType ADD_FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.corruption.add.failed"));
    private static final SimpleCommandExceptionType REMOVE_FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.corruption.remove.failed"));
    private static final SimpleCommandExceptionType CLEAR_FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.corruption.clear.failed"));
    private static final SimpleCommandExceptionType AFFLICTIONS_CLEAR_FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.corruption.afflictions.clear.failed"));

    private static final SuggestionProvider<CommandSourceStack> EFFECT_IDS =
            (context, builder) -> SharedSuggestionProvider.suggest(CorruptionEffects.ids(), builder);

    private CorruptionCommands() {
    }

    /** {@code corruption} 节点，挂在 {@code player <targets>} 下 */
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("corruption")
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ECCommands.targets(ctx))))
                .then(Commands.literal("add")
                        .then(Commands.argument(EFFECT, StringArgumentType.word()).suggests(EFFECT_IDS)
                                .executes(ctx -> add(ctx.getSource(), ECCommands.targets(ctx), StringArgumentType.getString(ctx, EFFECT)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument(EFFECT, StringArgumentType.word()).suggests(EFFECT_IDS)
                                .executes(ctx -> remove(ctx.getSource(), ECCommands.targets(ctx), StringArgumentType.getString(ctx, EFFECT)))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource(), ECCommands.targets(ctx))))
                .then(Commands.literal("afflictions")
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearAfflictions(ctx.getSource(), ECCommands.targets(ctx)))))
                .then(Commands.literal("tier")
                        .executes(ctx -> getTier(ctx.getSource(), ECCommands.targets(ctx)))
                        .then(Commands.argument(TIER, IntegerArgumentType.integer(-1, ECPentacle.MAX_TIER))
                                .executes(ctx -> setTier(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, TIER)))));
    }

    // ---------------------------------------------------------------- 子命令

    public static int list(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int count = 0;
        int last = 0;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data == null) {
                continue;
            }
            List<String> ids = List.copyOf(data.getCorruptionEffects());
            if (ids.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.corruption.list.none", player.getDisplayName()), false);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.corruption.list",
                        player.getDisplayName(), ids.size(), describe(ids)), false);
            }
            last = ids.size();
            count++;
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count == 1 ? last : count;
    }

    public static int add(CommandSourceStack source, Collection<? extends Player> targets, String id) throws CommandSyntaxException {
        CorruptionEffects.Effect effect = effect(id);
        List<Player> changed = modify(targets, data -> data.addCorruptionEffect(effect.id()));
        if (changed.isEmpty()) {
            throw ADD_FAILED.create();
        }
        feedback(source, changed,
                player -> Component.translatable("commands.essentialcraft5.corruption.add.success",
                        Component.translatable(effect.nameKey()), player.getDisplayName()),
                Component.translatable("commands.essentialcraft5.corruption.add.success.multiple",
                        Component.translatable(effect.nameKey()), changed.size()));
        return changed.size();
    }

    public static int remove(CommandSourceStack source, Collection<? extends Player> targets, String id) throws CommandSyntaxException {
        // 身上可能留着以后版本去掉了的 id：表里没有也照样能删
        CorruptionEffects.Effect effect = CorruptionEffects.byId(id);
        List<Player> changed = modify(targets, data -> data.removeCorruptionEffect(id));
        if (changed.isEmpty()) {
            if (effect == null) {
                throw UNKNOWN.create(id);
            }
            throw REMOVE_FAILED.create();
        }
        Component name = effect == null ? Component.literal(id) : Component.translatable(effect.nameKey());
        feedback(source, changed,
                player -> Component.translatable("commands.essentialcraft5.corruption.remove.success", name, player.getDisplayName()),
                Component.translatable("commands.essentialcraft5.corruption.remove.success.multiple", name, changed.size()));
        return changed.size();
    }

    public static int clear(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int[] removed = new int[1];
        List<Player> changed = modify(targets, data -> {
            int n = data.clearCorruptionEffects();
            removed[0] += n;
            return n > 0;
        });
        if (changed.isEmpty()) {
            throw CLEAR_FAILED.create();
        }
        int total = removed[0];
        feedback(source, changed,
                player -> Component.translatable("commands.essentialcraft5.corruption.clear.success", total, player.getDisplayName()),
                Component.translatable("commands.essentialcraft5.corruption.clear.success.multiple", total, changed.size()));
        return changed.size();
    }

    /** 清掉目标身上的矩阵后遗症；返回改了几个人，一个都没有时报错 */
    public static int clearAfflictions(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int[] removed = new int[1];
        List<Player> changed = new ArrayList<>();
        boolean any = false;
        for (Player player : targets) {
            if (PlayerDataEvents.get(player) == null) {
                continue;
            }
            any = true;
            int n = Afflictions.clear(player);
            if (n > 0) {
                removed[0] += n;
                changed.add(player);
            }
        }
        if (!any) {
            throw NO_DATA.create();
        }
        if (changed.isEmpty()) {
            throw AFFLICTIONS_CLEAR_FAILED.create();
        }
        int total = removed[0];
        feedback(source, changed,
                player -> Component.translatable("commands.essentialcraft5.corruption.afflictions.clear.success", total, player.getDisplayName()),
                Component.translatable("commands.essentialcraft5.corruption.afflictions.clear.success.multiple", total, changed.size()));
        return changed.size();
    }

    /** 每个目标一行：打开过的最高档五芒星（−1 写「还没有」）。只选中一个时返回档位，多个时返回人数 */
    public static int getTier(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int count = 0;
        int last = 0;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data == null) {
                continue;
            }
            int tier = data.getPentacleTier();
            source.sendSuccess(() -> tier < 0
                    ? Component.translatable("commands.essentialcraft5.corruption.tier.none", player.getDisplayName())
                    : Component.translatable("commands.essentialcraft5.corruption.tier.get", player.getDisplayName(), tier), false);
            last = tier;
            count++;
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count == 1 ? last : count;
    }

    /** 把目标的档位设成 tier（−1 ~ 3，可以往下改）；返回有数据的人数 */
    public static int setTier(CommandSourceStack source, Collection<? extends Player> targets, int tier) throws CommandSyntaxException {
        List<Player> changed = modify(targets, data -> {
            data.setPentacleTier(tier);
            return true;
        });
        feedback(source, changed,
                player -> Component.translatable("commands.essentialcraft5.corruption.tier.set", player.getDisplayName(), tier),
                Component.translatable("commands.essentialcraft5.corruption.tier.set.multiple", changed.size(), tier));
        return changed.size();
    }

    // ---------------------------------------------------------------- 共用

    private static CorruptionEffects.Effect effect(String id) throws CommandSyntaxException {
        CorruptionEffects.Effect effect = CorruptionEffects.byId(id);
        if (effect == null) {
            throw UNKNOWN.create(id);
        }
        return effect;
    }

    /** 「名字（id）」用顿号隔开；表里没有的 id 只写 id */
    private static Component describe(List<String> ids) {
        List<Component> parts = new ArrayList<>(ids.size());
        for (String id : ids) {
            CorruptionEffects.Effect effect = CorruptionEffects.byId(id);
            parts.add(effect == null ? Component.literal(id)
                    : Component.translatable("commands.essentialcraft5.corruption.list.entry", Component.translatable(effect.nameKey()), id));
        }
        return ComponentUtils.formatList(parts, Component.translatable("commands.essentialcraft5.corruption.list.separator"));
    }

    /** 对每个有数据的目标执行 action，返回 action 报告「改了」的那些玩家；一个有数据的都没有时报错 */
    private static List<Player> modify(Collection<? extends Player> targets, Predicate<IPlayerData> action) throws CommandSyntaxException {
        List<Player> changed = new ArrayList<>();
        boolean any = false;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                any = true;
                if (action.test(data)) {
                    changed.add(player);
                }
            }
        }
        if (!any) {
            throw NO_DATA.create();
        }
        return changed;
    }

    /** 只改了一个人时说名字，多个时说人数（原版 /effect 的写法） */
    private static void feedback(CommandSourceStack source, List<Player> changed,
                                 Function<Player, MutableComponent> single, MutableComponent multiple) {
        if (changed.size() == 1) {
            Player player = changed.get(0);
            source.sendSuccess(() -> single.apply(player), true);
        } else {
            source.sendSuccess(() -> multiple, true);
        }
    }
}
