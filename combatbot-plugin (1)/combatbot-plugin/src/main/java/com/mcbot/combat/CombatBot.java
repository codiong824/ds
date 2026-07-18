package com.mcbot.combat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;

/**
 * 마네킹을 조종하는 PvP 전투 AI. 실제 PvP 4대 기술을 구현합니다.
 *
 *  - 스트레이핑(Strafe): 사거리 안에서 좌우로 불규칙하게 움직여 피격을 줄임
 *  - 블록 히트(Block Hit): 공격하는 그 순간만 방패를 내리고 그 외엔 항상 든 채로 버팀
 *  - 더블탭(W-tap): 한 대 친 직후 살짝 물러났다(S) 다시 붙어(W) 후속타를 노림
 *  - 콤보(공중 연계): 띄운 상대가 착지하지 못하게 계속 쫓아가며 연속 타격
 *
 * 상태(State): CHASING(추격) → FIGHTING(교전, 위 4기술 전부 이 안에서 발생) → KITING(크게 거리 벌리기)
 */
public class CombatBot {

    private enum State { CHASING, FIGHTING, KITING }

    private static final double ATTACK_RANGE = 3.0;
    private static final double ENGAGE_LEASH_RANGE = 5.0; // 이 범위 안이면 교전을 계속 유지(넉백으로 잠깐 멀어져도 CHASING으로 안 빠짐)
    private static final double SWORD_BASE_DAMAGE = 7.0; // 다이아몬드 검 기준
    private static final double CRIT_MULTIPLIER = 1.5;
    private static final double SPRINT_HIT_KNOCKBACK_BONUS = 0.45;

    private final Mannequin entity;
    private final Difficulty difficulty;
    private final Random random = new Random();

    private BukkitTask task;

    private State state = State.CHASING;

    private int ticksSinceLastAttack = 0;
    private boolean wasApproachingFast = false;
    private boolean shieldUp = true;
    private int strafeDirectionFlipTimer = 0;
    private int strafeSign = 1;
    private int jumpCritCooldown = 0;

    private int comboHitsLeft = 0;
    private int kiteTicksLeft = 0;
    private int wtapTicksLeft = 0;

    public CombatBot(Mannequin entity, Difficulty difficulty) {
        this.entity = entity;
        this.difficulty = difficulty;
        this.entity.setInvulnerable(false);
        this.entity.setImmovable(false);
        this.entity.customName(net.kyori.adventure.text.Component.text(
                "전투봇 [" + difficulty.getDisplayName() + "]"));
        this.entity.setCustomNameVisible(true);
        equip();
    }

    private void equip() {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) return;
        eq.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        eq.setItemInOffHand(new ItemStack(Material.SHIELD));
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    public Mannequin getEntity() { return entity; }
    public Difficulty getDifficulty() { return difficulty; }
    public boolean isShieldUp() { return shieldUp; }

    /** 매 tick 즉시 판단/반응합니다. */
    public void start(CombatBotPlugin plugin) {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead() || !entity.isValid()) {
                    cancel();
                    plugin.onBotRemoved(CombatBot.this);
                    return;
                }
                tick();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        Player target = findTarget();
        if (target == null) {
            shieldUp = true;
            return;
        }

        faceTarget(target);

        double distance = entity.getLocation().distance(target.getLocation());

        switch (state) {
            case CHASING -> {
                if (distance <= ATTACK_RANGE) {
                    state = State.FIGHTING;
                } else {
                    approach(target, 0.28);
                }
            }
            case KITING -> handleKiting(target, distance);
            case FIGHTING -> handleFighting(target, distance);
        }

