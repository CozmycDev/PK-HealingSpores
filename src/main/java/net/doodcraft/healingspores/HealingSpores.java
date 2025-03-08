package net.doodcraft.healingspores;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.PlantAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.TempBlock;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class HealingSpores extends PlantAbility implements AddonAbility {

    private static final Map<UUID, HealingSpores> INSTANCES = new WeakHashMap<>();
    public static final Set<Location> ABILITY_SPORE_LOCATIONS = new HashSet<>();
    private static final ItemStack VALID_ITEM = new ItemStack(Material.LIME_DYE);
    private static final ItemStack INVALID_ITEM = new ItemStack(Material.GRAY_DYE);
    private static final String CONFIG_PATH = "ExtraAbilities.Cozmyc.HealingSpores.";
    private static final Vector3f DISPLAY_SCALE = new Vector3f(0.2f, 0.2f, 0.2f);
    private static final AxisAngle4f NO_ROTATION = new AxisAngle4f();
    private static final Vector3f NO_TRANSLATION = new Vector3f();

    private enum State {
        CHARGING,
        READY,
        ACTIVE
    }

    private final Location origin;
    private final long cooldown;
    private final long chargeTime;
    private final boolean seedUsage;
    private final int requiredSeeds;
    private final int maxRange;
    private final int healingLevel;
    private final double healingRadius;
    private final long duration;
    private final List<String> customMaterials;
    private final Particle mossParticle;

    private final long startTime;
    private long placedTime;
    private boolean ringSpawned;
    private State state;
    private TempBlock sporeBlock;
    private Location sporeBlockLocation;
    private boolean takenSeeds;
    private ItemDisplay handIndicator;

    public HealingSpores(Player player) {
        super(player);

        this.origin = player.getLocation();
        this.startTime = System.currentTimeMillis();
        this.state = State.CHARGING;
        this.ringSpawned = false;

        cooldown = ConfigManager.defaultConfig.get().getLong(CONFIG_PATH + "Cooldown");
        chargeTime = ConfigManager.defaultConfig.get().getLong(CONFIG_PATH + "ChargeTime");
        seedUsage = ConfigManager.defaultConfig.get().getBoolean(CONFIG_PATH + "UsesSeeds");
        requiredSeeds = ConfigManager.defaultConfig.get().getInt(CONFIG_PATH + "RequiredSeeds");
        maxRange = ConfigManager.defaultConfig.get().getInt(CONFIG_PATH + "UseDistance");
        healingRadius = ConfigManager.defaultConfig.get().getDouble(CONFIG_PATH + "HealingRadius");
        healingLevel = ConfigManager.defaultConfig.get().getInt(CONFIG_PATH + "HealingLevel");
        duration = ConfigManager.defaultConfig.get().getLong(CONFIG_PATH + "Duration");
        customMaterials = ConfigManager.defaultConfig.get().getStringList(CONFIG_PATH + "CustomValidSpawnMaterials");

        int serverVersion = GeneralMethods.getMCVersion();
        mossParticle = serverVersion >= 1205 ? Particle.valueOf("BLOCK") : Particle.valueOf("BLOCK_CRACK");

        if (bPlayer.isOnCooldown(this)) {
            return;
        }

        if (INSTANCES.containsKey(player.getUniqueId())) {
            INSTANCES.get(player.getUniqueId()).stop();
        }
        INSTANCES.put(player.getUniqueId(), this);

        start();
    }

    private boolean hasEnoughSeeds() {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.WHEAT_SEEDS) {
                total += item.getAmount();
                if (total >= requiredSeeds) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean removeSeeds() {
        if (!hasEnoughSeeds()) {
            return false;
        }

        int toRemove = requiredSeeds;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.WHEAT_SEEDS) {
                int amt = item.getAmount();
                if (amt <= toRemove) {
                    toRemove -= amt;
                    item.setAmount(0);
                } else {
                    item.setAmount(amt - toRemove);
                    toRemove = 0;
                }
                if (toRemove <= 0) {
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            cleanupHandIndicator();
            remove();
            return;
        }

        long currentTime = System.currentTimeMillis();

        switch (state) {
            case CHARGING:
                handleChargingState(currentTime);
                break;
            case READY:
                handleReadyState();
                break;
            case ACTIVE:
                handleActiveState(currentTime);
                break;
        }
    }

    private void handleChargingState(long currentTime) {
        if (player.isSneaking()) {
            if (currentTime >= startTime + chargeTime && !ringSpawned) {
                updateHandIndicator(false);
                ringSpawned = true;
                state = State.READY;
            } else {
                if (seedUsage) {
                    String seedText = requiredSeeds == 1 ? "wheat seed" : "wheat seeds";
                    TextComponent message = new TextComponent(
                            ChatColor.translateAlternateColorCodes('&',
                                    "&7Consuming " + requiredSeeds + " " + seedText + ", keep holding shift..")
                    );
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
                }
                spawnMossParticles(GeneralMethods.getMainHandLocation(player));
            }
        } else {
            cleanupHandIndicator();
            remove();
        }
    }

    private void handleReadyState() {
        if (seedUsage && !takenSeeds) {
            if (!removeSeeds()) {
                sendSeedRequirementMessage();
                cleanupHandIndicator();
                remove();
                return;
            }
            takenSeeds = true;
        }

        if (player.isSneaking()) {
            Block targetBlock = getTargetBlock();
            if (isValidSpawnLocation(targetBlock)) {
                spawnMossParticles(targetBlock.getLocation().clone().add(0.5, -0.01, 0.5));
                updateHandIndicator(true);
            } else {
                updateHandIndicator(false);
            }
        } else {
            handleReadyStateRelease();
        }
    }

    private void handleReadyStateRelease() {
        Block targetBlock = getTargetBlock();
        if (!isValidSpawnLocation(targetBlock)) {
            cleanupHandIndicator();
            returnSeeds();
            remove();
            return;
        }

        Block sporePlacement = targetBlock.getRelative(BlockFace.DOWN);
        if (!sporePlacement.getType().equals(Material.AIR)) {
            cleanupHandIndicator();
            returnSeeds();
            remove();
            return;
        }

        cleanupHandIndicator();
        playSporePlacementSound(sporePlacement);
        createSporeBlock(sporePlacement);

        placedTime = System.currentTimeMillis();
        state = State.ACTIVE;
    }

    private void handleActiveState(long currentTime) {
        bPlayer.addCooldown(this);

        if (!isSporeBlockValid()) {
            cleanupSporeBlock();
            remove();
            return;
        }

        if (currentTime >= placedTime + duration) {
            cleanupSporeBlock();
            remove();
            return;
        }

        applyHealing();
        spawnSporeParticles();
    }

    private void sendSeedRequirementMessage() {
        String seedText = requiredSeeds == 1 ? "wheat seed" : "wheat seeds";
        TextComponent message = new TextComponent(
                ChatColor.translateAlternateColorCodes('&',
                        "&cYou need at least " + requiredSeeds + " " + seedText + " to use this ability.")
        );
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
    }

    private void returnSeeds() {
        if (seedUsage) {
            player.getInventory().addItem(new ItemStack(Material.WHEAT_SEEDS, requiredSeeds));
            TextComponent message = new TextComponent(
                    ChatColor.translateAlternateColorCodes('&',
                            "&7HealingSpores not activated; your seeds were returned.")
            );
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
        }
    }

    private void playSporePlacementSound(Block location) {
        player.getWorld().playSound(
                location.getLocation(),
                Sound.BLOCK_NYLIUM_PLACE,
                SoundCategory.BLOCKS,
                1.0f,
                1.4f
        );
    }

    private void createSporeBlock(Block location) {
        sporeBlock = new TempBlock(location, Material.SPORE_BLOSSOM.createBlockData(), duration);
        sporeBlockLocation = sporeBlock.getLocation().clone();
        ABILITY_SPORE_LOCATIONS.add(sporeBlockLocation.getBlock().getLocation());
    }

    private boolean isSporeBlockValid() {
        return sporeBlockLocation.getBlock().getType().equals(Material.SPORE_BLOSSOM);
    }

    private void cleanupSporeBlock() {
        if (sporeBlockLocation != null) {
            ABILITY_SPORE_LOCATIONS.remove(sporeBlockLocation.getBlock().getLocation());
        }
    }

    private Block getTargetBlock() {
        return player.getTargetBlock(null, maxRange);
    }

    private boolean isValidSpawnLocation(Block block) {
        Material type = block.getType();
        if (block.getRelative(BlockFace.DOWN).getType() != Material.AIR) {
            return false;
        }
        return Tag.LEAVES.isTagged(type) || customMaterials.contains(type.toString());
    }

    private void spawnMossParticles(Location loc) {
        player.getWorld().spawnParticle(
                mossParticle,
                loc,
                4,
                0.0D,
                0.001D,
                0.0D,
                0.0D,
                Material.MOSS_BLOCK.createBlockData()
        );
    }

    private void updateHandIndicator(boolean isValid) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Location handLoc = GeneralMethods.getMainHandLocation(player);

        if (handIndicator == null) {
            createHandIndicator(handLoc);
        }

        handIndicator.teleport(handLoc);
        handIndicator.setItemStack(isValid ? VALID_ITEM : INVALID_ITEM);

        // Particles for Bedrock players
        if (Math.random() < 0.5) {
            Particle particleType = isValid ? Particle.COMPOSTER : Particle.ELECTRIC_SPARK;
            player.getWorld().spawnParticle(particleType, handLoc, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void createHandIndicator(Location location) {
        handIndicator = (ItemDisplay) player.getWorld().spawnEntity(location, EntityType.ITEM_DISPLAY);
        handIndicator.setBillboard(Display.Billboard.CENTER);
        handIndicator.setItemStack(new ItemStack(Material.AIR));
        handIndicator.setPersistent(false);
        handIndicator.setInvulnerable(true);
        handIndicator.setTransformation(new Transformation(
                NO_TRANSLATION,
                NO_ROTATION,
                DISPLAY_SCALE,
                NO_ROTATION
        ));
    }

    private void cleanupHandIndicator() {
        if (handIndicator != null && !handIndicator.isDead()) {
            handIndicator.remove();
        }
        handIndicator = null;
    }

    private void applyHealing() {
        if (sporeBlock == null) {
            return;
        }

        Location center = sporeBlock.getLocation().clone().add(0.5, 0.5, 0.5);
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (isPlayerInHealingRange(nearby, center)) {
                applyRegenerationEffect(nearby);
            }
        }
    }

    private boolean isPlayerInHealingRange(Player player, Location center) {
        return player.getWorld().equals(center.getWorld())
                && player.getLocation().distance(center) <= healingRadius;
    }

    private void applyRegenerationEffect(Player player) {
        PotionEffect effect = new PotionEffect(
                PotionEffectType.REGENERATION,
                40,
                healingLevel,
                true,
                true
        );
        player.addPotionEffect(effect, true);
    }

    private void spawnSporeParticles() {
        if (sporeBlock == null) {
            return;
        }

        Location loc = sporeBlock.getLocation().clone().add(0.5, 0.5, 0.5);
        sporeBlock.getLocation().getWorld().spawnParticle(
                Particle.SPORE_BLOSSOM_AIR,
                loc,
                50,
                4.0,
                -0.7,
                4.0
        );
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return "HealingSpores";
    }

    @Override
    public Location getLocation() {
        return sporeBlock != null ? sporeBlock.getLocation() : origin;
    }

    @Override
    public void load() {
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(
                new HealingSporesListener(),
                ProjectKorra.plugin
        );

        setupDefaultConfig();
    }

    private void setupDefaultConfig() {
        // Set default configuration values
        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "Cooldown", 22000);
        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "ChargeTime", 4000);
        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "UsesSeeds", true);
        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "RequiredSeeds", 1);
        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "UseDistance", 10);
        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "HealingRadius", 22);
        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "HealingLevel", 10);
        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "Duration", 5000);
        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "AllowSporeBlossomDrop", true);

        List<String> materialList = new ArrayList<>();
        materialList.add("DIRT");

        ConfigManager.defaultConfig.get().addDefault(CONFIG_PATH + "CustomValidSpawnMaterials", materialList);

        ConfigManager.defaultConfig.save();
    }

    @Override
    public void stop() {
        if (sporeBlock != null) {
            sporeBlock.revertBlock();
        }

        if (sporeBlockLocation != null) {
            ABILITY_SPORE_LOCATIONS.remove(sporeBlockLocation.getBlock().getLocation());
        }

        if (player != null) {
            INSTANCES.remove(player.getUniqueId());
        }

        cleanupHandIndicator();
        remove();
    }

    @Override
    public String getAuthor() {
        return "LuxaelNI, Cozmyc";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Plantbenders have learned to apply healing knowledge in their arts, allowing them to use plants to heal. This ability uses (wheat) seeds to grow a Spore Blossom which applies healing in a radius with its spores. The Blossom withers quickly unless it is harvested. Harvesting or otherwise destroying the Blossom stops the healing prematurely.";
    }

    @Override
    public String getInstructions() {
        return "With wheat seeds in your inventory, hold shift until the ability is charged > Keep holding shift and you will see gray or green particles if you are looking at a valid location > Release shift to grow the blossom where you are looking.";
    }
}