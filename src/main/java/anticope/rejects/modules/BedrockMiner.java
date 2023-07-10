package anticope.rejects.modules;

import anticope.rejects.MeteorRejectsAddon;
import anticope.rejects.events.HandleBlockBreakingEvent;
import anticope.rejects.utils.bedrockminer.handle.TaskHandle;
import anticope.rejects.utils.bedrockminer.handle.TaskStatus;
import anticope.rejects.utils.bedrockminer.utils.InventoryManagerUtils;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class BedrockMiner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final LinkedList<TaskHandle> tasks = new LinkedList<>();

    private final Setting<List<Block>> whitelist = sgGeneral.add(new BlockListSetting.Builder()
            .name("whitelist")
            .description("Whether this block can be broken by bedrock miner.")
            .defaultValue(Blocks.BEDROCK, Blocks.END_GATEWAY, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME)
            .build()
    );

    private final Setting<Integer> taskLimit = sgGeneral.add(new IntSetting.Builder()
            .name("task-limit")
            .description("Concurrent task limit.")
            .defaultValue(3)
            .build()
    );

    private final Setting<Boolean> excavator = sgGeneral.add(new BoolSetting.Builder()
            .name("excavator")
            .description("Add bedrock blocks in the selected area to the task list.")
            .defaultValue(false)
            .onChanged(b -> tasks.clear())
            .build()
    );

    private BlockPos start;
    private BlockBox box;
    private ExcavateStatus excavateStatus = ExcavateStatus.SEL_START;

    public BedrockMiner() {
        super(MeteorRejectsAddon.CATEGORY, "bedrock-miner", "Allows you to break bedrock under certain conditions. Left click to break bedrock.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) toggle();
        tasks.clear();
        excavateStatus = ExcavateStatus.SEL_START;
        if (mc.player.isCreative()) {
            ChatUtils.error("Only works in survival mode.");
            toggle();
        }
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (!excavator.get() || event.action != KeyAction.Press || event.button != GLFW_MOUSE_BUTTON_RIGHT || mc.currentScreen != null) {
            return;
        }

        if (mc.crosshairTarget instanceof BlockHitResult result) {
            if (excavateStatus == ExcavateStatus.SEL_START) {
                start = result.getBlockPos();
                excavateStatus = ExcavateStatus.SEL_END;
            } else if (excavateStatus == ExcavateStatus.SEL_END) {
                box = BlockBox.create(start, result.getBlockPos());
                excavateStatus = ExcavateStatus.WORKING;
                if (!checkConditions()) toggle();
                else {
                    for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
                        addBlock(pos.mutableCopy());
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!excavator.get()) return;
        switch (excavateStatus) {
            case SEL_START, SEL_END -> {
                if (mc.crosshairTarget instanceof BlockHitResult result) {
                    event.renderer.box(result.getBlockPos(), new Color(255, 255, 0, 150), new Color(255, 255, 255, 150), ShapeMode.Lines, 0);
                }
            }
            case WORKING -> {
                event.renderer.box(Box.from(box), new Color(255, 255, 0, 150), new Color(255, 255, 255, 150), ShapeMode.Lines, 0);
                if (tasks.isEmpty()) {
                    excavateStatus = ExcavateStatus.SEL_START;
                    info("Excavation completed.");
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (tasks.isEmpty()) return;
        if (!checkConditions() || mc.player.isCreative()) return;

        int count = 0;
        Iterator<TaskHandle> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            var currentTask = iterator.next();
            if (currentTask.getWorld() != mc.world) {
                iterator.remove();
                continue;
            }
            if (currentTask.getBlockPos().isWithinDistance(mc.player.getEyePos(), 3.5f)) {
                if (currentTask.getStatus() == TaskStatus.FINISH) iterator.remove();
                currentTask.tick();
                if (++count >= taskLimit.get()) break;
            }
        }
    }

    @EventHandler
    private void onBreakBlock(HandleBlockBreakingEvent event) {
        if (excavator.get() || !checkConditions() || mc.player.isCreative()) return;
        addBlock(event.blockPos);
    }

    private void addBlock(BlockPos blockPos) {
        Block b = mc.world.getBlockState(blockPos).getBlock();
        if (!whitelist.get().contains(b)) return;
        for (TaskHandle targetBlock : tasks) {
            if (targetBlock.getBlockPos().equals(blockPos))
                return;
        }
        TaskHandle targetBlock = new TaskHandle(b, blockPos, mc.world);
        tasks.add(targetBlock);
    }


    public boolean checkConditions() {
        String msg = "";
        if (mc.player.isCreative()) {
            msg += "Only works in survival mode. ";
        }
        if (InventoryManagerUtils.getInventoryItemCount(Items.PISTON) < 2) {
            msg += "You need more than 2 pistons. ";
        }
        if (InventoryManagerUtils.getInventoryItemCount(Items.REDSTONE_TORCH) < 1) {
            msg += "You need more than 1 redstone torches. ";
        }
        if (InventoryManagerUtils.getInventoryItemCount(Items.SLIME_BLOCK) < 1) {
            msg += "You need more than 2 slime blocks. ";
        }
        if (!InventoryManagerUtils.canInstantlyMinePiston()) {
            msg += "You need a diamond or netherite pick with efficiency 5 and haste 2. ";
        }
        if (!msg.isEmpty()) {
            info(msg);
            return false;
        }
        return true;
    }

    private enum ExcavateStatus {
        SEL_START,
        SEL_END,
        WORKING,
    }
}
