package com.leonardobishop.quests.bukkit.menu;

import com.google.common.primitives.Ints;
import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.config.BukkitQuestsConfig;
import com.leonardobishop.quests.bukkit.menu.element.*;
import com.leonardobishop.quests.bukkit.util.MenuUtils;
import com.leonardobishop.quests.bukkit.util.StringUtils;
import com.leonardobishop.quests.common.player.QPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PaginatedQMenu extends QMenu {

    protected final String title;
    protected final boolean trim;
    private final BukkitQuestsPlugin plugin;
    protected int currentPage;
    protected int pageSize;
    protected int minPage;
    protected int maxPage;

    public PaginatedQMenu(QPlayer owner, String title, boolean trim, int pageSize, BukkitQuestsPlugin plugin) {
        super(owner);
        this.title = title;
        this.trim = trim;
        this.plugin = plugin;
        this.pageSize = pageSize;
        this.currentPage = 1;
        this.minPage = 1;
        this.maxPage = 1;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = Math.max(minPage, Math.min(maxPage, currentPage));
    }

    public int getMinPage() {
        return minPage;
    }

    public void setMinPage(int minPage) {
        this.minPage = minPage;
    }

    public int getMaxPage() {
        return maxPage;
    }

    public void setMaxPage(int maxPage) {
        this.maxPage = maxPage;
    }

    public void populate(String customElementsPath, List<MenuElement> menuElementsToFill, MenuElement backMenuElement) {
        Player player = Bukkit.getPlayer(owner.getPlayerUUID());
        if (player == null) {
            return;
        }

        MenuElement[] staticMenuElements = new MenuElement[pageSize];
        int customStaticElements = 0;
        MenuElement spacer = new SpacerMenuElement();

        // populate custom elements first
        if (customElementsPath != null) {
            if (plugin.getConfig().isConfigurationSection(customElementsPath)) {
                for (String s : plugin.getConfig().getConfigurationSection(customElementsPath).getKeys(false)) {
                    if (!StringUtils.isNumeric(s)) continue;
                    int slot = Integer.parseInt(s);
                    int repeat = plugin.getConfig().getInt(customElementsPath + "." + s + ".repeat");
                    boolean staticElement = plugin.getConfig().getBoolean(customElementsPath + "." + s + ".static", false);
                    MenuElement menuElement;
                    if (plugin.getConfig().contains(customElementsPath + "." + s + ".display")) {
                        ItemStack is = plugin.getConfiguredItemStack(customElementsPath + "." + s + ".display", plugin.getConfig());
                        List<String> commands = plugin.getQuestsConfig().getStringList(customElementsPath + "." + s + ".commands");
                        menuElement = new CustomMenuElement(plugin, owner.getPlayerUUID(), player.getName(), is, commands);
                    } else if (plugin.getConfig().getBoolean(customElementsPath + "." + s + ".spacer", false)) {
                        menuElement = spacer;
                    } else continue; // user = idiot

                    for (int i = 0; i <= repeat; i++) {
                        if (staticElement) {
                            int boundedSlot = slot + i % pageSize;
                            staticMenuElements[boundedSlot] = menuElement;
                            customStaticElements++;
                        } else {
                            menuElements.put(slot + i, menuElement);
                        }
                    }
                }
            }
        }

        // TODO: make these page controls configurable
        // if the amount of predicted menu elements is greater than the size of a page, add
        // the page controls as menu elements
        // this won't check if static elements overlap normal ones first but i don't care
        int maxSize = pageSize - (backMenuElement == null ? 0 : 9);
        BukkitQuestsConfig config = (BukkitQuestsConfig) plugin.getQuestsConfig();
        if ((menuElements.isEmpty() ? 0 : Ints.max(menuElements.keys)) + 1 > maxSize
                || menuElements.size() + menuElementsToFill.size() + customStaticElements > maxSize) {
            MenuElement pageNextMenuElement = new PageNextMenuElement(config, this);
            MenuElement pagePrevMenuElement = new PagePrevMenuElement(config, this);
            MenuElement pageDescMenuElement = new PageDescMenuElement(config, this);
            staticMenuElements[45] = backMenuElement == null ? spacer : backMenuElement;
            staticMenuElements[46] = spacer;
            staticMenuElements[47] = spacer;
            staticMenuElements[48] = pagePrevMenuElement;
            staticMenuElements[49] = pageDescMenuElement;
            staticMenuElements[50] = pageNextMenuElement;
            staticMenuElements[51] = spacer;
            staticMenuElements[52] = spacer;
            staticMenuElements[53] = spacer;

            // else find a place for the back button if needed
        } else if (backMenuElement != null) {
            int slot = MenuUtils.getHigherOrEqualMultiple(menuElements.size() + menuElementsToFill.size() + customStaticElements, 9);
            staticMenuElements[slot] = backMenuElement;
        }

        boolean staticMenuElementsIsFull = true;
        for (MenuElement e : staticMenuElements) {
            if (e == null) {
                staticMenuElementsIsFull = false;
                break;
            }
        }
        if (staticMenuElementsIsFull) {
            // moving on will result in an infinite loop
            return;
        }

        // fill in the remaining menu elements into empty slots
        int slot = 0;
        for (MenuElement element : menuElementsToFill) {
            fillStaticMenuElements(slot, staticMenuElements);
            while (menuElements.containsKey(slot)) {
                slot++;
                fillStaticMenuElements(slot, staticMenuElements);
            }
            menuElements.put(slot, element);
        }

        this.minPage = 1;
        this.maxPage = (menuElements.isEmpty() ? 0 : Ints.max(menuElements.keys)) / pageSize + 1;
    }

    private void fillStaticMenuElements(int slot, MenuElement[] staticMenuElements) {
        if (slot % pageSize == 0) {
            // new page, put in static menu elements
            for (int i = 0; i < staticMenuElements.length; i++) {
                if (staticMenuElements[i] == null) {
                    continue;
                }

                menuElements.put(slot + i, staticMenuElements[i]);
            }
        }
    }

    @Override
    public Inventory draw() {
        int pageMin = pageSize * (currentPage - 1);
        int pageMax = pageSize * currentPage;

        Inventory inventory = Bukkit.createInventory(null, 54, title);

        int highestOnPage = 0;
        for (int pointer = pageMin; pointer < pageMax; pointer++) {
            if (menuElements.containsKey(pointer)) {
                inventory.setItem(pointer - ((currentPage - 1) * pageSize), menuElements.get(pointer).asItemStack());
                if (pointer + 1 > highestOnPage) highestOnPage = pointer + 1;
            }
        }

        if (trim && currentPage == 1) {
            int inventorySize = highestOnPage + (9 - highestOnPage % 9) * Math.min(1, highestOnPage % 9);
            inventorySize = inventorySize <= 0 ? 9 : inventorySize;
            if (inventorySize == 54) {
                return inventory;
            }

            Inventory trimmedInventory = Bukkit.createInventory(null, inventorySize, title);

            for (int slot = 0; slot < trimmedInventory.getSize(); slot++) {
                trimmedInventory.setItem(slot, inventory.getItem(slot));
            }
            return trimmedInventory;
        }

        return inventory;
    }

    @Override
    public @Nullable MenuElement getMenuElementAt(int slot) {
        int pageOffset = (currentPage - 1) * pageSize;
        return super.getMenuElementAt(slot + pageOffset);
    }
}