        ticksSinceLastAttack++;
        if (jumpCritCooldown > 0) jumpCritCooldown--;
        if (kiteTicksLeft > 0) kiteTicksLeft--;
        if (wtapTicksLeft > 0) wtapTicksLeft--;
    }

    // ---------------------------------------------------------------- 타겟팅/회전

    private Player findTarget() {
        List<org.bukkit.entity.Entity> nearby = entity.getNearbyEntities(16, 16, 16);
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity e : nearby) {
            if (!(e instanceof Player p)) continue;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (!p.isValid() || p.isDead()) continue;
            double d = entity.getLocation().distance(p.getLocation());
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private void faceTarget(Player target) {
        Vector dir = target.getEyeLocation().toVector().subtract(entity.getEyeLocation().toVector());
        if (dir.lengthSquared() < 1.0E-6) return;
        double yaw = Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        double horizontal = Math.sqrt(dir.getX() * dir.getX() + dir.getZ() * dir.getZ());
        double pitch = Math.toDegrees(-Math.atan2(dir.getY(), horizontal));
        entity.setRotation((float) yaw, (float) pitch);
    }

    // ---------------------------------------------------------------- 이동

    private void approach(Player target, double speed) {
        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-6) return;
        dir.normalize().multiply(speed);
        wasApproachingFast = true;

        Vector velocity = entity.getVelocity();
        entity.setVelocity(new Vector(dir.getX(), velocity.getY(), dir.getZ()));

        if (entity.isOnGround() && isBlockedAhead()) {
            entity.setVelocity(entity.getVelocity().setY(0.42));
        }
    }

    private boolean isBlockedAhead() {
        Location front = entity.getLocation().add(
                entity.getLocation().getDirection().setY(0).normalize().multiply(0.4));
        return front.getBlock().getType().isSolid()
                && !front.getBlock().getRelative(0, 1, 0).getType().isSolid();
    }

    /** 스트레이핑(Strafe) - 좌우로 불규칙하게 움직여 상대의 조준/타이밍을 흔듭니다. */
    private void strafe(Player target) {
        strafeDirectionFlipTimer--;
        // 정해진 타이머 외에도 낮은 확률로 예고 없이 방향을 바꿔 더 불규칙하게 만듦
        if (strafeDirectionFlipTimer <= 0 || random.nextDouble() < 0.05) {
            strafeDirectionFlipTimer = 10 + random.nextInt(15);
            strafeSign *= -1;
        }
        Vector toTarget = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        toTarget.setY(0).normalize();
        Vector side = new Vector(-toTarget.getZ(), 0, toTarget.getX()).multiply(strafeSign);
        Vector velocity = entity.getVelocity();
        Vector move = side.multiply(0.18);
        entity.setVelocity(new Vector(move.getX(), velocity.getY(), move.getZ()));
    }

    /** 타겟 반대 방향으로 물러납니다 (더블탭의 "S", 카이팅의 "빠지기"). */
    private void retreat(Player target) {
        Vector away = entity.getLocation().toVector().subtract(target.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0E-6) return;
        away.normalize().multiply(0.24);
        Vector velocity = entity.getVelocity();
        entity.setVelocity(new Vector(away.getX(), velocity.getY(), away.getZ()));
    }

    private void jumpForCrit() {
        if (entity.isOnGround()) {
            entity.setVelocity(entity.getVelocity().setY(0.42));
            jumpCritCooldown = 20;
        }
    }

    // ---------------------------------------------------------------- 상태별 처리

    private void handleFighting(Player target, double distance) {
        // 콤보/W탭 진행 중에는 넉백으로 잠깐 멀어져도 교전을 유지한다
        boolean mustStayEngaged = comboHitsLeft > 0 || wtapTicksLeft > 0;
        if (distance > ENGAGE_LEASH_RANGE && !mustStayEngaged) {
            state = State.CHASING;
            return;
        }

        if (comboHitsLeft > 0) {
            continueCombo(target, distance);
            return;
        }
        if (wtapTicksLeft > 0) {
            handleWTap(target, distance);
            return;
        }

        if (distance > ATTACK_RANGE) {
            approach(target, 0.28); // 사거리 밖으로 밀려났으면 다시 붙는다
            return;
        }

        strafe(target); // 평소엔 스트레이핑
        if (jumpCritCooldown <= 0 && random.nextDouble() < difficulty.getCritChance() * 0.05) {
            jumpForCrit();
        }
        tryAttack(target);
    }

    private void handleKiting(Player target, double distance) {
        if (distance < Difficulty.KITE_DISTANCE) {
            retreat(target);
        } else {
            strafe(target);
        }

        boolean rechargedEnough = ticksSinceLastAttack >= Difficulty.FULL_CHARGE_TICKS;
        boolean minTimeElapsed = kiteTicksLeft <= 0;
        if (rechargedEnough && minTimeElapsed) {
            state = distance <= ATTACK_RANGE ? State.FIGHTING : State.CHASING;
        }
    }

    /** 더블탭(W-tap) - 앞부분은 S(뒤로), 뒷부분은 다시 W(앞으로)로 붙어 후속타를 노립니다. */
    private void handleWTap(Player target, double distance) {
        boolean retreatPhase = wtapTicksLeft > Difficulty.WTAP_DURATION_TICKS / 2;
        if (retreatPhase) {
            retreat(target);
        } else {
            approach(target, 0.32); // 다시 붙을 때는 조금 더 빠르게 (탭 임팩트)
        }

        if (wtapTicksLeft <= 0) {
            if (distance <= ATTACK_RANGE) {
                tryAttack(target); // 붙자마자 바로 후속타 시도
            }
        }
    }

    /** 공격 간격만 확인하고 즉시 공격합니다. */
    private void tryAttack(Player target) {
        if (ticksSinceLastAttack < difficulty.getAttackIntervalTicks()) return;

        boolean isPowerHit = performAttack(target, false);
        ticksSinceLastAttack = 0;
        if (!isPowerHit) return;

        // 우선순위: 콤보(공중 연계) > 더블탭 > 카이팅. 하나만 시도한다.
        if (difficulty.getComboMaxHits() > 0 && random.nextDouble() < difficulty.getComboChance()) {
            comboHitsLeft = difficulty.getComboMaxHits();
        } else if (random.nextDouble() < difficulty.getWtapChance()) {
            wtapTicksLeft = Difficulty.WTAP_DURATION_TICKS;
        } else if (random.nextDouble() < difficulty.getKiteChance()) {
            enterKiting();
        }
    }

    /** 콤보(공중 연계) - 상대가 착지하지 못하게 띄운 채로 계속 쫓아가며 때립니다. */
    private void continueCombo(Player target, double distance) {
        if (distance > ATTACK_RANGE) {
            approach(target, 0.4); // 넉백으로 멀어진 상대를 빠르게 쫓아가 다시 붙는다 (러쉬)
            return;
        }
        if (ticksSinceLastAttack < Difficulty.COMBO_INTERVAL_TICKS) return;

        performAttack(target, true); // loft=true - 착지 못하게 위로 더 띄운다
        ticksSinceLastAttack = 0;
        comboHitsLeft--;

        if (comboHitsLeft <= 0) {
            if (random.nextDouble() < difficulty.getKiteChance()) {
                enterKiting();
            } else if (random.nextDouble() < difficulty.getWtapChance()) {
                wtapTicksLeft = Difficulty.WTAP_DURATION_TICKS;
            }
        }
    }

    private void enterKiting() {
        state = State.KITING;
        kiteTicksLeft = Difficulty.KITE_MIN_TICKS + random.nextInt(10);
        comboHitsLeft = 0;
        wtapTicksLeft = 0;
    }

    /**
     * 실제 검격 한 번을 처리합니다.
     * @param loft true면 상대가 착지하지 못하도록 넉백의 수직 성분을 더 크게 줍니다 (콤보용).
     * @return 풀차징(충전 90% 이상)으로 제대로 들어간 "강타"였는지 여부
     */
    private boolean performAttack(Player target, boolean loft) {
        shieldUp = false; // 블록 히트: 때리는 그 순간에만 방패를 내림
        entity.swingMainHand();

        double chargeRatio = Math.min(1.0, (double) ticksSinceLastAttack / Difficulty.FULL_CHARGE_TICKS);
        double attackStrengthMultiplier = 0.2 + 0.8 * chargeRatio;
        double damage = SWORD_BASE_DAMAGE * attackStrengthMultiplier;
        boolean isCrit = false;

        if (chargeRatio >= 0.7 && !entity.isOnGround() && entity.getVelocity().getY() < 0
                && random.nextDouble() < difficulty.getCritChance()) {
            damage *= CRIT_MULTIPLIER;
            isCrit = true;
        }

        target.damage(damage, entity);

        Vector knockDir = target.getLocation().toVector()
                .subtract(entity.getLocation().toVector()).normalize();
        double knockStrength = 0.4 + (wasApproachingFast ? SPRINT_HIT_KNOCKBACK_BONUS : 0.0);
        double verticalKnock = loft ? 0.5 : 0.35;
        target.setVelocity(target.getVelocity().add(knockDir.multiply(knockStrength).setY(verticalKnock)));
        wasApproachingFast = false;

        if (isCrit) {
            entity.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 12);
        }

        shieldUp = true; // 블록 히트: 공격 직후 다시 방패를 든다
        return chargeRatio >= 0.9;
    }
}
