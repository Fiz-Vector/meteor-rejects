package anticope.rejects.utils.bedrockminer.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;


public class BlockBreakerUtils {

    public static final Item[] tools = new Item[]{Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE};

    /**
     * 破坏活塞方块
     *
     * @param pos 位置
     * @return true为破坏成功，false表示正在破坏（需要等到下一个tick处理）
     */
    public static boolean instantBreakPistonBlock(BlockPos pos) {
        return instantBreakBlock(pos, Direction.UP, tools);
    }

    /**
     * 破坏活塞方块
     *
     * @param pos 位置
     * @return true为破坏成功，false表示正在破坏（需要等到下一个tick处理）
     */
    public static boolean breakPistonBlock(BlockPos pos) {
        return breakBlock(pos, Direction.UP, tools);
    }


    public static boolean instantBreakBlock(BlockPos pos, Item... useItem) {
        return instantBreakBlock(pos, Direction.UP, useItem);
    }

    public static boolean simpleBreakBlock(BlockPos pos, Item... useItem) {
        return breakBlock(pos, Direction.UP, useItem);
    }


    public static boolean instantBreakBlock(BlockPos blockPos, Direction direction, Item... useItem) {
        var client = MinecraftClient.getInstance();
        var world = client.world;
        var player = client.player;
        var interactionManager = client.interactionManager;
        if (world == null || player == null || interactionManager == null || blockPos == null) return false;
        var blockState = world.getBlockState(blockPos);
        // 获取玩家背包中可以使用的物品清单
        if (useItem.length == 0) {
            useItem = new Item[]{player.getInventory().getMainHandStack().getItem()};
        }
        var itemStacks = InventoryManagerUtils.getPlayerInventoryUsableItemSlotMap(useItem);
        for (var itemSlot : itemStacks.keySet()) {
            ItemStack itemStack = itemStacks.get(itemSlot);
            // 检查耐久是否发起警告(剩余耐久<=检查值)
            if (InventoryManagerUtils.isItemDamageWarning(itemStack, 10)) {
                continue;
            }
            // 检查目标方块是否可以瞬间破坏的(0.05秒内完成也就是1tick)
            if (InventoryManagerUtils.isInstantBreakingBlock(blockState, itemStack)) {
                InventoryManagerUtils.switchToSlot(itemSlot);        // 切换到物品
                interactionManager.attackBlock(blockPos, direction);     // 攻击方块
                return world.getBlockState(blockPos).isAir();
            }
        }
        return false;
    }


    public static boolean breakBlock(BlockPos blockPos, Direction direction, Item... useItem) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        PlayerEntity player = minecraftClient.player;
        ClientWorld world = minecraftClient.world;
        ClientPlayerInteractionManager interactionManager = minecraftClient.interactionManager;
        if (player == null || world == null || interactionManager == null || blockPos == null) {
            return false;
        }
        // 获取玩家背包中可以使用的物品清单
        if (useItem.length == 0) {
            useItem = new Item[]{player.getInventory().getMainHandStack().getItem()};
        }
        var itemStacks = InventoryManagerUtils.getPlayerInventoryUsableItemSlotMap(useItem);
        for (var itemSlot : itemStacks.keySet()) {
            InventoryManagerUtils.switchToSlot(itemSlot);    // 切换到物品
            // 更新方块正在破坏进程
            if (interactionManager.updateBlockBreakingProgress(blockPos, direction)) {
                minecraftClient.particleManager.addBlockBreakingParticles(blockPos, direction);
            }
            // 检查是否已经成功
            return world.getBlockState(blockPos).isAir();
        }
        return false;
    }

}