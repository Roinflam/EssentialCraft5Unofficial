package com.flameforge.essentialcraft5.espe;

/**
 * 某一面对外暴露 ESPE 的方式。机器按面决定（{@code MRUBlockEntity.espeAccess(side)}），
 * 对外的 capability 是按这个规则包过的 {@link SidedESPEHandler}，机器自己读写 {@link ESPEStorage} 不受限。
 * <p>
 * 原作所有 ESPE 方块都对任何面（包括 null）完整开放；米斯莱熔炉因此能被别人抽走 ESPE，移植后熔炉用 {@link #INSERT}（只进不出）。
 */
public enum ESPEAccess {
    /** 这一面不暴露 ESPE capability */
    NONE(false, false),
    /** 只能往里加（米斯莱熔炉） */
    INSERT(true, false),
    /** 只能往外取（纯粹的源头） */
    EXTRACT(false, true),
    /** 能进能出（原作的默认） */
    BOTH(true, true);

    public final boolean canInsert;
    public final boolean canExtract;

    ESPEAccess(boolean canInsert, boolean canExtract) {
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }
}
