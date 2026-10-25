/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.gui.modmenu.pages;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import cn.pupperclient.PupperClient;
import cn.pupperclient.PupperLogger;
import cn.pupperclient.utils.file.FileLocation;

/**
 * The cosmetic files on disk: which ones exist, which are valid, and adding a new one.
 *
 * <p>Kept apart from the page so the file rules can be read, and checked, without the
 * drawing code.
 */
final class CosmeticStore {

    /** One cosmetic file with the id shown to the user. */
    record Item(boolean skin, String id, File file) {}

    private CosmeticStore() {}

    /** Rescans both directories and registers every file with its manager. */
    static List<Item> load() {
        List<Item> items = new ArrayList<>();
        items.addAll(scan(FileLocation.CAPES_DIR, false));
        items.addAll(scan(FileLocation.SKINS_DIR, true));
        return items;
    }

    private static List<Item> scan(File directory, boolean skin) {
        if (!directory.isDirectory()) {
            return List.of();
        }
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null) {
            return List.of();
        }

        List<Item> items = new ArrayList<>(files.length);
        for (File file : files) {
            String id = stripPng(file.getName());
            items.add(new Item(skin, id, file));
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                if (skin) {
                    PupperClient.getInstance().getSkinManager().loadSkin(id, data);
                } else {
                    PupperClient.getInstance().getCapeManager().loadCape(id, data);
                }
            } catch (IOException failure) {
                PupperLogger.error("CosmeticStore", "Failed to load " + file.getName(), failure);
            }
        }
        return items;
    }

    private static String stripPng(String name) {
        return name.substring(0, name.length() - 4);
    }

    /**
     * Copies a chosen file into the cosmetic directory.
     *
     * @return the copied file, or {@code null} when the image is the wrong size or the
     *         name is already taken
     */
    static File add(File selected, boolean skin) {
        if (!isValidImage(selected, skin)) {
            PupperClient.LOGGER.warn("Invalid {} format: {}", skin ? "skin" : "cape",
                selected.getAbsolutePath());
            return null;
        }

        File directory = skin ? FileLocation.SKINS_DIR : FileLocation.CAPES_DIR;
        String name = selected.getName().replace(" ", "_");
        File target = new File(directory, name);
        if (target.exists()) {
            PupperClient.LOGGER.warn("{} already exists: {}", skin ? "Skin" : "Cape", name);
            return null;
        }

        try {
            Files.copy(selected.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException failure) {
            PupperLogger.error("CosmeticStore", "Failed to copy uploaded " + name, failure);
            return null;
        }
    }

    /**
     * Checks the dimensions the renderer relies on.
     *
     * <p>Skins are 64x64 or the legacy 64x32; capes are 64x32 or 128x64.
     */
    private static boolean isValidImage(File file, boolean skin) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return false;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (skin) {
                return width == 64 && (height == 64 || height == 32);
            }
            return (width == 64 && height == 32) || (width == 128 && height == 64);
        } catch (IOException unreadable) {
            return false;
        }
    }
}
