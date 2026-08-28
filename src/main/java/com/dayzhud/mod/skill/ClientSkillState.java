package com.dayzhud.mod.skill;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The client's copy of its own skill levels and body temperature, refreshed by
 * {@link SkillStatePacket}.
 *
 * Purely a DISPLAY cache. Every value here is computed server-side and pushed down; nothing
 * in this class is ever read back by the server, so a player editing it changes only what
 * their own HUD draws. That is the point of having moved stamina and temperature out of the
 * client in the first place.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkillState {

    private static final PlayerSkills SKILLS = new PlayerSkills();

    /** 0 = freezing, 1 = scorching, 0.5 = neutral. Starts neutral until the first sync. */
    private static float temperature01 = 0.5f;
    /** 0..1 of the player's current maximum, whatever Endurance has made that. */
    private static float stamina01 = 1f;
    private static boolean synced = false;

    private ClientSkillState() {}

    static void accept(PlayerSkills incoming, float temperature, float stamina) {
        SKILLS.copyFrom(incoming);
        temperature01 = temperature;
        stamina01 = stamina;
        synced = true;
    }

    public static int level(Skill skill) {
        return SKILLS.getLevel(skill);
    }

    public static float temperature01() {
        return temperature01;
    }

    /**
     * Raw synced stamina. The HUD reads VitalsTracker's smoothed view of this instead, because
     * syncs arrive several ticks apart and a bar that jumps in steps looks broken.
     */
    public static float stamina01() {
        return stamina01;
    }

    /**
     * False until the first packet lands. VitalsTracker uses this to fall back to its own
     * local temperature estimate for the handful of ticks between joining a world and the
     * first sync, so the HUD never flashes a wrong reading on join.
     */
    public static boolean isSynced() {
        return synced;
    }

    /**
     * Called on disconnect. Without this, skills from the last world would linger into the
     * next one and briefly size the stamina bar wrongly on the main menu or a new save.
     */
    public static void reset() {
        for (Skill skill : Skill.values()) SKILLS.setLevel(skill, 0);
        temperature01 = 0.5f;
        stamina01 = 1f;
        synced = false;
    }
}
