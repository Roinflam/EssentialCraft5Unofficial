package com.flameforge.essentialcraft5.enrichment;

/**
 * 富集仓外壳上的部件（接收器、输出器、存储方块、状态检测器、红石控制器）。对应原作 IStructurePiece。
 * 稳定器不是部件（不用方块实体、不用认领），方块类实现 {@link EnrichmentStabilizer} 即可。
 * 控制器扫描外壳时认领它们（{@link EnrichmentLink#bind}），部件通过 {@link EnrichmentLink#controller()} 找回控制器。
 */
public interface EnrichmentPart {

    EnrichmentLink enrichmentLink();

    /** 是否算作存储方块（每个给控制器加一份容量） */
    default boolean countsAsHoldingChamber() {
        return false;
    }
}
