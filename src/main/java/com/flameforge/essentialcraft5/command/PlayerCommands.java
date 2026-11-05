package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * {@code /ec5 player <targets> …}：查看、修改玩家的 EC 数据（{@link IPlayerData}）。
 * <ul>
 *   <li>{@code ubmru get|set <值>|add <值>}：原作 /setubmru 只有 set（参数 ≥ 0）。set ≥ 0；add 可以为负，结果夹到 [0, int 上限]（按 long 算，不绕回负数，
 *       同 UBMRUEvents 的规矩）。</li>
 *   <li>{@code matrix_damage get|set <值>}：矩阵损伤（原作 overhaulDamage），≥ 0。原作没有这条指令，调试腐化用。</li>
 *   <li>{@code matrix_type get|set <neutral|chaos|frozen|magic|shade>}：矩阵类型，序号 0~4 同 {@link IPlayerData#getMatrixType()}。原作没有这条指令，矩阵投影的效果靠它调。</li>
 * </ul>
 * 改动走 IPlayerData 的 setter（自动 markDirty），PlayerDataEvents 最多 10 tick 内同步给本人，不用自己发包。
 * <p>
 * 返回值（给 {@code execute store} 用）：get 只选中一个玩家时返回那个数，多个时返回人数；set / add 返回改了几个人。
 * 逻辑写成静态方法，参数是 {@code Collection<? extends Player>}，GameTest 用模拟玩家直接调（测试环境造不出能被选择器选中的在线玩家）。
 */
public final class PlayerCommands {

    /** 矩阵类型的参数名，下标就是 IPlayerData#getMatrixType 的序号（原作 EnumMatrixType：0 中性、1 混沌、2 冰冻、3 魔力、4 暗影） */
    public static final List<String> MATRIX_TYPES = List.of("neutral", "chaos", "frozen", "magic", "shade");

    private static final SimpleCommandExceptionType NO_DATA = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.no_player_data"));

    private PlayerCommands() {
    }

    static void addTo(ArgumentBuilder<CommandSourceStack, ?> targets) {
        targets.then(Commands.literal("ubmru")
                        .then(Commands.literal("get")
                                .executes(ctx -> getUBMRU(ctx.getSource(), ECCommands.targets(ctx))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setUBMRU(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> addUBMRU(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("matrix_damage")
                        .then(Commands.literal("get")
                                .executes(ctx -> getMatrixDamage(ctx.getSource(), ECCommands.targets(ctx))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setMatrixDamage(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(matrixType());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> matrixType() {
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        for (int i = 0; i < MATRIX_TYPES.size(); i++) {
            int type = i;
            set.then(Commands.literal(MATRIX_TYPES.get(i))
                    .executes(ctx -> setMatrixType(ctx.getSource(), ECCommands.targets(ctx), type)));
        }
        return Commands.literal("matrix_type")
                .then(Commands.literal("get")
                        .executes(ctx -> getMatrixType(ctx.getSource(), ECCommands.targets(ctx))))
                .then(set);
    }

    // ---------------------------------------------------------------- UBMRU

    public static int getUBMRU(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        return get(source, targets, IPlayerData::getUBMRU,
                (player, value) -> Component.translatable("commands.essentialcraft5.ubmru.get", player.getDisplayName(), value));
    }

    public static int setUBMRU(CommandSourceStack source, Collection<? extends Player> targets, int value) throws CommandSyntaxException {
        int count = modify(targets, IPlayerData::getUBMRU, IPlayerData::setUBMRU, old -> Math.max(0, value));
        feedback(source, targets, count,
                player -> Component.translatable("commands.essentialcraft5.setubmru.success", player.getDisplayName(), value),
                () -> Component.translatable("commands.essentialcraft5.setubmru.success.multiple", count, value));
        return count;
    }

    public static int addUBMRU(CommandSourceStack source, Collection<? extends Player> targets, int amount) throws CommandSyntaxException {
        int count = modify(targets, IPlayerData::getUBMRU, IPlayerData::setUBMRU,
                old -> (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) Math.max(0, old) + amount)));
        feedback(source, targets, count,
                player -> Component.translatable("commands.essentialcraft5.ubmru.add.success", amount, player.getDisplayName(),
                        valueOf(player, IPlayerData::getUBMRU)),
                () -> Component.translatable("commands.essentialcraft5.ubmru.add.success.multiple", amount, count));
        return count;
    }

    // ---------------------------------------------------------------- 矩阵损伤

    public static int getMatrixDamage(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        return get(source, targets, IPlayerData::getMatrixDamage,
                (player, value) -> Component.translatable("commands.essentialcraft5.matrix_damage.get", player.getDisplayName(), value));
    }

    public static int setMatrixDamage(CommandSourceStack source, Collection<? extends Player> targets, int value) throws CommandSyntaxException {
        int count = modify(targets, IPlayerData::getMatrixDamage, IPlayerData::setMatrixDamage, old -> Math.max(0, value));
        feedback(source, targets, count,
                player -> Component.translatable("commands.essentialcraft5.matrix_damage.set.success", player.getDisplayName(), value),
                () -> Component.translatable("commands.essentialcraft5.matrix_damage.set.success.multiple", count, value));
        return count;
    }

    // ---------------------------------------------------------------- 矩阵类型

    public static int getMatrixType(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        return get(source, targets, IPlayerData::getMatrixType,
                (player, value) -> Component.translatable("commands.essentialcraft5.matrix_type.get", player.getDisplayName(),
                        SoulStoneItem.matrixTypeName(value)));
    }

    /** @param type 0~4，见 {@link #MATRIX_TYPES} */
    public static int setMatrixType(CommandSourceStack source, Collection<? extends Player> targets, int type) throws CommandSyntaxException {
        int count = modify(targets, IPlayerData::getMatrixType, IPlayerData::setMatrixType, old -> type);
        feedback(source, targets, count,
                player -> Component.translatable("commands.essentialcraft5.matrix_type.set.success", player.getDisplayName(),
                        SoulStoneItem.matrixTypeName(type)),
                () -> Component.translatable("commands.essentialcraft5.matrix_type.set.success.multiple", count,
                        SoulStoneItem.matrixTypeName(type)));
        return count;
    }

    // ---------------------------------------------------------------- 共用

    @FunctionalInterface
    private interface ValueMessage {
        Component create(Player player, int value);
    }

    @FunctionalInterface
    private interface Setter {
        void set(IPlayerData data, int value);
    }

    /** 每个目标发一行，返回值见类注释 */
    private static int get(CommandSourceStack source, Collection<? extends Player> targets, ToIntFunction<IPlayerData> getter,
                           ValueMessage message) throws CommandSyntaxException {
        int count = 0;
        int last = 0;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                int value = getter.applyAsInt(data);
                source.sendSuccess(() -> message.create(player, value), false);
                last = value;
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count == 1 ? last : count;
    }

    /** 对每个有数据的目标：新值 = op(旧值)，经 setter 写回（setter 自带 markDirty）；返回改了几个人 */
    private static int modify(Collection<? extends Player> targets, ToIntFunction<IPlayerData> getter, Setter setter,
                              IntUnaryOperator op) throws CommandSyntaxException {
        int count = 0;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                setter.set(data, op.applyAsInt(getter.applyAsInt(data)));
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count;
    }

    /** 只改了一个人时说名字，多个时说人数（原版 /experience 的写法） */
    private static void feedback(CommandSourceStack source, Collection<? extends Player> targets, int count,
                                 Function<Player, Component> single, Supplier<Component> multiple) {
        if (count == 1) {
            Player player = first(targets);
            source.sendSuccess(() -> single.apply(player), true);
        } else {
            source.sendSuccess(multiple, true);
        }
    }

    /** targets 里第一个有数据的玩家（modify 已经保证至少有一个） */
    private static Player first(Collection<? extends Player> targets) {
        for (Player player : targets) {
            if (PlayerDataEvents.get(player) != null) {
                return player;
            }
        }
        throw new IllegalStateException("没有带玩家数据的目标");
    }

    private static int valueOf(Player player, ToIntFunction<IPlayerData> getter) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0 : getter.applyAsInt(data);
    }
}
