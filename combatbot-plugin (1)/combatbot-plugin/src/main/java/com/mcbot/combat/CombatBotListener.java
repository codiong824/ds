package com.mcbot.combat;

import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 플레이어가 봇을 때렸을 때 처리합니다.
 * 방패가 올라가 있으면(shieldUp) 대부분의 근접 데미지를 막습니다 (도끼는 예외 - 방어 무시).
 * W-tap 넉백 상쇄 로직은 제거되었습니다 - 맞은 넉백은 그대로 적용됩니다.
 */
public class CombatBotListener implements Listener {

    private final CombatBotPlugin plugin;

    public CombatBotListener(CombatBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBotDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mannequin mannequin)) return;
        CombatBot bot = plugin.getBot(mannequin);
        if (bot == null) return;

        boolean attackerHasAxe = event.getDamager() instanceof org.bukkit.entity.Player p
                && p.getInventory().getItemInMainHand().getType().name().endsWith("_AXE");

        if (bot.isShieldUp() && !attackerHasAxe) {
            event.setDamage(event.getDamage() * 0.05);
        }
    }
}
