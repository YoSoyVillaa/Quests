package com.leonardobishop.quests.bukkit.tasktype.type;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.bukkit.util.chat.Chat;
import com.leonardobishop.quests.common.config.ConfigProblem;
import com.leonardobishop.quests.common.config.ConfigProblemDescriptions;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.QuestProgress;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class MobkillingCertainTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;

    public MobkillingCertainTaskType(BukkitQuestsPlugin plugin) {
        super("mobkillingcertain", TaskUtils.TASK_ATTRIBUTION_STRING, "Kill a set amount of a specific entity type.");
        this.plugin = plugin;
    }

    @Override
    public @NotNull List<ConfigProblem> validateConfig(@NotNull String root, @NotNull HashMap<String, Object> config) {
        ArrayList<ConfigProblem> problems = new ArrayList<>();
        if (config.get("mob") == null && config.get("mobs") == null) {
            TaskUtils.configValidateExists(root + ".mob", config.get("mob"), problems, "mob", super.getType());
        } else {
            Object configMob;
            String source;
            if (config.containsKey("mob")) {
                source = "mob";
            } else {
                source = "mobs";
            }
            configMob = config.get(source);
            List<String> checkBlocks = new ArrayList<>();
            if (configMob instanceof List) {
                checkBlocks.addAll((List) configMob);
            } else {
                checkBlocks.add(String.valueOf(configMob));
            }
            for (String mobName : checkBlocks) {
                try {
                    EntityType.valueOf(mobName);
                } catch (IllegalArgumentException ex) {
                    problems.add(new ConfigProblem(ConfigProblem.ConfigProblemType.WARNING,
                            ConfigProblemDescriptions.UNKNOWN_ENTITY_TYPE.getDescription(mobName), root + "." + source));
                }
            }

        }
        if (TaskUtils.configValidateExists(root + ".amount", config.get("amount"), problems, "amount", super.getType()))
            TaskUtils.configValidateInt(root + ".amount", config.get("amount"), problems, false, true, "amount");
        return problems;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        Entity mob = event.getEntity();

        if (mob == null || mob instanceof Player) {
            return;
        }

        if (killer == null) {
            return;
        }

        if (killer.hasMetadata("NPC")) return;

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(killer.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        for (Quest quest : super.getRegisteredQuests()) {
            if (qPlayer.hasStartedQuest(quest)) {
                QuestProgress questProgress = qPlayer.getQuestProgressFile().getQuestProgress(quest);

                for (Task task : quest.getTasksOfType(super.getType())) {
                    if (!TaskUtils.validateWorld(killer, task)) continue;

                    TaskProgress taskProgress = questProgress.getTaskProgress(task.getId());

                    if (taskProgress.isCompleted()) {
                        continue;
                    }

                    Object configEntity = task.getConfigValues().containsKey("mob") ? task.getConfigValue("mob") : task.getConfigValue("mobs");

                    List<String> configEntities = new ArrayList<>();
                    if (configEntity instanceof List) {
                        configEntities.addAll((List) configEntity);
                    } else {
                        configEntities.add(String.valueOf(configEntity));
                    }
                    boolean validMob = false;
                    for (String entry : configEntities) {
                        try {
                            EntityType entity = EntityType.valueOf(entry);
                            if (mob.getType() == entity) {
                                validMob = true;
                            }
                        } catch (IllegalArgumentException ignored) { }
                    }

                    if (!validMob) continue;

                    Object configName = task.getConfigValues().containsKey("name") ? task.getConfigValue("name") : task.getConfigValue("names");

                    if (configName != null) {
                        List<String> configNames = new ArrayList<>();
                        if (configName instanceof List) {
                            configNames.addAll((List) configName);
                        } else {
                            configNames.add(String.valueOf(configName));
                        }

                        boolean validName = false;
                        for (String name : configNames) {
                            name = Chat.color(name);
                            if (mob.getCustomName() != null && !mob.getCustomName().equals(name)) {
                                validName = true;
                                break;
                            }
                        }

                        if (!validName) continue;
                    }

                    int mobKillsNeeded = (int) task.getConfigValue("amount");

                    int progressKills;
                    if (taskProgress.getProgress() == null) {
                        progressKills = 0;
                    } else {
                        progressKills = (int) taskProgress.getProgress();
                    }

                    taskProgress.setProgress(progressKills + 1);

                    if (((int) taskProgress.getProgress()) >= mobKillsNeeded) {
                        taskProgress.setCompleted(true);
                    }
                }
            }
        }
    }

}
