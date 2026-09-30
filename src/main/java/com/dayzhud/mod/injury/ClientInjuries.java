package com.dayzhud.mod.injury;

/** The local player's injuries as last sent by the server (InjurySyncPacket). */
public final class ClientInjuries {

    private static int light, heavy, pain, reliefSeconds;

    private ClientInjuries() {}

    static void accept(int[] v) {
        light = v[0];
        heavy = v[1];
        pain = v[2];
        reliefSeconds = v[3];
    }

    public static int light() {
        return light;
    }

    public static int heavy() {
        return heavy;
    }

    public static int pain() {
        return pain;
    }

    public static int reliefSeconds() {
        return reliefSeconds;
    }

    public static boolean inPain() {
        return reliefSeconds <= 0 && pain >= InjurySystem.PAIN_MILD;
    }
}
