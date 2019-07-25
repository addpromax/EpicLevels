package com.songoda.epiclevels.listeners;

import com.songoda.epiclevels.EpicLevels;
import com.songoda.epiclevels.killstreaks.Killstreak;
import com.songoda.epiclevels.players.EPlayer;
import com.songoda.epiclevels.utils.Methods;
import com.songoda.epiclevels.utils.Rewards;
import com.songoda.epiclevels.utils.settings.Setting;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.stream.Collectors;

public class DeathListeners implements Listener {

    private final EpicLevels plugin;

    public DeathListeners(EpicLevels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;

        EPlayer damaged = plugin.getPlayerManager().getPlayer(event.getEntity().getUniqueId()); // A very sensitive person with a troubling past.
        EPlayer damager = plugin.getPlayerManager().getPlayer(event.getDamager().getUniqueId()); // The douche who ruined the guys life.

        if (Setting.BLACKLISTED_WORLDS.getStringList().stream()
                .anyMatch(worldStr -> worldStr.equalsIgnoreCase(event.getEntity().getWorld().getName())))
            return;

        if (damager.getLevel() < Setting.START_PVP_LEVEL.getInt()) {
            plugin.getLocale().getMessage("event.pvp.deny").sendPrefixedMessage(damager.getPlayer().getPlayer());
            event.setCancelled(true);
        } else if (damaged.getLevel() < Setting.START_PVP_LEVEL.getInt()) {
            plugin.getLocale().getMessage("event.pvp.denythem").sendPrefixedMessage(damager.getPlayer().getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {

        if (event.getEntity().getKiller() == null) return;

        if (Setting.BLACKLISTED_WORLDS.getStringList().stream()
                .anyMatch(worldStr -> worldStr.equalsIgnoreCase(event.getEntity().getWorld().getName())))
            return;

        double expPlayer = Setting.EXP_PLAYER.getDouble();
        double expMob = Setting.EXP_MOB.getDouble();

        Player player = event.getEntity().getKiller();
        EPlayer ePlayer = plugin.getPlayerManager().getPlayer(player);

        if (event.getEntity() instanceof Player) {
            Player killed = (Player) event.getEntity();
            EPlayer eKilled = plugin.getPlayerManager().getPlayer(killed);

            if (player == killed) return;

            if (!ePlayer.canGainExperience(killed.getUniqueId()) && Setting.ANTI_GRINDER.getBoolean()) {
                if (Setting.GRINDER_ALERT.getBoolean())
                    plugin.getLocale().getMessage("event.antigrinder.trigger")
                            .processPlaceholder("killed", killed.getPlayer().getName())
                            .sendPrefixedMessage(player);
                return;
            }

            eKilled.addDeath();

            double killedExpBefore = eKilled.getExperience();
            double killedExpAfter = eKilled.addExperience(0L - Setting.EXP_DEATH.getDouble());

            if (Setting.SEND_DEATH_MESSAGE.getBoolean())
                plugin.getLocale().getMessage("event.player.death").processPlaceholder("name", ChatColor.stripColor(player.getName()))
                        .processPlaceholder("exp", Methods.formatDecimal(-(killedExpAfter - killedExpBefore))).sendPrefixedMessage(killed);

            if (Setting.SEND_BROADCAST_DEATH_MESSAGE.getBoolean() && eKilled.getKillstreak() >= Setting.SEND_KILLSTREAK_ALERTS_AFTER.getInt())
                for (Player pl : Bukkit.getOnlinePlayers().stream().filter(p -> p != player && p != killed).collect(Collectors.toList()))
                    plugin.getLocale().getMessage("event.player.death.broadcast")
                            .processPlaceholder("killed", killed.getName())
                            .processPlaceholder("exp", player.getName())
                            .sendPrefixedMessage(pl);

            if (Setting.SEND_KILLSTREAK_BROKEN_MESSAGE.getBoolean() && eKilled.getKillstreak() >= Setting.SEND_KILLSTREAK_ALERTS_AFTER.getInt()) {
                plugin.getLocale().getMessage("event.killstreak.broken")
                        .processPlaceholder("streak", eKilled.getKillstreak())
                        .sendPrefixedMessage(killed);
                plugin.getLocale().getMessage("event.killstreak.broke")
                        .processPlaceholder("killed", killed.getName())
                        .processPlaceholder("streak", eKilled.getKillstreak())
                        .sendPrefixedMessage(player);
            }

            if (Setting.SEND_BROADCAST_BROKEN_KILLSTREAK.getBoolean())
                for (Player pl : Bukkit.getOnlinePlayers().stream().filter(p -> p != player && p != killed).collect(Collectors.toList()))
                    plugin.getLocale().getMessage("event.killstreak.brokenannounce")
                            .processPlaceholder("player", player.getName())
                            .processPlaceholder("killed", killed.getName())
                            .processPlaceholder("streak", eKilled.getKillstreak())
                            .sendPrefixedMessage(pl);

            eKilled.resetKillstreak();

            ePlayer.increaseKillstreak();
            ePlayer.addPlayerKill(killed.getUniqueId());

            int every = Setting.RUN_KILLSTREAK_EVERY.getInt();
            if (every != 0 && ePlayer.getKillstreak() % every == 0) {
                Killstreak def = plugin.getKillstreakManager().getKillstreak(-1);
                if (def != null)
                    Rewards.run(def.getRewards(), player, ePlayer.getKillstreak(), true);
                if (plugin.getKillstreakManager().getKillstreak(ePlayer.getKillstreak()) == null) return;
                Rewards.run(plugin.getKillstreakManager().getKillstreak(ePlayer.getKillstreak()).getRewards(), player, ePlayer.getKillstreak(), true);
            }

            double playerExpBefore = ePlayer.getExperience();
            double playerExpAfter = ePlayer.addExperience(expPlayer);

            if (Setting.SEND_PLAYER_KILL_MESSAGE.getBoolean())
                plugin.getLocale().getMessage("event.player.killed")
                        .processPlaceholder("name", ChatColor.stripColor(killed.getDisplayName()))
                        .processPlaceholder("exp", Methods.formatDecimal(playerExpAfter - playerExpBefore))
                        .sendPrefixedMessage(player);

        } else {
            ePlayer.addMobKill();
            double playerExpBefore = ePlayer.getExperience();
            double playerExpAfter = ePlayer.addExperience(expMob);

            if (Setting.SEND_MOB_KILL_MESSAGE.getBoolean()) {
                plugin.getLocale().getMessage("event.mob.killed")
                        .processPlaceholder("type", Methods.formatText(event.getEntity().getType().name(), true))
                        .processPlaceholder("exp", Methods.formatDecimal(playerExpAfter - playerExpBefore))
                        .sendPrefixedMessage(player);
            }
        }

    }
}
