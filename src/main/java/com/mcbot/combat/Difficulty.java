package com.mcbot.combat;

/**
 * 난이도별 전투 파라미터.
 * comboChance  - 상대를 띄운 뒤 착지 못하게 계속 몰아치는 콤보(공중 연계) 시도 확률
 * wtapChance   - 한 대 친 직후 살짝 물러났다 다시 붙는 더블탭(W-tap) 시도 확률
 * kiteChance   - 한바탕 몰아친 뒤 크게 거리를 벌리는 치고빠지기 시도 확률
 */
public enum Difficulty {

    //            표시명    공격간격(tick)  크리티컬확률  콤보최대타수  콤보확률  W탭확률  카이팅확률
    EASY       ("쉬움",    6,             0.05,        0,          0.00,    0.00,   0.00),
    NORMAL     ("보통",    8,             0.15,        1,          0.15,    0.15,   0.05),
    HARD       ("어려움",  10,            0.30,        2,          0.30,    0.35,   0.25),
    BRUTAL     ("극악",    10,            0.45,        3,          0.45,    0.50,   0.40),
    IMPOSSIBLE ("불가능",  10,            0.60,        3,          0.55,    0.65,   0.55);

    /** 무기(검)가 완전히 재충전되는 데 걸리는 tick 수 (바닐라 1.9+ 검 기준 근사치). */
    public static final int FULL_CHARGE_TICKS = 10;
    /** 콤보(공중 연계) 연속 타격 사이 간격. */
    public static final int COMBO_INTERVAL_TICKS = 4;
    /** W-tap 한 사이클(뒤로-다시 앞으로) 지속 tick 수. */
    public static final int WTAP_DURATION_TICKS = 6;
    /** 카이팅(치고 빠지기) 시 유지하려는 거리. */
    public static final double KITE_DISTANCE = 4.5;
    /** 카이팅 최소 지속 시간. */
    public static final int KITE_MIN_TICKS = 15;

    private final String displayName;
    private final int attackIntervalTicks;
    private final double critChance;
    private final int comboMaxHits;
    private final double comboChance;
    private final double wtapChance;
    private final double kiteChance;

    Difficulty(String displayName, int attackIntervalTicks, double critChance,
               int comboMaxHits, double comboChance, double wtapChance, double kiteChance) {
        this.displayName = displayName;
        this.attackIntervalTicks = attackIntervalTicks;
        this.critChance = critChance;
        this.comboMaxHits = comboMaxHits;
        this.comboChance = comboChance;
        this.wtapChance = wtapChance;
        this.kiteChance = kiteChance;
    }

    public static Difficulty fromString(String raw) {
        if (raw == null) return null;
        switch (raw.toLowerCase()) {
            case "easy": case "쉬움": return EASY;
            case "normal": case "보통": return NORMAL;
            case "hard": case "어려움": return HARD;
            case "brutal": case "극악": return BRUTAL;
            case "impossible": case "불가능": return IMPOSSIBLE;
            default: return null;
        }
    }

    public String getDisplayName() { return displayName; }
    public int getAttackIntervalTicks() { return attackIntervalTicks; }
    public double getCritChance() { return critChance; }
    public int getComboMaxHits() { return comboMaxHits; }
    public double getComboChance() { return comboChance; }
    public double getWtapChance() { return wtapChance; }
    public double getKiteChance() { return kiteChance; }
}
