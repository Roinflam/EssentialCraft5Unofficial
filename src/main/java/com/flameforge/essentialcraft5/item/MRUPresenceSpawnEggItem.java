package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * MRU 聚集体刷怪蛋。对应原作 ItemSpawnEggEC 的 entityEgg:0（EntityMRUPresence）。原作一个物品按 meta 放 14 种实体，这里拆成每种活体一个原版式刷怪蛋；
 * 聚集体在 1.20 不是 Mob（原版 SpawnEggItem / ForgeSpawnEggItem 只收 Mob 类型），所以自己写。
 * <p>
 * 右键方块：在点到的那一面外侧那格（点到的方块没有碰撞箱时就是它自己那格，同原版刷怪蛋）正中放一团 {@value #MRU} MRU、平衡值 1.0 的聚集体，
 * 第一轮结算照常（flag = false）、MRU 归零也不消失（alwaysStay = true）。非创造模式消耗一个。
 * 出生点保护由服务端在调用本方法之前检查（原版对所有「对方块使用物品」都查）。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>原作 spawnCreature 先 spawnEntity，再从新实体上取 MRU 能力设 500；但它取的是方块用的 MRU_HANDLER_CAPABILITY
 *       （聚集体挂的是 MRU_HANDLER_ENTITY_CAPABILITY），拿到 null 抛空指针，被外面的 catch 吞掉，实体已经放出去了，存量是 0。这里真的给 500。</li>
 *   <li>原作放在方块底部（y 加上点到的方块碰撞箱的高度差）；聚集体悬浮、没有碰撞，这里放在格子正中，和 /ec5 mrucu create 一致。</li>
 *   <li>改过名的蛋不把名字带给聚集体（原作因为上面那个异常，spawnCreature 返回 null，起名那段也从没执行过）。</li>
 * </ul>
 */
public class MRUPresenceSpawnEggItem extends Item {

    public static final int MRU = 500;
    public static final float BALANCE = 1.0F;
    /** 蛋壳底色（深紫）和斑点色（纯净 MRU 的青色），客户端 GearClient 注册成两层物品颜色 */
    public static final int BACKGROUND_COLOR = 0x2A1745;
    public static final int HIGHLIGHT_COLOR = 0x4DE8E8;

    public MRUPresenceSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clicked);
        BlockPos pos = state.getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
        Player player = context.getPlayer();
        spawn(serverLevel, pos);
        ItemStack stack = context.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        return InteractionResult.CONSUME;
    }

    /** 在 pos 这格正中放一团 {@value #MRU} MRU 的聚集体 */
    public static MRUPresenceEntity spawn(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, center.x, center.y, center.z, MRU, BALANCE, false, true);
        level.addFreshEntity(presence);
        return presence;
    }
}
