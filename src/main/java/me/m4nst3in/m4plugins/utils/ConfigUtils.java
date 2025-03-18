package me.m4nst3in.m4plugins.utils;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigUtils {

    public static ItemStack createItemFromConfig(ConfigurationSection section) {
        if (section == null) return null;

        String materialName = section.getString("material");
        if (materialName == null) return null;

        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        int quantidade = section.getInt("quantidade", 1);
        ItemStack item = new ItemStack(material, quantidade);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Nome do item
        if (section.contains("nome")) {
            meta.setDisplayName(MessageUtils.color(section.getString("nome")));
        }

        // Lore do item
        if (section.contains("lore")) {
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(MessageUtils.color(line));
            }
            meta.setLore(lore);
        }

        item.setItemMeta(meta);

        // Encantamentos
        if (section.contains("encantamentos")) {
            ConfigurationSection enchantSection = section.getConfigurationSection("encantamentos");
            if (enchantSection != null) {
                for (String enchantName : enchantSection.getKeys(false)) {
                    Enchantment enchantment = getEnchantmentByName(enchantName);
                    if (enchantment != null) {
                        int level = enchantSection.getInt(enchantName);
                        item.addUnsafeEnchantment(enchantment, level);
                    }
                }
            }
        }

        return item;
    }

    private static Enchantment getEnchantmentByName(String name) {
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment.getKey().getKey().equalsIgnoreCase(name)) {
                return enchantment;
            }
        }
        return null;
    }
}