package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageEvent;
import com.flameforge.essentialcraft5.player.AttunementBudget;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /ec5 attunement <玩家> …}（batch13.md §3.3.5，权限 2，由 {@code ECAttunement.init()} 挂到 {@code /ec5} 下）：
 * <pre>
 * /ec5 attunement &lt;玩家&gt; get &lt;槽位&gt;                      看那件同调装备的阶、同调度、累计、阶戳
 * /ec5 attunement &lt;玩家&gt; set &lt;槽位&gt; &lt;阶&gt; [同调度]          直接设（阶夹到家族最高阶，同调度夹到本阶门槛）
 * /ec5 attunement &lt;玩家&gt; budget [armor|weapon] [值]          看 / 设同调预算（夹到 0 ~ 上限）
 * </pre>
 * 槽位：head / chest / legs / feet / mainhand。set 会丢掉这个槽上还没写进物品的点，并发 {@link AttunementStageEvent}（{@code COMMAND}）。
 * 返回值（{@code execute store} 用）：get 只选中一名玩家时返回阶（budget 返回预算取整），多名时返回人数；set 返回改了几个人。
 * 逻辑写成静态方法，GameTest 直接调。
 */
public final class AttunementCommands {

    static final List<String> SLOTS = List.of("head", "chest", "legs", "feet", "mainhand");

    private static final SimpleCommandExceptionType NO_DATA = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.no_player_data"));
    private static final DynamicCommandExceptionType BAD_SLOT = new DynamicCommandExceptionType(
            slot -> Component.translatable("commands.essentialcraft5.attunement.bad_slot", slot));
    private static final SimpleCommandExceptionType NOT_GEAR = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.attunement.not_gear"));

    private AttunementCommands() {
    }

