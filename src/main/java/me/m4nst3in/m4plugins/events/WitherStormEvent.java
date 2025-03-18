package me.m4nst3in.m4plugins.events;

import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.utils.ConfigUtils;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class WitherStormEvent extends AbstractEvent {

    private BukkitTask prepareTask;
    private BukkitTask checkTask;
    private Wither mainWither;
    private BossBar bossBar;
    private final Map<UUID, Wither> guardianWithers;
    private final Set<UUID> witherSkeletons;
    private double originalWitherHealth;
    private UUID lastDamager;
    private boolean phase1Triggered;
    private boolean phase2Triggered;
    private boolean phase3Triggered;

    public WitherStormEvent(M4Eventos plugin) {
        super(plugin, "witherstorm", "Wither Storm");
        this.guardianWithers = new HashMap<>();
        this.witherSkeletons = new HashSet<>();
        this.phase1Triggered = false;
        this.phase2Triggered = false;
        this.phase3Triggered = false;

        // Carregando recompensas
        loadRewards();
    }

    private void loadRewards() {
        ConfigurationSection rewardsSection = plugin.getConfig().getConfigurationSection("eventos.witherstorm.recompensas");
        if (rewardsSection == null) return;

        this.rewardCoins = rewardsSection.getInt("coins", 75000);

        ConfigurationSection itemsSection = rewardsSection.getConfigurationSection("itens");
        if (itemsSection == null) return;

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
            if (itemSection == null) continue;

            ItemStack item = ConfigUtils.createItemFromConfig(itemSection);
            if (item != null) {
                rewardItems.add(item);
            }
        }
    }

    @Override
    public boolean start() {
        if (running) return false;
        if (eventLocation == null || playerSpawnLocation == null) return false;

        running = true;
        open = true;

        // Criando BossBar
        bossBar = Bukkit.createBossBar(
                ChatColor.DARK_PURPLE + "Wither Storm",
                BarColor.PURPLE,
                BarStyle.SEGMENTED_20);

        // Broadcast início do evento
        Bukkit.broadcastMessage(MessageUtils.color(
                plugin.getConfig().getString("mensagens.prefix") +
                        plugin.getConfig().getString("mensagens.evento-iniciado").replace("%evento%", name)));

        // Fase de preparação - 5 minutos para jogadores entrarem
        prepareTask = new BukkitRunnable() {
            private int timeLeft = 300; // 5 minutos em segundos

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    closeForPlayers();
                    spawnWitherStorm();
                    startCheckTask();
                    cancel();
                    return;
                }

                if (timeLeft % 60 == 0 || timeLeft <= 30 && timeLeft % 10 == 0 || timeLeft <= 5) {
                    broadcast(ChatColor.YELLOW + "O evento " + ChatColor.GOLD + name +
                            ChatColor.YELLOW + " começará em " + ChatColor.RED +
                            formatTime(timeLeft));
                }

                timeLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L);

        onEventStart();
        return true;
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return (minutes > 0 ? minutes + " minuto" + (minutes > 1 ? "s" : "") + " e " : "") +
                remainingSeconds + " segundo" + (remainingSeconds != 1 ? "s" : "");
    }

    private void spawnWitherStorm() {
        if (eventLocation == null) return;

        World world = eventLocation.getWorld();
        if (world == null) return;

        // Spawn principal wither
        mainWither = (Wither) world.spawnEntity(eventLocation, EntityType.WITHER);
        mainWither.setCustomName(ChatColor.DARK_PURPLE + "Wither Storm");
        mainWither.setCustomNameVisible(true);
        mainWither.setRemoveWhenFarAway(false);
        mainWither.setMetadata("witherstorm", new FixedMetadataValue(plugin, true));

        // Configurar atributos do wither
        originalWitherHealth = 1000.0;
        mainWither.getAttribute(Attribute.MAX_HEALTH).setBaseValue(originalWitherHealth);
        mainWither.setHealth(originalWitherHealth);
        mainWither.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(40.0); // Dobro do dano normal

        // Atualizar BossBar
        bossBar.setProgress(1.0);
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                bossBar.addPlayer(player);
            }
        }

        // Anúncio que o evento começou oficialmente
        broadcast(ChatColor.RED + "O Wither Storm foi invocado! Prepare-se para a batalha!");
        world.playSound(eventLocation, Sound.ENTITY_WITHER_SPAWN, 10.0f, 0.8f);

        // Efeito visual
        world.spawnParticle(Particle.EXPLOSION, eventLocation, 10, 2, 2, 2);
        world.strikeLightningEffect(eventLocation);
    }

    private void startCheckTask() {
        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running || mainWither == null || mainWither.isDead()) {
                    cancel();
                    return;
                }

                // Atualizar BossBar
                double health = mainWither.getHealth();
                bossBar.setProgress(Math.max(0, Math.min(1.0, health / originalWitherHealth)));

                // Verificar fases
                if (!phase1Triggered && health <= originalWitherHealth * 0.5) {
                    phase1Triggered = true;
                    triggerPhase1();
                }

                if (!phase2Triggered && health <= originalWitherHealth * 0.25) {
                    phase2Triggered = true;
                    triggerPhase2();
                }

                if (!phase3Triggered && health <= originalWitherHealth * 0.05) {
                    phase3Triggered = true;
                    triggerPhase3();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void triggerPhase1() {
        broadcast(ChatColor.RED + "O Wither Storm está enfraquecendo! Ele convoca ajudantes!");
        spawnWitherSkeletons(10);
    }

    private void triggerPhase2() {
        broadcast(ChatColor.DARK_RED + "O Wither Storm está em perigo! Ele convoca mais ajudantes!");
        spawnWitherSkeletons(10);
    }

    private void triggerPhase3() {
        broadcast(ChatColor.DARK_RED + "" + ChatColor.BOLD + "O Wither Storm está à beira da morte! Seus guardiões o protegem!");
        spawnGuardianWithers();
    }

    private void spawnWitherSkeletons(int count) {
        if (mainWither == null || !mainWither.isValid()) return;

        Location spawnLocation = mainWither.getLocation();
        World world = spawnLocation.getWorld();

        for (int i = 0; i < count; i++) {
            // Randomizar um pouco a localização
            double offsetX = ThreadLocalRandom.current().nextDouble(-5, 5);
            double offsetZ = ThreadLocalRandom.current().nextDouble(-5, 5);
            Location skeletonLoc = spawnLocation.clone().add(offsetX, 0, offsetZ);

            WitherSkeleton skeleton = (WitherSkeleton) world.spawnEntity(skeletonLoc, EntityType.WITHER_SKELETON);
            skeleton.setCustomName(ChatColor.DARK_GRAY + "Servo do Wither Storm");
            skeleton.setCustomNameVisible(true);
            skeleton.getAttribute(Attribute.MAX_HEALTH).setBaseValue(50.0);
            skeleton.setHealth(50.0);
            skeleton.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(10.0);
            skeleton.setMetadata("witherstorm_skeleton", new FixedMetadataValue(plugin, true));

            witherSkeletons.add(skeleton.getUniqueId());
        }

        world.playSound(spawnLocation, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.8f);
        world.spawnParticle(Particle.SMOKE, spawnLocation, 50, 3, 3, 3, 0.1);
    }

    private void spawnGuardianWithers() {
        if (mainWither == null || !mainWither.isValid()) return;

        Location spawnLocation = mainWither.getLocation();
        World world = spawnLocation.getWorld();

        for (int i = 0; i < 2; i++) {
            // Spawn os withers guardiões próximos ao principal
            double offsetX = ThreadLocalRandom.current().nextDouble(-8, 8);
            double offsetZ = ThreadLocalRandom.current().nextDouble(-8, 8);
            Location witherLoc = spawnLocation.clone().add(offsetX, 0, offsetZ);

            Wither guardian = (Wither) world.spawnEntity(witherLoc, EntityType.WITHER);
            guardian.setCustomName(ChatColor.RED + "Guardião do Wither Storm");
            guardian.setCustomNameVisible(true);
            guardian.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(300.0);
            guardian.setHealth(300.0);
            guardian.setMetadata("witherstorm_guardian", new FixedMetadataValue(plugin, true));

            guardianWithers.put(guardian.getUniqueId(), guardian);
        }

        world.playSound(spawnLocation, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        world.spawnParticle(Particle.EXPLOSION, spawnLocation, 20, 3, 3, 3);
        world.strikeLightningEffect(spawnLocation);

        // Não permitir dano ao Wither Storm enquanto os guardiões estiverem vivos
        protectMainWither(true);
    }

    private void protectMainWither(boolean protect) {
        if (mainWither == null || !mainWither.isValid()) return;

        if (protect) {
            mainWither.setAI(false);
            mainWither.setGlowing(true);
            mainWither.setInvulnerable(true);
            broadcast(ChatColor.RED + "O Wither Storm está protegido! Derrote os guardiões primeiro!");
        } else {
            mainWither.setAI(true);
            mainWither.setGlowing(false);
            mainWither.setInvulnerable(false);
            broadcast(ChatColor.GREEN + "O Wither Storm está vulnerável novamente!");
        }
    }

    public void handleEntityDamage(EntityDamageByEntityEvent event) {
        // Impedir que o Wither Storm quebre blocos
        if (event.getEntity() instanceof Block && event.getDamager() != null) {
            if (isWitherStormEntity(event.getDamager())) {
                event.setCancelled(true);
                return;
            }
        }

        // Registrar o último jogador que atacou o Wither Storm
        if (mainWither != null && event.getEntity().equals(mainWither)) {
            Player player = getPlayerDamager(event.getDamager());
            if (player != null) {
                lastDamager = player.getUniqueId();
            }
        }

        // Impedir dano ao Wither Storm principal se os guardiões estiverem vivos
        if (mainWither != null && event.getEntity().equals(mainWither) && !guardianWithers.isEmpty()) {
            event.setCancelled(true);

            // Efeito visual de proteção
            mainWither.getWorld().spawnParticle(Particle.FALLING_DUST, mainWither.getLocation(),
                    30, 1, 1, 1, 0.1, null, true);
            mainWither.getWorld().playSound(mainWither.getLocation(),
                    Sound.ENTITY_WITHER_HURT, 1.0f, 2.0f);

            Player player = getPlayerDamager(event.getDamager());
            if (player != null) {
                MessageUtils.send(player, ChatColor.RED + "O Wither Storm está protegido! Derrote os guardiões primeiro!");
            }
        }
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        } else if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
            return (Player) ((Projectile) damager).getShooter();
        }
        return null;
    }

    private boolean isWitherStormEntity(Entity entity) {
        return (entity instanceof Wither && entity.hasMetadata("witherstorm")) ||
                (entity instanceof Wither && entity.hasMetadata("witherstorm_guardian")) ||
                (entity instanceof WitherSkeleton && entity.hasMetadata("witherstorm_skeleton"));
    }

    public void handleEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // Verificar se é um guardião do Wither Storm
        if (entity instanceof Wither && entity.hasMetadata("witherstorm_guardian")) {
            guardianWithers.remove(entity.getUniqueId());

            // Se todos os guardiões morreram, tornar o Wither Storm vulnerável novamente
            if (guardianWithers.isEmpty() && mainWither != null && mainWither.isValid()) {
                protectMainWither(false);
            }
        }

        // Verificar se é um esqueleto do Wither Storm
        if (entity instanceof WitherSkeleton && entity.hasMetadata("witherstorm_skeleton")) {
            witherSkeletons.remove(entity.getUniqueId());
        }

        // Verificar se é o próprio Wither Storm
        if (entity instanceof Wither && entity.hasMetadata("witherstorm")) {
            Player killer = ((Wither) entity).getKiller();
            UUID winnerUUID = (killer != null) ? killer.getUniqueId() : lastDamager;

            if (winnerUUID != null) {
                Player winner = Bukkit.getPlayer(winnerUUID);
                if (winner != null && winner.isOnline()) {
                    handlePlayerWin(winner);
                }
            }

            // Finalizar o evento
            new BukkitRunnable() {
                @Override
                public void run() {
                    stop();
                }
            }.runTaskLater(plugin, 100L); // 5 segundos após a morte do Wither Storm
        }
    }

    @Override
    public boolean stop() {
        if (!running) return false;

        running = false;
        open = false;

        // Limpar entidades relacionadas ao evento
        if (mainWither != null && mainWither.isValid()) {
            mainWither.remove();
        }

        guardianWithers.values().forEach(wither -> {
            if (wither != null && wither.isValid()) wither.remove();
        });
        guardianWithers.clear();

        for (UUID uuid : witherSkeletons) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid()) entity.remove();
        }
        witherSkeletons.clear();

        // Remover BossBar
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Cancelar tasks
        if (prepareTask != null && !prepareTask.isCancelled()) {
            prepareTask.cancel();
        }

        if (checkTask != null && !checkTask.isCancelled()) {
            checkTask.cancel();
        }

        // Resetar variáveis de estado
        mainWither = null;
        phase1Triggered = false;
        phase2Triggered = false;
        phase3Triggered = false;
        lastDamager = null;

        // Teleportar jogadores de volta ao spawn
        for (UUID uuid : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.teleport(player.getWorld().getSpawnLocation());
                plugin.getEventManager().removePlayerFromEvent(player, this);
            }
        }

        onEventEnd();
        return true;
    }

    @Override
    public boolean canStart() {
        return eventLocation != null && playerSpawnLocation != null && !running;
    }

    @Override
    public boolean openForPlayers() {
        if (!running) return false;
        open = true;
        return true;
    }

    @Override
    public boolean closeForPlayers() {
        if (!running) return false;
        open = false;
        return true;
    }

    @Override
    protected void onEventStart() {
        Bukkit.broadcastMessage(MessageUtils.color(
                plugin.getConfig().getString("mensagens.prefix") +
                        "&aO evento &6" + name + "&a foi iniciado! Use &b/evento witherstorm&a para participar!"));
    }

    @Override
    protected void onEventEnd() {
        Bukkit.broadcastMessage(MessageUtils.color(
                plugin.getConfig().getString("mensagens.prefix") +
                        "&cO evento &6" + name + "&c foi encerrado!"));
    }

    @Override
    protected void onPlayerJoin(Player player) {
        if (playerSpawnLocation != null) {
            player.teleport(playerSpawnLocation);
        }

        if (bossBar != null) {
            bossBar.addPlayer(player);
        }

        broadcast(ChatColor.GREEN + player.getName() + " entrou no evento!");
    }

    @Override
    protected void onPlayerLeave(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }

        player.teleport(player.getWorld().getSpawnLocation());
        broadcast(ChatColor.YELLOW + player.getName() + " saiu do evento!");
    }

    @Override
    protected void handlePlayerWin(Player player) {
        // Registrar vitória no banco de dados
        plugin.getDatabaseManager().addEventWin(player.getUniqueId(), id);

        // Anunciar vencedor
        String message = plugin.getConfig().getString("mensagens.evento-vencedor")
                .replace("%jogador%", player.getName())
                .replace("%evento%", name);
        Bukkit.broadcastMessage(MessageUtils.color(plugin.getConfig().getString("mensagens.prefix") + message));

        // Dar recompensas
        if (plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(player, rewardCoins);
        }

        String recompensaMsg = plugin.getConfig().getString("mensagens.evento-recompensa")
                .replace("%coins%", String.valueOf(rewardCoins));
        MessageUtils.send(player, recompensaMsg);

        // Dar item aleatório se houver itens configurados
        if (!rewardItems.isEmpty()) {
            ItemStack randomItem = rewardItems.get(ThreadLocalRandom.current().nextInt(rewardItems.size()));
            player.getInventory().addItem(randomItem);
        }

        // Efeitos visuais e sonoros para o vencedor
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation(), 100, 1, 1, 1, 0.5);
    }
}