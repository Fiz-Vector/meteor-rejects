package anticope.rejects.events;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class HandleBlockBreakingEvent {
    private static final HandleBlockBreakingEvent INSTANCE = new HandleBlockBreakingEvent();

    public BlockHitResult blockHitResult;
    public BlockPos blockPos;
    public Direction direction;

    public static HandleBlockBreakingEvent get(BlockHitResult blockHitResult, BlockPos blockPos, Direction direction) {
        INSTANCE.blockHitResult = blockHitResult;
        INSTANCE.blockPos = blockPos;
        INSTANCE.direction = direction;
        return INSTANCE;
    }
}