    /** 形状符合 {@link ECCommands.Subcommand} */
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("attunement")
                .then(Commands.argument(ECCommands.TARGETS, EntityArgument.players())
                        .then(Commands.literal("get")
                                .then(slotArgument()
                                        .executes(ctx -> get(ctx.getSource(), players(ctx), slot(ctx)))))
                        .then(Commands.literal("set")
                                .then(slotArgument()
                                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, Attunement.MAX_STAGE))
                                                .executes(ctx -> set(ctx.getSource(), players(ctx), slot(ctx),
                                                        IntegerArgumentType.getInteger(ctx, "stage"), 0))
                                                .then(Commands.argument("points", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> set(ctx.getSource(), players(ctx), slot(ctx),
                                                                IntegerArgumentType.getInteger(ctx, "stage"),
                                                                IntegerArgumentType.getInteger(ctx, "points")))))))
                        .then(Commands.literal("budget")
                                .executes(ctx -> budget(ctx.getSource(), players(ctx), null))
                                .then(budgetBranch(IPlayerData.BudgetKind.ARMOR))
                                .then(budgetBranch(IPlayerData.BudgetKind.WEAPON))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> budgetBranch(IPlayerData.BudgetKind kind) {
        return Commands.literal(kind.key())
                .executes(ctx -> budget(ctx.getSource(), players(ctx), kind))
                .then(Commands.argument("value", FloatArgumentType.floatArg(0F))
                        .executes(ctx -> setBudget(ctx.getSource(), players(ctx), kind, FloatArgumentType.getFloat(ctx, "value"))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> slotArgument() {
        return Commands.argument("slot", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SLOTS, builder));
    }

    private static Collection<ServerPlayer> players(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, ECCommands.TARGETS);
    }

    private static EquipmentSlot slot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return parseSlot(StringArgumentType.getString(ctx, "slot"));
    }

    /** head / chest / legs / feet / mainhand → 装备槽 */
    public static EquipmentSlot parseSlot(String name) throws CommandSyntaxException {
        String key = name.toLowerCase(Locale.ROOT);
        if (!SLOTS.contains(key)) {
            throw BAD_SLOT.create(name);
        }
        return EquipmentSlot.byName(key);
    }

    static Component slotName(EquipmentSlot slot) {
        return Component.translatable("attunement.essentialcraft5.slot." + slot.getName());
    }

    static Component budgetName(IPlayerData.BudgetKind kind) {
        return Component.translatable("attunement.essentialcraft5.budget." + kind.key());
    }

    public static int get(CommandSourceStack source, Collection<? extends Player> targets, EquipmentSlot slot) throws CommandSyntaxException {
        int count = 0;
        int last = 0;
        for (Player player : targets) {
            ItemStack stack = player.getItemBySlot(slot);
            AttunementFamily family = AttunementFamily.of(stack);
            if (family == null) {
                source.sendFailure(Component.translatable("commands.essentialcraft5.attunement.none", player.getDisplayName(), slotName(slot)));
                continue;
            }
            int stage = Attunement.stage(stack);
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.attunement.get", player.getDisplayName(), slotName(slot),
                    stack.getHoverName(), stage, family.maxStage(), Attunement.points(stack), Attunement.need(family, stage),
                    Attunement.total(stack), Attunement.rankStamp(stack)), false);
            last = stage;
            count++;
        }
        if (count == 0) {
            throw NOT_GEAR.create();
        }
        return count == 1 ? last : count;
    }

    public static int set(CommandSourceStack source, Collection<? extends Player> targets, EquipmentSlot slot, int stage, int points)
            throws CommandSyntaxException {
        int count = 0;
        for (Player player : targets) {
            ItemStack stack = player.getItemBySlot(slot);
            AttunementFamily family = AttunementFamily.of(stack);
            if (family == null) {
                source.sendFailure(Component.translatable("commands.essentialcraft5.attunement.none", player.getDisplayName(), slotName(slot)));
                continue;
            }
            int before = Attunement.stage(stack);
            AttunementGains.discardPending(player, slot);
            Attunement.set(stack, stage, points);
            int after = Attunement.stage(stack);
            if (after != before) {
                MinecraftForge.EVENT_BUS.post(new AttunementStageEvent(player, stack, family, before, after, AttunementStageEvent.Cause.COMMAND));
            }
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.attunement.set", player.getDisplayName(), slotName(slot),
                    after, Attunement.points(stack)), true);
            count++;
        }
        if (count == 0) {
            throw NOT_GEAR.create();
        }
        return count;
    }

    /** kind 为 null 时两份都报 */
    public static int budget(CommandSourceStack source, Collection<? extends Player> targets, IPlayerData.BudgetKind kind) throws CommandSyntaxException {
        int count = 0;
        int last = 0;
        int cap = AttunementConfig.budgetCap();
        for (Player player : targets) {
            if (PlayerDataEvents.get(player) == null) {
                continue;
            }
            float armor = AttunementBudget.get(player, IPlayerData.BudgetKind.ARMOR);
            float weapon = AttunementBudget.get(player, IPlayerData.BudgetKind.WEAPON);
            if (kind == null) {
                source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.attunement.budget", player.getDisplayName(),
                        format(armor), format(weapon), cap), false);
                last = (int) armor;
            } else {
                float value = kind == IPlayerData.BudgetKind.ARMOR ? armor : weapon;
                source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.attunement.budget.one", player.getDisplayName(),
                        budgetName(kind), format(value), cap), false);
                last = (int) value;
            }
            count++;
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count == 1 ? last : count;
    }

    public static int setBudget(CommandSourceStack source, Collection<? extends Player> targets, IPlayerData.BudgetKind kind, float value)
            throws CommandSyntaxException {
        int count = 0;
        for (Player player : targets) {
            if (PlayerDataEvents.get(player) == null) {
                continue;
            }
            AttunementBudget.set(player, kind, value);
            float now = AttunementBudget.get(player, kind);
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.attunement.budget.set", player.getDisplayName(),
                    budgetName(kind), format(now)), true);
            count++;
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count;
    }

    static String format(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
