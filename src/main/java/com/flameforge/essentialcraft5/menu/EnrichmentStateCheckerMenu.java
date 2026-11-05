package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CollapseState;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CondenseState;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.ShadeHint;
import com.flameforge.essentialcraft5.blockentity.EnrichmentStateCheckerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * 富集仓状态检测器界面（原作 ContainerMRUInfo：没有机器槽，只有玩家背包）。
 * MRU、上限、平衡值走 MachineData 的基础格（方块实体的 getMRUStorage 连着仓时就是控制器的存储）；
 * 额外数值 11 格：外壳 MROV（×100 定点拆两格）、是否连着一座已成形的仓（0/1），
 * 以及聚集体坍缩（第十批）：进度、要攒的进度（各拆两格）、状态序号、外壳里虚空级方块的块数、仓里那团聚集体的存量（≤ 20000，一格放得下）、是否在天灾里（0/1）；
 * 第十三批 13a 接在后面 8 格，凝结矩阵同样的一套：进度、要攒的进度（各拆两格）、状态序号、外壳里护板的块数、那团聚集体的存量、是否在天灾里。
 * 第十三批 13e 再接 2 格：暗影核心的提示序号（{@link ShadeHint}）、外壳里的影晶数。坍缩那支「要攒的进度」改从控制器取（走暗影分支时是暗影核心的秒数）。
 */
public class EnrichmentStateCheckerMenu extends MachineMenu {

    public static final int EXTRA_COUNT = 21;

    private static final int LINKED = 2;
    private static final int COLLAPSE_PROGRESS = 3;
    private static final int COLLAPSE_SECONDS = 5;
    private static final int COLLAPSE_STATE = 7;
    private static final int VOID_CASING = 8;
    private static final int PRESENCE_MRU = 9;
    private static final int COLLAPSE_EVENT = 10;
    private static final int CONDENSE_PROGRESS = 11;
    private static final int CONDENSE_SECONDS = 13;
    private static final int CONDENSE_STATE = 15;
    private static final int CONDENSE_CASING = 16;
    private static final int CONDENSE_PRESENCE_MRU = 17;
    private static final int CONDENSE_EVENT = 18;
    private static final int SHADE_HINT = 19;
    private static final int UMBRAL = 20;

