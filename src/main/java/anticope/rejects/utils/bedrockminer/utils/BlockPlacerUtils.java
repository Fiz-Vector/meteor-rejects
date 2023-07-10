package anticope.rejects.utils.bedrockminer.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class BlockPlacerUtils {
    public static void simpleBlockPlacement(BlockPos blockPos, Item item) {
        InventoryManagerUtils.switchToItem(item);
        simpleBlockPlacement(blockPos);
    }


    public static void placement(BlockPos blockPos, Direction facing, Item item) {
        InventoryManagerUtils.switchToItem(item);
        placement(blockPos, facing);
    }

    /**
     * 简单方块放置
     *
     * @param blockPos 待放置位置
     */
    public static void simpleBlockPlacement(BlockPos blockPos) {
        placement(blockPos, Direction.UP);
    }

    /**
     * 活塞放置
     *
     * @param blockPos 活塞放置坐标
     * @param facing   活塞放置方向
     */
    public static void placement(BlockPos blockPos, Direction facing) {
        if (blockPos == null || facing == null) return;
        var client = MinecraftClient.getInstance();
        var world = client.world;
        var player = client.player;
        var networkHandler = client.getNetworkHandler();
        var interactionManager = client.interactionManager;
        if (world == null || player == null || networkHandler == null || interactionManager == null) return;
        if (!world.getBlockState(blockPos).isReplaceable()) return;
        var yaw = player.getYaw();
        var pitch = switch (facing) {
            case UP -> 90F;
            case DOWN -> -90F;
            default -> 0.0F;
        };
        // 模拟选中位置(凭空放置)
        var HitPos = blockPos.offset(facing.getOpposite());
        var HitVec3d = HitPos.toCenterPos().offset(facing, 0.5F);   // 放置面中心坐标
        var hitResult = new BlockHitResult(HitVec3d, facing, blockPos, false);
        // 发送修改视角数据包
        networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround()));
        // 发送交互方块数据包(同时通知客户端)
        interactionManager.sendSequencedPacket(world, (sequence) -> {
            interactionManager.interactBlockInternal(player, Hand.MAIN_HAND, hitResult);
            return new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, sequence);
        });
    }

}
