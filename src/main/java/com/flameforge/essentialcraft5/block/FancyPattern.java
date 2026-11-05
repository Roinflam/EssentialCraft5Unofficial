package com.flameforge.essentialcraft5.block;

/**
 * 花纹（原作 {@code BlockFancy.FancyBlockType}，按原作 meta 0~15 的顺序，也是创造栏里的顺序）。
 * 注册名段照原作的 {@code getName()}；贴图 {@code textures/block/fancyblocks/overlay/overlay_<去掉下划线>.png}（二值透明，叠在底图上）。
 * 合成树和产量见 {@code tools/gen/fancy_recipes.py}（原作 {@code RecipesCore.registerFancyBlockCrafts} L270~373）。
 */
public enum FancyPattern {

    ANCIENT_TILE("ancient_tile"),
    BIG_TILE("big_tile"),
    BRICK("brick"),
    FANCY_TILE("fancy_tile"),
    PRESSURED_TILE("pressured_tile"),
    SMALL_TILES("small_tiles"),
    TEMPLE("temple"),
    TILES("tiles"),
    FUTURISTIC_TILE("futuristic_tile"),
    MACHINE("machine"),
    RUNIC("runic"),
    NETHER_STAR("nether_star"),
    PLATE("plate"),
    PACKED_PLATE("packed_plate"),
    DOUBLE_PLATE("double_plate"),
    GEM("gem");

    private final String id;

    FancyPattern(String id) {
        this.id = id;
    }

    /** 注册名里的花纹段：{@code fancy_<底材>_<id>}、{@code fancy_mimic_<id>} */
    public String id() {
        return id;
    }

    /** 花纹贴图（相对 textures/）：{@code block/fancyblocks/overlay/overlay_<id 去掉下划线>} */
    public String overlayTexture() {
        return "block/fancyblocks/overlay/overlay_" + id.replace("_", "");
    }
}
