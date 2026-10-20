package com.dayzhud.mod.recap;

import net.minecraftforge.common.ForgeConfigSpec;

/** Death recap (2.19.0): config/dayzhud-recap.toml. */
public final class RecapConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue WINDOW_SECONDS;
    public static final ForgeConfigSpec.BooleanValue NOTIFY_KILLER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("recap");
        ENABLED = b.comment(
                        "Shows a recap panel on the death screen: who killed you, with what, from how",
                        "far, whether it was a headshot, the damage you took just before dying, how",
                        "long you survived and how many kills you had this life.")
                .define("enabled", true);
        WINDOW_SECONDS = b.comment(
                        "The recap's damage list covers this many seconds before death.")
                .defineInRange("damageWindowSeconds", 30, 5, 600);
        NOTIFY_KILLER = b.comment(
                        "Tell a player who kills another player who they killed and from how far",
                        "(above the hotbar).")
                .define("notifyKiller", true);
        b.pop();
        SPEC = b.build();
    }

    private RecapConfig() {}
}
