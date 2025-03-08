package net.doodcraft.healingspores;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class HealingSporesListener implements Listener {

    private static final String CONFIG_PATH = "ExtraAbilities.Cozmyc.HealingSpores.";

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

        if (bPlayer == null) {
            return;
        }

        if (!bPlayer.canBendIgnoreCooldowns(CoreAbility.getAbility(HealingSpores.class))) {
            return;
        }

        if (event.isSneaking() && isHealingSporesBound(bPlayer) && !hasActiveHealingSpores(player)) {
            new HealingSpores(player);
        }
    }

    private boolean isHealingSporesBound(BendingPlayer bPlayer) {
        return "HealingSpores".equalsIgnoreCase(bPlayer.getBoundAbilityName());
    }

    private boolean hasActiveHealingSpores(Player player) {
        return CoreAbility.hasAbility(player, HealingSpores.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Block block = event.getBlock();

        if (block.getType() != Material.SPORE_BLOSSOM) {
            return;
        }

        if (isAbilitySporeBlock(block) && !isSporeBlossomDropAllowed()) {
            event.getItems().clear();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        if (!(entity instanceof Item item)) {
            return;
        }

        if (item.getItemStack().getType() != Material.SPORE_BLOSSOM) {
            return;
        }

        if (isAbilitySporeItemLocation(item) && !isSporeBlossomDropAllowed()) {
            event.setCancelled(true);
        }
    }

    private boolean isAbilitySporeBlock(Block block) {
        return HealingSpores.ABILITY_SPORE_LOCATIONS.contains(block.getLocation());
    }

    private boolean isAbilitySporeItemLocation(Item item) {
        return HealingSpores.ABILITY_SPORE_LOCATIONS.contains(item.getLocation().getBlock().getLocation());
    }

    private boolean isSporeBlossomDropAllowed() {
        return ConfigManager.defaultConfig.get().getBoolean(CONFIG_PATH + "AllowSporeBlossomDrop", true);
    }
}