package anticope.rejects.utils.bedrockminer.utils;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;

import java.util.HashMap;


public class InventoryManagerUtils {
    public static HashMap<Integer, ItemStack> getPlayerInventoryUsableItemSlotMap(Item... items) {
        var map = new HashMap<Integer, ItemStack>();
        var client = MinecraftClient.getInstance();
        var player = client.player;
        var interactionManager = client.interactionManager;
        var networkHandler = client.getNetworkHandler();
        if (player != null && interactionManager != null && networkHandler != null) {
            var playerInventory = player.getInventory();
            for (int i = 0; i < playerInventory.size(); i++) {
                var itemStack = playerInventory.getStack(i);
                for (var item : items) {
                    if (itemStack.isOf(item)) {
                        map.put(i, itemStack);
                    }
                }
            }
        }
        return map;
    }

    public static void switchToSlot(int slot) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        var interactionManager = client.interactionManager;
        var networkHandler = client.getNetworkHandler();
        if (player == null || interactionManager == null || networkHandler == null) return;
        var inventory = player.getInventory();
        // 如果当前槽位在热键栏中
        if (PlayerInventory.isValidHotbarIndex(slot)) {
            inventory.selectedSlot = slot;
        } else {
            interactionManager.pickFromInventory(slot); // 切换物品到热键槽位
        }
        networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(inventory.selectedSlot)); // 发送更新手持物品的数据包
    }

    /**
     * 将玩家手持的物品切换为指定的物品
     *
     * @param item 要切换到的物品
     */
    public static void switchToItem(Item item) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        var interactionManager = client.interactionManager;
        var networkHandler = client.getNetworkHandler();
        if (player == null || interactionManager == null || networkHandler == null) return;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            var stack = inventory.getStack(i);
            if (stack.isEmpty() || !stack.isOf(item)) continue;
            switchToSlot(i);
        }
    }

    /**
     * 判断物品的耐久度是否小于等于给定的最小值
     *
     * @param itemStack 要检查耐久度的物品堆栈
     * @param minDamage 最小的剩余耐久度
     * @return 如果物品的剩余耐久度小于等于最小值则返回true，否则返回false
     */
    public static boolean isItemDamageWarning(ItemStack itemStack, int minDamage) {
        int damageMax = itemStack.getMaxDamage();   // 获取物品的最大耐久度
        if (damageMax > 0) {
            int damage = itemStack.getDamage();     // 获取物品的已使用耐久度
            int damageSurplus = damageMax - damage; // 计算物品的剩余耐久度
            return damageSurplus <= minDamage;      // 如果剩余耐久度小于等于给定的最小值，则返回true
        }
        return false;   // 如果物品没有耐久度，则返回false
    }

    /**
     * 判断玩家是否可以瞬间破坏活塞方块
     *
     * @return 如果可以瞬间破坏活塞方块则返回true，否则返回false
     */
    public static boolean canInstantlyMinePiston() {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null) return false;
        var playerInventory = player.getInventory();
        for (int i = 0; i < playerInventory.size(); i++) {
            if (isInstantBreakingBlock(Blocks.PISTON.getDefaultState(), playerInventory.getStack(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否可以瞬间破坏方块
     *
     * @param blockState 要破坏的方块状态
     * @param itemStack  使用工具/物品破坏方块
     * @return true为可以瞬间破坏
     */
    public static boolean isInstantBreakingBlock(BlockState blockState, ItemStack itemStack) {
        float hardness = blockState.getBlock().getHardness();       // 当前方块硬度
        if (hardness < 0) return false;                             // 无硬度(如基岩无法破坏)
        float speed = getBlockBreakingSpeed(blockState, itemStack); // 当前破坏速度
        return speed > (hardness * 30);
    }

    /**
     * 获取方块破坏所需的总时间
     *
     * @param blockState 要破坏的方块状态
     * @param itemStack  使用工具/物品破坏方块
     * @return 当前物品破坏该方块所需的时间 (单位为秒)
     */
    public static float getBlockBreakingTotalTime(BlockState blockState, ItemStack itemStack) {
        var hardness = blockState.getBlock().getHardness();         // 当前方块硬度
        if (hardness < 0) return -1;
        var speed = getBlockBreakingSpeed(blockState, itemStack);   // 当前工具的破坏速度系数
        return (float) ((hardness * 1.5) / speed);
    }

    /**
     * 获取当前物品能够破坏指定方块的破坏速度.
     *
     * @param blockState 要破坏的方块状态
     * @param itemStack  使用工具/物品破坏方块
     * @return 当前物品破坏该方块所需的时间（单位为 tick）
     */
    private static float getBlockBreakingSpeed(BlockState blockState, ItemStack itemStack) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null) return 0;
        var toolSpeed = itemStack.getMiningSpeedMultiplier(blockState);  // 当前物品的破坏系数速度
        // 根据工具的"效率"附魔增加破坏速度
        if (toolSpeed > 1.0F) {
            int toolLevel = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, itemStack);
            if (toolLevel > 0 && !itemStack.isEmpty()) {
                toolSpeed += (float) (toolLevel * toolLevel + 1);
            }
        }
        // 根据玩家"急迫"状态效果增加破坏速度
        if (StatusEffectUtil.hasHaste(player)) {
            toolSpeed *= 1.0F + (float) (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2F;
        }
        // 根据玩家"挖掘疲劳"状态效果减缓破坏速度
        if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            float k;
            StatusEffectInstance statusEffect = player.getStatusEffect(StatusEffects.MINING_FATIGUE);   //采矿疲劳;
            if (statusEffect == null) {
                return 0;
            }
            k = switch (statusEffect.getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            toolSpeed *= k;
        }
        // 如果玩家在水中并且没有"水下速掘"附魔，则减缓破坏速度
        if (player.isSubmergedIn(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) {
            toolSpeed /= 5.0F;
        }
        // 如果玩家不在地面上，则减缓破坏速度
        if (!player.isOnGround()) {
            toolSpeed /= 5.0F;
        }
        return toolSpeed;
    }

    /*** 获取背包物品数量 ***/
    public static int getInventoryItemCount(Item item) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null) return 0;
        var playerInventory = player.getInventory();
        return playerInventory.count(item);
    }
}
