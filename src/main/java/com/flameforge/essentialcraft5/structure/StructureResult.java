package com.flameforge.essentialcraft5.structure;

/**
 * 多方块结构的判定结果，见 {@link StructurePattern#match} 和 {@link StructureCache}。
 */
public enum StructureResult {
    /** 结构完整 */
    MATCH,
    /** 确定不完整：已加载的区块里至少有一格不符（能说出第一个不符的位置，见 {@link StructurePattern.Mismatch}） */
    MISMATCH,
    /**
     * 判断不了：结构有一部分落在还没完全加载的区块里，而已加载的部分都对得上。
     * 这时不应改动机器状态（原作 1.12 会顺手把区块加载起来，1.20 里绝不触发加载），StructureCache 会保留上次的确定结果。
     */
    UNKNOWN
}
