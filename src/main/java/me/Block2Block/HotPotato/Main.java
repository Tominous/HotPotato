package me.Block2Block.HotPotato;

import me.Block2Block.HotPotato.Commands.CommandHotPotato;
import me.Block2Block.HotPotato.Entities.Game;
import me.Block2Block.HotPotato.Kits.Abilities.Leaper;
import me.Block2Block.HotPotato.Kits.Abilities.PotatoWhacker;
import me.Block2Block.HotPotato.Kits.KitLoader;
import me.Block2Block.HotPotato.Listeners.*;
import me.Block2Block.HotPotato.Managers.CacheManager;
import me.Block2Block.HotPotato.Managers.QueueManager;
import me.Block2Block.HotPotato.Managers.StorageManager.DatabaseManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

public class Main extends JavaPlugin {

    private static Main i;

    private static File configFile;
    private static FileConfiguration config;

    private static QueueManager queueManager;
    private static DatabaseManager dbManager;
    private static KitLoader kl;

    @Override
    public void onEnable() {

        i = this;

        kl = new KitLoader();

        //Generating/Loading Config File
        if (!getDataFolder().exists()) getDataFolder().mkdir();

        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            copy(getResource("config.yml"), configFile);
        }

        config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        config.options().copyHeader(true);

        File dataFolder = new File(this.getDataFolder().getAbsolutePath() + "/maps");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        queueManager = new QueueManager();

        dbManager = new DatabaseManager();
        try {
            dbManager.setup();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "There has been an error loading the Database. The plugin will be disabled. Stack Trace:");
            e.printStackTrace();
            Bukkit.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        List<Location> signsToRemove = new ArrayList<>();

        //Update signs.
        for (Location loc : CacheManager.getSigns().keySet()) {
            if (!(loc.getBlock().getState() instanceof Sign)) {
                Bukkit.getLogger().info("One of your signs has been deleted from the world. This sign will now be deleted from the database.");
                getDbManager().removeSign(loc, CacheManager.getSigns().get(loc));
                signsToRemove.add(loc);
                continue;
            }
            if (CacheManager.getSigns().get(loc).equals("queue")) {
                Sign sign = (Sign) loc.getBlock().getState();
                sign.setLine(3, Main.c(null, "Players Queued: &a" + Main.getQueueManager().playersQueued()));
                sign.update(true);
            } else if (CacheManager.getSigns().get(loc).equals("stats")) {
                Sign sign = (Sign) loc.getBlock().getState();
                sign.setLine(1, Main.c(null, "Games Active: &a" + CacheManager.getGames().size()));
                sign.setLine(2, Main.c(null, "Players: &a" + CacheManager.getPlayers().size()));
                sign.setLine(3, Main.c(null, "Players Queued: &a" + Main.getQueueManager().playersQueued()));

                sign.update(true);
            }
        }

        for (Location l : signsToRemove) {
            CacheManager.getSigns().remove(l);
        }

        registerListeners(new BlockBreakListener(),new HealthListener(), new HungerListener(), new JoinListener(), new LeaveListener(),new SignClickListener(), new SignPlaceListener(), new KitSelectionListener(), new TeamSelectionListener(), new EditModeListener(), new TeleportListener(), new PotatoWhacker(), new Leaper());

        getCommand("hotpotato").setExecutor(new CommandHotPotato());

    }

    private void copy(InputStream in, File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        for (int m : CacheManager.getGames().keySet()) {
            Game game = CacheManager.getGames().get(m);
            for (Player p : game.getPlayers()) {
                p.teleport(CacheManager.getLobby());
            }
            World w = game.getWorld();
            Bukkit.getServer().unloadWorld(w, false);
            File worldFolder = w.getWorldFolder();
            try  {
                FileUtils.deleteDirectory(worldFolder);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (Bukkit.getWorld("HPEdit") != null) {
            for (Player p : Bukkit.getWorld("HPEdit").getPlayers()) {
                p.teleport(CacheManager.getLobby());
            }
            Bukkit.getServer().unloadWorld("HPEdit",true);
        }
    }

    private void registerListeners(Listener... listeners) {
        Arrays.stream(listeners).forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));
    }

    public static Main getInstance() {return i;}

    public static String c(String prefix, String message) {
        return ChatColor.translateAlternateColorCodes('&', ((prefix==null)?"&r":"&2"+prefix+">> &r") + message);
    }

    public static String newVersionCheck() {
        try {
            String oldVersion = getInstance().getDescription().getVersion();
            String newVersion = fetchSpigotVersion();
            if(!newVersion.equals(oldVersion)) {
                return newVersion;
            }
            return null;
        }
        catch(Exception e) {
            getInstance().getLogger().info("Unable to check for new versions.");
        }
        return null;
    }

    private static String fetchSpigotVersion() {
        try {
            // We're connecting to spigot's API
            URL url = new URL("https://www.spigotmc.org/api/general.php");
            // Creating a connection
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            // We're writing a body that contains the API access key (Not required and obsolete, but!)
            con.setDoOutput(true);

            // Can't think of a clean way to represent this without looking bad
            String body = "key" + "=" + "98BE0FE67F88AB82B4C197FAF1DC3B69206EFDCC4D3B80FC83A00037510B99B4" + "&" +
                    "resource=47713";

            // Get the output stream, what the site receives
            try (OutputStream stream = con.getOutputStream()) {
                // Write our body containing version and access key
                stream.write(body.getBytes(StandardCharsets.UTF_8));
            }

            // Get the input stream, what we receive
            try (InputStream input = con.getInputStream()) {
                // Read it to string
                String version = IOUtils.toString(input);

                // If the version is not empty, return it
                if (!version.isEmpty()) {
                    return version;
                }
            }
        }
        catch (Exception ex) {
            Bukkit.getLogger().warning("Failed to check for a update on spigot.");
        }

        return null;
    }

    public static void addConfig(String path, Object value) {
        config.set(path, value);
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public static void saveConfigFile() {
        try {
            config.save(configFile);
            config = YamlConfiguration.loadConfiguration(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int chooseRan(int min, int max){
        Random rn = new Random();
        return rn.nextInt(max - min + 1) + min;
    }

    public static QueueManager getQueueManager() {
        return queueManager;
    }

    public static DatabaseManager getDbManager() {return dbManager;}

    public static KitLoader getKitLoader() {
        return kl;
    }
}