    public EnrichmentStateCheckerMenu(int id, Inventory playerInventory, EnrichmentStateCheckerBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), serverData(be), be);
    }

    public EnrichmentStateCheckerMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), MachineData.client(EXTRA_COUNT), null);
    }

    private EnrichmentStateCheckerMenu(int id, Inventory playerInventory, BlockPos pos, MachineData data,
                                       @Nullable EnrichmentStateCheckerBlockEntity be) {
        super(ECEnrichment.EC_STATE_CHECKER_MENU.get(), id, playerInventory, pos, data, ECEnrichment.EC_STATE_CHECKER.get(), be);
        addPlayerInventory(playerInventory);
    }

    private static MachineData serverData(EnrichmentStateCheckerBlockEntity be) {
        IntSupplier[] resistance = MachineData.wideFixed(be::resistance);
        IntSupplier[] progress = MachineData.wide(fromController(be, EnrichmentControllerBlockEntity::collapseProgress));
        IntSupplier[] seconds = MachineData.wide(() -> {
            EnrichmentControllerBlockEntity controller = be.controller();
            return controller == null ? EnrichmentControllerBlockEntity.Config.collapse().seconds() : controller.collapseSecondsNeeded();
        });
        IntSupplier[] condenseProgress = MachineData.wide(fromController(be, EnrichmentControllerBlockEntity::condenseProgress));
        IntSupplier[] condenseSeconds = MachineData.wide(() -> EnrichmentControllerBlockEntity.Config.condense().seconds());
        return MachineData.server(be, resistance[0], resistance[1], () -> be.isLinked() ? 1 : 0,
                progress[0], progress[1], seconds[0], seconds[1],
                fromController(be, c -> c.collapseState().ordinal()),
                fromController(be, EnrichmentControllerBlockEntity::voidCasing),
                fromController(be, EnrichmentControllerBlockEntity::collapsePresenceMRU),
                fromController(be, c -> c.collapseEvent() ? 1 : 0),
                condenseProgress[0], condenseProgress[1], condenseSeconds[0], condenseSeconds[1],
                fromController(be, c -> c.condenseState().ordinal()),
                fromController(be, c -> Math.min(Short.MAX_VALUE, c.condenseCasing())),
                fromController(be, EnrichmentControllerBlockEntity::condensePresenceMRU),
                fromController(be, c -> c.condenseEvent() ? 1 : 0),
                fromController(be, c -> c.shadeHint().ordinal()),
                fromController(be, c -> Math.min(Short.MAX_VALUE, c.umbralCount())));
    }

    /** 连着成形的仓时从控制器取，没连上时为 0 */
    private static IntSupplier fromController(EnrichmentStateCheckerBlockEntity be, ToIntFunction<EnrichmentControllerBlockEntity> getter) {
        return () -> {
            EnrichmentControllerBlockEntity controller = be.controller();
            return controller == null ? 0 : getter.applyAsInt(controller);
        };
    }

    /** 外壳各方块 MRU 抗性之和 */
    public double resistance() {
        return data.extraFixed(0);
    }

    /** 是否连着一座已成形的富集仓 */
    public boolean linked() {
        return data.extra(LINKED) != 0;
    }

    /** 坍缩进度（秒） */
    public int collapseProgress() {
        return data.extraWide(COLLAPSE_PROGRESS);
    }

    /** 要攒的坍缩进度（秒） */
    public int collapseSeconds() {
        return data.extraWide(COLLAPSE_SECONDS);
    }

    public CollapseState collapseState() {
        return CollapseState.byId(data.extra(COLLAPSE_STATE));
    }

    public int voidCasing() {
        return data.extra(VOID_CASING);
    }

    public int presenceMRU() {
        return data.extra(PRESENCE_MRU);
    }

    public boolean collapseEvent() {
        return data.extra(COLLAPSE_EVENT) != 0;
    }

    /**
     * 界面上要不要显示坍缩那一条：连着仓，而且坍缩开着、在能坍缩的维度里（状态不是关闭 / 没成形 / 维度不对），或者已经攒了进度。
     * 早期在主世界搭的富集仓看不到它。
     */
    public boolean showsCollapse() {
        if (!linked()) {
            return false;
        }
        CollapseState state = collapseState();
        return collapseProgress() > 0 || shadeHint() != ShadeHint.NONE
                || (state != CollapseState.DISABLED && state != CollapseState.NOT_FORMED && state != CollapseState.WRONG_DIMENSION);
    }

    /** 暗影核心那一支差什么（13e） */
    public ShadeHint shadeHint() {
        return ShadeHint.byId(data.extra(SHADE_HINT));
    }

    /** 外壳里的影晶数 */
    public int umbral() {
        return data.extra(UMBRAL);
    }

    /** 凝结进度（秒） */
    public int condenseProgress() {
        return data.extraWide(CONDENSE_PROGRESS);
    }

    /** 要攒的凝结进度（秒） */
    public int condenseSeconds() {
        return data.extraWide(CONDENSE_SECONDS);
    }

    public CondenseState condenseState() {
        return CondenseState.byId(data.extra(CONDENSE_STATE));
    }

    /** 外壳里魔力护板、淡青护板的块数 */
    public int condenseCasing() {
        return data.extra(CONDENSE_CASING);
    }

    public int condensePresenceMRU() {
        return data.extra(CONDENSE_PRESENCE_MRU);
    }

    public boolean condenseEvent() {
        return data.extra(CONDENSE_EVENT) != 0;
    }

    /**
     * 界面上要不要显示凝结那一条：连着仓，而且凝结开着、在能凝结的维度里（默认所有维度），或者已经攒了进度。
     */
    public boolean showsCondense() {
        if (!linked()) {
            return false;
        }
        CondenseState state = condenseState();
        return condenseProgress() > 0
                || (state != CondenseState.DISABLED && state != CondenseState.NOT_FORMED && state != CondenseState.WRONG_DIMENSION);
    }
}
