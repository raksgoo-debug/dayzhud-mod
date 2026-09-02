package com.dayzhud.mod.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * A player's rouble balance, as a single number.
 *
 * Physical rouble notes ({@code tarkovdayz:rubble_100} and friends) are absorbed into this
 * on pickup - see {@link WalletEvents} - so cash never occupies inventory space. Notes can
 * be printed back out at a trader terminal if a player wants to hand money to someone.
 *
 * The balance is a long. Roubles in this economy run to seven figures and an int would
 * overflow at about two million, which a player selling a stack of LEDX can reach.
 */
public class Wallet implements INBTSerializable<CompoundTag> {

    private static final String KEY_BALANCE = "Balance";

    private long balance;

    public long getBalance() {
        return balance;
    }

    public void setBalance(long value) {
        this.balance = Math.max(0L, value);
    }

    /** Adds (or, with a negative amount, subtracts) and clamps at zero. Saturating. */
    public void add(long amount) {
        long next = this.balance + amount;
        // Guard the overflow rather than letting a huge sale wrap to negative.
        if (amount > 0 && next < this.balance) next = Long.MAX_VALUE;
        this.balance = Math.max(0L, next);
    }

    public boolean canAfford(long amount) {
        return amount >= 0 && this.balance >= amount;
    }

    /**
     * Spends the amount if it is affordable. Returns false and changes nothing otherwise,
     * so callers can use this as the single check-and-charge step rather than testing the
     * balance and deducting separately - which is where a double-spend would live.
     */
    public boolean spend(long amount) {
        if (!canAfford(amount)) return false;
        this.balance -= amount;
        return true;
    }

    public void copyFrom(Wallet other) {
        this.balance = other.balance;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_BALANCE, balance);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.balance = tag.getLong(KEY_BALANCE);
    }
}
