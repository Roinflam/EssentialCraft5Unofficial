package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity.Config;
import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.ForceDomeMenu;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 力场穹顶界面（第十批 10c-M4）。左边 MRU 条 (7, 4)；中间：状态条 (27, 4)（悬停写原因）、绑定宝石状态 (27, 22)、
 * 区块三种计数 (27, 40)（可拦绿 / 未确认黄 / 不可拦红，悬停逐条解释）、半径与每秒耗电 (27, 58)；右上绑定宝石格 (152, 5)。
 * 状态颜色照地脉凝聚器：工作绿、暂停 / 排队橙、缺东西黄、错误红（{@link Status#color}）。数值都从菜单同步来，不读客户端方块实体；
 * 提示里的名额、领地结论记多久读服务端配置（进服时 Forge 发给客户端）。
 */
public class ForceDomeScreen extends MachineScreen<ForceDomeMenu> {

    private static final String KEY = "gui.essentialcraft5.force_dome.";

    public ForceDomeScreen(ForceDomeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(new StatusTextElement(27, 4, 120, () -> statusText(menu.status()), () -> menu.status().color,
                null, 0, this::statusTooltip));
        elements.add(boundGemState(27, 22));
        elements.add(new StatusTextElement(27, 40, 145, this::coverageText, () -> 0xFFFFFF, null, 0, this::coverageTooltip));
        elements.add(new StatusTextElement(27, 58, 145,
                () -> Component.translatable(KEY + "range", menu.radius(), menu.costPerSecond()), () -> 0xFFFFFF, null, 0, this::rangeTooltip));
    }

    static Component statusText(Status status) {
        return Component.translatable(switch (status) {
            case WORKING -> KEY + "status.working";
            case DISABLED -> KEY + "status.disabled";
            case NO_OWNER -> "gui.essentialcraft5.owner.none";
            case OWNER_LIMIT -> KEY + "status.owner_limit";
            case POWERED -> "gui.essentialcraft5.devices.redstone_paused";
            case NO_CLAIM -> KEY + "status.no_claim";
            case UNCONFIRMED -> KEY + "status.unconfirmed";
            case NO_MRU -> "gui.essentialcraft5.no_mru";
        });
    }

    private List<Component> statusTooltip() {
        List<Component> lines = new ArrayList<>();
        switch (menu.status()) {
            case WORKING -> lines.add(Component.translatable(KEY + "status.working.hint", menu.blockable(), menu.costPerSecond()));
            case NO_OWNER -> lines.add(Component.translatable(KEY + "status.no_owner.hint"));
            case OWNER_LIMIT -> {
                lines.add(Component.translatable(KEY + "status.owner_limit_count", ECConfig.get(Config.maxPerOwner)));
                lines.add(Component.translatable(KEY + "status.owner_limit.hint").withStyle(ChatFormatting.GRAY));
            }
            case POWERED -> lines.add(Component.translatable(KEY + "status.powered.hint"));
            case NO_CLAIM -> lines.add(Component.translatable(KEY + "status.no_claim.hint"));
            case UNCONFIRMED -> lines.add(Component.translatable(menu.ownerState() == ECProtection.OwnerState.ONLINE
                    ? KEY + "status.unconfirmed.online" : KEY + "status.unconfirmed.offline"));
            case NO_MRU -> {
                lines.add(Component.translatable(KEY + "status.no_mru.hint", nextCost()));
                if (menu.blockable() == 0) {
                    // 没有可拦的区块还是「MRU 不够」：连一块区块一秒都付不起，还没开始问领地
                    lines.add(Component.translatable(KEY + "status.no_mru.no_probe").withStyle(ChatFormatting.GRAY));
                }
            }
            default -> {
            }
        }
        if (menu.ownerState() == ECProtection.OwnerState.OFFLINE && menu.status() == Status.WORKING) {
            lines.add(Component.translatable(KEY + "status.owner_offline").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /**
     * MRU 不够时下一秒要多少：可拦的区块按比例；一块可拦的都没有（连一块区块一秒都付不起、还没问领地）时是拦一块区块一秒的钱，
     * 同 {@code ForceDomeBlockEntity.oneChunkCostPerSecond}
     */
    private int nextCost() {
        int covered = menu.blockable() + menu.unconfirmed() + menu.denied();
        if (covered <= 0) {
            return 0;
        }
        long full = menu.fullCostPerSecond();
        long chunks = Math.max(menu.blockable(), 1);
        return (int) ((full * chunks + covered - 1) / covered);
    }

    private Component coverageText() {
        return Component.translatable(KEY + "coverage",
                Component.literal(String.valueOf(menu.blockable())).withStyle(ChatFormatting.GREEN),
                Component.literal(String.valueOf(menu.unconfirmed())).withStyle(ChatFormatting.YELLOW),
                Component.literal(String.valueOf(menu.denied())).withStyle(ChatFormatting.RED));
    }

    private List<Component> coverageTooltip() {
        int covered = menu.blockable() + menu.unconfirmed() + menu.denied();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "coverage.chunks", covered));
        lines.add(Component.translatable(KEY + "coverage.blockable").withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(KEY + "coverage.unconfirmed").withStyle(ChatFormatting.YELLOW));
        lines.add(Component.translatable(KEY + "coverage.denied").withStyle(ChatFormatting.RED));
        int cache = ECConfig.get(Config.claimCacheTicks);
        lines.add(Component.translatable(KEY + "coverage.cache", minutes(cache)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(KEY + "coverage.cache_denied", hours((long) cache * ForceDomeBlockEntity.DENIED_CACHE_MULTIPLIER))
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private List<Component> rangeTooltip() {
        int full = menu.fullCostPerSecond();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "range.full", full, (long) full * 3600L));
        lines.add(Component.translatable(KEY + "range.partial").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(KEY + "range.column").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** 刻 → 分钟（去掉多余的 0：72000 → 60） */
    private static String minutes(int ticks) {
        return BigDecimal.valueOf(Math.max(ticks, 0)).divide(BigDecimal.valueOf(1200), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** 刻 → 小时（去掉多余的 0：432000 → 6） */
    private static String hours(long ticks) {
        return BigDecimal.valueOf(Math.max(ticks, 0L)).divide(BigDecimal.valueOf(72_000), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
