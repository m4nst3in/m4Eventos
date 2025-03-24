package me.m4nst3in.m4plugins;

import lombok.Getter;
import me.m4nst3in.m4plugins.commands.EventoCommand;
import me.m4nst3in.m4plugins.commands.EventosCommand;
import me.m4nst3in.m4plugins.commands.FrogCommand;
import me.m4nst3in.m4plugins.commands.WitherStormCommand;
import me.m4nst3in.m4plugins.database.DatabaseManager;
import me.m4nst3in.m4plugins.events.EventManager;
import me.m4nst3in.m4plugins.events.FrogEvent;
import me.m4nst3in.m4plugins.events.WitherStormEvent;
import me.m4nst3in.m4plugins.gui.MenuManager;
import me.m4nst3in.m4plugins.listeners.CommandBlockListener;
import me.m4nst3in.m4plugins.listeners.EventListener;
import me.m4nst3in.m4plugins.listeners.SelectionListener;
import me.m4nst3in.m4plugins.scheduler.EventScheduler;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import me.m4nst3in.m4plugins.utils.SelectionManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.logging.Level;

@Getter
public class M4Eventos extends JavaPlugin {

    @Getter
    private SelectionManager selectionManager;
    private static M4Eventos instance;
    private YamlConfiguration config;
    private DatabaseManager databaseManager;
    private EventManager eventManager;
    private MenuManager menuManager;
    private EventScheduler eventScheduler;
    private Economy economy;
    private File configFile;

