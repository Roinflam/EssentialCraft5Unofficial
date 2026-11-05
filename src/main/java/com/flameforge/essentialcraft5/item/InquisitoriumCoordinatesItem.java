package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 审判庭坐标（{@code inquisitorium_coordinates}，第十批 10c，batch10.md §5.10.3）：辐射仓解码故障核心的产物，根标签记着
 * {@code Dim}（维度 id，解码出来的都是海纳尔）、{@code X}、{@code Z}——海纳尔里离解码处最近的一片暗影腐化之地（找不到时是解码处固定方向上的一点，见 {@code GlitchyCoreDecoder.fallbackRandom}）。
 * 召唤审判庭信标成功时也给召唤者补发 {@code arrived}（先做好信标再来的人没拿着坐标走过来）。
 * <ul>
 *   <li>主手或副手拿着、人在那个维度时，客户端每 10 刻在快捷栏上方写方向（八方位）和水平距离（{@code client/InquisitoriumClient}，
 *       客户端按 NBT 自己算，不发包）；不在那个维度时写「在海纳尔」。</li>
 *   <li>服务端每 20 刻（按玩家错开）查一次拿着它的玩家：在那个维度、水平距离 ≤ {@code general.inquisitorium.arrive_radius} 就发
 *       {@code guide/inquisitorium/arrived}（{@link #checkArrival}）。</li>
 *   <li>提示行写坐标。做审判庭信标时整份 NBT 带进信标（{@code recipe/KeepNbtRecipes} 的 nbt_source）。</li>
 * </ul>
 * GameTest 里「海纳尔」用 {@code ECHoanna.setTestHoannaOverride}：维度 id 是 {@code essentialcraft5:hoanna} 的坐标在被当成海纳尔的世界里也算到了。
 */
public class InquisitoriumCoordinatesItem extends Item {

    public static final String TAG_DIM = "Dim";
    public static final String TAG_X = "X";
    public static final String TAG_Z = "Z";
    /** 根标签里的三个键（信标方块实体、掉落表 copy_nbt 共用） */
    public static final List<String> KEYS = List.of(TAG_DIM, TAG_X, TAG_Z);
    /** 服务端多久查一次「到没到」 */
    public static final int ARRIVAL_INTERVAL = 20;

    /** 坐标：维度和水平位置 */
    public record Target(ResourceLocation dimension, int x, int z) {

        /** (x, z) 到这里的水平距离 */
        public double horizontalDistance(double fromX, double fromZ) {
            double dx = x + 0.5D - fromX;
            double dz = z + 0.5D - fromZ;
            return Math.sqrt(dx * dx + dz * dz);
        }

        /** 这个世界是不是坐标所在的维度（维度 id 一样；海纳尔另认 GameTest 的覆盖） */
        public boolean isIn(Level level) {
            return level.dimension().location().equals(dimension) || (dimension.equals(ECHoanna.ID) && ECHoanna.isHoanna(level));
        }

        public void write(CompoundTag tag) {
            tag.putString(TAG_DIM, dimension.toString());
            tag.putInt(TAG_X, x);
            tag.putInt(TAG_Z, z);
        }

        /** 从一个根标签读（三个键都在、维度 id 读得懂才算） */
        @Nullable
        public static Target read(@Nullable CompoundTag tag) {
            if (tag == null || !tag.contains(TAG_DIM, Tag.TAG_STRING) || !tag.contains(TAG_X, Tag.TAG_ANY_NUMERIC)
                    || !tag.contains(TAG_Z, Tag.TAG_ANY_NUMERIC)) {
                return null;
            }
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIM));
            return dimension == null ? null : new Target(dimension, tag.getInt(TAG_X), tag.getInt(TAG_Z));
        }
    }

    public InquisitoriumCoordinatesItem(Properties properties) {
        super(properties);
    }

    /** 一份指向 target 的坐标 */
    public static ItemStack create(Target target) {
        ItemStack stack = new ItemStack(ECInquisitorium.INQUISITORIUM_COORDINATES.get());
        target.write(stack.getOrCreateTag());
        return stack;
    }

    /** 物品上的坐标；没有（创造栏拿的空白坐标）返回 null */
    @Nullable
    public static Target target(ItemStack stack) {
        return Target.read(stack.getTag());
    }

    /**
     * 拿着坐标的玩家到了没有：在坐标所在的维度、水平距离 ≤ {@code arrive_radius} 就发 {@code guide/inquisitorium/arrived}，返回 true。
     * 服务端每 {@value #ARRIVAL_INTERVAL} 刻调一次（{@link #inventoryTick}；GameTest 直接调）。
     */
    public static boolean checkArrival(ServerPlayer player, ItemStack stack) {
        Target target = target(stack);
        if (target == null || !target.isIn(player.level())) {
            return false;
        }
        if (target.horizontalDistance(player.getX(), player.getZ()) > ECInquisitorium.Rules.values().arriveRadius()) {
            return false;
        }
        PlayerPentacleBlockEntity.award(player, ECInquisitorium.ADVANCEMENT_ARRIVED, ECInquisitorium.ADVANCEMENT_CRITERION);
        return true;
    }

    /**
     * 八方位（0 北、1 东北、2 东、3 东南、4 南、5 西南、6 西、7 西北），dx / dz 是目标减自己（北是 −Z、东是 +X）。两端共用，GameTest 直接核。
     */
    public static int heading(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        if (degrees < 0D) {
            degrees += 360D;
        }
        return (int) Math.round(degrees / 45D) % 8;
    }

    /** 方位的语言键（{@code direction.essentialcraft5.0 ~ 7}） */
    public static String headingKey(int heading) {
        return "direction.essentialcraft5." + Math.floorMod(heading, 8);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!selected && player.getOffhandItem() != stack) {
            return;
        }
        if ((player.tickCount + player.getId()) % ARRIVAL_INTERVAL == 0) {
            checkArrival(player, stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Target target = target(stack);
        if (target == null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.inquisitorium_coordinates.blank").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(where(target).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.inquisitorium_coordinates.usage").withStyle(ChatFormatting.GRAY));
    }

    /** 「海纳尔 X 123，Z −456」这样的一行（维度不是海纳尔时写维度 id） */
    public static MutableComponent where(Target target) {
        return target.dimension().equals(ECHoanna.ID)
                ? Component.translatable("tooltip.essentialcraft5.inquisitorium_coordinates.hoanna", target.x(), target.z())
                : Component.translatable("tooltip.essentialcraft5.inquisitorium_coordinates.other", target.dimension().toString(), target.x(), target.z());
    }
}
