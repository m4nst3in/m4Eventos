package me.m4nst3in.m4plugins.events;

import lombok.Getter;
import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.utils.ConfigUtils;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class FrogEvent extends AbstractEvent {

    @Getter private Location pos1;
    @Getter private Location pos2;
    @Getter private Location spawnPos1;
    @Getter private Location spawnPos2;
    private BukkitTask prepareTask;
    private BukkitTask gameTask;
    private BossBar bossBar;
    private final Map<Material, List<Location>> blockTypeLocations;
    private final List<Material> availableBlockTypes;
    private final List<Material> usedBlockTypes;
    private final List<Location> snowLocations;
    private boolean finalPhase;
    private Location diamondLocation;

    public FrogEvent(M4Eventos plugin) {
        super(plugin, "frog", "Frog Race");
        this.blockTypeLocations = new HashMap<>();
        this.availableBlockTypes = new ArrayList<>();
        this.usedBlockTypes = new ArrayList<>();
        this.snowLocations = new ArrayList<>();
        this.finalPhase = false;

        // Inicializa lista de blocos disponíveis (exclui neve, diamante e blocos não sólidos)
        for (Material material : Material.values()) {
            if (material.isBlock() && material.isSolid() &&
                    material != Material.SNOW_BLOCK &&
                    material != Material.DIAMOND_BLOCK &&
                    !material.name().contains("GLASS") &&
                    !material.name().contains("SLAB") &&
                    !material.name().contains("STAIR") &&
                    !material.name().contains("FENCE") &&
                    !material.name().contains("WALL") &&
                    !material.name().contains("DOOR") &&
                    !material.name().contains("CARPET")) {
                availableBlockTypes.add(material);
            }
        }

        // Carregando recompensas
        loadRewards();
    }

    private void loadRewards() {
        ConfigurationSection rewardsSection = plugin.getConfig().getConfigurationSection("eventos.frog.recompensas");
        if (rewardsSection == null) return;

        this.rewardCoins = rewardsSection.getInt("coins", 50000);

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

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
        saveLocationToConfig("eventos.frog.area.pos1", pos1);
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
        saveLocationToConfig("eventos.frog.area.pos2", pos2);
    }

    public void setSpawnPos1(Location pos1) {
        this.spawnPos1 = pos1;
        saveLocationToConfig("eventos.frog.spawn.pos1", pos1);
    }

    public void setSpawnPos2(Location pos2) {
        this.spawnPos2 = pos2;
        saveLocationToConfig("eventos.frog.spawn.pos2", pos2);
    }

    @Override
    public boolean start() {
        if (running) return false;
        if (pos1 == null || pos2 == null || spawnPos1 == null || spawnPos2 == null) return false;

        // Verificar se a área é válida (10x10 máximo)
        if (!isValidArea()) {
            return false;
        }

        running = true;
        open = true;

        // Resetar variáveis de controle
        blockTypeLocations.clear();
        usedBlockTypes.clear();
        snowLocations.clear();
        finalPhase = false;
        diamondLocation = null;

        // Criando BossBar
        bossBar = Bukkit.createBossBar(
                ChatColor.AQUA + "Frog Race",
                BarColor.GREEN,
                BarStyle.SOLID);

        // Broadcast início do evento
        Bukkit.broadcastMessage(MessageUtils.color(
                plugin.getConfig().getString("mensagens.prefix") +
                        plugin.getConfig().getString("mensagens.evento-iniciado").replace("%evento%", name)));

        // Fase de preparação - 2 minutos para jogadores entrarem
        prepareTask = new BukkitRunnable() {
            private int timeLeft = 120; // 2 minutos em segundos
            private boolean spawnZoneUnlocked = false;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    closeForPlayers();
                    setupGameArea();
                    startGameLogic();
                    cancel();
                    return;
                }

                if (timeLeft % 30 == 0 || timeLeft <= 10) {
                    broadcast(ChatColor.YELLOW + "O evento " + ChatColor.GOLD + name +
                            ChatColor.YELLOW + " começará em " + ChatColor.RED +
                            formatTime(timeLeft));
                }

                // 30 segundos antes do início, libera a zona de spawn
                if (timeLeft == 30 && !spawnZoneUnlocked) {
                    broadcast(ChatColor.GREEN + "⚠ A zona de spawn foi liberada! Você já pode entrar na área do evento!");
                    spawnZoneUnlocked = true;
                }

                // Atualizar BossBar
                if (bossBar != null) {
                    bossBar.setTitle(ChatColor.GREEN + "Frog Race começa em " + formatTime(timeLeft));
                    bossBar.setProgress(timeLeft / 120.0);
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

    private boolean isValidArea() {
        if (pos1 == null || pos2 == null) return false;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;

        return width <= 10 && length <= 10;
    }

    private void setupGameArea() {
        if (pos1 == null || pos2 == null) return;

        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y = pos1.getBlockY();
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        int totalBlocks = (maxX - minX + 1) * (maxZ - minZ + 1);
        int blockTypesNeeded = Math.min(25, totalBlocks / 4); // Máximo de 25 tipos diferentes, mínimo 4 de cada tipo

        List<Material> selectedTypes = new ArrayList<>(availableBlockTypes);
        Collections.shuffle(selectedTypes);
        selectedTypes = selectedTypes.subList(0, blockTypesNeeded);

        // Guardar cópia dos blocos originais (para restauração futura)
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, y - 1, z);
                // Poderia salvar os blocos originais aqui se necessário

                // Criar água abaixo da área
                world.getBlockAt(x, y - 2, z).setType(Material.WATER);
            }
        }

        // Definir blocos aleatórios, garantindo pelo menos 4 de cada tipo
        List<Location> allLocations = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                allLocations.add(new Location(world, x, y, z));
            }
        }
        Collections.shuffle(allLocations);

        // Primeiro, garantir pelo menos 4 de cada tipo
        for (Material type : selectedTypes) {
            blockTypeLocations.put(type, new ArrayList<>());
            for (int i = 0; i < 4 && !allLocations.isEmpty(); i++) {
                Location loc = allLocations.remove(0);
                Block block = world.getBlockAt(loc);
                block.setType(type);
                blockTypeLocations.get(type).add(loc);
            }
            usedBlockTypes.add(type);
        }

        // Preencher o restante aleatoriamente
        for (Location loc : allLocations) {
            Material randomType = selectedTypes.get(ThreadLocalRandom.current().nextInt(selectedTypes.size()));
            Block block = world.getBlockAt(loc);
            block.setType(randomType);
            blockTypeLocations.get(randomType).add(loc);
        }

        // Teleportar jogadores fora da área para o spawn do servidor
        for (UUID uuid : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                boolean inArea = isPlayerInEventArea(player);
                if (!inArea) {
                    MessageUtils.send(player, "&c✖ Você não estava na área do evento quando ele começou!");
                    player.teleport(player.getWorld().getSpawnLocation());
                    plugin.getEventManager().removePlayerFromEvent(player, this);
                }
            }
        }
    }

    private boolean isPlayerInEventArea(Player player) {
        if (pos1 == null || pos2 == null) return false;

        Location loc = player.getLocation();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y = pos1.getBlockY();
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        return loc.getWorld().equals(pos1.getWorld()) &&
                loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                loc.getBlockY() == y &&
                loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    private void startGameLogic() {
        if (bossBar != null) {
            bossBar.setTitle(ChatColor.GREEN + "Frog Race");
            bossBar.setProgress(1.0);
        }

        broadcast(ChatColor.GREEN + "✦ O evento Frog Race começou! Boa sorte!");

        gameTask = new BukkitRunnable() {
            private int timer = 0;
            private Material nextBlockTypeToSnow = null;
            private int countdownToSnow = 0;

            @Override
            public void run() {
                // Verificar se o evento ainda tem jogadores suficientes
                if (players.size() <= 1) {
                    if (players.size() == 1) {
                        // Um único jogador vencedor
                        UUID lastPlayerUuid = players.iterator().next();
                        Player winner = Bukkit.getPlayer(lastPlayerUuid);
                        if (winner != null) {
                            handlePlayerWin(winner);
                        }
                    }
                    stop();
                    cancel();
                    return;
                }

                // Verificar jogadores que caíram na água
                checkPlayersInWater();

                // Lógica principal do jogo
                if (countdownToSnow > 0) {
                    // Contagem regressiva para transformar em neve
                    countdownToSnow--;

                    // Atualizar barra de progresso
                    if (bossBar != null && nextBlockTypeToSnow != null) {
                        float progress = countdownToSnow / (float)(finalPhase ? 5 : 10);
                        bossBar.setProgress(progress);
                        bossBar.setTitle(ChatColor.RED + "Blocos de " +
                                formatMaterialName(nextBlockTypeToSnow) +
                                " virarão neve em " + countdownToSnow + "s");
                    }

                    if (countdownToSnow == 0 && nextBlockTypeToSnow != null) {
                        // Transformar blocos em neve
                        turnBlocksToSnow(nextBlockTypeToSnow);
                        nextBlockTypeToSnow = null;

                        // Se for a fase final, criar caminhos de neve
                        if (finalPhase && diamondLocation != null) {
                            createSnowPaths();
                        }
                    }
                } else if (timer % 20 == 0 && !usedBlockTypes.isEmpty()) {
                    // A cada 20 segundos, escolher um tipo de bloco para virar neve
                    if (usedBlockTypes.size() <= 1) {
                        // Fase final - último tipo de bloco
                        handleFinalPhase();
                    } else {
                        // Escolher um tipo de bloco aleatório para virar neve
                        int randomIndex = ThreadLocalRandom.current().nextInt(usedBlockTypes.size());
                        nextBlockTypeToSnow = usedBlockTypes.get(randomIndex);
                        countdownToSnow = (usedBlockTypes.size() <= 5) ? 5 : 10; // 5 segundos para os últimos 5 tipos

                        String message = plugin.getConfig().getString("mensagens.frog-aviso-bloco")
                                .replace("%bloco%", formatMaterialName(nextBlockTypeToSnow))
                                .replace("%tempo%", String.valueOf(countdownToSnow));
                        broadcast(message);

                        // Efeito sonoro de aviso
                        for (UUID uuid : players) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                            }
                        }
                    }
                }

                timer++;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private void turnBlocksToSnow(Material blockType) {
        List<Location> locations = blockTypeLocations.remove(blockType);
        usedBlockTypes.remove(blockType);

        if (locations != null) {
            for (Location loc : locations) {
                Block block = loc.getBlock();
                block.setType(Material.SNOW_BLOCK);
                snowLocations.add(loc);

                // Efeito visual
                block.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        loc.clone().add(0.5, 1.0, 0.5),
                        10, 0.3, 0.3, 0.3, 0.05);
            }

            // Tocar som de gelo para todos
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
                }
            }

            // Programar a remoção dos blocos de neve após 10 segundos
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Location loc : new ArrayList<>(snowLocations)) {
                        Block block = loc.getBlock();
                        if (block.getType() == Material.SNOW_BLOCK) {
                            block.setType(Material.AIR);
                            snowLocations.remove(loc);

                            // Efeito visual de desaparecimento
                            block.getWorld().spawnParticle(Particle.CLOUD,
                                    loc.clone().add(0.5, 0.5, 0.5),
                                    15, 0.3, 0.3, 0.3, 0.05);
                        }
                    }
                }
            }.runTaskLater(plugin, 200L); // 10 segundos
        }
    }

    private void handleFinalPhase() {
        if (finalPhase) return;

        finalPhase = true;
        Material lastBlockType = usedBlockTypes.get(0);

        // Anúncio da fase final
        broadcast(plugin.getConfig().getString("mensagens.frog-fase-final"));

        // Escolher uma localização aleatória para o bloco de diamante
        List<Location> lastTypeLocations = blockTypeLocations.get(lastBlockType);
        if (lastTypeLocations != null && !lastTypeLocations.isEmpty()) {
            int randomIndex = ThreadLocalRandom.current().nextInt(lastTypeLocations.size());
            diamondLocation = lastTypeLocations.get(randomIndex);

            // Transformar em bloco de diamante
            Block block = diamondLocation.getBlock();
            block.setType(Material.DIAMOND_BLOCK);

            // Remover da lista de localizações
            lastTypeLocations.remove(randomIndex);

            // Anúncio do bloco de diamante
            broadcast(plugin.getConfig().getString("mensagens.frog-bloco-diamante"));

            // Efeitos visuais e sonoros
            block.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    diamondLocation.clone().add(0.5, 1.5, 0.5),
                    50, 0.5, 0.5, 0.5, 0.1);

            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }

            // Criar caminhos de neve para o bloco de diamante
            createSnowPaths();
        }
    }

    private void createSnowPaths() {
        if (diamondLocation == null) return;

        World world = diamondLocation.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y = pos1.getBlockY();
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        // Criar alguns blocos de neve aleatórios como caminho
        int pathBlockCount = ThreadLocalRandom.current().nextInt(5, 15); // Entre 5 e 15 blocos de caminho

        for (int i = 0; i < pathBlockCount; i++) {
            int x = ThreadLocalRandom.current().nextInt(minX, maxX + 1);
            int z = ThreadLocalRandom.current().nextInt(minZ, maxZ + 1);
            Location pathLoc = new Location(world, x, y, z);

            // Não substituir o bloco de diamante ou outros blocos de neve
            if (pathLoc.distance(diamondLocation) > 1.5 && !snowLocations.contains(pathLoc)) {
                Block block = world.getBlockAt(pathLoc);
                Material originalType = block.getType();

                // Só substituir se não for ar ou neve
                if (originalType != Material.AIR && originalType != Material.SNOW_BLOCK) {
                    // Atualizar as listas de controle
                    for (Map.Entry<Material, List<Location>> entry : blockTypeLocations.entrySet()) {
                        entry.getValue().remove(pathLoc);
                    }

                    block.setType(Material.SNOW_BLOCK);
                    snowLocations.add(pathLoc);

                    // Efeito visual
                    world.spawnParticle(Particle.SNOWFLAKE,
                            pathLoc.clone().add(0.5, 1.0, 0.5),
                            10, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
    }

    private void checkPlayersInWater() {
        for (UUID uuid : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Material blockType = player.getLocation().clone().subtract(0, 1, 0).getBlock().getType();

                // Verificar se o jogador está no bloco de diamante (vencedor)
                if (diamondLocation != null &&
                        player.getLocation().getBlockX() == diamondLocation.getBlockX() &&
                        player.getLocation().getBlockY() == diamondLocation.getBlockY() &&
                        player.getLocation().getBlockZ() == diamondLocation.getBlockZ()) {

                    handlePlayerWin(player);
                    stop();
                    return;
                }

                // Verificar se o jogador caiu na água
                if (player.getLocation().getBlock().getType() == Material.WATER ||
                        blockType == Material.WATER) {

                    // Jogador eliminado
                    MessageUtils.send(player, plugin.getConfig().getString("mensagens.frog-eliminado"));
                    player.teleport(player.getWorld().getSpawnLocation());
                    plugin.getEventManager().removePlayerFromEvent(player, this);

                    // Broadcast de eliminação
                    broadcast(ChatColor.RED + "☠ " + player.getName() + " caiu na água e foi eliminado!");

                    // Efeito sonoro para os outros jogadores
                    for (UUID pUuid : players) {
                        Player p = Bukkit.getPlayer(pUuid);
                        if (p != null) {
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 1.0f);
                        }
                    }
                }
            }
        }
    }

    public boolean isPlayerInSpawnArea(Player player) {
        if (spawnPos1 == null || spawnPos2 == null) return false;

        Location loc = player.getLocation();
        int minX = Math.min(spawnPos1.getBlockX(), spawnPos2.getBlockX());
        int maxX = Math.max(spawnPos1.getBlockX(), spawnPos2.getBlockX());
        int minY = Math.min(spawnPos1.getBlockY(), spawnPos2.getBlockY());
        int maxY = Math.max(spawnPos1.getBlockY(), spawnPos2.getBlockY());
        int minZ = Math.min(spawnPos1.getBlockZ(), spawnPos2.getBlockZ());
        int maxZ = Math.max(spawnPos1.getBlockZ(), spawnPos2.getBlockZ());

        return loc.getWorld().equals(spawnPos1.getWorld()) &&
                loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                loc.getBlockY() >= minY && loc.getBlockY() <= maxY &&
                loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    @Override
    public boolean stop() {
        if (!running) return false;

        running = false;
        open = false;

        // Cancelar tarefas
        if (prepareTask != null && !prepareTask.isCancelled()) {
            prepareTask.cancel();
        }

        if (gameTask != null && !gameTask.isCancelled()) {
            gameTask.cancel();
        }

        // Remover BossBar
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Restaurar área do jogo (opcional)
        // restoreGameArea();

        // Teleportar jogadores para o spawn
        for (UUID uuid : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.teleport(player.getWorld().getSpawnLocation());
                plugin.getEventManager().removePlayerFromEvent(player, this);
            }
        }

        // Limpar variáveis
        blockTypeLocations.clear();
        usedBlockTypes.clear();
        snowLocations.clear();
        diamondLocation = null;
        finalPhase = false;

        onEventEnd();
        return true;
    }

    @Override
    public boolean canStart() {
        return pos1 != null && pos2 != null && spawnPos1 != null && spawnPos2 != null && !running && isValidArea();
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
                        "&a✦ O evento &b&lFrog Race &afoi iniciado! Use &e/evento frog &apara participar!"));
    }

    @Override
    protected void onEventEnd() {
        Bukkit.broadcastMessage(MessageUtils.color(
                plugin.getConfig().getString("mensagens.prefix") +
                        "&c✖ O evento &b&lFrog Race &cfoi encerrado!"));
    }

    @Override
    protected void onPlayerJoin(Player player) {
        // Teleportar para a área de espera
        if (spawnPos1 != null && spawnPos2 != null) {
            int minX = Math.min(spawnPos1.getBlockX(), spawnPos2.getBlockX());
            int maxX = Math.max(spawnPos1.getBlockX(), spawnPos2.getBlockX());
            int y = spawnPos1.getBlockY();
            int minZ = Math.min(spawnPos1.getBlockZ(), spawnPos2.getBlockZ());
            int maxZ = Math.max(spawnPos1.getBlockZ(), spawnPos2.getBlockZ());

            // Calcular posição central da área de spawn
            int centerX = minX + (maxX - minX) / 2;
            int centerZ = minZ + (maxZ - minZ) / 2;

            Location spawnLoc = new Location(spawnPos1.getWorld(), centerX + 0.5, y + 1, centerZ + 0.5);
            player.teleport(spawnLoc);
        }

        if (bossBar != null) {
            bossBar.addPlayer(player);
        }

        broadcast(ChatColor.GREEN + "✓ " + player.getName() + " entrou no evento!");
    }

    @Override
    protected void onPlayerLeave(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }

        player.teleport(player.getWorld().getSpawnLocation());
        broadcast(ChatColor.YELLOW + "✧ " + player.getName() + " saiu do evento!");
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

        // Fogos de artifício
        World world = player.getWorld();
        world.spawn(player.getLocation(), org.bukkit.entity.Firework.class, fw -> {
            org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .withColor(Color.AQUA, Color.GREEN, Color.YELLOW)
                    .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                    .withFlicker()
                    .withTrail()
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        });
    }
}