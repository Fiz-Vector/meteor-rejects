package anticope.rejects.utils.bedrockminer.handle;

import anticope.rejects.utils.bedrockminer.Debug;
import anticope.rejects.utils.bedrockminer.utils.BlockBreakerUtils;
import anticope.rejects.utils.bedrockminer.utils.BlockPlacerUtils;
import anticope.rejects.utils.bedrockminer.utils.CheckingEnvironmentUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class TaskHandle {
    private final UUID id;
    private final Block block;
    private final BlockPos blockPos;
    private final ClientWorld world;

    @Nullable
    private BlockPos pistonBlockPos;
    @Nullable
    private BlockPos redstoneTorchBlockPos;
    @Nullable
    private BlockPos slimeBlockPos;

    private int timeoutCount;
    private final int timeoutCountMax;
    private boolean hasTried;
    private TaskStatus status;
    private int recycleCount;
    private boolean retrying;
    private int retryCount;
    private final int retryMax;
    private int delayCount;
    private final int delayCountMax;

    private int waitCount;

    /**
     * 构造函数
     *
     * @param block    目标方块
     * @param blockPos 目标方块所在位置
     * @param world    目标方块所在世界
     */
    public TaskHandle(Block block, BlockPos blockPos, ClientWorld world) {
        this.block = block;
        this.blockPos = blockPos;
        this.world = world;
        this.timeoutCountMax = 30;
        this.retryMax = 2;
        this.delayCountMax = 10;
        this.id = UUID.randomUUID();
        this.status = TaskStatus.INITIALIZATION;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public ClientWorld getWorld() {
        return world;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void tick() {
        Debug.info();
        Debug.info("[%s][%s][状态]: %s", id, timeoutCount, status);
        if (handleStatus()) {
            updateStatus();
        }
    }

    private boolean handleStatus() {
        var minecraftClient = MinecraftClient.getInstance();
        var player = minecraftClient.player;
        if (player == null) return true;
        switch (status) {
            case INITIALIZATION -> {
                this.pistonBlockPos = null;
                this.slimeBlockPos = null;
                this.redstoneTorchBlockPos = null;
                this.timeoutCount = 0;
                this.hasTried = false;
                this.retrying = false;
                this.delayCount = 0;
                this.status = TaskStatus.WAIT_GAME_UPDATE;  // 等待更新状态
                Debug.info("[%s][%s][状态处理][初始化]: 完成", id, timeoutCount);
            }
            case FIND_PISTON_POSITION -> {
                return onFindPistonPosition();
            }
            case FIND_SLIME_POSITION -> {
                return onFindSlimePosition();
            }
            case FIND_REDSTONE_TORCH_POSITION -> {
                return onFindRedstoneTorchPosition();
            }
            case PLACE_PISTON -> {
                return onPlacePiston();
            }
            case PLACE_SLIME_BLOCK -> {
                return onPlaceSlimeBlock();
            }
            case PLACE_REDSTONE_TORCH -> {
                return onPlaceRedStoneTorch();
            }
            case PLACE_ERROR_PISTON -> BlockBreakerUtils.breakPistonBlock(pistonBlockPos);
            case PLACE_ERROR_REDSTONE_TORCH -> BlockBreakerUtils.breakPistonBlock(redstoneTorchBlockPos);
            case EXTENDED_START -> {
                if (!hasTried && pistonBlockPos != null) {
                    Debug.info("[%s][%s][状态处理][执行]：准备开始", id, timeoutCount);
                    // 打掉活塞附近能充能的红石火把
                    var nearByRedstoneTorchPosList = CheckingEnvironmentUtils.findNearbyRedstoneTorch(world, pistonBlockPos);
                    for (BlockPos pos : nearByRedstoneTorchPosList) {
                        Debug.info("[%s][%s][状态处理][执行]：打掉红石火把, %s", id, timeoutCount, pos);
                        BlockBreakerUtils.instantBreakPistonBlock(pos);
                    }
                    // 打掉活塞
                    Debug.info("[%s][%s][状态处理][执行]：打掉活塞, %s", id, timeoutCount, pistonBlockPos);
                    if (BlockBreakerUtils.instantBreakPistonBlock(pistonBlockPos)) {
                        this.status = TaskStatus.FAILED;
                    }

                    // 放置朝下的活塞
                    Debug.info("[%s][%s][状态处理][执行]：放置朝下的活塞, %s", id, timeoutCount, pistonBlockPos);
                    BlockPlacerUtils.placement(pistonBlockPos, Direction.DOWN, Items.PISTON);

                    hasTried = true;
                    Debug.info("[%s][%s][状态处理][执行]：执行完成", id, timeoutCount);
                    status = TaskStatus.WAIT;  // 等待状态
                }
            }
            case PISTON_MOVING -> status = TaskStatus.WAIT_GAME_UPDATE;  // 等待更新状态
            case WAIT -> {
                // ping延迟补偿
                var playerListEntry = player.networkHandler.getPlayerListEntry(player.getUuid());
                if (playerListEntry != null) {
                    var ping = playerListEntry.getLatency();
                    var waitTick = ping / 50 + 1; // 50mspt=(1000ms/20tick)
                    Debug.info("[%s][%s][状态处理][等待]：延迟时间, %s", id, timeoutCount, ping);
                    Debug.info("[%s][%s][状态处理][等待]：等待总刻, %s", id, timeoutCount, waitTick);
                    if (waitCount++ > waitTick) {
                        status = TaskStatus.WAIT_GAME_UPDATE;
                        return false;
                    }
                }
            }
            case WAIT_GAME_UPDATE -> {
                if (waitCount > 0) {
                    waitCount = 0;
                }
                return true;
            }
            case TIME_OUT -> {
                status = TaskStatus.ITEM_RECYCLING;
                return false;
            }
            case FAILED -> {
                status = TaskStatus.ITEM_RECYCLING;
                Debug.info("[%s][%s][状态处理][失败]：准备物品回收", id, timeoutCount);
                if (retryCount++ < retryMax) {
                    Debug.info("[%s][%s][状态处理][失败]: 准备重试", id, timeoutCount);
                    retrying = true;
                }
                return false;
            }
            case ITEM_RECYCLING -> {
                // 回收任务超时直接退出任务
                if (recycleCount++ < 20) {
                    // 跳过几tick, 因为活塞位置可能在执行活塞回收任务
                    if (recycleCount < 3) {
                        return false;
                    }
                    // 活塞
                    if (pistonBlockPos != null) {
                        // 可能残留的活塞1
                        BlockPos pistonPos1 = pistonBlockPos.up();
                        if (world.getBlockState(pistonPos1).isOf(Blocks.PISTON)) {
                            Debug.info("[%s][%s][状态处理][物品回收][%s][活塞up]: %s", id, timeoutCount, recycleCount, pistonPos1);
                            BlockBreakerUtils.breakPistonBlock(pistonPos1);
                            return false;
                        }
                        // 可能残留的活塞2
                        BlockPos pistonPos2 = pistonBlockPos.up().up();
                        if (world.getBlockState(pistonPos2).isOf(Blocks.PISTON)) {
                            Debug.info("[%s][%s][状态处理][物品回收][%s][活塞upup]: %s", id, timeoutCount, recycleCount, pistonPos2);
                            BlockBreakerUtils.breakPistonBlock(pistonPos1);
                            return false;
                        }

                        // 活塞
                        var blockState = world.getBlockState(pistonBlockPos);
                        if (blockState.isOf(Blocks.PISTON)) {
                            Debug.info("[%s][%s][状态处理][物品回收][%s][活塞]: %s", id, timeoutCount, recycleCount, pistonBlockPos);
                            // 破坏活塞
                            if (BlockBreakerUtils.breakPistonBlock(pistonBlockPos)) {
                                pistonBlockPos = null;
                            }
                        } else if (blockState.isAir()) {
                            pistonBlockPos = null;
                        }
                        return false;
                    }
                    // 红石火把
                    if (redstoneTorchBlockPos != null) {
                        var blockState = world.getBlockState(redstoneTorchBlockPos);
                        if (blockState.isOf(Blocks.REDSTONE_TORCH) || blockState.isOf(Blocks.REDSTONE_WALL_TORCH)) {
                            Debug.info("[%s][%s][状态处理][物品回收][%s][红石火把]: %s", id, timeoutCount, recycleCount, redstoneTorchBlockPos);
                            if (BlockBreakerUtils.simpleBreakBlock(redstoneTorchBlockPos)) {
                                redstoneTorchBlockPos = null;
                            }
                        } else if (blockState.isAir()) {
                            redstoneTorchBlockPos = null;
                        }
                        return false;
                    }
                    // 粘液块
                    if (slimeBlockPos != null) {
                        var blockState = world.getBlockState(slimeBlockPos);
                        if (blockState.isOf(Blocks.SLIME_BLOCK)) {
                            Debug.info("[%s][%s][状态处理][物品回收][%s][粘液块]: %s", id, timeoutCount, recycleCount, slimeBlockPos);
                            if (BlockBreakerUtils.simpleBreakBlock(slimeBlockPos)) {
                                slimeBlockPos = null;
                            }
                            return false;
                        } else if (blockState.isAir()) {
                            slimeBlockPos = null;
                            return false;
                        }
                    }
                    if (pistonBlockPos == null && redstoneTorchBlockPos == null && slimeBlockPos == null) {
                        if (retrying) {
                            status = TaskStatus.RETRY;
                        } else {
                            status = TaskStatus.FINISH;
                        }
                        return false;
                    }
                }
                if (retrying) {
                    status = TaskStatus.RETRY;
                    return false;
                }
                status = TaskStatus.FINISH;
                return false;
            }
            case RETRY -> {
                this.status = TaskStatus.INITIALIZATION;
                Debug.info("[%s][%s][状态处理][重试]: 重新尝试", id, timeoutCount);
                return false;
            }
            case FINISH -> {
                return false;
            }
        }
        return true;
    }


    private void updateStatus() {
        //  延迟补偿
        if (status == TaskStatus.WAIT) {
            return;
        }

        // 检查超时
        if (timeoutCount++ > timeoutCountMax) {
            Debug.info("[%s][%s][玩家交互更新]: 超时", id, timeoutCount);
            status = TaskStatus.TIME_OUT;
            return;
        }

        // 检查目标方块
        var blockState = world.getBlockState(blockPos);
        if (blockState.isAir()) {
            Debug.info("[%s][%s][更新状态]: 目标方块(%s)已不存在, 准备执行回收任务", id, timeoutCount, block.getName().getString());
            status = TaskStatus.ITEM_RECYCLING;
            return;
        }


        // 检查活塞
        if (pistonBlockPos == null) {
            Debug.info("[%s][%s][更新状态]: 活塞位置未获取", id, timeoutCount);
            status = TaskStatus.FIND_PISTON_POSITION;
            return;
        } else {
            Debug.info("[%s][%s][更新状态]: 活塞位置已获取", id, timeoutCount);
            var pistonState = world.getBlockState(pistonBlockPos);
            // 检查活塞当前是否还处于技术性方块(36号方块)
            if (pistonState.isOf(Blocks.MOVING_PISTON)) {
                Debug.info("[%s][%s][更新状态]: 活塞移动中", id, timeoutCount);
                status = TaskStatus.PISTON_MOVING;
                return;
            }
            // 检查活塞状态
            if (pistonState.isOf(Blocks.PISTON)) {
                var direction = world.getBlockState(pistonBlockPos).get(PistonBlock.FACING);
                Debug.info("[%s][%s][更新状态]: 活塞已放置, %s", id, timeoutCount, direction);
                // 检查执行情况
                if (hasTried) {
                    Debug.info("[%s][%s][更新状态]: 已执行过, %s", id, timeoutCount, direction);
                    // 等待几个tick让活塞任务处理完成
                    if (delayCount++ > delayCountMax) {
                        status = TaskStatus.FAILED;
                    }
                    return;
                } else {
                    Debug.info("[%s][%s][更新状态]: 未执行过, %s", id, timeoutCount, direction);
                    if (direction == Direction.UP) {
                        // 活塞已充能(执行开始)
                        if (world.getBlockState(pistonBlockPos).get(PistonBlock.EXTENDED)) {
                            Debug.info("[%s][%s][更新状态]: 条件充足, 准备执行", id, timeoutCount);
                            status = TaskStatus.EXTENDED_START;
                            return;
                        }
                    } else {
                        Debug.info("[%s][%s][更新状态]: 活塞放置方向错误", id, timeoutCount);
                        status = TaskStatus.PLACE_ERROR_REDSTONE_TORCH;
                        return;
                    }
                }
            } else {
                if (!hasTried) {
                    Debug.info("[%s][%s][更新状态]: 活塞未放置", id, timeoutCount);
                    status = TaskStatus.PLACE_PISTON;
                    return;
                }
            }
        }


        // 检查红石火把基座
        if (!hasTried) {
            if (slimeBlockPos != null) {
                var slimeBlockState = world.getBlockState(slimeBlockPos);
                if (slimeBlockState.isReplaceable()) {
                    status = TaskStatus.PLACE_SLIME_BLOCK;
                    return;
                }
            }
        }


        // 检查红石火把
        if (!hasTried) {
            if (redstoneTorchBlockPos == null) {
                Debug.info("[%s][%s][更新状态]: 红石火把位置未获取", id, timeoutCount);
                status = TaskStatus.FIND_REDSTONE_TORCH_POSITION;
                return;
            }
            var redstoneTorchState = world.getBlockState(redstoneTorchBlockPos);
            var baseBlockState = world.getBlockState(redstoneTorchBlockPos.down());
            if (baseBlockState.isReplaceable()) {
                Debug.info("[%s][%s][更新状态]: 需要放置基座, 准备查找基座方块位置", id, timeoutCount);
                status = TaskStatus.FIND_SLIME_POSITION;
                return;
            }
            if (redstoneTorchState.isReplaceable()) {
                Debug.info("[%s][%s][更新状态]: 红石火把未放置", id, timeoutCount);
                status = TaskStatus.PLACE_REDSTONE_TORCH;
            } else if (redstoneTorchState.isOf(Blocks.REDSTONE_TORCH)) {
                Debug.info("[%s][%s][更新状态]: 红石火把已放置", id, timeoutCount);
            } else if (redstoneTorchState.isOf(Blocks.REDSTONE_WALL_TORCH)) {
                Debug.info("[%s][%s][更新状态]: 红石火把放置状态错误", id, timeoutCount);
                status = TaskStatus.PLACE_ERROR_REDSTONE_TORCH;
            }
        }
    }


    private boolean onFindPistonPosition() {
        var pos = blockPos.up();
        if (CheckingEnvironmentUtils.has2BlocksOfPlaceToPlacePiston(world, blockPos)) {
            Debug.info("[%s][%s][状态处理][查找活塞位置]: 完成, %s", id, timeoutCount, pos);
            pistonBlockPos = pos;
            status = TaskStatus.WAIT_GAME_UPDATE;  // 等待更新状态
            return true;
        }
        Debug.info("[%s][%s][状态处理][查找活塞位置]: 失败", id, timeoutCount);
        ChatUtils.error("Failed to place piston.");
        status = TaskStatus.FAILED;    // 失败状态
        return false;
    }

    private boolean onFindSlimePosition() {
        if (redstoneTorchBlockPos != null) {
            var pos = redstoneTorchBlockPos.down();
            if (world.getBlockState(pos).isReplaceable()) {
                slimeBlockPos = pos;
                Debug.info("[%s][%s][状态处理][查找基座位置]: 成功, %s", id, timeoutCount, slimeBlockPos);
                status = TaskStatus.WAIT_GAME_UPDATE;
                return true;
            }
        }
        Debug.info("[%s][%s][状态处理][查找基座位置]: 失败", id, timeoutCount);
        status = TaskStatus.FAILED;    // 失败状态
        return false;
    }

    private boolean onFindRedstoneTorchPosition() {
        if (pistonBlockPos != null) {
            // 优选1
            var redstoneTorchBlockPosList = CheckingEnvironmentUtils.findNearbyFlatBlockToPlaceRedstoneTorch(world, pistonBlockPos);
            if (redstoneTorchBlockPosList.size() > 0 && redstoneTorchBlockPos == null) {
                for (var pos : redstoneTorchBlockPosList) {
                    if (Block.sideCoversSmallSquare(world, pos.down(), Direction.UP)) {
                        Debug.info("[%s][%s][状态处理][查找红石火把位置]: 优选1, %s", id, timeoutCount, pos);
                        redstoneTorchBlockPos = pos;
                        break;
                    }
                }
            }

            // 优选2
            var possibleSlimeBlockPosList = CheckingEnvironmentUtils.findPossibleSlimeBlockPos(world, pistonBlockPos);
            if (possibleSlimeBlockPosList.size() > 0 && redstoneTorchBlockPos == null) {
                for (var pos : possibleSlimeBlockPosList) {
                    if (CheckingEnvironmentUtils.canPlace(pos, Blocks.SLIME_BLOCK, Direction.UP)) {
                        Debug.info("[%s][%s][状态处理][查找红石火把位置]: 优选2, 基座：%s", id, timeoutCount, pos);
                        Debug.info("[%s][%s][状态处理][查找红石火把位置]: 优选2, %s", id, timeoutCount, pos.up());
                        slimeBlockPos = pos;
                        redstoneTorchBlockPos = pos.up();
                        break;
                    }
                }
            }

            // 无优选，使用默认方法1
            if (redstoneTorchBlockPosList.size() > 0 && redstoneTorchBlockPos == null) {
                redstoneTorchBlockPos = redstoneTorchBlockPosList.get(0);
                Debug.info("[%s][%s][状态处理][查找红石火把位置]: 默认1, %s", id, timeoutCount, redstoneTorchBlockPos);
            }
            if (possibleSlimeBlockPosList.size() > 0 && redstoneTorchBlockPos == null) {
                slimeBlockPos = possibleSlimeBlockPosList.get(0);
                redstoneTorchBlockPos = possibleSlimeBlockPosList.get(0).up();
                Debug.info("[%s][%s][状态处理][查找红石火把位置]: 默认2, 基座：%s", id, timeoutCount, slimeBlockPos);
                Debug.info("[%s][%s][状态处理][查找红石火把位置]: 默认2, %s", id, timeoutCount, redstoneTorchBlockPos);
            }

            if (redstoneTorchBlockPos != null) {
                Debug.info("[%s][%s][状态处理][查找红石火把位置]: 成功, %s", id, timeoutCount, redstoneTorchBlockPos);
                status = TaskStatus.WAIT_GAME_UPDATE;  // 等待更新状态
                return true;
            }
            Debug.info("[%s][%s][状态处理][查找红石火把位置]: 失败", id, timeoutCount);
        }

        status = TaskStatus.FAILED;    // 失败状态
        return false;
    }

    private boolean onPlacePiston() {
        if (pistonBlockPos == null) {
            Debug.info("[%s][%s][状态处理][放置活塞]: 活塞位置未获取", id, timeoutCount);
            redstoneTorchBlockPos = null;
            slimeBlockPos = null;
            status = TaskStatus.FIND_PISTON_POSITION;  // 等待更新状态
            return false;
        }
        if (!CheckingEnvironmentUtils.has2BlocksOfPlaceToPlacePiston(world, blockPos)) {
            Debug.info("[%s][%s][状态处理][放置活塞]: 放置失败, 该位置可能无法放置或有实体存在, %s", id, timeoutCount, pistonBlockPos);
            ChatUtils.error("Failed to place piston.");
        }
        BlockPlacerUtils.placement(pistonBlockPos, Direction.UP, Items.PISTON);
        status = TaskStatus.WAIT;  // 等待更新状态
        return true;
    }

    private boolean onPlaceSlimeBlock() {
        if (slimeBlockPos == null) {
            Debug.info("[%s][%s][状态处理][放置基座方块]: 基座方块未知未获取, 无法放置", id, timeoutCount);
            status = TaskStatus.FIND_SLIME_POSITION;
            return false;
        }
        if (!CheckingEnvironmentUtils.canPlace(slimeBlockPos, Blocks.SLIME_BLOCK, Direction.UP)) {
            Debug.info("[%s][%s][状态处理][放置基座方块]: 放置失败, 该位置可能无法放置或有实体存在, %s", id, timeoutCount, slimeBlockPos);
            ChatUtils.error("Failed to place slime block.");
        }
        Debug.info("[%s][%s][状态处理][放置基座方块]: 放置, %s", id, timeoutCount, slimeBlockPos);
        BlockPlacerUtils.simpleBlockPlacement(slimeBlockPos, Items.SLIME_BLOCK);
        status = TaskStatus.WAIT;  // 等待更新状态
        return true;
    }

    private boolean onPlaceRedStoneTorch() {
        if (redstoneTorchBlockPos == null) {
            Debug.info("[%s][%s][状态处理][放置红石火把]: 红石火把未获取", id, timeoutCount);
            slimeBlockPos = null;
            status = TaskStatus.FIND_REDSTONE_TORCH_POSITION;
            return false;
        }
        Debug.info("[%s][%s][状态处理][放置红石火把]: 放置红石火把, %s", id, timeoutCount, redstoneTorchBlockPos);
        BlockPlacerUtils.simpleBlockPlacement(redstoneTorchBlockPos, Items.REDSTONE_TORCH);
        status = TaskStatus.WAIT;
        return true;
    }

}


