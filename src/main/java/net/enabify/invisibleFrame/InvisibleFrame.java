package net.enabify.invisibleFrame;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class InvisibleFrame extends JavaPlugin implements Listener {

    private NamespacedKey invisibleFrameKey;
    private final Set<UUID> invisibleFrameMode = new HashSet<>();

    @Override
    public void onEnable() {
        getLogger().info("InvisibleFramePlugin has been enabled!");
        
        // Foliaの対応のため、io.papermc.paper.threadedregions.RegionizedServerを検出
        boolean isFolia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected, using region-aware scheduler");
        } catch (ClassNotFoundException e) {
            getLogger().info("Using standard Bukkit scheduler");
        }
        
        invisibleFrameKey = new NamespacedKey(this, "invisible_frame");
        
        // コマンド登録
        this.getCommand("invisibleframe").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED+"このコマンドはプレイヤーのみ使用できます");
                    return true;
                }

                if (!sender.hasPermission("invisibleframe.use")) {
                    sender.sendMessage(ChatColor.RED+"このコマンドを使用する権限がありません");
                    return true;
                }
                
                UUID playerUUID = player.getUniqueId();
                boolean isActive = invisibleFrameMode.contains(playerUUID);
                
                if (isActive) {
                    invisibleFrameMode.remove(playerUUID);
                    player.sendMessage(ChatColor.LIGHT_PURPLE+"透明額縁モードを無効にしました");
                } else {
                    invisibleFrameMode.add(playerUUID);
                    player.sendMessage(ChatColor.LIGHT_PURPLE+"透明額縁モードを有効にしました");
                    player.sendMessage(ChatColor.LIGHT_PURPLE+"額縁を右クリックで透明化、左クリックで可視化します");
                }
                
                return true;
            }
        });
        
        // イベントリスナー登録
        getServer().getPluginManager().registerEvents(this, this);
    }
    
    // 右クリック - 額縁を透明化
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        
        // プレイヤーが透明額縁モードでない場合は何もしない
        if (!invisibleFrameMode.contains(player.getUniqueId())) {
            return;
        }
        
        // 対象が額縁でない場合は何もしない
        if (entity.getType() != EntityType.ITEM_FRAME && entity.getType() != EntityType.GLOW_ITEM_FRAME) {
            return;
        }
        
        // 額縁を透明化
        ItemFrame frame = (ItemFrame) entity;
        
        // イベントをキャンセルして通常の右クリック操作を防止
        event.setCancelled(true);
        
        // Foliaを考慮したスケジューリング
        if (isUsingFolia()) {
            // Foliaの場合、エンティティが存在するリージョンでタスクを実行
            entity.getScheduler().run(this, task -> {
                toggleFrameVisibility(frame, player, false);
            }, () -> {
                player.sendMessage(ChatColor.RED+"タスクの実行に失敗しました。");
            });
        } else {
            // 通常のBukkit/Paperの場合
            getServer().getScheduler().runTask(this, () -> {
                toggleFrameVisibility(frame, player, false);
            });
        }
    }
    
    // 左クリック - 額縁を可視化（透明解除）
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 攻撃者がプレイヤーでない場合は何もしない
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        
        Entity entity = event.getEntity();
        
        // プレイヤーが透明額縁モードでない場合は何もしない
        if (!invisibleFrameMode.contains(player.getUniqueId())) {
            return;
        }
        
        // 対象が額縁でない場合は何もしない
        if (entity.getType() != EntityType.ITEM_FRAME && entity.getType() != EntityType.GLOW_ITEM_FRAME) {
            return;
        }
        
        // イベントをキャンセルして通常のダメージを防止
        event.setCancelled(true);
        
        // 額縁を可視化
        ItemFrame frame = (ItemFrame) entity;
        
        // Foliaを考慮したスケジューリング
        if (isUsingFolia()) {
            // Foliaの場合、エンティティが存在するリージョンでタスクを実行
            entity.getScheduler().run(this, task -> {
                toggleFrameVisibility(frame, player, true);
            }, () -> {
                player.sendMessage(ChatColor.RED+"タスクの実行に失敗しました。");
            });
        } else {
            // 通常のBukkit/Paperの場合
            getServer().getScheduler().runTask(this, () -> {
                toggleFrameVisibility(frame, player, true);
            });
        }
    }
    
    private void toggleFrameVisibility(ItemFrame frame, Player player, boolean makeVisible) {
        boolean isCurrentlyVisible = frame.isVisible();
        
        if (makeVisible) {
            // 可視化する操作
            if (!isCurrentlyVisible) {
                frame.setVisible(true);
                frame.setFixed(false);
                player.sendMessage(ChatColor.LIGHT_PURPLE+"額縁を可視化しました");
                
                // データを削除
                frame.getPersistentDataContainer().remove(invisibleFrameKey);
            } else {
                player.sendMessage(ChatColor.LIGHT_PURPLE+"この額縁は既に可視化されています");
            }
        } else {
            // 透明化する操作
            if (isCurrentlyVisible) {
                frame.setVisible(false);
                frame.setFixed(true);
                player.sendMessage(ChatColor.LIGHT_PURPLE+"額縁を透明化し、空中固定しました！");
                
                // データを保存
                frame.getPersistentDataContainer().set(invisibleFrameKey, PersistentDataType.BOOLEAN, true);
            } else {
                player.sendMessage(ChatColor.LIGHT_PURPLE+"この額縁は既に透明化されています");
            }
        }
    }
    
    private boolean isUsingFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
