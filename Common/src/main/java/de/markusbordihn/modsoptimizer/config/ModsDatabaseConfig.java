/*
 * Copyright 2022 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.modsoptimizer.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import de.markusbordihn.modsoptimizer.Constants;
import de.markusbordihn.modsoptimizer.data.ModFileData.ModEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class ModsDatabaseConfig {

  public static final Path CONFIG_PATH =
      Paths.get("").toAbsolutePath().resolve("config").resolve(Constants.MOD_ID);

  public static final String DEBUG_ENABLED = "debugEnabled";
  public static final String DEBUG_FORCE_SIDE = "debugForceSide";
  public static final String CONFIG_FILE_NAME = "config.toml";
  private static final Map<String, String> modsMap = new HashMap<>();
  private static boolean debugEnabled = false;
  private static String debugForceSide = "default";

  static {
    // Create config file if not exists.
    File configFile = getConfigFile();
    if (configFile == null || !configFile.exists()) {
      configFile = createConfigFile(configFile);
    }

    // Read config file.
    readConfigFile(configFile);
  }

  protected ModsDatabaseConfig() {}

  public static String getConfigFileName() {
    return CONFIG_FILE_NAME;
  }

  public static Map<String, String> getConfig() {
    return modsMap;
  }

  public static boolean isDebugEnabled() {
    return debugEnabled;
  }

  public static String getDebugForceSide() {
    return debugForceSide;
  }

  public static boolean containsMod(String modId) {
    return modsMap.containsKey(modId);
  }

  public static ModEnvironment getModEnvironment(String modId) {
    if (modsMap.containsKey(modId)) {
      String modType = modsMap.get(modId);
      if (modType.equals("client")) {
        return ModEnvironment.CLIENT;
      }
      if (modType.equals("server")) {
        return ModEnvironment.SERVER;
      }
    }
    return ModEnvironment.DEFAULT;
  }

  private static void readConfigFile(File file) {
    if (file == null) {
      file = getConfigFile();
    }
    if (file == null || !file.exists() || !file.canWrite() || !file.canRead()) {
      Constants.LOG.error("⚠ Unable to load config file {}!", file);
      return;
    }

    Constants.LOG.info("Loading Mods Database Config File from {}", file);
    try {
      Map<String, Object> config = new Toml().read(file).toMap();

      // Read mods from config file.
      if (config.containsKey("Mods")) {
        Map<String, String> mods = (Map<String, String>) config.get("Mods");
        for (Map.Entry<String, String> entry : mods.entrySet()) {
          String modId = entry.getKey();
          String modType = entry.getValue();
          if (modId == null || modId.isEmpty() || modType == null || modType.isEmpty()) {
            continue;
          }
          modsMap.put(modId, modType);
        }
      }

      // Read debug options from config file.
      if (config.containsKey("Debug")) {
        Map<String, String> debug = (Map<String, String>) config.get("Debug");
        if (debug.containsKey(DEBUG_ENABLED)) {
          debugEnabled = Boolean.parseBoolean(debug.get(DEBUG_ENABLED));
        }
        if (debug.containsKey(DEBUG_FORCE_SIDE)) {
          debugForceSide = debug.get(DEBUG_FORCE_SIDE);
        }
      }
    } catch (Exception exception) {
      Constants.LOG.error("There was an error, loading the config file {}:", file, exception);
    }
  }

  private static void appendFileHeader(StringBuilder stringBuilder) {
    stringBuilder
        .append("# This file was auto-generated by ")
        .append(Constants.MOD_NAME)
        .append("\n");
    stringBuilder.append("#\n");
    stringBuilder.append("# This file contains a list of known client and server side mods.\n");
    stringBuilder.append("# Most of the mods in this list using the wrong signals or are not\n");
    stringBuilder.append("# compatible with the dedicated server.\n");
    stringBuilder.append("#\n");
    stringBuilder.append(
        "# If your mod is included in this list, please refer to the following documentation for guidance:\n");
    stringBuilder.append(
        "# This documentation will assist you in supporting automatic detection of the correct side:\n");
    stringBuilder.append(
        "# https://github.com/MarkusBordihn/BOs-Mods-Optimizer/wiki/Define-the-correct-environment-for-a-Mod\n");
    stringBuilder.append("#\n");
    stringBuilder.append("# Add additional mod ids and their correct environment, if needed.\n");
    stringBuilder.append("# Remove mod ids, if they are not needed anymore or\n");
    stringBuilder.append("# use mod_id=\"default\" to disable any optimization for them.\n");
    stringBuilder.append("#\n");
    stringBuilder.append("# Last update: ").append(LocalDateTime.now()).append("\n");
    stringBuilder.append(
        "# Note: To automatic update this file after an mod update, just delete the file.\n");
    stringBuilder.append(
        "# Normally you only need to update this file, if you run into problems with specific mods.\n");
    stringBuilder.append("\n");
    stringBuilder.append("[Mods]").append("\n");
    stringBuilder.append("client_side_mod_id=\"client\"\n");
    stringBuilder.append("server_side_mod_id=\"server\"\n");
    stringBuilder.append("default_side_mod_id=\"default\"\n");
  }

  private static File createConfigFile(File file) {
    Constants.LOG.info("Creating Mods Database Config File under {}", file);

    // Add default header
    StringBuilder textContent = new StringBuilder();
    appendFileHeader(textContent);

    // Prepare toml writer.
    OutputStream outputStream = new ByteArrayOutputStream();
    TomlWriter tomlWriter = new TomlWriter.Builder().build();

    // Adds mods to the toml config.
    Map<String, String> sortedModDatabaseMap = getSortedModDatabaseMap();
    if (!sortedModDatabaseMap.isEmpty()) {
      try {
        tomlWriter.write(sortedModDatabaseMap, outputStream);
        textContent.append(outputStream);
      } catch (Exception exception) {
        Constants.LOG.error(
            "There was an error, adding the mods database to the config file {}:", file, exception);
        return null;
      }
    } else {
      Constants.LOG.warn("No mods found inside the built-in mods database!");
    }

    // Define debug options.
    Map<String, String> debugOptions = new HashMap<>();
    debugOptions.put(DEBUG_ENABLED, debugEnabled ? "true" : "false");
    debugOptions.put(DEBUG_FORCE_SIDE, debugForceSide);

    // Add debug options to the toml config.
    outputStream = new ByteArrayOutputStream();
    try {
      tomlWriter.write(debugOptions, outputStream);
      textContent.append("\n");
      textContent.append("[Debug]\n");
      textContent.append(outputStream);
    } catch (Exception exception) {
      Constants.LOG.error(
          "There was an error, adding the debug options to the config file {}:", file, exception);
      return null;
    }

    // Write config file.
    try {
      Files.writeString(file.toPath(), textContent, StandardOpenOption.CREATE_NEW);
    } catch (Exception exception) {
      Constants.LOG.error("There was an error, writing the config file to {}:", file, exception);
      return null;
    }

    return file;
  }

  private static Map<String, String> getSortedModDatabaseMap() {
    Map<String, String> modIdMap = new HashMap<>();
    for (String modId : ClientModsDatabase.getClientSideModsList()) {
      modIdMap.put(modId, "client");
    }

    // Add known server side mods to list.
    for (String modId : ServerModsDatabase.getServerSideModsList()) {
      modIdMap.put(modId, "server");
    }

    // Add known both side mods to list.
    for (String modId : DefaultModsDatabase.getDefaultModsList()) {
      modIdMap.put(modId, "default");
    }

    return new TreeMap<>(modIdMap);
  }

  public static File getConfigFile() {
    Path path = getConfigDirectory();
    if (path != null) {
      return path.resolve(getConfigFileName()).toFile();
    }
    return null;
  }

  private static Path getConfigDirectory() {
    Path resultPath = null;
    try {
      resultPath = Files.createDirectories(CONFIG_PATH);
    } catch (Exception exception) {
      Constants.LOG.error(
          "There was an error, creating the config directory {}:", CONFIG_PATH, exception);
    }
    return resultPath;
  }
}
