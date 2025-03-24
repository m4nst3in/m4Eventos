package me.m4nst3in.m4plugins.events;

import lombok.Getter;
import me.m4nst3in.m4plugins.M4Eventos;
import me.m4nst3in.m4plugins.utils.ConfigUtils;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
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

    @Getter
    private Location pos1;
    @Getter
    private Location pos2;
    @Getter
    private Location spawnPos1;
    @Getter
    private Location spawnPos2;
    private BukkitTask prepareTask;
    private BukkitTask gameTask;
    private BossBar bossBar;
    private final Map<Material, List<Location>> blockTypeLocations;
    private final List<Material> availableBlockTypes;
    private final List<Material> usedBlockTypes;
    private final List<Location> snowLocations;
    private boolean finalPhase;
    private Location diamondLocation;
    private final Map<Location, Material> barrierBlocks = new HashMap<>(); // Armazenar os blocos originais onde a barreira será colocada
    private boolean barrierActive = false;
    private final Map<Location, BlockData> originalBlocks = new HashMap<>();
    private boolean gameStarted = false;

    public FrogEvent(M4Eventos plugin) {
        super(plugin, "frog", "Frog Race");
        this.blockTypeLocations = new HashMap<>();
        this.availableBlockTypes = new ArrayList<>();
        this.usedBlockTypes = new ArrayList<>();
        this.snowLocations = new ArrayList<>();
        this.finalPhase = false;

        Material[] solidBlocksMaterials = {
                Material.STONE, Material.GRANITE, Material.POLISHED_GRANITE,
                Material.DIORITE, Material.POLISHED_DIORITE, Material.ANDESITE,
                Material.POLISHED_ANDESITE, Material.DEEPSLATE, Material.COBBLESTONE,
                Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD,
                Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD,
                Material.MANGROVE_WOOD, Material.CHERRY_WOOD, Material.CRIMSON_HYPHAE,
                Material.WARPED_HYPHAE, Material.GOLD_BLOCK, Material.IRON_BLOCK,
                Material.EMERALD_BLOCK, Material.LAPIS_BLOCK, Material.COPPER_BLOCK,
                Material.COAL_BLOCK, Material.NETHERITE_BLOCK, Material.OBSIDIAN,
                Material.CRYING_OBSIDIAN, Material.QUARTZ_BLOCK, Material.AMETHYST_BLOCK,
                Material.CLAY, Material.HAY_BLOCK, Material.TERRACOTTA, Material.WHITE_TERRACOTTA,
                Material.ORANGE_TERRACOTTA, Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA,
                Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA,
                Material.GRAY_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA,
                Material.PURPLE_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA,
                Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA, Material.BLACK_TERRACOTTA,
                Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE,
                Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
                Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
                Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
                Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE,
                Material.BLACK_CONCRETE, Material.WHITE_WOOL, Material.ORANGE_WOOL,
                Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL,
                Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
                Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL,
                Material.BLUE_WOOL, Material.BROWN_WOOL, Material.GREEN_WOOL,
                Material.RED_WOOL, Material.BLACK_WOOL, Material.SMOOTH_STONE,
                Material.BRICKS, Material.BOOKSHELF, Material.MOSSY_COBBLESTONE,
                Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE,
                Material.PURPUR_BLOCK, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS
        };

        // Adicionar apenas os blocos garantidamente sólidos
        for (Material material : solidBlocksMaterials) {
            if (material != Material.SNOW_BLOCK && material != Material.DIAMOND_BLOCK) {
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

        // Verificar se a área é válida (25x25 máximo)
        if (!isValidArea()) {
            return false;
        }

        running = true;
        open = true;
        gameStarted = false;

        // Resetar variáveis de controle
        blockTypeLocations.clear();
        usedBlockTypes.clear();
        snowLocations.clear();
        finalPhase = false;
        diamondLocation = null;
        barrierBlocks.clear();
        originalBlocks.clear(); // Limpar blocos originais anteriores

        // Salvar o estado original dos blocos antes de alterá-los
        saveOriginalBlocks();

        // Criar barreira entre o spawn e a área do evento
        createBarrier();

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

                    // Rebloquear a área de spawn após o início do evento
                    reblockSpawnArea();

                    gameStarted = true; // Marcar que o jogo está em andamento para o bloqueio de comandos
                    startGameLogic();
                    cancel();
                    return;
                }

                if (timeLeft % 30 == 0 || timeLeft <= 10) {
                    broadcast(ChatColor.YELLOW + "O evento " + ChatColor.GOLD + name +
                            ChatColor.YELLOW + " começará em " + ChatColor.RED +
                            formatTime(timeLeft));
                }

                // 30 segundos antes do início, libera a zona de spawn (remove a barreira)
                if (timeLeft == 30 && !spawnZoneUnlocked) {
                    removeBarrier();
                    broadcast(ChatColor.GREEN + "⚠ &a&lA BARREIRA FOI REMOVIDA! &eEntrem na área do evento agora!");
                    broadcast(ChatColor.YELLOW + "☢ Jogadores que não estiverem na área quando o evento começar serão eliminados!");

                    // Efeito sonoro para todos os jogadores
                    for (UUID uuid : players) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
                            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1.0f);
                            player.sendTitle(
                                    ChatColor.GREEN + "BARREIRA REMOVIDA!",
                                    ChatColor.YELLOW + "Entre na área do evento agora!",
                                    5, 60, 10
                            );
                        }
                    }

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

    private void saveOriginalBlocks() {
        if (pos1 == null || pos2 == null) return;

        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int y = pos1.getBlockY();

        // Salvar blocos na altura do evento
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Salvar o bloco na altura do jogo
                Location loc = new Location(world, x, y, z);
                Block block = world.getBlockAt(loc);
                originalBlocks.put(loc.clone(), block.getBlockData().clone());

                // Salvar também blocos abaixo para onde estaria a água
                Location belowLoc = new Location(world, x, y - 2, z);
                Block belowBlock = world.getBlockAt(belowLoc);
                originalBlocks.put(belowLoc.clone(), belowBlock.getBlockData().clone());

                // E o nível intermediário
                Location midLoc = new Location(world, x, y - 1, z);
                Block midBlock = world.getBlockAt(midLoc);
                originalBlocks.put(midLoc.clone(), midBlock.getBlockData().clone());
            }
        }

        plugin.getLogger().info("Salvos " + originalBlocks.size() + " blocos originais para restauração futura");
    }

    private void restoreOriginalBlocks() {
        if (originalBlocks.isEmpty()) return;

        plugin.getLogger().info("Restaurando " + originalBlocks.size() + " blocos ao estado original");

        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Location loc = entry.getKey();
            BlockData data = entry.getValue();

            // Se o mundo estiver carregado e o chunk também
            if (loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                Block block = loc.getBlock();
                block.setBlockData(data);
            }
        }

        originalBlocks.clear();
    }

    private void createBarrier() {
        if (pos1 == null || pos2 == null || spawnPos1 == null || spawnPos2 == null) return;

        // Determine os limites da área do evento
        int eventMinX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int eventMaxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int eventY = pos1.getBlockY();
        int eventMinZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int eventMaxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        // Determine os limites da área de spawn
        int spawnMinX = Math.min(spawnPos1.getBlockX(), spawnPos2.getBlockX());
        int spawnMaxX = Math.max(spawnPos1.getBlockX(), spawnPos2.getBlockX());
        int spawnMinY = Math.min(spawnPos1.getBlockY(), spawnPos2.getBlockY());
        int spawnMaxY = Math.max(spawnPos1.getBlockY(), spawnPos2.getBlockY());
        int spawnMinZ = Math.min(spawnPos1.getBlockZ(), spawnPos2.getBlockZ());
        int spawnMaxZ = Math.max(spawnPos1.getBlockZ(), spawnPos2.getBlockZ());

        World world = pos1.getWorld();

        // Limpar qualquer barreira anterior
        barrierBlocks.clear();

        // Determinar onde a barreira deve ser colocada com base na proximidade das áreas
        // Abordagem: verificar cada lado da área do evento e ver se está próximo à área de spawn

        // Verificar lado -X do evento (oeste)
        if (Math.abs(eventMinX - spawnMaxX) <= 3) {
            // A área de spawn está perto do lado oeste do evento
            int barrierX = (eventMinX + spawnMaxX) / 2;
            for (int z = Math.min(eventMinZ, spawnMinZ); z <= Math.max(eventMaxZ, spawnMaxZ); z++) {
                for (int y = spawnMinY; y <= spawnMaxY + 2; y++) { // +2 para altura da barreira
                    Location loc = new Location(world, barrierX, y, z);
                    placeBarrierBlock(loc);
                }
            }
        }

        // Verificar lado +X do evento (leste)
        if (Math.abs(eventMaxX - spawnMinX) <= 3) {
            // A área de spawn está perto do lado leste do evento
            int barrierX = (eventMaxX + spawnMinX) / 2;
            for (int z = Math.min(eventMinZ, spawnMinZ); z <= Math.max(eventMaxZ, spawnMaxZ); z++) {
                for (int y = spawnMinY; y <= spawnMaxY + 2; y++) {
                    Location loc = new Location(world, barrierX, y, z);
                    placeBarrierBlock(loc);
                }
            }
        }

        // Verificar lado -Z do evento (norte)
        if (Math.abs(eventMinZ - spawnMaxZ) <= 3) {
            // A área de spawn está perto do lado norte do evento
            int barrierZ = (eventMinZ + spawnMaxZ) / 2;
            for (int x = Math.min(eventMinX, spawnMinX); x <= Math.max(eventMaxX, spawnMaxX); x++) {
                for (int y = spawnMinY; y <= spawnMaxY + 2; y++) {
                    Location loc = new Location(world, x, y, barrierZ);
                    placeBarrierBlock(loc);
                }
            }
        }

        // Verificar lado +Z do evento (sul)
        if (Math.abs(eventMaxZ - spawnMinZ) <= 3) {
            // A área de spawn está perto do lado sul do evento
            int barrierZ = (eventMaxZ + spawnMinZ) / 2;
            for (int x = Math.min(eventMinX, spawnMinX); x <= Math.max(eventMaxX, spawnMaxX); x++) {
                for (int y = spawnMinY; y <= spawnMaxY + 2; y++) {
                    Location loc = new Location(world, x, y, barrierZ);
                    placeBarrierBlock(loc);
                }
            }
        }

        // Marcamos como ativa
        barrierActive = true;

        // Broadcast sobre a barreira
        broadcast(ChatColor.YELLOW + "⚠ Uma barreira foi criada entre as áreas de spawn e evento!");
        broadcast(ChatColor.YELLOW + "⚠ Ela será removida 30 segundos antes do início do evento!");
    }

    // Método auxiliar para colocar um bloco de barreira e salvar o bloco original
    private void placeBarrierBlock(Location location) {
        Block block = location.getBlock();

        // Salvar o material atual
        barrierBlocks.put(location.clone(), block.getType());

        // Definir o bloco como barreira
        block.setType(Material.BARRIER);

        // Adicionar efeito visual para destacar o bloco
        location.getWorld().spawnParticle(Particle.ASH,
                location.clone().add(0.5, 0.5, 0.5),
                1, 0, 0, 0, 0,
                new org.bukkit.Particle.DustOptions(Color.RED, 1));
    }

    // Método para remover a barreira
    private void removeBarrier() {
        if (!barrierActive) return;

        // Restaurar blocos originais
        for (Map.Entry<Location, Material> entry : barrierBlocks.entrySet()) {
            Location loc = entry.getKey();
            Material originalType = entry.getValue();

            // Restaurar o bloco original
            loc.getBlock().setType(originalType);

            // Efeito visual
            loc.getWorld().spawnParticle(
                    Particle.CLOUD,
                    loc.clone().add(0.5, 0.5, 0.5),
                    5, 0.2, 0.2, 0.2, 0.05);
        }

        barrierBlocks.clear();
        barrierActive = false;
    }


    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return (minutes > 0 ? minutes + " minuto" + (minutes > 1 ? "s" : "") + " e " : "") +
                remainingSeconds + " segundo" + (remainingSeconds != 1 ? "s" : "");
    }

    public boolean isValidArea() {
        if (pos1 == null || pos2 == null) return false;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;

        return width <= 25 && length <= 25;
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
        int blockTypesNeeded = Math.max(1, totalBlocks / 25);

        plugin.getLogger().info("Total de blocos na arena: " + totalBlocks);
        plugin.getLogger().info("Tipos de blocos que serão usados: " + blockTypesNeeded);

        List<Material> selectedTypes = new ArrayList<>(availableBlockTypes);
        Collections.shuffle(selectedTypes);
        selectedTypes = selectedTypes.subList(0, Math.min(blockTypesNeeded, selectedTypes.size()));

        plugin.getLogger().info("Tipos de blocos selecionados: " + selectedTypes.size());

        // Configurar void (ar) embaixo da área em vez de água
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Definir como AR em vez de água
                world.getBlockAt(x, y - 1, z).setType(Material.AIR);
                world.getBlockAt(x, y - 2, z).setType(Material.AIR);
            }
        }

        // Definir blocos aleatórios, garantindo pelo menos 25 de cada tipo
        List<Location> allLocations = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                allLocations.add(new Location(world, x, y, z));
            }
        }
        Collections.shuffle(allLocations);

        // Primeiro, garantir pelo menos 25 de cada tipo (ou o máximo possível)
        for (Material type : selectedTypes) {
            blockTypeLocations.put(type, new ArrayList<>());
            // Determinar quantos blocos deste tipo (até 25)
            int blocksOfThisType = Math.min(25, allLocations.size() / selectedTypes.size());

            for (int i = 0; i < blocksOfThisType && !allLocations.isEmpty(); i++) {
                Location loc = allLocations.remove(0);
                Block block = world.getBlockAt(loc);
                block.setType(type);
                blockTypeLocations.get(type).add(loc);
            }
            usedBlockTypes.add(type);
        }

        // Preencher o restante aleatoriamente, mantendo o equilíbrio
        if (!allLocations.isEmpty()) {
            int typeIndex = 0;
            for (Location loc : allLocations) {
                Material type = selectedTypes.get(typeIndex);
                Block block = world.getBlockAt(loc);
                block.setType(type);
                blockTypeLocations.get(type).add(loc);

                // Avançar para o próximo tipo
                typeIndex = (typeIndex + 1) % selectedTypes.size();
            }
        }

        // Teleportar todos os jogadores para a área do jogo
        teleportPlayersToGameArea();
    }

    private void reblockSpawnArea() {
        if (pos1 == null || pos2 == null || spawnPos1 == null || spawnPos2 == null) return;

        // Criar barreira novamente
        createBarrier();

        broadcast(ChatColor.RED + "⚠ A barreira entre as áreas foi restaurada! O evento está em andamento.");

        // Eliminar jogadores que ficaram para trás no spawn
        if (open) {
            Set<UUID> playersToRemove = new HashSet<>();

            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && !isPlayerInEventArea(player)) {
                    MessageUtils.send(player, "&c✖ Você não entrou na área do evento a tempo e foi eliminado!");
                    player.teleport(player.getWorld().getSpawnLocation());
                    playersToRemove.add(uuid);

                    // Efeitos para jogador eliminado
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5f, 1.0f);
                    player.sendTitle(
                            ChatColor.RED + "ELIMINADO!",
                            ChatColor.YELLOW + "Você não entrou na área a tempo",
                            10, 60, 10
                    );
                }
            }

            // Remover jogadores eliminados
            players.removeAll(playersToRemove);
        }
    }

    private void teleportPlayersToGameArea() {
        if (pos1 == null || pos2 == null || players.isEmpty()) return;

        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y = pos1.getBlockY();
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        broadcast(ChatColor.GOLD + "✦ Teleportando todos os jogadores para a área do evento...");

        // Criar uma lista de posições possíveis
        List<Location> teleportLocations = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                teleportLocations.add(loc);
            }
        }

        // Embaralhar as posições
        Collections.shuffle(teleportLocations);

        // Teleportar cada jogador para uma posição diferente na área
        int locationIndex = 0;
        for (UUID uuid : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (locationIndex < teleportLocations.size()) {
                    player.teleport(teleportLocations.get(locationIndex));
                    locationIndex++;
                } else {
                    // Se não houver mais posições disponíveis, use a primeira novamente
                    player.teleport(teleportLocations.get(0));
                }

                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.sendTitle(
                        ChatColor.GREEN + "Frog Race",
                        ChatColor.YELLOW + "Boa sorte!",
                        10, 40, 10);
            }
        }
    }

    public boolean isPlayerInEventArea(Player player) {
        if (pos1 == null || pos2 == null) return false;

        Location loc = player.getLocation();

        // Verificar se o jogador está no mesmo mundo
        if (!loc.getWorld().equals(pos1.getWorld())) return false;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int y = pos1.getBlockY();

        // Verificar se está na área do evento (com alguma tolerância vertical)
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ &&
                Math.abs(loc.getBlockY() - y) <= 2;
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

        // Se a barreira ainda estiver ativa, remova-a
        if (barrierActive) {
            removeBarrier();
        }

        running = false;
        open = false;
        gameStarted = false;

        // Restaurar os blocos originais
        restoreOriginalBlocks();

        // Cancelar tarefas
        if (prepareTask != null) {
            prepareTask.cancel();
            prepareTask = null;
        }

        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }

        // Remover BossBar
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Broadcast fim do evento
        Bukkit.broadcastMessage(MessageUtils.color(
                plugin.getConfig().getString("mensagens.prefix") +
                        plugin.getConfig().getString("mensagens.evento-fechado").replace("%evento%", name)));

        // Teleportar jogadores de volta ao spawn
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(player.getWorld().getSpawnLocation());
            }
        }

        players.clear();
        return true;
    }

    public boolean canUseCommand(String command) {
        // Se o evento não estiver rodando ou o jogo não iniciou, permitir comandos
        if (!running || !gameStarted) return true;

        // Se for o comando /g, permitir
        return command.equalsIgnoreCase("g") || command.startsWith("g ");
    }

    // Getter para verificar se o jogo está em andamento
    public boolean isGameStarted() {
        return gameStarted;
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
        // Teleportar para a área de espera (longe da barreira)
        if (spawnPos1 != null && spawnPos2 != null) {
            int minX = Math.min(spawnPos1.getBlockX(), spawnPos2.getBlockX()) + 2; // +2 para evitar teleporte na borda
            int maxX = Math.max(spawnPos1.getBlockX(), spawnPos2.getBlockX()) - 2; // -2 para evitar teleporte na borda
            int y = spawnPos1.getBlockY();
            int minZ = Math.min(spawnPos1.getBlockZ(), spawnPos2.getBlockZ()) + 2;
            int maxZ = Math.max(spawnPos1.getBlockZ(), spawnPos2.getBlockZ()) - 2;

            // Ajustar se a área for muito pequena
            if (maxX - minX < 2) minX = maxX = (minX + maxX) / 2;
            if (maxZ - minZ < 2) minZ = maxZ = (minZ + maxZ) / 2;

            // Calcular posição central da área de spawn, ligeiramente afastada da barreira
            int centerX = minX + (maxX - minX) / 2;
            int centerZ = minZ + (maxZ - minZ) / 2;

            Location spawnLoc = new Location(spawnPos1.getWorld(), centerX + 0.5, y + 1, centerZ + 0.5);
            player.teleport(spawnLoc);
        }

        if (bossBar != null) {
            bossBar.addPlayer(player);
        }

        // Enviar mensagem sobre a barreira se estiver ativa
        if (barrierActive) {
            MessageUtils.send(player, "&e⚠ Uma barreira separa a área de spawn da área do evento!");
            MessageUtils.send(player, "&eEla será removida 30 segundos antes do início!");
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