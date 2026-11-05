package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.client.BootstrapClient;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.matrix.MatrixPool;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.matrix.MatrixSiphon;
import com.flameforge.essentialcraft5.matrix.MatrixTooltip;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 灵魂石：记下一个玩家，放进矩阵换能器后把这个玩家的 UBMRU 换成 MRU。对应原作 ItemSoulStone（soulStone:0）。
 * 只能靠绿宝石仪式拿到（见 ritual.GroundRituals），没有合成配方。
 * <p>
 * 右键绑定到自己（已绑定别人的也会改绑，同原作），潜行右键解除绑定。
 * NBT：owner（UUID，原作 playerName 里存的其实是 UUID 字符串）、owner_name（绑定时的玩家名，给 tooltip 显示用）。
 * <p>
 * <b>第十三批 13a</b>（batch13.md §3.1.4）：绑定自己的灵魂石潜行右键多了两种用法，都只认主手，按这个顺序判（{@link #use}）：
 * <ol>
 *   <li><b>矩阵虹吸</b>（四阶起）：对着 {@value MatrixSiphon#REACH} 格内的聚集体按住 {@value MatrixSiphon#USE_TICKS} 刻，见 {@link MatrixSiphon}。
 *       面前 {@value MatrixSiphon#REACH} 格开外、6 格以内有聚集体时什么都不做（两端判定可能不一致，不拿它去开关换入）。</li>
 *   <li><b>开关矩阵容量的换入</b>（二阶起，有容量时）：{@code MatrixPool.toggleAbsorb}，动作栏说现在开着还是关着。</li>
 *   <li>别的情况照原作解除绑定（零、一阶没有容量，或者石头绑的是别人）。二阶起自己的石头潜行右键不再解绑；要换人，让对方右键改绑。</li>
 * </ol>
 * 副手拿着自己的石头（一阶起为了看聚集体常这么拿）潜行右键什么都不做：不开关、不解绑，免得主手空着时误触。
 * 潜行右键矩阵淬炼台时交给台子处理（{@link #doesSneakBypassUse}：存试炼储备），不开关换入。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>tooltip：原作按石头上的玩家判断「数据存在」，显示的却是<b>查看者自己</b>的 UBMRU 和矩阵属性（bug）。
 *       这里显示绑定者的名字；UBMRU、矩阵属性、风之眷顾只有绑定者本人看自己的石头时才显示
 *       （客户端只有本人的玩家数据；原作靠「包 1」向服务端要别人的数据，那个包已删除，不恢复）。</li>
 *   <li>血魔法灌注版（meta 1）和血魔法生命源质提示属于联动，不移植。</li>
 * </ul>
 */
public class SoulStoneItem extends Item {

    private static final String TAG_OWNER = "owner";
    private static final String TAG_OWNER_NAME = "owner_name";
    /** 潜行右键这块方块时让方块自己处理（矩阵淬炼台：存试炼储备），不走 {@link #use} */
    private static final ResourceLocation MATRIX_CRUCIBLE = EssentialCraft5.id("matrix_crucible");

    public SoulStoneItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static UUID getOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(TAG_OWNER_NAME);
    }

    public static void bind(ItemStack stack, UUID owner, String ownerName) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_OWNER, owner);
        tag.putString(TAG_OWNER_NAME, ownerName);
    }

    public static void bind(ItemStack stack, Player player) {
        bind(stack, player.getUUID(), player.getGameProfile().getName());
    }

    public static void unbind(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            // 只删自己的键，改名之类的保留
            tag.remove(TAG_OWNER);
            tag.remove(TAG_OWNER_NAME);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean sneaking = player.isShiftKeyDown();
        UUID owner = getOwner(stack);
        if (sneaking && owner == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (sneaking && owner.equals(player.getUUID())) {
            // 副手拿着自己的石头多半是为了一阶的矩阵视觉：潜行右键时主手空着或主手的东西没反应，原版会轮到副手，
            // 这时不开关、也不解绑（解绑、开关都只认主手）
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResultHolder.pass(stack);
            }
            // 虹吸：两端各判一次（阶随同步包到客户端、聚集体是同步的实体），结论一致才不会一边在按一边没按
            if (MatrixSiphon.canStart(level, player)) {
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            // 四阶起面前 REACH + 1 格内有聚集体：当作没对准，什么都不做。5 格上下两端可能判得不一样（客户端开始按、服务端判不能），
            // 不在这里退回去开关，免得服务端悄悄把换入关了、客户端却在拉弓
            if (MatrixRank.atLeast(player, MatrixSiphon.RANK) && nearPresence(level, player)) {
                return InteractionResultHolder.pass(stack);
            }
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null && MatrixPool.capacity(data) > 0) {
                if (!level.isClientSide) {
                    MatrixPool.toggleAbsorb(player);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        if (!level.isClientSide) {
            if (sneaking) {
                unbind(stack);
            } else {
                bind(stack, player);
            }
        }
        // 原作返回 PASS（没有挥手动作）；这里返回成功，让玩家看到点了有反应
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 视线前方 {@code MatrixSiphon.REACH} + 1 格内有没有聚集体（虹吸的边缘判定用，比开始按住的 5 格宽一格） */
    private static boolean nearPresence(Level level, Player player) {
        MRUPresenceEntity found = MRUMoverItem.findTarget(level, player);
        return found != null && found.isAlive() && found.distanceTo(player) <= MatrixSiphon.REACH + 1D;
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return MATRIX_CRUCIBLE.equals(ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock()));
    }

    // ------------------------------------------------------------------ 虹吸（按住）

    @Override
    public int getUseDuration(ItemStack stack) {
        return MatrixSiphon.USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /** 按住的过程中：对不准了就停；客户端在聚集体和手之间冒粒子 */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof Player player)) {
            return;
        }
        MRUPresenceEntity target = MatrixSiphon.target(level, player);
        if (target == null) {
            player.stopUsingItem();
            return;
        }
        if (level.isClientSide && remaining % 2 == 0) {
            Vec3 to = player.getEyePosition().add(player.getLookAngle().scale(0.6D)).subtract(0D, 0.3D, 0D);
            Vec3 at = target.position().lerp(to, level.random.nextDouble());
            level.addParticle(ParticleTypes.WITCH, at.x, at.y, at.z, 0D, 0D, 0D);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player && player.getUUID().equals(getOwner(stack))) {
            MatrixSiphon.siphon(player, this);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        UUID owner = getOwner(stack);
        if (owner == null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.unbound").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.tracking",
                Component.literal(getOwnerName(stack)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.DARK_GRAY));

        // 客户端只有本人的玩家数据，所以只有绑定者本人才看得到数值（客户端类只在 lambda 执行时加载）
        Player viewer = level != null && level.isClientSide
                ? DistExecutor.<Player>unsafeCallWhenOn(Dist.CLIENT, () -> BootstrapClient::localPlayer)
                : null;
        if (viewer == null || !owner.equals(viewer.getUUID())) {
            return;
        }
        IPlayerData data = PlayerDataEvents.get(viewer);
        if (data == null) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.ubmru",
                Component.literal(String.valueOf(data.getUBMRU())).withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.matrix_type",
                matrixTypeName(data.getMatrixType())).withStyle(ChatFormatting.DARK_GRAY));
        // 矩阵阶、图谱、容量，以及别处登记的矩阵行（第十三批 13a）
        MatrixTooltip.appendSoulStone(viewer, data, tooltip);
        if (data.isWindbound()) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.windbound").withStyle(ChatFormatting.GREEN));
        }
    }

    /**
     * 矩阵属性的名字和颜色（原作 ItemSoulStone.addInformation 里的 switch）：
     * 0 中性（绿）、1 混沌（红）、2 冰冻（蓝）、3 魔力（粉）、4 暗影（灰），其它显示「未知」（绿）。
     * 矩阵投影、枪械透镜以后也可以用。
     */
    public static Component matrixTypeName(int type) {
        return switch (type) {
            case 0 -> Component.translatable("gui.essentialcraft5.matrix_type.neutral").withStyle(ChatFormatting.GREEN);
            case 1 -> Component.translatable("gui.essentialcraft5.matrix_type.chaos").withStyle(ChatFormatting.RED);
            case 2 -> Component.translatable("gui.essentialcraft5.matrix_type.frozen").withStyle(ChatFormatting.BLUE);
            case 3 -> Component.translatable("gui.essentialcraft5.matrix_type.magic").withStyle(ChatFormatting.LIGHT_PURPLE);
            case 4 -> Component.translatable("gui.essentialcraft5.matrix_type.shade").withStyle(ChatFormatting.GRAY);
            default -> Component.translatable("gui.essentialcraft5.matrix_type.unknown").withStyle(ChatFormatting.GREEN);
        };
    }
}
