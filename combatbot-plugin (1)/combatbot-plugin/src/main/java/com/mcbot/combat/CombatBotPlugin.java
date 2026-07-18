package com.mcbot.combat;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatBotPlugin extends JavaPlugin {

    private final Map<UUID, CombatBot> bots = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new CombatBotListener(this), this);
        getLogger().info("CombatBot 활성화 완료. /combatbot spawn <easy|normal|hard|brutal|impossible>");
    }

    @Override
    public void onDisable() {
        for (CombatBot bot : bots.values()) {
            bot.stop();
            bot.getEntity().remove();
        }
        bots.clear();
    }

    public CombatBot getBot(Mannequin mannequin) {
        return bots.get(mannequin.getUniqueId());
    }

    public void onBotRemoved(CombatBot bot) {
        bots.remove(bot.getEntity().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("이 명령어는 게임 안에서 플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("사용법: /combatbot spawn <easy|normal|hard|brutal|impossible> | /combatbot remove | /combatbot removeall");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                if (args.length < 2) {
                    player.sendMessage("난이도를 입력하세요: easy, normal, hard, brutal, impossible");
                    return true;
                }
                Difficulty difficulty = Difficulty.fromString(args[1]);
                if (difficulty == null) {
                    player.sendMessage("잘못된 난이도입니다. easy / normal / hard / brutal / impossible 중 선택하세요.");
                    return true;
                }
                spawnBot(player, difficulty);
            }
            case "remove" -> removeNearestBot(player);
            case "removeall" -> removeAllBots(player);
            default -> player.sendMessage("알 수 없는 하위 명령어입니다. spawn / remove / removeall");
        }
        return true;
    }

    private void spawnBot(Player player, Difficulty difficulty) {
        Location spawnLoc = player.getLocation().clone().add(
                player.getLocation().getDirection().setY(0).normalize().multiply(3));

        Mannequin mannequin = (Mannequin) player.getWorld().spawnEntity(spawnLoc, EntityType.MANNEQUIN);
        mannequin.setImmovable(false);

        CombatBot bot = new CombatBot(mannequin, difficulty);
        bots.put(mannequin.getUniqueId(), bot);
        bot.start(this);

        player.sendMessage("전투봇 소환됨 - 난이도: " + difficulty.getDisplayName());
    }

    private void removeNearestBot(Player player) {
        CombatBot closest = null;
        double closestDist = Double.MAX_VALUE;
        for (CombatBot bot : bots.values()) {
            if (!bot.getEntity().getWorld().equals(player.getWorld())) continue;
            double d = bot.getEntity().getLocation().distance(player.getLocation());
            if (d < closestDist) {
                closestDist = d;
                closest = bot;
            }
        }
        if (closest == null) {
            player.sendMessage("제거할 전투봇이 근처에 없습니다.");
            return;
        }
        closest.stop();
        closest.getEntity().remove();
        bots.remove(closest.getEntity().getUniqueId());
        player.sendMessage("전투봇을 제거했습니다.");
    }

    private void removeAllBots(Player player) {
        Iterator<Map.Entry<UUID, CombatBot>> it = bots.entrySet().iterator();
        int count = 0;
        while (it.hasNext()) {
            CombatBot bot = it.next().getValue();
            bot.stop();
            bot.getEntity().remove();
            it.remove();
            count++;
        }
        player.sendMessage(count + "개의 전투봇을 모두 제거했습니다.");
    }
}
