package dev.leonetic.util.inventory;

import dev.leonetic.util.inventory.strategy.HoldingStrategy;
import dev.leonetic.util.inventory.strategy.HotbarStrategy;
import dev.leonetic.util.inventory.strategy.InventoryStrategy;
import dev.leonetic.util.inventory.strategy.SwapStrategy;
import dev.leonetic.util.traits.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class InventoryUtil implements Util {
    static final Result NONE = new Result(-1, ItemStack.EMPTY, ResultType.NONE);

    public static final int OFFHAND_SWAP_BUTTON = 40;

    public static final EnumSet<ResultType> HOTBAR_SCOPE = EnumSet.of(ResultType.OFFHAND, ResultType.HOTBAR);
    public static final EnumSet<ResultType> INVENTORY_SCOPE = EnumSet.of(ResultType.OFFHAND, ResultType.INVENTORY);
    public static final EnumSet<ResultType> FULL_SCOPE = EnumSet.of(ResultType.OFFHAND, ResultType.HOTBAR, ResultType.INVENTORY);
    public static final EnumSet<ResultType> PLACE_SCOPE = EnumSet.of(ResultType.HOTBAR, ResultType.INVENTORY);

    private static final List<SwapStrategy> STRATEGIES = List.of(
            HoldingStrategy.INSTANCE,
            HotbarStrategy.INSTANCE,
            InventoryStrategy.INSTANCE
    );

    private InventoryUtil() {
        throw new AssertionError();
    }

    public static ItemStack cursor() {
        return mc.player.containerMenu.getCarried();
    }

    public static int selected() {
        return mc.player.getInventory().getSelectedSlot();
    }

    public static int fireworkRefireTicks(ItemStack stack) {
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        int flight = fireworks != null ? fireworks.flightDuration() : 1;
        return 10 * (1 + flight) - 1;
    }

    public static void click(int slot, int button, ClickType type) {
        int id = mc.player.containerMenu.containerId;
        mc.gameMode.handleInventoryMouseClick(id, slot, button, type, mc.player);
    }

    public static void withSwap(Result result, Runnable action) {
        withSwap(result, r -> action.run());
    }

    public static void withSwap(Result result, Consumer<Result> action) {
        int lastSlot = selected();
        if (InventoryUtil.swap(result)) {
            action.accept(result);
            InventoryUtil.swapBack(result, lastSlot);
        }
    }

    public static void swap(int to) {
        if (to < 0 || to > 8) return;
        mc.player.getInventory().setSelectedSlot(to);
        mc.gameMode.ensureHasSentCarriedItem();
    }

    public static boolean swap(Result result) {
        for (SwapStrategy strategy : STRATEGIES) {
            if (strategy.swap(result))
                return true;
        }
        return false;
    }

    public static boolean swapBack(Result result, int last) {
        for (SwapStrategy strategy : STRATEGIES) {
            if (strategy.swapBack(last, result))
                return true;
        }
        return false;
    }

    public static boolean swapSilent(Result result) {
        if (result.holding()) return true;
        if (result.type() != ResultType.HOTBAR && result.type() != ResultType.INVENTORY) return false;
        int containerSlot = result.type() == ResultType.HOTBAR ? result.slot() + 36 : result.slot();
        click(containerSlot, selected(), ClickType.SWAP);
        return true;
    }

    // Container-click the found item into a specific hotbar slot (a "click slot
    // on top of the hotbar update") instead of changing the carried slot. Used
    // when another swap already owns the hotbar this tick: we move the item into
    // the slot that swap is holding. Calling it again with the same args restores
    // the two slots, since ClickType.SWAP is its own inverse.
    public static boolean altSwapInto(Result result, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8 || !result.found()) return false;
        if (result.type() == ResultType.OFFHAND) return true;
        if (result.type() == ResultType.HOTBAR && result.slot() == hotbarSlot) return true;
        if (result.type() != ResultType.HOTBAR && result.type() != ResultType.INVENTORY) return false;
        swapToHotbarSlot(result.slot(), hotbarSlot);
        return true;
    }

    public static boolean swapBackSilent(Result result) {
        return swapSilent(result);
    }

    public static Result find(Item target, EnumSet<ResultType> scopes) {
        return find(stack -> stack.is(target), scopes);
    }

    public static Result find(Predicate<ItemStack> predicate, EnumSet<ResultType> scopes) {
        return find((item, scope) -> scopes.contains(scope) && predicate.test(item));
    }

    public static void swapToOffhand(int inventorySlot) {
        int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        click(containerSlot, OFFHAND_SWAP_BUTTON, ClickType.SWAP);
    }

    public static void swapToHotbarSlot(int inventorySlot, int hotbarSlot) {
        int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        click(containerSlot, hotbarSlot, ClickType.SWAP);
    }

    public static Result find(BiPredicate<ItemStack, ResultType> predicate) {
        ItemStack offhand = mc.player.getOffhandItem();
        if (predicate.test(offhand, ResultType.OFFHAND)) {
            return Result.fromOffhand(offhand);
        }

        for (int i = 0; i < 36; i++) {
            ItemStack item = mc.player.getInventory().getItem(i);
            ResultType type = i < 9 ? ResultType.HOTBAR : ResultType.INVENTORY;
            if (predicate.test(item, type)) {
                return new Result(i, item, type);
            }
        }

        return NONE;
    }
}
