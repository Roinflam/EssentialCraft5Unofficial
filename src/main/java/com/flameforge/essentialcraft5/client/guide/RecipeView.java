package com.flameforge.essentialcraft5.client.guide;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一条配方在研究手册里怎么画：机器（顶上一行的小图标 + 名字）、原料格（Ingredient + 数量）、产物、说明文字、可选的背景图。
 * 模块把自己的配方类型转成它，用 {@link GuideRecipeViews#register} 登记，配方页就能画这个类型了。
 * <p>
 * 两种排版：
 * <ul>
 *   <li><b>自动</b>（格子都不写坐标）：原料每行最多 3 格、靠右贴着箭头，箭头右边是产物（每列最多 3 个），说明文字居中写在下面（薰衣草色）。
 *       和内置的工作台 / 熔炉图同宽同位置，同一页上下排列时对得齐。</li>
 *   <li><b>手动</b>（写了 background，或者任何一个格子写了坐标）：全部格子都要写坐标（相对图的左上角，18×18 的格子左上角），
 *       有背景就先画背景，{@link Builder#arrow} 给了位置就在那里画会动的箭头。</li>
 * </ul>
 * 例（米斯莱熔炉：原料 ×数量 → 产物，写 ESPE）：
 * <pre>{@code
 * RecipeView.builder()
 *         .station(ECMithriline.MITHRILINE_FURNACE.get())
 *         .input(recipe.getInput(), recipe.getCount())
 *         .output(recipe.getResultItem(GuideRecipeViews.registryAccess()))
 *         .note(Component.translatable("gui.essentialcraft5.espe", Figures.number(recipe.getEspe())))
 *         .build();
 * }</pre>
 */
public final class RecipeView {

    /** 格子没写坐标 */
    public static final int AUTO = Integer.MIN_VALUE;

    /**
     * 一个原料格。
     *
     * @param count 数量，画在格子右下角（每个候选物品都按这个数量显示）
     * @param x     手动排版时格子左上角相对图的位置，自动排版为 {@link #AUTO}
     */
    public record Input(Ingredient ingredient, int count, int x, int y) {
    }

    public record Output(ItemStack stack, int x, int y) {
    }

    /**
     * 背景图（手动排版用）：从贴图的 (u, v) 取 width × height 画在图的左上角。
     *
     * @param textureWidth 贴图文件的实际尺寸
     */
    public record Background(ResourceLocation texture, int u, int v, int width, int height, int textureWidth, int textureHeight) {
    }

    private final ItemStack station;
    @Nullable
    private final Component stationName;
    private final List<Input> inputs;
    private final List<Output> outputs;
    private final List<Component> notes;
    @Nullable
    private final Background background;
    private final int arrowX;
    private final int arrowY;
    private final boolean manual;

    private RecipeView(Builder builder) {
        this.station = builder.station;
        this.stationName = builder.stationName;
        this.inputs = List.copyOf(builder.inputs);
        this.outputs = List.copyOf(builder.outputs);
        this.notes = List.copyOf(builder.notes);
        this.background = builder.background;
        this.arrowX = builder.arrowX;
        this.arrowY = builder.arrowY;
        this.manual = builder.manual();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 机器图标，可为 EMPTY（不画顶上那一行） */
    public ItemStack station() {
        return station;
    }

    /** 机器名；没指定时用图标物品的名字 */
    @Nullable
    public Component stationName() {
        return stationName;
    }

    public List<Input> inputs() {
        return inputs;
    }

    /** 产物；为空时画配方自己的 getResultItem */
    public List<Output> outputs() {
        return outputs;
    }

    public List<Component> notes() {
        return notes;
    }

    @Nullable
    public Background background() {
        return background;
    }

    public int arrowX() {
        return arrowX;
    }

    public int arrowY() {
        return arrowY;
    }

    /** 是否手动排版（有背景或格子写了坐标） */
    public boolean manual() {
        return manual;
    }

    public static final class Builder {

        private ItemStack station = ItemStack.EMPTY;
        @Nullable
        private Component stationName;
        private final List<Input> inputs = new ArrayList<>();
        private final List<Output> outputs = new ArrayList<>();
        private final List<Component> notes = new ArrayList<>();
        @Nullable
        private Background background;
        private int arrowX = AUTO;
        private int arrowY = AUTO;

        private Builder() {
        }

        /** 做这个配方的机器：顶上一行画它的小图标和名字（取物品名，不另写译文） */
        public Builder station(ItemLike machine) {
            this.station = new ItemStack(machine);
            return this;
        }

        public Builder station(ItemStack icon, @Nullable Component name) {
            this.station = icon.copy();
            this.stationName = name;
            return this;
        }

        public Builder input(Ingredient ingredient) {
            return input(ingredient, 1);
        }

        public Builder input(Ingredient ingredient, int count) {
            inputs.add(new Input(Objects.requireNonNull(ingredient), Math.max(1, count), AUTO, AUTO));
            return this;
        }

        /** 手动排版的原料格，(x, y) 是 18×18 格子左上角相对图左上角的位置 */
        public Builder input(Ingredient ingredient, int count, int x, int y) {
            inputs.add(new Input(Objects.requireNonNull(ingredient), Math.max(1, count), x, y));
            return this;
        }

        public Builder output(ItemStack stack) {
            outputs.add(new Output(stack.copy(), AUTO, AUTO));
            return this;
        }

        public Builder output(ItemStack stack, int x, int y) {
            outputs.add(new Output(stack.copy(), x, y));
            return this;
        }

        /** 一行说明（薰衣草色、居中；太长会折行）。例：「60 ESPE」「每 tick 50 MRU × 400」。compact 页只显示第一行 */
        public Builder note(Component text) {
            notes.add(Objects.requireNonNull(text));
            return this;
        }

        /** 背景图（手动排版）。背景的尺寸就是图的尺寸（下面另加说明文字） */
        public Builder background(ResourceLocation texture, int u, int v, int width, int height, int textureWidth, int textureHeight) {
            this.background = new Background(texture, u, v, width, height, textureWidth, textureHeight);
            return this;
        }

        /** 手动排版时在 (x, y) 画会动的箭头（22×15）；自动排版不用管 */
        public Builder arrow(int x, int y) {
            this.arrowX = x;
            this.arrowY = y;
            return this;
        }

        private boolean manual() {
            if (background != null) {
                return true;
            }
            for (Input input : inputs) {
                if (input.x() != AUTO) {
                    return true;
                }
            }
            for (Output output : outputs) {
                if (output.x() != AUTO) {
                    return true;
                }
            }
            return false;
        }

        /** 手动排版时有格子没写坐标会抛异常（在登记的函数里抛出，手册会跳过这条配方并打日志） */
        public RecipeView build() {
            if (manual()) {
                for (Input input : inputs) {
                    if (input.x() == AUTO || input.y() == AUTO) {
                        throw new IllegalStateException("手动排版（有背景或写了坐标）时每个原料格都要写坐标");
                    }
                }
                for (Output output : outputs) {
                    if (output.x() == AUTO || output.y() == AUTO) {
                        throw new IllegalStateException("手动排版（有背景或写了坐标）时每个产物格都要写坐标");
                    }
                }
            }
            return new RecipeView(this);
        }
    }
}
