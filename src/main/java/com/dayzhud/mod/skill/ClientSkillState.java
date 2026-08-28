package com.dayzhud.mod.skill;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The client's copy of its own skill levels and body temperature, refreshed by
 * {@link SkillStatePacket}.
 *
 * WHY THE CLIENT NEEDS THIS AT ALL: two things are computed client-side and so need the
 * numbers locally - the stamina bar (Endurance changes its size and drain) and the HUD's
 * temperature gauge. Everything with actual consequences (health, damage, hunger, cold
 * damage) is applied server-side from the authoritative capability; this cache is for
 * DISPLAY and for the local stamina model only. Nothing here is trusted by the server.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkillState {

    private static final PlayerSkills SKILLS = new PlayerSkills();

    /** 0 = freezing, 1 = scorching, 0.5 = neutral. Starts neutral until the first sync. */
    private static float temperature01 = 0.5f;
    private static boolean synced = false;

    private ClientSkillState() {}

    static void accept(PlayerSkills incoming, float temperature) {
        SKILLS.copyFrom(incoming);
        temperature01 = temperature;
        synced = true;
    }

    public static int level(Skill skill) {
        return SKILLS.getLevel(skill);
    }

    public static float temperature01() {
        return temperature01;
    }

    /**
     * False until the first packet lands. The stamina tracker uses this to fall back to its
     * own local temperature estimate for the handful of ticks between joining a world and
     * the first sync, so the HUD never flashes a wrong reading on join.
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
        synced = false;
    }
}
