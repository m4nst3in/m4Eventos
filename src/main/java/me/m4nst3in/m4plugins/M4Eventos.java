package me.m4nst3in.m4plugins;

import lombok.Getter;
import me.m4nst3in.m4plugins.commands.EventoCommand;
import me.m4nst3in.m4plugins.commands.EventosCommand;
import me.m4nst3in.m4plugins.commands.WitherStormCommand;
import me.m4nst3in.m4plugins.database.DatabaseManager;
import me.m4nst3in.m4plugins.events.EventManager;
import me.m4nst3in.m4plugins.events.WitherStormEvent;
import me.m4nst3in.m4plugins.gui.MenuManager;
import me.m4nst3in.m4plugins.listeners.EventListener;
import me.m4nst3in.m4plugins.scheduler.EventScheduler;
import me.m4nst3in.m4plugins.utils.MessageUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

@Getter
public class M4Eventos extends JavaPlugin {

    @Getter
    private static M4Eventos instance;
    private FileConfiguration config;
    private DatabaseManager databaseManager;
    private EventManager eventManager;
    private MenuManager menuManager;
    private EventScheduler eventScheduler;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;

        // Configuração
        saveDefaultConfig();
        this.config = getConfig();
        MessageUtils.init(this);

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
        Objects.requireNonNull(getCommand("evento")).setExecutor(new EventoCommand(this));
        Objects.requireNonNull(getCommand("eventos")).setExecutor(new EventosCommand(this));

        // Registro dos listeners
        getServer().getPluginManager().registerEvents(new EventListener(this), this);

        // Iniciar agendamento
        this.eventScheduler.startScheduler();

        getLogger().info("M4Eventos foi ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        if (this.eventManager != null) {
            this.eventManager.stopAllEvents();
        }

        if (this.databaseManager != null) {
            this.databaseManager.close();
        }

        getLogger().info("M4Eventos foi desativado com sucesso!");
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
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Erro ao inicializar o banco de dados", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerEvents() {
        // Registrar eventos disponíveis
        this.eventManager.registerEvent(new WitherStormEvent(this));
    }

}