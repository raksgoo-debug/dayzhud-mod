package com.dayzhud.mod.item;

import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A half-litre of drinking water.
 *
 * Uses the DRINK use animation deliberately: TemperatureSystem already cools the player by
 * 0.06 for anything drunk with that animation, matched on the animation rather than on item
 * ids so third-party canteens work. Using DRINK here means this bottle gets the cooling for
 * free and stays consistent with every other drink in the pack.
 *
 * Restores Thirst Was Taken's thirst when it is installed and falls back to vanilla
 * saturation when it is not, the same way the HUD's water gauge does.
 */
public class WaterBottleItem extends Item {

    private final int thirst;
    private final int quenched;

    public WaterBottleItem(Properties properties, int thirst, int quenched) {
        super(properties);
        this.thirst = thirst;
        this.quenched = quenched;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                                     net.minecraft.world.InteractionHand hand) {
        return net.minecraft.world.item.ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player) {
            if (!level.isClientSide) {
                boolean handled = ThirstWasTakenCompat.quench(player, thirst, quenched);
                if (!handled) {
                    // No thirst mod: the water still has to do something, and saturation is
                    // what the HUD reads in that case.
                    player.getFoodData().eat(1, 0.4f);
                }
            }
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK,
                    SoundSource.PLAYERS, 0.5f, 1.0f + level.random.nextFloat() * 0.1f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return stack;
    }
}