    @Override
    public void onEnable() {
        instance = this;

        // Vamos simplificar e focar especificamente no problema da configuração
        if (!setupConfig()) {
            // Se a configuração falhar, desabilite o plugin
            getLogger().severe("Falha ao configurar o arquivo config.yml. O plugin será desativado.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.selectionManager = new SelectionManager();

        // Inicializar mensagens com valores pré-definidos
        MessageUtils.setPrefix("&8[&b&l᠌ᐈ &f&lM4&b&lEventos &8] ");

        try {
            // Inicialização do banco de dados
            setupDatabase();

            // Configuração do Vault (Economia)
            if (!setupEconomy()) {
                getLogger().severe("Vault não encontrado ou nenhum plugin de economia está habilitado!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // Inicialização dos managers
            this.eventManager = new EventManager(this);
            this.menuManager = new MenuManager(this);
            this.eventScheduler = new EventScheduler(this);

            // Registro dos eventos
            registerEvents();

            // Registro dos comandos
            Objects.requireNonNull(getCommand("witherstorm")).setExecutor(new WitherStormCommand(this));
            Objects.requireNonNull(getCommand("frog")).setExecutor(new FrogCommand(this));
            Objects.requireNonNull(getCommand("evento")).setExecutor(new EventoCommand(this));
            Objects.requireNonNull(getCommand("eventos")).setExecutor(new EventosCommand(this));

            // Registro dos listeners
            getServer().getPluginManager().registerEvents(new EventListener(this), this);
            getServer().getPluginManager().registerEvents(new SelectionListener(this), this);
            getServer().getPluginManager().registerEvents(new CommandBlockListener(this), this);

            // Iniciar agendamento
            this.eventScheduler.startScheduler();

            getLogger().info("✦ M4Eventos foi ativado com sucesso! ✦");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ocorreu um erro durante a inicialização do plugin:", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private boolean setupConfig() {
        try {
            // 1. Criar diretório do plugin se não existir
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                boolean created = dataFolder.mkdirs();
                getLogger().info("Diretório do plugin criado: " + created);

                // Pequena pausa para garantir que o sistema de arquivos reconheça
                Thread.sleep(100);
            }

            // 2. Criar arquivo config.yml com caminho absoluto
            configFile = new File(dataFolder, "config.yml");
            getLogger().info("Caminho absoluto do arquivo de configuração: " + configFile.getAbsolutePath());

            if (!configFile.exists()) {
                // Criar arquivo
                boolean created = configFile.createNewFile();
                getLogger().info("Arquivo de configuração criado: " + created);

                // Escrever conteúdo padrão
                writeDefaultConfig();

                // Pequena pausa para garantir que o sistema de arquivos reconheça
                Thread.sleep(100);

                // Verificar se o arquivo foi realmente criado
                if (!configFile.exists() || configFile.length() == 0) {
                    getLogger().severe("Arquivo de configuração não foi criado corretamente.");
                    return false;
                }
            }

            // 3. Verificar se o arquivo é legível
            if (!configFile.canRead()) {
                getLogger().severe("O arquivo de configuração não pode ser lido. Verifique as permissões.");
                return false;
            }

            // 4. Verificar o conteúdo do arquivo antes de carregá-lo
            try (BufferedReader reader = Files.newBufferedReader(configFile.toPath())) {
                boolean hasContent = reader.ready();
                getLogger().info("O arquivo de configuração tem conteúdo: " + hasContent);

                if (!hasContent) {
                    getLogger().info("O arquivo de configuração está vazio, escrevendo configuração padrão...");
                    writeDefaultConfig();

                    // Pequena pausa novamente
                    Thread.sleep(100);
                }
            }

            // 5. Carregar a configuração usando YamlConfiguration
            this.config = YamlConfiguration.loadConfiguration(configFile);

            // 6. Verificar se a configuração foi carregada corretamente
            if (config == null || config.getKeys(false).isEmpty()) {
                getLogger().severe("A configuração foi carregada, mas está vazia.");

                // Tentar reescrever e carregar novamente
                writeDefaultConfig();
                this.config = YamlConfiguration.loadConfiguration(configFile);

                if (config == null || config.getKeys(false).isEmpty()) {
                    getLogger().severe("Não foi possível carregar a configuração mesmo após reescrevê-la.");
                    return false;
                }
            }

            // 7. Extrair um valor para confirmar
            String prefix = config.getString("mensagens.prefix");
            getLogger().info("Valor do prefixo carregado: " + prefix);

            if (prefix != null) {
                MessageUtils.setPrefix(prefix);
                getLogger().info("Configuração carregada com sucesso!");
                return true;
            } else {
                getLogger().warning("Prefixo não encontrado na configuração, usando valor padrão.");
                return true; // Continue mesmo assim, usaremos o valor padrão
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Erro durante a configuração:", e);
            return false;
        }
    }

    private void writeDefaultConfig() throws IOException {
        // Usar FileWriter com flush para garantir que os dados sejam escritos
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("# Configuração M4Eventos\n\n");
            writer.write("# Configurações gerais\n");
            writer.write("mensagens:\n");
            writer.write("  prefix: '&8[&b&l᠌ᐈ &f&lM4&b&lEventos &8] '\n");
            writer.write("  evento-iniciado: '&a✦ O evento %evento% foi iniciado! &7⚡ Use &e/evento %evento% &7para participar!'\n");
            writer.write("  evento-fechado: '&c✖ O evento %evento% foi encerrado!'\n");
            writer.write("  evento-vencedor: '&6✪ &e&lPARABÉNS! &6✪ &f%jogador% &avenceu o evento &b%evento%&a!'\n");
            writer.write("  evento-recompensa: '&a✓ Você recebeu &e%coins% coins &ae um &6item especial&a!'\n");
            writer.write("  evento-sem-permissao: '&c✖ Você não tem permissão para usar este comando!'\n");
            writer.write("  evento-nao-encontrado: '&c✖ Evento não encontrado!'\n");
            writer.write("  evento-ja-iniciado: '&c✖ Este evento já está em andamento!'\n");
            writer.write("  evento-nao-iniciado: '&c✖ Este evento não está em andamento!'\n");
            writer.write("  evento-entrou: '&a✓ Você entrou no evento &b%evento%&a! Boa sorte!'\n");
            writer.write("  evento-ja-participando: '&c✖ Você já está participando de um evento!'\n");
            writer.write("  evento-spawn-definido: '&a✓ Localização de spawn &e%tipo% &adefinida com sucesso!'\n");
            writer.write("  frog-aviso-bloco: '&4⚠ &cAtenção! &fBlocos de &e%bloco% &fvirarão neve em &c%tempo% &fsegundos!'\n");
            writer.write("  frog-bloco-diamante: '&b✧ &b&lUm bloco de diamante apareceu! &b&lSeja o primeiro a alcançá-lo!'\n");
            writer.write("  frog-eliminado: '&c☠ Você caiu na água e foi eliminado!'\n");
            writer.write("  frog-pos-definida: '&a✓ Posição &e%posicao% &adefinida com sucesso!'\n");
            writer.write("  frog-area-invalida: '&c✖ A área definida é maior que o limite de 10x10 blocos!'\n");
            writer.write("  frog-preparando: '&e⚠ O evento começará em &c%tempo% &esegundos! Encontre um bloco seguro!'\n");
            writer.write("  frog-fase-final: '&b✦ &b&lFASE FINAL! &7Apenas um bloco de cada tipo restante! Caminhos de neve estão aparecendo!'\n\n");

            writer.write("# Configurações dos eventos\n");
            writer.write("eventos:\n");
            writer.write("  witherstorm:\n");
            writer.write("    nome: 'Wither Storm'\n");
            writer.write("    habilitado: true\n");
            writer.write("    agendamento: 'WEDNESDAY-16:00'\n");
            writer.write("    recompensas:\n");
            writer.write("      coins: 75000\n");
            writer.write("      itens:\n");
            writer.write("        - material: NETHERITE_SWORD\n");
            writer.write("          quantidade: 1\n");
            writer.write("          encantamentos:\n");
            writer.write("            DAMAGE_ALL: 5\n");
            writer.write("            FIRE_ASPECT: 3\n");
            writer.write("          nome: '&5✦ Espada do Destruidor ✦'\n");
            writer.write("          lore:\n");
            writer.write("            - '&7Forjada com o poder'\n");
            writer.write("            - '&7do Wither Storm'\n");
            writer.write("            - ''\n");
            writer.write("            - '&8\"&7A tempestade que destrói mundos&8\"'\n");
            writer.write("  frog:\n");
            writer.write("    nome: 'Frog Race'\n");
            writer.write("    habilitado: true\n");
            writer.write("    agendamento: 'FRIDAY-19:00'\n");
            writer.write("    recompensas:\n");
            writer.write("      coins: 50000\n");
            writer.write("      itens:\n");
            writer.write("        - material: DIAMOND_BOOTS\n");
            writer.write("          quantidade: 1\n");
            writer.write("          encantamentos:\n");
            writer.write("            PROTECTION_FALL: 4\n");
            writer.write("            DEPTH_STRIDER: 3\n");
            writer.write("          nome: '&b✦ Botas do Sapo Saltador ✦'\n");
            writer.write("          lore:\n");
            writer.write("            - '&7Salte como o mais ágil dos sapos'\n");
            writer.write("            - '&7e nunca tema a queda!'\n\n");

            writer.write("# Configurações de database\n");
            writer.write("database:\n");
            writer.write("  tipo: SQLITE\n");
            writer.write("  arquivo: 'database.db'\n");
            writer.write("  hikari:\n");
            writer.write("    maximumPoolSize: 10\n");
            writer.write("    connectionTimeout: 30000\n");

            // Garantir que tudo seja escrito em disco
            writer.flush();
        }
        getLogger().info("Configuração padrão escrita com sucesso: " + configFile.length() + " bytes.");
    }

    @Override
    public void onDisable() {
        if (this.eventManager != null) {
            this.eventManager.stopAllEvents();
        }

        if (this.databaseManager != null) {
            this.databaseManager.close();
        }

        getLogger().info("✦ M4Eventos foi desativado com sucesso! ✦");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void setupDatabase() {
        try {
            String dbFile = config.getString("database.arquivo", "database.db");

            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Erro ao inicializar o banco de dados", e);
            throw e;
        }
    }

    private void registerEvents() {
        // Registrar eventos disponíveis
        this.eventManager.registerEvent(new WitherStormEvent(this));
        this.eventManager.registerEvent(new FrogEvent(this));
    }

    public static M4Eventos getInstance() {
        return instance;
    }

    // Sobrescrever métodos de configuração para usar nossa implementação personalizada
    @Override
    public YamlConfiguration getConfig() {
        return this.config;
    }

    @Override
    public void saveConfig() {
        try {
            if (config != null && configFile != null) {
                config.save(configFile);
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Não foi possível salvar a configuração em " + configFile, e);
        }
    }

    @Override
    public void reloadConfig() {
        if (configFile != null && configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }
    }
}