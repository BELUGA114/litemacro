package com.alfre.macrobuilder;

import static com.alfre.macrobuilder.MacroText.tr;

import com.alfre.macrobuilder.mixin.BossHealthOverlayAccessor;
import com.alfre.macrobuilder.mixin.CreativeSlotClickInvoker;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

final class MacroRunner {
   private static final int PLAYER_INVENTORY_SLOT_COUNT = 36;
   private static final int OUTSIDE_CONTAINER_SLOT = -999;
   private static final int SCREEN_TIMEOUT_TICKS = 100;
   private static final int CLOSE_SETTLE_TICKS = 4;
   private static final int MINE_AREA_MOVE_TIMEOUT_TICKS = 600;
   private static final int MINE_AREA_BREAK_TIMEOUT_TICKS = 400;
   private static final int BOW_DRAW_TICKS = 25;
   private static final int ENTITY_REACH_DISTANCE = 3;
   private static final int RECORDED_FORWARD = 1;
   private static final int RECORDED_BACK = 1 << 1;
   private static final int RECORDED_LEFT = 1 << 2;
   private static final int RECORDED_RIGHT = 1 << 3;
   private static final int RECORDED_JUMP = 1 << 4;
   private static final int RECORDED_CROUCH = 1 << 5;
   private static final int RECORDED_SPRINT = 1 << 6;
   private static final int RECORDED_ATTACK = 1 << 7;
   private static final int RECORDED_USE = 1 << 8;
   private static final int RECORDED_INVENTORY = 1 << 9;
   private static final int RECORDED_DROP = 1 << 10;
   private static final int RECORDED_SWAP_HANDS = 1 << 11;
   private static final int MAX_RECORDED_ACTION_TICKS = 20 * 60 * 10;
   private static final Set<String> VANILLA_RAIDER_ENTITY_IDS = Set.of(
      "minecraft:evoker",
      "minecraft:pillager",
      "minecraft:ravager",
      "minecraft:vindicator",
      "minecraft:illusioner",
      "minecraft:witch"
   );
   private static final int TELEPORT_DISTANCE_SQUARED = 64;
   private static final int CHAT_MESSAGE_LIMIT = 256;
   private static final Pattern PRICE_RANGE_PATTERN = Pattern.compile(
      "\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt]?)\\s*(?:-|\\.\\.|to)\\s*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt]?)",
      Pattern.CASE_INSENSITIVE
   );
   private static final Pattern PRICE_PATTERN = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt]?)", Pattern.CASE_INSENSITIVE);
   private static final Pattern FIRST_INTEGER_PATTERN = Pattern.compile("-?\\d+");
   private static final Pattern FIRST_DOUBLE_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
   private static final HttpClient DISCORD_HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();
   static final int MACRO_COUNT = 100;
   private static final String DEFAULT_EXAMPLE_EXPORT = "/macro_builder/default_macro2_export.json";
   private final Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve("litemacro");
   private final Path previousBrandConfigDirectory = FabricLoader.getInstance().getConfigDir().resolve("litemarco");
   private final Path legacyConfigDirectory = FabricLoader.getInstance().getConfigDir().resolve("minebot-macro-builder");
   private final Path settingsPath = this.configDirectory.resolve("settings.json");
   private final boolean childRunner;
   private final MacroRunner parentRunner;
   private final List<MacroRunner> childRunners = new ArrayList<>();
   private final List<MacroRunner> pendingChildRunners = new ArrayList<>();
   private final boolean[] macroKeyWasDown = new boolean[100];
   private MacroModel model;
   private MacroBuilderSettings settings = new MacroBuilderSettings();
   private String copiedMacroJson;
   private String copiedMacroLabel = "";
   private int copiedMacroNumber;
   private int currentMacroNumber = 1;
   private final Map<String, String> runtimeValues = new HashMap<>();
   private final Map<TextNumberObservation.Key, TextNumberObservation> numberObservations = new HashMap<>();
   private Object numberObservationWorld;
   private boolean conditionWaitAnnounced;
   private final boolean[] observedMacroRunning = new boolean[MACRO_COUNT];
   private final Map<KeyMapping, Integer> rootHeldKeyMappings = new HashMap<>();
   private final Map<InputConstants.Key, Integer> rootHeldRawKeys = new HashMap<>();
   private final Set<KeyMapping> ownedKeyMappings = new HashSet<>();
   private final Set<InputConstants.Key> ownedRawKeys = new HashSet<>();
   private boolean running;
   private String currentNodeId;
   private String activeNodeId;
   private int activeTicks;
   private boolean activeEnchantClicked;
   private int activeEnchantClickTick;
   private boolean activeGrindstoneResultClicked;
   private boolean activeGrindstoneResultReleased;
   private int activeGrindstoneResultClickTick;
   private int activeFishPhase;
   private int activeFishPhaseTick;
   private int activeFishCatchCount;
   private int stepDelayTicksRemaining;
   private Component lastStatus = Component.translatable("macro_builder.runner.stopped");
   private float lastHealth = Float.NaN;
   private Vec3 lastPlayerPos;
   private String lastWorldId = "";
   private final Set<UUID> knownPlayerIds = new HashSet<>();
   private final Set<UUID> currentPlayerIds = new HashSet<>();
   private final Map<UUID, String> knownPlayerNames = new HashMap<>();
   private long chatSequence;
   private long actionbarSequence;
   private long damageSequence;
   private long teleportSequence;
   private long playerSpawnSequence;
   private long playerDespawnSequence;
   private long kickedSequence;
   private long scheduleMinute = -1L;
   private long nodeChatBaseline;
   private long nodeActionbarBaseline;
   private long nodeDamageBaseline;
   private long nodeTeleportBaseline;
   private long nodeSpawnBaseline;
   private long nodeDespawnBaseline;
   private long nodeKickedBaseline;
   private String lastChatMessage = "";
   private String lastActionbarMessage = "";
   private String lastPlayerSpawnName = "";
   private String lastPlayerDespawnName = "";
   private String lastKickReason = "";
   private CompletableFuture<MacroRunner.DiscordResult> activeDiscordRequest;
   private String activeDiscordNodeId;
   private BlockPos activeFarmCropPos;
   private String activeFarmSeedItem = "";
   private boolean activeFarmHarvested;
   private BlockPos activeMineAreaPos;
   private BlockPos activeMineAreaDescentTarget;
   private int activeMineAreaDescentStartY;
   private int activeMineAreaTicks;
   private int activeMineAreaTravelTicks;
   private final Set<BlockPos> activeMineAreaSkippedBlocks = new HashSet<>();
   private BlockPos activeJumpPlaceSupportPos;
   private String lastOpenContainerTagFilter = "";
   private String lastOpenContainerTagNextNodeId = "";
   private final Map<String, Integer> repeatCounters = new HashMap<>();
   private final Map<String, Boolean> autonomousConditionStates = new HashMap<>();
   private boolean waitingForWorld;
   private long autoStartChatBaseline;
   private long autoStartActionbarBaseline;
   private long autoStartKickedBaseline;
   private ServerData lastServerData;
   private ServerData pendingRejoinServer;
   private String pendingRejoinNodeId = "";
   private String pendingRejoinLabel = "Rejoin Server";
   private int pendingRejoinDelayTicks;
   private int pendingRejoinConnectTicks;
   private boolean pendingRejoinConnecting;
   private boolean autoReconnectAttempted;
   private boolean wasWorldAvailable;
   private InputConstants.Key activePressedKey;
   private Boolean previousPauseOnLostFocus;
   private boolean actionRecording;
   private MacroModel actionRecordingModel;
   private String actionRecordingNodeId = "";
   private int actionRecordingMacroNumber;
   private int actionRecordingStopKey = -1;
   private boolean actionRecordingStopKeyWasDown;
   private boolean actionRecordingStopKeyArmed;
   private int actionRecordingTicks;
   private final List<RecordedActionFrame> actionRecordingFrames = new ArrayList<>();
   private final List<RecordedActionEvent> actionRecordingEvents = new ArrayList<>();
   private List<RecordedActionFrame> activeRecordedActionFrames = List.of();
   private List<RecordedActionEvent> activeRecordedActionEvents = List.of();
   private int activeRecordedActionFrameIndex;
   private int activeRecordedActionFrameTicks;
   private int activeRecordedActionPreviousFlags;
   private int activeRecordedActionEventIndex;
   private int activeRecordedActionPlaybackTick;
   private int activeRecordedActionEventWaitTicks;

   MacroRunner() {
      this(false, null);
   }

   private MacroRunner(boolean childRunner, MacroRunner parentRunner) {
      this.childRunner = childRunner;
      this.parentRunner = parentRunner;
   }

   void loadOrCreate() {
      try {
         this.ensureConfigFiles();
         this.settings = MacroBuilderSettings.load(this.settingsPath);
         this.model = MacroModel.load(this.macroPath(this.currentMacroNumber));
      } catch (RuntimeException | IOException var2) {
         MacroBuilderClient.LOGGER.error("Failed to load Litemacro file", var2);
         this.model = MacroModel.createDefault();
         this.lastStatus = Component.translatable("macro_builder.runner.load_failed", var2.getMessage());
      }
   }

   MacroModel model() {
      if (this.model == null) {
         this.loadOrCreate();
      }

      return this.model;
   }

   Path macroPath() {
      return this.macroPath(this.currentMacroNumber);
   }

   Path macroFilePath(int macroNumber) {
      return this.macroPath(Math.max(1, Math.min(100, macroNumber)));
   }

   int currentMacroNumber() {
      return this.currentMacroNumber;
   }

   int macroCount() {
      return 100;
   }

   boolean chatNotifications() {
      return this.settings.chatNotifications();
   }

   void setChatNotifications(boolean enabled) {
      this.settings.setChatNotifications(enabled);
      this.saveSettings();
      this.lastStatus = enabled ? Component.translatable("macro_builder.runner.chat_notifications_on") : Component.translatable("macro_builder.runner.chat_notifications_off");
   }

   void toggleChatNotifications() {
      this.setChatNotifications(!this.settings.chatNotifications());
   }

   boolean runningOverlay() {
      return this.settings.runningOverlay();
   }

   void setRunningOverlay(boolean enabled) {
      this.settings.setRunningOverlay(enabled);
      this.saveSettings();
      this.lastStatus = enabled ? Component.translatable("macro_builder.runner.running_overlay_on") : Component.translatable("macro_builder.runner.running_overlay_off");
   }

   void toggleRunningOverlay() {
      this.setRunningOverlay(!this.settings.runningOverlay());
   }

   int macroKey(int macroNumber) {
      return this.settings.macroKey(macroNumber);
   }

   void setMacroKey(int macroNumber, int keyCode) {
      this.settings.setMacroKey(macroNumber, keyCode);
      int index = Math.max(1, Math.min(100, macroNumber)) - 1;
      this.macroKeyWasDown[index] = keyCode >= 0;
      this.saveSettings();
      this.lastStatus = Component.translatable("macro_builder.runner.set_macro_key_to", macroNumber, keyName(keyCode));
   }

   void clearMacroKey(int macroNumber) {
      this.settings.setMacroKey(macroNumber, -1);
      int index = Math.max(1, Math.min(100, macroNumber)) - 1;
      this.macroKeyWasDown[index] = false;
      this.saveSettings();
      this.lastStatus = Component.translatable("macro_builder.runner.cleared_macro_key", macroNumber);
   }

   String macroKeyName(int macroNumber) {
      return keyName(this.settings.macroKey(macroNumber));
   }

   boolean actionRecording() {
      return this.actionRecording;
   }

   int actionRecordingTicks() {
      return this.actionRecordingTicks;
   }

   String actionRecordingStopKeyName() {
      return keyName(this.actionRecordingStopKey);
   }

   boolean startActionRecording(Minecraft client, MacroModel recordingModel, MacroModel.Node node) {
      if (client == null || client.player == null || client.level == null || recordingModel == null || node == null) {
         this.lastStatus = Component.translatable("macro_builder.runner.recording_needs_an_active_world_and_a_selected_recorded_actions_component");
         return false;
      }

      if (!MacroModel.PLAYER_RECORDED_ACTIONS.equals(node.type)) {
         this.lastStatus = Component.translatable("macro_builder.runner.select_a_recorded_actions_component_first");
         return false;
      }

      int stopKey = this.settings.macroKey(this.currentMacroNumber);
      if (stopKey <= 0) {
         this.lastStatus = Component.translatable("macro_builder.runner.assign_a_key_to_macro_before_recording", this.currentMacroNumber);
         this.send(client, this.lastStatus.getString());
         return false;
      }

      this.stopMacroRuns(client, this.currentMacroNumber, tr("macro_builder.runner.stopped_before_recording_actions"));
      this.actionRecording = true;
      this.actionRecordingModel = recordingModel;
      this.actionRecordingNodeId = node.id;
      this.actionRecordingMacroNumber = this.currentMacroNumber;
      this.actionRecordingStopKey = stopKey;
      this.actionRecordingStopKeyWasDown = InputConstants.isKeyDown(stopKey);
      this.actionRecordingStopKeyArmed = !this.actionRecordingStopKeyWasDown;
      this.actionRecordingTicks = 0;
      this.actionRecordingFrames.clear();
      this.actionRecordingEvents.clear();
      this.actionRecordingEvents.clear();
      this.lastStatus = Component.translatable("macro_builder.runner.recording_actions_press_to_stop_and_save", keyName(stopKey));
      this.send(client, this.lastStatus.getString());
      return true;
   }

   void recordContainerInput(int slotId, int button, String inputType, int menuSlotCount, String menuType) {
      if (this.actionRecording && inputType != null && menuType != null) {
         this.actionRecordingEvents.add(
            RecordedActionEvent.container(this.actionRecordingTicks, slotId, button, inputType, menuSlotCount, menuType)
         );
      }
   }

   void recordContainerClose(int menuSlotCount, String menuType) {
      this.recordContainerInput(0, 0, "CLOSE", menuSlotCount, menuType);
   }

   void recordSentChat(String message, boolean command) {
      if (this.actionRecording && message != null && !message.isBlank()) {
         this.actionRecordingEvents.add(RecordedActionEvent.message(this.actionRecordingTicks, command, message));
      }
   }

   void selectMacro(Minecraft client, int macroNumber) {
      int clamped = Math.max(1, Math.min(100, macroNumber));
      this.currentMacroNumber = clamped;
      this.repeatCounters.clear();
      this.autonomousConditionStates.clear();

      try {
         this.ensureConfigFiles();
         this.model = MacroModel.load(this.macroPath(this.currentMacroNumber));
         this.lastStatus = Component.translatable("macro_builder.runner.selected_macro", this.currentMacroNumber);
      } catch (RuntimeException | IOException var5) {
         MacroBuilderClient.LOGGER.error("Failed to select macro {}", this.currentMacroNumber, var5);
         this.model = MacroModel.createDefault();
         this.model.setName("Macro " + this.currentMacroNumber);
         this.lastStatus = Component.translatable("macro_builder.runner.macro_failed_to_load", this.currentMacroNumber, var5.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   void toggleMacro(Minecraft client, int macroNumber) {
      int clamped = Math.max(1, Math.min(100, macroNumber));
      if (this.stopMacroRuns(client, clamped, tr("macro_builder.runner.stopped_macro", clamped))) {
         this.lastStatus = Component.translatable("macro_builder.runner.stopped_macro", clamped);
      } else {
         this.startMacroSlot(client, clamped);
      }
   }

   String macroName(int macroNumber) {
      int clamped = Math.max(1, Math.min(100, macroNumber));

      try {
         this.ensureConfigFiles();
         Path path = this.macroPath(clamped);
         if (Files.exists(path)) {
            return MacroModel.load(path).name();
         }
      } catch (RuntimeException | IOException var4) {
         MacroBuilderClient.LOGGER.warn("Failed to read macro {} name", clamped, var4);
      }

      return tr("macro_builder.runner.macro", clamped);
   }

   void save(MacroModel model) throws IOException {
      this.model = model;
      this.ensureConfigFiles();
      model.save(this.macroPath(this.currentMacroNumber));
      this.replaceActiveModel(this.currentMacroNumber, model);
      this.lastStatus = Component.translatable("macro_builder.runner.saved_macro", this.currentMacroNumber);
   }

   void resetMacro(Minecraft client, int macroNumber) {
      int clamped = Math.max(1, Math.min(100, macroNumber));

      try {
         this.ensureConfigFiles();
         this.stopMacroRuns(client, clamped, tr("macro_builder.runner.stopped_before_resetting_macro"));

         MacroModel reset = MacroModel.createDefault();
         reset.setName("Macro " + clamped);
         reset.save(this.macroPath(clamped));
         this.settings.setMacroKey(clamped, -1);
         this.saveSettings();
         if (this.currentMacroNumber == clamped) {
            this.model = reset;
         }

         this.lastStatus = Component.translatable("macro_builder.runner.reset_macro_to_new", clamped);
         this.send(client, this.lastStatus.getString());
      } catch (RuntimeException | IOException var5) {
         MacroBuilderClient.LOGGER.error("Failed to reset macro {}", clamped, var5);
         this.lastStatus = Component.translatable("macro_builder.runner.reset_failed", var5.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   void copyMacro(Minecraft client, int macroNumber) {
      int clamped = Math.max(1, Math.min(100, macroNumber));

      try {
         this.ensureConfigFiles();
         Path path = this.macroPath(clamped);
         this.copiedMacroJson = Files.readString(path);
         this.copiedMacroLabel = MacroModel.load(path).name();
         this.copiedMacroNumber = clamped;
         this.lastStatus = Component.translatable("macro_builder.runner.copied_macro", clamped, this.copiedMacroLabel);
         this.send(client, this.lastStatus.getString());
      } catch (RuntimeException | IOException var5) {
         MacroBuilderClient.LOGGER.error("Failed to copy macro {}", clamped, var5);
         this.lastStatus = Component.translatable("macro_builder.runner.copy_failed", var5.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   void pasteCopiedMacro(Minecraft client, int macroNumber) {
      int clamped = Math.max(1, Math.min(100, macroNumber));
      if (this.copiedMacroJson != null && !this.copiedMacroJson.isBlank()) {
         try {
            this.ensureConfigFiles();
            this.stopMacroRuns(client, clamped, tr("macro_builder.runner.stopped_before_pasting_macro"));

            Path target = this.macroPath(clamped);
            Files.writeString(target, this.copiedMacroJson);
            MacroModel pasted = MacroModel.load(target);
            pasted.save(target);
            if (this.currentMacroNumber == clamped) {
               this.model = pasted;
            }

            this.lastStatus = Component.translatable("macro_builder.runner.pasted_macro_into_macro", this.copiedMacroNumber, clamped);
            this.send(client, this.lastStatus.getString());
         } catch (RuntimeException | IOException var6) {
            MacroBuilderClient.LOGGER.error("Failed to paste macro into {}", clamped, var6);
            this.lastStatus = Component.translatable("macro_builder.runner.paste_failed", var6.getMessage());
            this.send(client, this.lastStatus.getString());
         }
      } else {
         this.lastStatus = Component.translatable("macro_builder.runner.no_copied_macro_to_paste");
         this.send(client, this.lastStatus.getString());
      }
   }

   void exportMacroToDownloads(Minecraft client, int macroNumber) {
      int clamped = Math.max(1, Math.min(100, macroNumber));

      try {
         this.ensureConfigFiles();
         Path downloads = this.downloadsDirectory();
         Files.createDirectories(downloads);
         Path target = downloads.resolve("Litemacro-Macro-" + clamped + ".json");
         Files.copy(this.macroPath(clamped), target, StandardCopyOption.REPLACE_EXISTING);
         this.lastStatus = Component.translatable("macro_builder.runner.downloaded_macro_to", clamped, target);
         this.send(client, tr("macro_builder.runner.downloaded_macro_to_downloads", clamped));
      } catch (RuntimeException | IOException var6) {
         MacroBuilderClient.LOGGER.error("Failed to download macro {}", clamped, var6);
         this.lastStatus = Component.translatable("macro_builder.runner.download_failed", var6.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   void importNewestMacroFromDownloads(Minecraft client, int macroNumber) {
      try {
         this.importMacroFromDownloads(client, macroNumber, this.newestJsonInDownloads());
      } catch (RuntimeException | IOException var4) {
         MacroBuilderClient.LOGGER.error("Failed to import newest macro into {}", macroNumber, var4);
         this.lastStatus = Component.translatable("macro_builder.runner.import_failed", var4.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   void importMacroFromDownloads(Minecraft client, int macroNumber, Path source) {
      int clamped = Math.max(1, Math.min(100, macroNumber));

      try {
         this.ensureConfigFiles();
         MacroModel imported = this.readMacroImport(source, clamped);
         this.stopMacroRuns(client, clamped, tr("macro_builder.runner.stopped_before_importing_macro"));

         Path target = this.macroPath(clamped);
         imported.save(target);
         if (this.currentMacroNumber == clamped) {
            this.model = imported;
         }

         this.lastStatus = Component.translatable("macro_builder.runner.imported_into_macro", source.getFileName(), clamped);
         this.send(client, this.lastStatus.getString());
      } catch (RuntimeException | IOException var7) {
         MacroBuilderClient.LOGGER.error("Failed to import macro into {}", clamped, var7);
         this.lastStatus = Component.translatable("macro_builder.runner.import_failed", var7.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   void uploadMacroToMarketplace(Minecraft client, int macroNumber) {
      int clamped = Math.max(1, Math.min(100, macroNumber));

      try {
         this.ensureConfigFiles();
         Path marketplace = this.marketplaceDirectory();
         Files.createDirectories(marketplace);
         MacroModel uploaded = MacroModel.load(this.macroPath(clamped));
         Path target = marketplace.resolve("Litemacro-Macro-" + clamped + "-" + this.safeMarketplaceName(uploaded.name()) + ".json");
         Files.copy(this.macroPath(clamped), target, StandardCopyOption.REPLACE_EXISTING);
         this.lastStatus = Component.translatable("macro_builder.runner.uploaded_macro_to_marketplace_2", clamped);
         this.send(client, tr("macro_builder.runner.uploaded_macro_to_marketplace", clamped));
      } catch (RuntimeException | IOException var7) {
         MacroBuilderClient.LOGGER.error("Failed to upload macro {}", clamped, var7);
         this.lastStatus = Component.translatable("macro_builder.runner.marketplace_upload_failed", var7.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   void exportMarketplaceMacroToDownloads(Minecraft client, Path source) {
      try {
         Files.createDirectories(this.downloadsDirectory());
         Path target = this.downloadsDirectory().resolve(source.getFileName().toString());
         Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
         this.lastStatus = Component.translatable("macro_builder.runner.downloaded_from_marketplace", source.getFileName());
         this.send(client, tr("macro_builder.runner.downloaded_marketplace_macro_to_downloads"));
      } catch (RuntimeException | IOException var4) {
         MacroBuilderClient.LOGGER.error("Failed to download marketplace macro", var4);
         this.lastStatus = Component.translatable("macro_builder.runner.marketplace_download_failed", var4.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   void reload(Minecraft client) {
      this.stopMacroRuns(client, this.currentMacroNumber, tr("macro_builder.runner.stopped_before_reload"));
      this.clearSelfRuntimeState();

      try {
         this.ensureConfigFiles();
         this.model = MacroModel.load(this.macroPath(this.currentMacroNumber));
         this.lastStatus = Component.translatable("macro_builder.runner.reloaded_macro_2", this.currentMacroNumber);
         this.send(client, tr("macro_builder.runner.reloaded_macro", this.currentMacroNumber));
      } catch (RuntimeException | IOException var3) {
         MacroBuilderClient.LOGGER.error("Failed to reload Litemacro file", var3);
         this.lastStatus = Component.translatable("macro_builder.runner.reload_failed", var3.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   void resume(Minecraft client, MacroModel model) {
      if (!this.childRunner) {
         this.startModelRuns(client, this.currentMacroNumber, model);
         return;
      }

      this.resumeFromNode(client, model, model.startNode());
   }

   private void resumeFromNode(Minecraft client, MacroModel model, MacroModel.Node start) {
      this.model = model;
      if (start == null) {
         this.lastStatus = Component.translatable("macro_builder.runner.no_macro_entry_point");
         this.send(client, this.lastStatus.getString());
      } else {
         this.currentNodeId = start.id;
         this.activeNodeId = null;
         this.activeTicks = 0;
         this.stepDelayTicksRemaining = 0;
         this.repeatCounters.clear();
         this.runtimeValues.clear();
         this.numberObservations.clear();
         this.numberObservationWorld = null;
         this.conditionWaitAnnounced = false;
         this.waitingForWorld = false;
         this.clearPendingRejoin();
         this.autoReconnectAttempted = false;
         this.captureEventBaselines();
         this.autoStartChatBaseline = this.chatSequence;
         this.autoStartActionbarBaseline = this.actionbarSequence;
         this.autoStartKickedBaseline = this.kickedSequence;
         this.running = true;
         this.refreshNumberObservations(client);
         this.lastStatus = Component.translatable("macro_builder.runner.running_macro", this.currentMacroNumber, model.name());
         this.send(client, tr("macro_builder.runner.resumed_macro", this.currentMacroNumber, model.name()));
      }
   }

   void stop(Minecraft client, String reason) {
      if (!this.childRunner) {
         this.stopSelf(client, reason);
         for (MacroRunner child : new ArrayList<>(this.childRunners)) {
            child.stop(client, reason);
         }

         this.childRunners.clear();
         this.pendingChildRunners.clear();
         this.restorePauseOnLostFocus(client);
         return;
      }

      this.stopSelf(client, reason);
   }

   private void stopSelf(Minecraft client, String reason) {
      this.releaseHeldKeys(client);
      this.clearDiscordRequest();
      this.clearFarmState();
      this.clearMineAreaState();
      this.running = false;
      this.waitingForWorld = false;
      this.activeNodeId = null;
      this.activeTicks = 0;
      this.stepDelayTicksRemaining = 0;
      this.repeatCounters.clear();
      this.runtimeValues.clear();
      this.numberObservations.clear();
      this.numberObservationWorld = null;
      this.conditionWaitAnnounced = false;
      this.clearPendingRejoin();
      this.autoStartChatBaseline = this.chatSequence;
      this.autoStartActionbarBaseline = this.actionbarSequence;
      this.autoStartKickedBaseline = this.kickedSequence;
      this.lastStatus = Component.literal(reason);
      this.send(client, reason);
   }

   boolean running() {
      return this.running || this.hasRunningChildren();
   }

   String status() {
      String step = this.currentNodeId == null ? "none" : this.currentNodeId;
      int runningCount = this.runningChildCount();
      return tr("macro_builder.runner.step", this.lastStatus.getString(), step, (runningCount > 0 ? tr("macro_builder.runner.active", runningCount) : ""));
   }

   List<MacroRunner.OverlaySnapshot> overlaySnapshots() {
      List<MacroRunner.OverlaySnapshot> snapshots = new ArrayList<>();
      this.collectOverlaySnapshots(snapshots);
      return snapshots;
   }

   private void clearSelfRuntimeState() {
      this.running = false;
      this.activeNodeId = null;
      this.activeTicks = 0;
      this.stepDelayTicksRemaining = 0;
      this.repeatCounters.clear();
      this.runtimeValues.clear();
      this.waitingForWorld = false;
      this.clearPendingRejoin();
      this.autoReconnectAttempted = false;
      this.activePressedKey = null;
   }

   private MacroRunner rootRunner() {
      MacroRunner runner = this;
      while (runner.parentRunner != null) {
         runner = runner.parentRunner;
      }

      return runner;
   }

   private boolean hasRunningChildren() {
      for (MacroRunner child : this.childRunners) {
         if (child.running()) {
            return true;
         }
      }

      return false;
   }

   private int runningChildCount() {
      int count = this.running ? 1 : 0;

      for (MacroRunner child : this.childRunners) {
         count += child.runningChildCount();
      }

      return count;
   }

   private void collectOverlaySnapshots(List<MacroRunner.OverlaySnapshot> snapshots) {
      if (this.running) {
         snapshots.add(
            new MacroRunner.OverlaySnapshot(
               this.currentMacroNumber,
               this.model == null ? tr("macro_builder.runner.macro", this.currentMacroNumber) : this.model.name(),
               this.activeComponentLabel(),
               this.lastStatus.getString(),
               this.activeTicks,
               this.runningChildCount()
            )
         );
      }

      for (MacroRunner child : this.childRunners) {
         child.collectOverlaySnapshots(snapshots);
      }
   }

   private String activeComponentLabel() {
      if (this.model == null) {
         return tr("macro_builder.runner.waiting");
      }

      MacroModel.Node node = null;
      if (this.activeNodeId != null) {
         node = this.model.node(this.activeNodeId);
      }

      if (node == null && this.currentNodeId != null) {
         node = this.model.node(this.currentNodeId);
      }

      return node == null ? tr("macro_builder.runner.waiting") : node.displayLabel();
   }

   private boolean hasRunningMacro(int macroNumber) {
      if (this.running && this.currentMacroNumber == macroNumber) {
         return true;
      }

      for (MacroRunner child : this.childRunners) {
         if (child.hasRunningMacro(macroNumber)) {
            return true;
         }
      }

      for (MacroRunner child : this.pendingChildRunners) {
         if (child.hasRunningMacro(macroNumber)) {
            return true;
         }
      }

      return false;
   }

   private boolean stopMacroRuns(Minecraft client, int macroNumber, String reason) {
      boolean stopped = false;
      for (MacroRunner child : new ArrayList<>(this.childRunners)) {
         if (child.currentMacroNumber == macroNumber || child.hasRunningMacro(macroNumber)) {
            child.stop(client, reason);
            stopped = true;
         }
      }

      Iterator<MacroRunner> iterator = this.pendingChildRunners.iterator();
      while (iterator.hasNext()) {
         MacroRunner child = iterator.next();
         if (child.currentMacroNumber == macroNumber || child.hasRunningMacro(macroNumber)) {
            child.stop(client, reason);
            iterator.remove();
            stopped = true;
         }
      }

      if (this.childRunner && this.running && this.currentMacroNumber == macroNumber) {
         this.stopSelf(client, reason);
         stopped = true;
      }

      return stopped;
   }

   private void replaceActiveModel(int macroNumber, MacroModel model) {
      if (this.currentMacroNumber == macroNumber) {
         this.model = model;
      }

      for (MacroRunner child : this.childRunners) {
         child.replaceActiveModel(macroNumber, model);
      }
   }

   private void startMacroSlot(Minecraft client, int macroNumber) {
      try {
         this.ensureConfigFiles();
         MacroModel macro = MacroModel.load(this.macroPath(macroNumber));
         this.startModelRuns(client, macroNumber, macro);
      } catch (RuntimeException | IOException error) {
         MacroBuilderClient.LOGGER.error("Failed to start macro {}", macroNumber, error);
         this.lastStatus = Component.translatable("macro_builder.runner.macro_failed_to_start", macroNumber, error.getMessage());
         this.send(client, this.lastStatus.getString());
      }
   }

   private void startModelRuns(Minecraft client, int macroNumber, MacroModel model) {
      this.stopMacroRuns(client, macroNumber, tr("macro_builder.runner.restarted_macro", macroNumber));
      this.model = macroNumber == this.currentMacroNumber ? model : this.model;
      List<MacroModel.Node> starts = model.startNodes();
      if (starts.isEmpty()) {
         this.lastStatus = Component.translatable("macro_builder.runner.no_macro_entry_point");
         this.send(client, this.lastStatus.getString());
         return;
      }

      for (MacroModel.Node start : starts) {
         MacroRunner child = new MacroRunner(true, this.rootRunner());
         child.settings = this.settings;
         child.currentMacroNumber = macroNumber;
         child.resumeFromNode(client, model, start);
         this.rootRunner().queueChildRunner(child);
      }

      this.lastStatus = Component.translatable("macro_builder.runner.running_macro", macroNumber, model.name());
   }

   private void queueChildRunner(MacroRunner child) {
      if (this.childRunner) {
         this.rootRunner().queueChildRunner(child);
      } else {
         this.pendingChildRunners.add(child);
      }
   }

   private MacroRunner spawnBranch(String targetNodeId, MacroModel.Node previousNode) {
      MacroRunner branch = new MacroRunner(true, this.rootRunner());
      branch.settings = this.settings;
      branch.model = this.model;
      branch.currentMacroNumber = this.currentMacroNumber;
      branch.currentNodeId = targetNodeId;
      branch.activeNodeId = null;
      branch.activeTicks = 0;
      branch.stepDelayTicksRemaining = this.stepDelayTicks(previousNode);
      branch.waitingForWorld = false;
      branch.lastServerData = this.lastServerData;
      branch.chatSequence = this.chatSequence;
      branch.actionbarSequence = this.actionbarSequence;
      branch.damageSequence = this.damageSequence;
      branch.teleportSequence = this.teleportSequence;
      branch.playerSpawnSequence = this.playerSpawnSequence;
      branch.playerDespawnSequence = this.playerDespawnSequence;
      branch.kickedSequence = this.kickedSequence;
      branch.lastChatMessage = this.lastChatMessage;
      branch.lastActionbarMessage = this.lastActionbarMessage;
      branch.lastPlayerSpawnName = this.lastPlayerSpawnName;
      branch.lastPlayerDespawnName = this.lastPlayerDespawnName;
      branch.lastKickReason = this.lastKickReason;
      branch.lastHealth = this.lastHealth;
      branch.lastPlayerPos = this.lastPlayerPos;
      branch.lastWorldId = this.lastWorldId;
      branch.runtimeValues.putAll(this.runtimeValues);
      branch.numberObservations.putAll(this.numberObservations);
      branch.numberObservationWorld = this.numberObservationWorld;
      branch.knownPlayerIds.addAll(this.knownPlayerIds);
      branch.knownPlayerNames.putAll(this.knownPlayerNames);
      branch.captureEventBaselines();
      branch.autoStartChatBaseline = this.chatSequence;
      branch.autoStartActionbarBaseline = this.actionbarSequence;
      branch.autoStartKickedBaseline = this.kickedSequence;
      branch.running = true;
      branch.lastStatus = Component.translatable("macro_builder.runner.running_macro_branch", this.currentMacroNumber);
      return branch;
   }

   private void updateLastServerData(Minecraft client) {
      ServerData server = client.getCurrentServer();
      if (server != null && server.ip != null && !server.ip.isBlank()) {
         this.lastServerData = server;
      }
   }

   private void refreshRuntimeValues(Minecraft client) {
      this.putRuntimeValue("macro.number", Integer.toString(this.currentMacroNumber));
      this.putRuntimeValue("macro.name", this.model == null ? "" : this.model.name());
      this.putRuntimeValue("macro.node", this.currentNodeId);
      this.putRuntimeValue("macro.active_node", this.activeNodeId);
      this.putRuntimeValue("macro.status", this.lastStatus.getString());
      this.putRuntimeValue("macro.running", Boolean.toString(this.running));

      if (this.lastServerData != null) {
         this.putRuntimeValue("server.name", this.lastServerData.name);
         this.putRuntimeValue("server.ip", this.lastServerData.ip);
      }

      Screen screen = client == null ? null : client.gui.screen();
      this.putRuntimeValue("screen.name", screen == null ? "" : screen.getTitle().getString());
      this.putRuntimeValue("screen.type", screen == null ? "" : screen.getClass().getSimpleName());

      if (client == null || client.player == null) {
         this.clearPlayerRuntimeValues();
         return;
      }

      BlockPos playerPos = client.player.blockPosition();
      this.putRuntimeValue("player.name", client.player.getName().getString());
      this.putRuntimeValue("player.uuid", client.player.getUUID().toString());
      this.putRuntimeValue("player.x", Integer.toString(playerPos.getX()));
      this.putRuntimeValue("player.y", Integer.toString(playerPos.getY()));
      this.putRuntimeValue("player.z", Integer.toString(playerPos.getZ()));
      this.putRuntimeValue("player.pos", blockPosText(playerPos));
      this.putRuntimeValue("player.exact_x", coordinateText(client.player.getX()));
      this.putRuntimeValue("player.exact_y", coordinateText(client.player.getY()));
      this.putRuntimeValue("player.exact_z", coordinateText(client.player.getZ()));
      this.putRuntimeValue(
         "player.exact_pos", coordinateText(client.player.getX()) + " " + coordinateText(client.player.getY()) + " " + coordinateText(client.player.getZ())
      );
      this.putRuntimeValue("player.yaw", Integer.toString(Math.round(client.player.getYRot())));
      this.putRuntimeValue("player.pitch", Integer.toString(Math.round(client.player.getXRot())));
      this.putRuntimeValue("player.health", Integer.toString(Mth.ceil(client.player.getHealth())));
      this.putRuntimeValue("player.food", Integer.toString(client.player.getFoodData().getFoodLevel()));
      this.putRuntimeValue("player.xp_level", Integer.toString(client.player.experienceLevel));
      int selectedSlot = this.selectedHotbarSlot(client);
      this.putRuntimeValue("player.selected_slot", selectedSlot < 0 ? "" : Integer.toString(selectedSlot + 1));
      this.putRuntimeValue("player.held_item", itemId(client.player.getMainHandItem()));

      if (client.level != null) {
         this.putRuntimeValue("world.dimension", client.level.dimension().identifier().toString());
      }

      this.refreshTargetRuntimeValues(client);
   }

   private void clearPlayerRuntimeValues() {
      for (String key : List.of(
         "player.name",
         "player.uuid",
         "player.x",
         "player.y",
         "player.z",
         "player.pos",
         "player.exact_x",
         "player.exact_y",
         "player.exact_z",
         "player.exact_pos",
         "player.yaw",
         "player.pitch",
         "player.health",
         "player.food",
         "player.xp_level",
         "player.selected_slot",
         "player.held_item",
         "world.dimension",
         "target.block.x",
         "target.block.y",
         "target.block.z",
         "target.block.pos",
         "target.block.id",
         "target.entity.name",
         "target.entity.id",
         "target.entity.runtime_id",
         "target.entity.uuid"
      )) {
         this.putRuntimeValue(key, "");
      }
   }

   private void refreshTargetRuntimeValues(Minecraft client) {
      if (client.level != null && client.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
         BlockPos pos = hit.getBlockPos();
         this.putRuntimeValue("target.block.x", Integer.toString(pos.getX()));
         this.putRuntimeValue("target.block.y", Integer.toString(pos.getY()));
         this.putRuntimeValue("target.block.z", Integer.toString(pos.getZ()));
         this.putRuntimeValue("target.block.pos", blockPosText(pos));
         this.putRuntimeValue("target.block.id", BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString());
      } else {
         this.putRuntimeValue("target.block.x", "");
         this.putRuntimeValue("target.block.y", "");
         this.putRuntimeValue("target.block.z", "");
         this.putRuntimeValue("target.block.pos", "");
         this.putRuntimeValue("target.block.id", "");
      }

      if (client.hitResult instanceof EntityHitResult hit && hit.getType() == Type.ENTITY) {
         Entity entity = hit.getEntity();
         this.putRuntimeValue("target.entity.name", entity.getName().getString());
         this.putRuntimeValue("target.entity.id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
         this.putRuntimeValue("target.entity.runtime_id", Integer.toString(entity.getId()));
         this.putRuntimeValue("target.entity.uuid", entity.getUUID().toString());
      } else {
         this.putRuntimeValue("target.entity.name", "");
         this.putRuntimeValue("target.entity.id", "");
         this.putRuntimeValue("target.entity.runtime_id", "");
         this.putRuntimeValue("target.entity.uuid", "");
      }
   }

   private void maybeAutoStart(Minecraft client) {
      if (this.model != null) {
         if (this.hasRunningMacro(this.currentMacroNumber)) {
            return;
         }

         String mode = this.model.alwaysOn() ? "always" : this.model.autoStartMode();
         if ("always".equals(mode)) {
            this.resume(client, this.model);
         } else {
            boolean worldAvailable = client != null && client.player != null && client.level != null;
            if (!worldAvailable && !isKickedCondition(mode)) {
               return;
            }

            Boolean triggered = this.idleConditionMatches(
               client, mode, this.model.autoStartFilter(), this.autoStartChatBaseline, this.autoStartActionbarBaseline, this.autoStartKickedBaseline
            );
            if (Boolean.TRUE.equals(triggered)) {
               this.resume(client, this.model);
            }
         }
      }
   }

   private boolean pollComponentAutoTriggers(Minecraft client) {
      if (this.model == null || client == null || client.player == null || client.level == null) {
         return false;
      }

      boolean canStart = !this.hasRunningMacro(this.currentMacroNumber);
      List<MacroModel.Node> triggeredNodes = new ArrayList<>();
      for (MacroModel.Node node : this.model.nodes()) {
         if (!node.enabled || (!MacroModel.EVENT_STATUS_EFFECT.equals(node.type) && !MacroModel.EVENT_BOSSBAR.equals(node.type))) {
            continue;
         }

         Boolean matches = MacroModel.EVENT_STATUS_EFFECT.equals(node.type)
            ? this.statusEffectMatches(client, node)
            : this.bossbarContains(client, node);
         String key = this.currentMacroNumber + ":" + node.id + ":" + node.type + ":" + node.value + ":" + node.value2;
         boolean previous = this.autonomousConditionStates.getOrDefault(key, false);
         boolean current = Boolean.TRUE.equals(matches);
         this.autonomousConditionStates.put(key, current);
         if (canStart && current && !previous) {
            triggeredNodes.add(node);
         }
      }

      if (triggeredNodes.isEmpty()) {
         return false;
      }

      this.startMacroStateRuns(client, this.currentMacroNumber, this.model, triggeredNodes);
      this.lastStatus = Component.translatable("macro_builder.runner.triggered_macro_from_an_effect_or_bossbar", this.currentMacroNumber);
      return true;
   }

   private void detectMacroStateChanges(Minecraft client) {
      for (int index = 0; index < this.observedMacroRunning.length; index++) {
         int macroNumber = index + 1;
         boolean active = this.hasRunningMacro(macroNumber);
         if (active == this.observedMacroRunning[index]) {
            continue;
         }

         this.observedMacroRunning[index] = active;
         this.startMacrosWatchingState(client, macroNumber, active);
      }
   }

   private void startMacrosWatchingState(Minecraft client, int targetMacroNumber, boolean active) {
      for (int ownerMacroNumber = 1; ownerMacroNumber <= MACRO_COUNT; ownerMacroNumber++) {
         if (ownerMacroNumber == targetMacroNumber || this.hasRunningMacro(ownerMacroNumber)) {
            continue;
         }

         Path path = this.macroPath(ownerMacroNumber);
         if (!Files.exists(path)) {
            continue;
         }

         try {
            MacroModel candidate = MacroModel.load(path);
            List<MacroModel.Node> triggerNodes = new ArrayList<>();
            for (MacroModel.Node node : candidate.nodes()) {
               if (!MacroModel.MACRO_ACTIVE_STATE.equals(node.type) || !node.enabled) {
                  continue;
               }

               int watchedMacroNumber = parseInt(node.value, -1);
               Boolean expectedActive = macroActiveState(node.value2);
               if (watchedMacroNumber == targetMacroNumber && expectedActive != null && expectedActive == active) {
                  triggerNodes.add(node);
               }
            }

            if (!triggerNodes.isEmpty()) {
               this.startMacroStateRuns(client, ownerMacroNumber, candidate, triggerNodes);
            }
         } catch (RuntimeException | IOException exception) {
            MacroBuilderClient.LOGGER.warn("Failed to inspect Macro {} state triggers", ownerMacroNumber, exception);
         }
      }
   }

   private void startMacroStateRuns(
      Minecraft client, int ownerMacroNumber, MacroModel ownerModel, List<MacroModel.Node> triggerNodes
   ) {
      for (MacroModel.Node triggerNode : triggerNodes) {
         MacroRunner child = new MacroRunner(true, this.rootRunner());
         child.settings = this.settings;
         child.currentMacroNumber = ownerMacroNumber;
         child.resumeFromNode(client, ownerModel, triggerNode);
         this.rootRunner().queueChildRunner(child);
      }

      this.lastStatus = Component.translatable("macro_builder.runner.triggered_macro_from_macro_state_watcher", ownerMacroNumber, ownerModel.name());
   }

   private Boolean macroActiveStateMatches(MacroModel.Node node) {
      int macroNumber = parseInt(this.resolveRuntimeText(node.value), -1);
      Boolean expectedActive = macroActiveState(this.resolveRuntimeText(node.value2));
      if (macroNumber < 1 || macroNumber > 100 || macroNumber == this.currentMacroNumber || expectedActive == null) {
         return null;
      }

      return this.rootRunner().hasRunningMacro(macroNumber) == expectedActive;
   }

   private static Boolean macroActiveState(String value) {
      String state = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      return switch (state) {
         case "", "active", "running", "run", "on", "true" -> true;
         case "inactive", "deactive", "disabled", "stopped", "stop", "off", "false" -> false;
         default -> null;
      };
   }

   private Boolean idleConditionMatches(Minecraft client, String conditionText, String filterText, long chatBaseline, long actionbarBaseline, long kickedBaseline) {
      String condition = conditionText == null ? "" : conditionText.trim().toLowerCase(Locale.ROOT);
      return switch (condition) {
         case "", "off", "none", "false" -> false;
         case "chat", "message", "text" -> this.chatSequence > chatBaseline && chatFilterMatches(this.lastChatMessage, filterText);
         case "actionbar", "action_bar", "title_actionbar" -> this.actionbarSequence > actionbarBaseline && chatFilterMatches(this.lastActionbarMessage, filterText);
         case "player", "nearby", "player_nearby" -> this.anyNearbyPlayerMatches(client, filterText);
         case "inventory", "inventory_full", "full" -> this.isInventoryFull(client);
         case "kick", "kicked", "disconnect", "disconnected" -> this.kickedSequence > kickedBaseline && eventTextMatches(this.lastKickReason, filterText, "any");
         case "item", "has_item", "inventory_item" -> this.inventoryItemConditionMatches(client, filterText);
         case "xp", "level", "xp_level" -> this.xpLevelConditionMatches(client, filterText);
         case "health", "health_below", "low_health" -> this.healthBelowConditionMatches(client, filterText);
         case "food", "hunger", "food_below", "low_food", "low_hunger" -> this.foodBelowConditionMatches(client, filterText);
         case "water", "in_water" -> client != null && client.player != null && client.player.isInWater();
         case "effect", "status_effect", "potion" -> this.statusEffectMatches(client, filterText, "1");
         case "bossbar", "boss_bar" -> this.bossbarContains(client, filterText, "contains");
         default -> null;
      };
   }

   private static boolean isKickedCondition(String conditionText) {
      String condition = conditionText == null ? "" : conditionText.trim().toLowerCase(Locale.ROOT);
      return "kick".equals(condition) || "kicked".equals(condition) || "disconnect".equals(condition) || "disconnected".equals(condition);
   }

   private static boolean canIdleConditionRunWithoutWorld(String conditionText) {
      String condition = conditionText == null ? "" : conditionText.trim().toLowerCase(Locale.ROOT);
      return switch (condition) {
         case "chat", "message", "text", "actionbar", "action_bar", "title_actionbar", "kick", "kicked", "disconnect", "disconnected" -> true;
         default -> false;
      };
   }

   private boolean autoReconnectConfigured() {
      return this.model != null && isKickedCondition(this.model.autoStartMode());
   }

   private static boolean chatFilterMatches(String message, String filterText) {
      String filter = filterText == null ? "" : filterText.trim();
      if (filter.isBlank()) {
         return true;
      }

      String text = message == null ? "" : message;
      return text.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)) || nameMatchesList(text, filter) || nameMatchesRegex(text, filter);
   }

   private Boolean anyNearbyPlayerMatches(Minecraft client, String filterText) {
      if (client == null || client.player == null || client.level == null) {
         return false;
      }

      String filter = filterText == null ? "" : filterText.trim();

      for (AbstractClientPlayer player : client.level.players()) {
         if (!player.getUUID().equals(client.player.getUUID())) {
            if (filter.isBlank() || eventTextMatches(player.getName().getString(), filter, "any")) {
               return true;
            }
         }
      }

      return false;
   }

   private Boolean inventoryItemConditionMatches(Minecraft client, String filterText) {
      if (client == null || client.player == null) {
         return false;
      }

      String filter = filterText == null ? "" : filterText.trim();
      if (filter.isBlank()) {
         return null;
      }

      String[] tokens = filter.replace(',', ' ').split("\\s+");
      String itemId = MacroModel.normalizeItemId(tokens[0]);
      if (!isKnownItemId(itemId)) {
         return null;
      }

      int minCount = 1;
      for (int index = 1; index < tokens.length; index++) {
         String token = tokens[index].trim().toLowerCase(Locale.ROOT);
         if (token.startsWith("x")) {
            token = token.substring(1);
         } else if (token.startsWith("count=")) {
            token = token.substring("count=".length());
         }

         Integer parsed = parseIntOrNull(token);
         if (parsed != null) {
            minCount = Math.max(1, parsed);
            break;
         }
      }

      int count = 0;
      for (int index = 0; index < client.player.getInventory().getContainerSize(); index++) {
         ItemStack stack = client.player.getInventory().getItem(index);
         if (stackMatches(stack, itemId)) {
            count += stack.getCount();
            if (count >= minCount) {
               return true;
            }
         }
      }

      return false;
   }

   private Boolean xpLevelConditionMatches(Minecraft client, String filterText) {
      if (client == null || client.player == null) {
         return false;
      }

      Integer level = firstInteger(filterText);
      return level == null ? null : client.player.experienceLevel >= level;
   }

   private Boolean healthBelowConditionMatches(Minecraft client, String filterText) {
      if (client == null || client.player == null) {
         return false;
      }

      Double health = firstDouble(filterText);
      return health == null ? null : client.player.getHealth() <= health;
   }

   private Boolean foodBelowConditionMatches(Minecraft client, String filterText) {
      if (client == null || client.player == null) {
         return false;
      }

      Integer food = firstInteger(filterText);
      return food == null ? null : client.player.getFoodData().getFoodLevel() <= food;
   }

   private String idleConditionLabel(String conditionText) {
      String condition = conditionText == null ? "" : conditionText.trim().toLowerCase(Locale.ROOT);
      return switch (condition) {
         case "chat", "message", "text" -> "chat";
         case "actionbar", "action_bar", "title_actionbar" -> "actionbar";
         case "player", "nearby", "player_nearby" -> tr("macro_builder.runner.player_nearby");
         case "inventory", "inventory_full", "full" -> tr("macro_builder.runner.inventory_full");
         case "kick", "kicked", "disconnect", "disconnected" -> "kick/disconnect";
         case "item", "has_item", "inventory_item" -> "item";
         case "xp", "level", "xp_level" -> tr("macro_builder.runner.xp_level");
         case "health", "health_below", "low_health" -> "health";
         case "food", "hunger", "food_below", "low_food", "low_hunger" -> "hunger";
         case "water", "in_water" -> "water";
         default -> "trigger";
      };
   }

   private void pauseUntilWorldReturns(Minecraft client) {
      this.releaseHeldKeys(client);
      this.clearDiscordRequest();
      this.clearFarmState();
      this.clearMineAreaState();
      this.activeJumpPlaceSupportPos = null;
      this.activeNodeId = null;
      this.activeTicks = 0;
      this.stepDelayTicksRemaining = 0;
      if (!this.waitingForWorld) {
         this.waitingForWorld = true;
         this.lastStatus = Component.translatable("macro_builder.runner.waiting_for_server_world_to_return");
      }
   }

   private boolean pendingRejoinActive() {
      return this.pendingRejoinNodeId != null && !this.pendingRejoinNodeId.isBlank();
   }

   private void scheduleAutoReconnect(Minecraft client) {
      this.releaseHeldKeys(client);
      this.clearDiscordRequest();
      this.clearFarmState();
      this.clearMineAreaState();
      this.pendingRejoinServer = this.copyServerData(this.lastServerData);
      this.pendingRejoinNodeId = "__auto_reconnect__";
      this.pendingRejoinLabel = "Auto Reconnect";
      this.pendingRejoinDelayTicks = 40;
      this.pendingRejoinConnectTicks = 0;
      this.pendingRejoinConnecting = false;
      this.autoReconnectAttempted = true;
      this.waitingForWorld = false;
      this.lastStatus = Component.translatable("macro_builder.runner.disconnected_reconnecting_once");
   }

   private void tickPendingRejoin(Minecraft client) {
      String label = this.pendingRejoinLabel == null || this.pendingRejoinLabel.isBlank() ? "Rejoin Server" : this.pendingRejoinLabel;
      if (this.pendingRejoinServer == null || this.pendingRejoinServer.ip == null || this.pendingRejoinServer.ip.isBlank()) {
         this.stop(client, tr("macro_builder.runner.stopped_lost_the_saved_server", label));
      } else if (this.pendingRejoinDelayTicks > 0) {
         this.pendingRejoinDelayTicks--;
         this.lastStatus = Component.translatable("macro_builder.runner.in_ms", label, (this.pendingRejoinDelayTicks * 50));
      } else if (!this.pendingRejoinConnecting) {
         if (client == null) {
            this.lastStatus = Component.translatable("macro_builder.runner.waiting_for_minecraft_client_before", label);
         } else {
            ConnectScreen.startConnecting(
               new TitleScreen(),
               client,
               ServerAddress.parseString(this.pendingRejoinServer.ip),
               this.pendingRejoinServer,
               false,
               null
            );
            this.pendingRejoinConnecting = true;
            this.pendingRejoinConnectTicks = 0;
            this.lastStatus = Component.translatable("macro_builder.runner.connecting_to", this.pendingRejoinServer.ip);
         }
      } else {
         this.pendingRejoinConnectTicks++;
         if (this.pendingRejoinConnectTicks > 600) {
            this.stop(client, tr("macro_builder.runner.stopped_timed_out", label));
         }
      }
   }

   private void finishPendingRejoin(Minecraft client) {
      String nodeId = this.pendingRejoinNodeId;
      String label = this.pendingRejoinLabel == null || this.pendingRejoinLabel.isBlank() ? "Rejoin Server" : this.pendingRejoinLabel;
      this.clearPendingRejoin();
      if ("__auto_reconnect__".equals(nodeId)) {
         this.waitingForWorld = false;
         this.activeNodeId = null;
         this.activeTicks = 0;
         this.stepDelayTicksRemaining = 0;
         this.captureEventBaselines();
         this.lastStatus = Component.translatable("macro_builder.runner.reconnected_macro", this.currentMacroNumber);
         return;
      }

      MacroModel.Node node = this.model == null ? null : this.model.node(nodeId);
      if (node == null) {
         this.stop(client, tr("macro_builder.runner.stopped_component_disappeared", label));
      } else {
         this.complete(client, node, "completed");
      }
   }

   private void clearPendingRejoin() {
      this.pendingRejoinServer = null;
      this.pendingRejoinNodeId = "";
      this.pendingRejoinDelayTicks = 0;
      this.pendingRejoinConnectTicks = 0;
      this.pendingRejoinConnecting = false;
      this.pendingRejoinLabel = "Rejoin Server";
   }

   private ServerData copyServerData(ServerData original) {
      ServerData copy = new ServerData(original.name, original.ip, original.type());
      copy.copyFrom(original);
      return copy;
   }

   void tick(Minecraft client) {
      if (!this.childRunner) {
         if (this.tickActionRecording(client)) {
            return;
         }

         this.pollMacroHotkeys(client);
         this.detectMacroStateChanges(client);
      }

      boolean worldAvailable = client != null && client.player != null && client.level != null;
      if (!this.childRunner && this.wasWorldAvailable && !worldAvailable) {
         this.recordDisconnect(tr("macro_builder.runner.disconnected"));
      }

      this.wasWorldAvailable = worldAvailable;
      if (worldAvailable) {
         this.updateLastServerData(client);
      }

      this.refreshRuntimeValues(client);
      this.refreshNumberObservations(client);
      if (!this.childRunner) {
         this.maintainBackgroundMacroState(client);
         this.pollComponentAutoTriggers(client);
      }

      if (!this.running) {
         if (!this.childRunner && this.model != null) {
            if (worldAvailable) {
               this.updatePolledEvents(client);
            }

            this.maybeAutoStart(client);
         }

         this.tickChildRunners(client);
         return;
      }

      if (this.running) {
         if (!worldAvailable) {
            if (this.pendingRejoinActive()) {
               this.tickPendingRejoin(client);
            } else if (this.runNoWorldNodeIfPossible(client)) {
               this.tickChildRunners(client);
               return;
            } else if (this.autoReconnectConfigured() && !this.autoReconnectAttempted && this.lastServerData != null) {
               this.scheduleAutoReconnect(client);
            } else if (this.model != null && this.model.alwaysOn()) {
               this.pauseUntilWorldReturns(client);
            } else {
               this.stop(client, tr("macro_builder.runner.stopped_player_or_world_was_unavailable"));
            }
         } else if (this.model == null) {
            this.stop(client, tr("macro_builder.runner.stopped_no_macro_loaded"));
         } else if (this.pendingRejoinActive()) {
            this.finishPendingRejoin(client);
         } else {
            if (this.waitingForWorld) {
               this.waitingForWorld = false;
               this.activeNodeId = null;
               this.activeTicks = 0;
               this.stepDelayTicksRemaining = 0;
               this.captureEventBaselines();
               this.lastStatus = Component.translatable("macro_builder.runner.running_macro_after_rejoin", this.currentMacroNumber);
            }

            this.updatePolledEvents(client);
            if (this.stepDelayTicksRemaining > 0) {
               this.stepDelayTicksRemaining--;
            } else {
               MacroModel.Node node = this.model.node(this.currentNodeId);
               if (node == null) {
                  this.stop(client, tr("macro_builder.runner.stopped_missing_component", this.currentNodeId));
               } else {
                  if (!node.id.equals(this.activeNodeId)) {
                     if (!node.id.equals(this.lastOpenContainerTagNextNodeId)) {
                        this.clearOpenContainerTagMatch();
                     }

                     this.activeNodeId = node.id;
                     this.activeTicks = 0;
                     this.clearFarmState();
                     this.clearMineAreaState();
                     this.clearAutoFishState();
                     this.activeJumpPlaceSupportPos = null;
                     this.captureEventBaselines();
                  }

                  this.activeTicks++;

                   if (!node.enabled) {
                      this.skipDisabledNode(client, node);
                   } else {
                      try {
                      String exception = node.type;
                      switch (exception) {
                        case "official:start":
                           this.complete(client, node, "started");
                           break;
                        case "official:player.respawn":
                           this.runRespawn(client, node);
                           break;
                        case "official:player.move":
                           this.runPlayerMove(client, node);
                           break;
                        case "official:player.look":
                           this.runPlayerLook(client, node);
                           break;
                        case "builder:player.recordedActions":
                           this.runRecordedActions(client, node);
                           break;
                        case "official:player.setMouseButton":
                           this.runSetMouseButton(client, node);
                           break;
                        case "official:player.shootBow":
                           this.runShootBow(client, node);
                           break;
                        case "official:player.isAccount":
                           this.completeCondition(client, node, this.isAccount(client, node), tr("macro_builder.runner.failed_is_account_needs_at_least_one_name"));
                           break;
                        case "official:player.inAction":
                           this.completeCondition(client, node, this.isInAction(client, node), tr("macro_builder.runner.failed_player_in_action_needs_a_known_state"));
                           break;
                        case "official:player.isAlive":
                           this.completeCondition(client, node, client.player.getHealth() > 0.0F && !client.player.isDeadOrDying(), "");
                           break;
                        case "official:player.setCrouch":
                           this.runSetCrouch(client, node);
                           break;
                        case "builder:player.setSprint":
                           this.runSetSprint(client, node);
                           break;
                        case "official:player.jump":
                           this.runJump(client, node);
                           break;
                        case "builder:player.autoClick":
                           this.runAutoClick(client, node);
                           break;
                        case "builder:player.autoFish":
                           this.runAutoFish(client, node);
                           break;
                        case "builder:player.pressKey":
                           this.runPressKey(client, node);
                           break;
                        case "official:inventory.isFull":
                           this.completeCondition(client, node, this.isInventoryFull(client), "");
                           break;
                        case "official:inventory.hasItem":
                        case "builder:inventory.hasItem":
                           this.completeCondition(client, node, this.inventoryHasItem(client, node), tr("macro_builder.runner.failed_inventory_has_item_needs_a_valid_item_id"));
                           break;
                        case "builder:player.healthBelow":
                           this.completeCondition(
                              client, node, doubleAtOrBelow(client.player.getHealth(), this.resolveRuntimeText(node.value)), tr("macro_builder.runner.failed_health_below_needs_a_number")
                           );
                           break;
                        case "builder:player.foodBelow":
                           this.completeCondition(
                              client,
                              node,
                              intAtOrBelow(client.player.getFoodData().getFoodLevel(), this.resolveRuntimeText(node.value)),
                              tr("macro_builder.runner.failed_food_below_needs_a_whole_number")
                           );
                           break;
                         case "builder:player.xpLevelAtLeast":
                            this.completeCondition(
                               client,
                               node,
                               intAtLeast(client.player.experienceLevel, this.resolveRuntimeText(node.value)),
                               tr("macro_builder.runner.failed_xp_level_at_least_needs_a_whole_number")
                            );
                            break;
                         case "builder:player.stopAtXpLevel":
                            this.runStopAtXpLevel(client, node);
                            break;
                        case "builder:player.onGround":
                           this.completeCondition(client, node, client.player.onGround(), "");
                           break;
                        case "builder:player.inWater":
                           this.completeCondition(client, node, client.player.isInWater(), "");
                           break;
                        case "official:player.isAtLocation":
                           this.completeCondition(client, node, this.isAtLocation(client, node), tr("macro_builder.runner.failed_player_at_location_needs_x_y_z_and_a_max_distance"));
                           break;
                        case "official:entity.player_nearby":
                           this.completeCondition(client, node, this.playerNearby(client, node), tr("macro_builder.runner.failed_player_nearby_mode_must_be_any_allow_or_block"));
                           break;
                        case "builder:logic.scoreboardContains":
                           this.completeCondition(client, node, this.scoreboardContains(client, node), tr("macro_builder.runner.failed_scoreboard_contains_mode_must_be_contains_exact_or_regex"));
                           break;
                        case MacroModel.SCOREBOARD_NUMBER:
                        case MacroModel.CHAT_NUMBER:
                           this.completeCondition(client, node, this.numberCondition(client, node), tr("macro_builder.runner.failed_invalid_number_comparison"));
                           break;
                        case MacroModel.WAIT_FOR_CONDITION:
                           this.runWaitForCondition(client, node);
                           break;
                        case MacroModel.CLICK_WITH_CONDITION:
                           this.runClickWithCondition(client, node);
                           break;
                        case MacroModel.CLEAR_NUMBER_OBSERVATION:
                           this.clearNumberObservation(client, node);
                           break;
                        case "builder:logic.actionbarContains":
                           this.completeCondition(client, node, this.actionbarContains(node), tr("macro_builder.runner.failed_actionbar_contains_mode_must_be_contains_exact_or_regex"));
                           break;
                        case "builder:logic.hasStatusEffect":
                           this.completeCondition(client, node, this.statusEffectMatches(client, node), tr("macro_builder.runner.failed_has_status_effect_needs_an_effect_id_and_a_minimum_level"));
                           break;
                        case "builder:logic.bossbarContains":
                           this.completeCondition(client, node, this.bossbarContains(client, node), tr("macro_builder.runner.failed_bossbar_contains_mode_must_be_contains_exact_or_regex"));
                           break;
                        case "builder:logic.macroActiveState":
                           this.completeCondition(
                              client,
                              node,
                              this.macroActiveStateMatches(node),
                              tr("macro_builder.runner.failed_macro_active_state_needs_another_macro_slot_from_1_to_100")
                           );
                           break;
                        case "official:misc.wait":
                           this.runWait(client, node);
                           break;
                        case "official:misc.chat":
                           this.runChat(client, node);
                           break;
                        case "official:inventory.clickOpenContainerSlot":
                           this.runClickOpenContainerSlot(client, node);
                           break;
                        case "official:inventory.hotbarSelect":
                           this.runHotbarSelect(client, node);
                           break;
                        case "official:inventory.hotbarUse":
                           this.runHotbarUse(client, node);
                           break;
                        case "official:inventory.dropItems":
                           this.runDropItems(client, node);
                           break;
                        case "official:inventory.chestWithdrawItems":
                           this.runChestWithdrawItems(client, node);
                           break;
                         case "official:inventory.chestDepositItems":
                            this.runChestDepositItems(client, node);
                            break;
                         case "builder:inventory.autoEnchant":
                            this.runAutoEnchant(client, node);
                            break;
                         case "builder:inventory.autoGrindstone":
                            this.runAutoGrindstone(client, node);
                            break;
                        case "builder:inventory.heldItemIs":
                           this.completeCondition(client, node, this.heldItemIs(client, node), tr("macro_builder.runner.failed_held_item_is_needs_a_valid_item_id"));
                           break;
                        case "builder:inventory.slotHasItem":
                           this.completeCondition(client, node, this.slotHasItem(client, node), tr("macro_builder.runner.failed_slot_has_item_needs_slot_1_36_and_a_valid_item"));
                           break;
                        case "builder:inventory.itemOrSlotHasTag":
                           this.completeCondition(
                              client, node, this.itemOrSlotHasTag(client, node), tr("macro_builder.runner.failed_item_slot_has_tag_needs_a_valid_item_slot_and_item")
                           );
                           break;
                        case "builder:inventory.itemDurability":
                           this.completeCondition(
                              client, node, this.itemDurabilityMatches(client, node), tr("macro_builder.runner.failed_item_durability_needs_item_slot_and_lower_higher_percent")
                           );
                           break;
                        case "builder:inventory.emptySlotsAtLeast":
                           this.completeCondition(client, node, this.emptySlotsAtLeast(client, node), tr("macro_builder.runner.failed_empty_slots_at_least_needs_a_whole_number"));
                           break;
                        case "official:inventory.hasOpenContainer":
                           this.completeCondition(client, node, hasOpenContainer(client), "");
                           break;
                        case "official:inventory.isOpenContainerFull":
                           this.completeCondition(client, node, this.isOpenContainerFull(client), tr("macro_builder.runner.failed_open_gui_is_full_needs_an_open_gui"));
                           break;
                        case "builder:inventory.openContainerHasItem":
                           this.completeCondition(
                              client, node, this.openContainerHasItem(client, node), tr("macro_builder.runner.failed_open_gui_has_item_needs_an_open_gui_and_valid_item")
                           );
                           break;
                        case "official:inventory.openInventory":
                           this.runOpenInventory(client, node);
                           break;
                        case "official:inventory.closeOpenContainer":
                           this.runCloseOpenContainer(client, node);
                           break;
                        case "official:world.interactWithBlock":
                           this.runWorldInteract(client, node);
                           break;
                        case "official:world.mineBlock":
                           this.runWorldMine(client, node);
                           break;
                        case "official:world.placeBlock":
                           this.runWorldPlace(client, node);
                           break;
                        case "official:world.jumpAndPlaceBlock":
                           this.runJumpAndPlaceBlock(client, node);
                           break;
                        case "builder:world.farmArea":
                           this.runFarmArea(client, node);
                           break;
                        case "builder:world.openNearestContainer":
                           this.runOpenNearestContainer(client, node);
                           break;
                        case "builder:world.openNearestChest":
                           this.runOpenNearestContainer(client, node, "chest", tr("macro_builder.runner.open_nearest_chest"));
                           break;
                        case "builder:world.openNearestBarrel":
                           this.runOpenNearestContainer(client, node, "barrel", tr("macro_builder.runner.open_nearest_barrel"));
                           break;
                        case "builder:world.openNearestShulker":
                           this.runOpenNearestContainer(client, node, "shulker", tr("macro_builder.runner.open_nearest_shulker"));
                           break;
                        case "builder:world.openNearestEnchantingTable":
                           this.runOpenNearestEnchantingTable(client, node);
                           break;
                        case "builder:world.openNearestGrindstone":
                           this.runOpenNearestGrindstone(client, node);
                           break;
                        case "builder:world.autoBoneMeal":
                           this.runAutoBoneMeal(client, node);
                           break;
                        case "builder:world.mineArea":
                           this.runMineArea(client, node);
                           break;
                        case "builder:world.blockIs":
                           this.completeCondition(
                              client, node, this.blockAtMatches(client, node), tr("macro_builder.runner.failed_block_at_location_is_needs_loaded_x_y_z_and_valid")
                           );
                           break;
                        case "builder:world.lookingAtBlock":
                           this.completeCondition(
                              client,
                              node,
                              this.lookingAtBlockMatches(client, node),
                              tr("macro_builder.runner.failed_looking_at_block_needs_you_to_look_at_a_block_and")
                           );
                           break;
                        case "official:entity.attack":
                           this.runEntityAction(client, node, true);
                           break;
                        case "official:entity.interact":
                           this.runEntityAction(client, node, false);
                           break;
                        case "builder:entity.nearby":
                           this.completeCondition(client, node, this.entityNearby(client, node), tr("macro_builder.runner.failed_entity_nearby_needs_a_positive_max_distance"));
                           break;
                        case "builder:inventory.selectHotbarSlot":
                           this.runSelectHotbarSlot(client, node);
                           break;
                        case "builder:inventory.dropSelectedItem":
                           this.runDropSelectedItem(client, node);
                           break;
                        case "builder:localMessage":
                           this.runLocalMessage(client, node);
                           break;
                        case "builder:misc.repeatMacro":
                           this.runRepeatMacro(client, node);
                           break;
                        case "builder:misc.repeatSection":
                           this.runRepeatSection(client, node);
                           break;
                        case "builder:misc.repeatUntil":
                           this.runRepeatUntil(client, node);
                           break;
                        case "builder:misc.idleUntil":
                           this.runIdleUntil(client, node);
                           break;
                        case "builder:flow.skipIfTrue":
                           this.runSkipIfTrue(client, node);
                           break;
                        case "builder:flow.endConnection":
                           this.endBranch(client, tr("macro_builder.runner.ended_connection"));
                           break;
                        case "builder:flow.note":
                           this.endBranch(client, tr("macro_builder.runner.ended_at_note"));
                           break;
                        case "builder:stop":
                           this.stop(client, tr("macro_builder.runner.stopped_by_macro"));
                           break;
                        case "official:misc.disconnect":
                           this.runDisconnect(client, node);
                           break;
                        case "official:misc.rejoinServer":
                           this.runRejoinServer(client, node);
                           break;
                        case "official:misc.joinServer":
                           this.runJoinServer(client, node);
                           break;
                        case "official:misc.macroStop":
                           this.runMacroStop(client, node);
                           break;
                        case "official:misc.macroStart":
                           this.runMacroStart(client, node);
                           break;
                        case "official:misc.random":
                           this.runRandom(client, node);
                           break;
                        case "builder:misc.randomOutput3":
                           this.runRandomOutput3(client, node);
                           break;
                        case "official:misc.updateSign":
                           this.runUpdateSign(client, node);
                           break;
                        case "official:misc.isInLobby":
                           this.completeCondition(client, node, this.isLobbyMatch(client, node), tr("macro_builder.runner.failed_is_in_lobby_needs_a_player_name"));
                           break;
                        case "official:notification.discord":
                           this.runNotification(client, node);
                           break;
                        case "official:login.repeat":
                           this.complete(client, node, "completed");
                           break;
                        case "builder:event.ifChatSame":
                           this.completeCondition(client, node, this.chatSameMatches(node), tr("macro_builder.runner.failed_if_chat_same_mode_must_be_same_contains_or_regex"));
                           break;
                        case "official:event.chat":
                           this.runEventChat(client, node);
                           break;
                        case "builder:event.actionbar":
                           this.runEventActionbar(client, node);
                           break;
                        case "builder:event.statusEffect":
                           this.runEventStatusEffect(client, node);
                           break;
                        case "builder:event.bossbar":
                           this.runEventBossbar(client, node);
                           break;
                        case "builder:event.kicked":
                           this.runEventKicked(client, node);
                           break;
                        case "official:event.death":
                           this.runEventDeath(client, node);
                           break;
                        case "official:event.damageReceived":
                           this.runEventDamage(client, node);
                           break;
                        case "official:event.teleport":
                           this.runEventTeleport(client, node);
                           break;
                        case "official:event.schedule":
                           this.runEventSchedule(client, node);
                           break;
                        case "official:event.player_spawned":
                           this.runEventPlayerChange(client, node, true);
                           break;
                        case "official:event.player_despawned":
                           this.runEventPlayerChange(client, node, false);
                           break;
                        default:
                           this.stop(client, tr("macro_builder.runner.stopped_unsupported_component", node.type));
                     }
                      } catch (IllegalArgumentException error) {
                         this.fail(client, node, tr("macro_builder.runner.failed_2", node.displayLabel(), error.getMessage()));
                      } catch (RuntimeException var5) {
                        MacroBuilderClient.LOGGER.error("Litemacro component failed: {}", node.type, var5);
                        this.fail(client, node, tr("macro_builder.runner.failed_hit_an_internal_safety_check", node.displayLabel()));
                     }
                  }
               }
            }
         }
      }

      this.tickChildRunners(client);
   }

   private void tickChildRunners(Minecraft client) {
      Iterator<MacroRunner> iterator = this.childRunners.iterator();

      while (iterator.hasNext()) {
         MacroRunner child = iterator.next();
         child.tick(client);
         if (!child.running()) {
            iterator.remove();
         }
      }

      if (!this.pendingChildRunners.isEmpty()) {
         this.childRunners.addAll(this.pendingChildRunners);
         this.pendingChildRunners.clear();
      }
   }

   private boolean runNoWorldNodeIfPossible(Minecraft client) {
      if (this.model == null) {
         return false;
      }

      MacroModel.Node node = this.model.node(this.currentNodeId);
      if (node == null) {
         this.stop(client, tr("macro_builder.runner.stopped_missing_component", this.currentNodeId));
         return true;
      }

      if (!this.canRunWithoutWorld(node)) {
         return false;
      }

      if (!node.id.equals(this.activeNodeId)) {
         this.activeNodeId = node.id;
         this.activeTicks = 0;
         this.clearFarmState();
         this.clearMineAreaState();
         this.activeJumpPlaceSupportPos = null;
         this.captureEventBaselines();
      }

      this.activeTicks++;

      try {
         if (!node.enabled) {
            this.skipDisabledNode(client, node);
            return true;
         }

         switch (node.type) {
            case "official:start":
               this.complete(client, node, "started");
               break;
            case "official:misc.wait":
               this.runWait(client, node);
               break;
            case "builder:event.kicked":
               this.runEventKicked(client, node);
               break;
            case "builder:event.actionbar":
               this.runEventActionbar(client, node);
               break;
            case "builder:logic.actionbarContains":
               this.completeCondition(client, node, this.actionbarContains(node), tr("macro_builder.runner.failed_actionbar_contains_mode_must_be_contains_exact_or_regex"));
               break;
            case "builder:logic.macroActiveState":
               this.completeCondition(
                  client,
                  node,
                  this.macroActiveStateMatches(node),
                  tr("macro_builder.runner.failed_macro_active_state_needs_another_macro_slot_from_1_to_100")
               );
               break;
            case "builder:misc.repeatMacro":
               this.runRepeatMacro(client, node);
               break;
            case "builder:misc.repeatSection":
               this.runRepeatSection(client, node);
               break;
            case "builder:misc.repeatUntil":
               this.runRepeatUntil(client, node);
               break;
            case "builder:misc.idleUntil":
               this.runIdleUntil(client, node);
               break;
            case "builder:flow.skipIfTrue":
               this.runSkipIfTrue(client, node);
               break;
            case "builder:flow.endConnection":
               this.endBranch(client, tr("macro_builder.runner.ended_connection"));
               break;
            case "builder:flow.note":
               this.endBranch(client, tr("macro_builder.runner.ended_at_note"));
               break;
            case "official:misc.rejoinServer":
               this.runRejoinServer(client, node);
               break;
            case "official:misc.joinServer":
               this.runJoinServer(client, node);
               break;
            case "builder:localMessage":
               this.runLocalMessage(client, node);
               break;
            case "official:notification.discord":
               this.runNotification(client, node);
               break;
            case "builder:stop":
               this.stop(client, tr("macro_builder.runner.stopped_by_macro"));
               break;
            case "official:misc.random":
               this.runRandom(client, node);
               break;
            case "builder:misc.randomOutput3":
               this.runRandomOutput3(client, node);
               break;
            default:
               return false;
         }
      } catch (RuntimeException error) {
         MacroBuilderClient.LOGGER.error("Litemacro offline component failed: {}", node.type, error);
         this.fail(client, node, tr("macro_builder.runner.failed_hit_an_internal_safety_check", node.displayLabel()));
      }

      return true;
   }

   private boolean canRunWithoutWorld(MacroModel.Node node) {
      if (node == null) {
         return false;
      }

      return switch (node.type) {
         case "official:start",
            "official:misc.wait",
            "builder:flow.endConnection",
            "builder:flow.note",
            "builder:event.kicked",
            "builder:event.actionbar",
            "builder:logic.actionbarContains",
            "builder:logic.macroActiveState",
            "official:misc.rejoinServer",
            "official:misc.joinServer",
            "builder:localMessage",
            "official:notification.discord",
            "builder:stop",
            "builder:misc.repeatMacro",
            "builder:misc.repeatSection",
            "builder:flow.skipIfTrue",
            "official:misc.random",
            "builder:misc.randomOutput3" -> true;
         case "builder:misc.idleUntil" -> canIdleConditionRunWithoutWorld(node.value);
         case "builder:misc.repeatUntil" -> canIdleConditionRunWithoutWorld(node.value);
         default -> false;
      };
   }

   private void pollMacroHotkeys(Minecraft client) {
      if (macroHotkeysAvailable(client)) {
         for (int index = 0; index < this.macroKeyWasDown.length; index++) {
            int keyCode = this.settings.macroKey(index + 1);
            boolean down = keyCode > 0 && InputConstants.isKeyDown(keyCode);
            if (down && !this.macroKeyWasDown[index]) {
               this.toggleMacro(client, index + 1);
            }

            this.macroKeyWasDown[index] = down;
         }
      } else {
         for (int index = 0; index < this.macroKeyWasDown.length; index++) {
            this.macroKeyWasDown[index] = false;
         }
      }
   }

   void handleMacroHotkeyPress(Minecraft client, int keyCode) {
      if (this.actionRecording && keyCode == this.actionRecordingStopKey) {
         this.finishActionRecording(client, true, "");
         return;
      }

      if (!macroHotkeysAvailable(client) || keyCode < 0) {
         return;
      }

      for (int index = 0; index < this.macroKeyWasDown.length; index++) {
         if (this.settings.macroKey(index + 1) == keyCode) {
            this.toggleMacro(client, index + 1);
            this.macroKeyWasDown[index] = true;
            return;
         }
      }
   }

   private static boolean macroHotkeysAvailable(Minecraft client) {
      if (client == null) {
         return false;
      }

      Screen screen = client.gui.screen();
      return screen == null || screen instanceof TitleScreen || screen instanceof ChatScreen || screen.isPauseScreen() || client.player == null || client.level == null;
   }

   private void closePassiveGameplayScreenIfNeeded(Minecraft client, String type) {
   }

   private static boolean needsClearGameplayScreen(String type) {
      return switch (type) {
         case "official:player.respawn",
            "official:player.move",
            "official:player.look",
            "official:player.setMouseButton",
            "official:player.shootBow",
            "official:player.setCrouch",
            "builder:player.setSprint",
            "official:player.jump",
            "builder:player.autoClick",
            "builder:player.pressKey",
            "builder:player.stopAtXpLevel",
            "official:inventory.hotbarSelect",
            "official:inventory.hotbarUse",
            "official:inventory.dropItems",
            "builder:inventory.selectHotbarSlot",
            "builder:inventory.dropSelectedItem",
            "official:world.interactWithBlock",
            "official:world.mineBlock",
            "official:world.placeBlock",
            "official:world.jumpAndPlaceBlock",
            "builder:world.farmArea",
            "builder:world.openNearestContainer",
            "builder:world.openNearestChest",
            "builder:world.openNearestBarrel",
            "builder:world.openNearestShulker",
            "builder:world.openNearestEnchantingTable",
            "builder:world.openNearestGrindstone",
            "builder:world.autoBoneMeal",
            "builder:world.mineArea",
            "official:entity.attack",
            "official:entity.interact" -> true;
         default -> false;
      };
   }

   void recordChatMessage(String message) {
      if (message != null && !message.isBlank()) {
         this.lastChatMessage = message;
         this.chatSequence++;
         for (MacroRunner child : this.childRunners) {
            child.recordChatMessage(message);
         }

         for (MacroRunner child : this.pendingChildRunners) {
            child.recordChatMessage(message);
         }
      }
   }

   void recordActionbarMessage(String message) {
      if (message != null && !message.isBlank()) {
         this.lastActionbarMessage = message;
         this.actionbarSequence++;
         for (MacroRunner child : this.childRunners) {
            child.recordActionbarMessage(message);
         }

         for (MacroRunner child : this.pendingChildRunners) {
            child.recordActionbarMessage(message);
         }
      }
   }

   void recordDisconnect(String reason) {
      String text = reason == null || reason.isBlank() ? tr("macro_builder.runner.disconnected") : reason.trim();
      this.lastKickReason = text;
      this.kickedSequence++;

      for (MacroRunner child : this.childRunners) {
         child.recordDisconnect(text);
      }

      for (MacroRunner child : this.pendingChildRunners) {
         child.recordDisconnect(text);
      }
   }

   private void runWait(Minecraft client, MacroModel.Node node) {
      Integer durationMs = parseIntOrNull(this.resolveRuntimeText(node.value));
      if (durationMs != null && durationMs >= 0) {
         int durationTicks = Math.max(1, (int)Math.ceil(durationMs.intValue() / 50.0));
         if (this.activeTicks >= durationTicks) {
            this.complete(client, node, "completed");
         }
      } else {
         this.fail(client, node, tr("macro_builder.runner.failed_wait_needs_a_delay_of_0_ms_or_more"));
      }
   }

   private void runRespawn(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1 && (client.player.isDeadOrDying() || client.player.getHealth() <= 0.0F)) {
         client.player.respawn();
      }

      this.complete(client, node, "completed");
   }

   private Boolean isAccount(Minecraft client, MacroModel.Node node) {
      String list = this.resolveRuntimeText(node.value);
      return list.isBlank() ? null : nameMatchesList(client.player.getName().getString(), list);
   }

   private Boolean isInAction(Minecraft client, MacroModel.Node node) {
      String state = this.resolveRuntimeText(node.value).trim().toLowerCase(Locale.ROOT);

      return switch (state) {
         case "sneak", "sneaking", "crouch", "crouching" -> client.player.isShiftKeyDown() || client.player.isCrouching();
         case "use", "using", "using_item" -> client.player.isUsingItem();
         case "dead" -> client.player.isDeadOrDying() || client.player.getHealth() <= 0.0F;
         case "alive" -> !client.player.isDeadOrDying() && client.player.getHealth() > 0.0F;
         default -> null;
      };
   }

   private void runSetCrouch(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         Boolean crouching = parseBooleanOrDefault(this.resolveRuntimeText(node.value), true);
         if (crouching == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_set_crouch_needs_true_or_false"));
            return;
         }

         this.setMacroKeyDown(client, client.options.keyShift, crouching);
      }

      this.complete(client, node, "completed");
   }

   private void runSetSprint(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         Boolean sprinting = parseBooleanOrDefault(this.resolveRuntimeText(node.value), true);
         if (sprinting == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_set_sprint_needs_true_or_false"));
            return;
         }

         this.setMacroKeyDown(client, client.options.keySprint, sprinting);
         client.player.setSprinting(sprinting);
      }

      this.complete(client, node, "completed");
   }

   private void runJump(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         client.player.jumpFromGround();
      }

      this.complete(client, node, "completed");
   }

   private void runAutoClick(Minecraft client, MacroModel.Node node) {
      Integer button = parseMouseButton(this.resolveRuntimeText(node.value));
      MacroRunner.AutoClickOptions options = autoClickOptions(this.resolveRuntimeText(node.value2));
      if (button == null || options == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_auto_click_needs_button_left_right_and_options_like_count_5"));
      } else if (options.hold()) {
         if (this.activeTicks == 1) {
            this.setMouseButtonDown(client, button, true);
         }

         if (this.activeTicks >= durationTicks(options.durationMs())) {
            this.setMouseButtonDown(client, button, false);
            this.complete(client, node, "completed");
         }
      } else {
         int intervalTicks = Math.max(1, durationTicks(options.intervalMs()));
         int clickIndex = (this.activeTicks - 1) / intervalTicks;
         if (clickIndex >= options.count()) {
            this.setMouseButtonDown(client, button, false);
            this.complete(client, node, "completed");
         } else if ((this.activeTicks - 1) % intervalTicks == 0) {
            this.clickMouseButton(client, button);
         }
      }
   }

   private void runAutoFish(Minecraft client, MacroModel.Node node) {
      Integer rodLowPercent = parseIntOrNull(this.resolveRuntimeText(node.value));
      Integer timeoutSeconds = parseIntOrNull(this.resolveRuntimeText(node.value2));
      if (rodLowPercent == null || rodLowPercent < 0 || rodLowPercent > 100 || timeoutSeconds == null || timeoutSeconds < 5 || timeoutSeconds > 300) {
         this.fail(client, node, tr("macro_builder.runner.failed_auto_fish_needs_rod_low_durability_from_0_100_and_a"));
         return;
      }

      if (this.activeFishPhase == 0) {
         if (this.isInventoryFull(client)) {
            this.complete(client, node, "inventory_full");
            return;
         }

         int rodSlot = this.bestFishingRodSlot(client, rodLowPercent);
         if (rodSlot < 0) {
            if (this.hasHotbarFishingRod(client)) {
               this.complete(client, node, "rod_low");
            } else {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_fish_needs_a_fishing_rod_in_the_hotbar"));
            }

            return;
         }

         client.player.getInventory().setSelectedSlot(rodSlot);
         ItemStack rod = client.player.getInventory().getItem(rodSlot);
         this.putRuntimeValue("fish.rod_slot", Integer.toString(rodSlot + 1));
         this.putRuntimeValue("fish.rod_durability_percent", Integer.toString(remainingDurabilityPercent(rod)));
         if (!this.useFishingRod(client)) {
            this.fail(client, node, tr("macro_builder.runner.failed_auto_fish_could_not_cast_the_fishing_rod"));
            return;
         }

         this.activeFishPhase = 1;
         this.activeFishPhaseTick = this.activeTicks;
         return;
      }

      int phaseTicks = this.activeTicks - this.activeFishPhaseTick;
      if (this.activeFishPhase == 1) {
         var bobber = client.player.fishing;
         if (bobber == null) {
            if (phaseTicks > 40) {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_fish_did_not_create_a_fishing_bobber"));
            }

            return;
         }

         if (phaseTicks >= 15 && bobber.getDeltaMovement().y < -0.04) {
            if (!this.useFishingRod(client)) {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_fish_could_not_reel_in_the_fishing_rod"));
               return;
            }

            this.activeFishPhase = 2;
            this.activeFishPhaseTick = this.activeTicks;
         } else if (phaseTicks >= timeoutSeconds * 20) {
            this.useFishingRod(client);
            this.activeFishPhase = 3;
            this.activeFishPhaseTick = this.activeTicks;
         }

         return;
      }

      if (phaseTicks < 8) {
         return;
      }

      if (this.activeFishPhase == 2) {
         this.activeFishCatchCount++;
         this.putRuntimeValue("fish.catch_count", Integer.toString(this.activeFishCatchCount));
         this.complete(client, node, this.isInventoryFull(client) ? "inventory_full" : "caught");
      } else {
         this.clearAutoFishState();
      }
   }

   private boolean useFishingRod(Minecraft client) {
      if (client.gameMode == null) {
         return false;
      }

      client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
      client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
      return true;
   }

   private int bestFishingRodSlot(Minecraft client, int lowPercent) {
      int bestSlot = -1;
      int bestRemaining = -1;
      for (int slot = 0; slot < 9 && slot < client.player.getInventory().getContainerSize(); slot++) {
         ItemStack stack = client.player.getInventory().getItem(slot);
         if ("minecraft:fishing_rod".equals(itemId(stack))) {
            int remainingPercent = remainingDurabilityPercent(stack);
            if (remainingPercent > lowPercent && remainingPercent > bestRemaining) {
               bestSlot = slot;
               bestRemaining = remainingPercent;
            }
         }
      }

      return bestSlot;
   }

   private boolean hasHotbarFishingRod(Minecraft client) {
      for (int slot = 0; slot < 9 && slot < client.player.getInventory().getContainerSize(); slot++) {
         if ("minecraft:fishing_rod".equals(itemId(client.player.getInventory().getItem(slot)))) {
            return true;
         }
      }

      return false;
   }

   private static int remainingDurabilityPercent(ItemStack stack) {
      if (stack == null || stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
         return 100;
      }

      int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
      return Mth.clamp((int)Math.floor(remaining * 100.0 / stack.getMaxDamage()), 0, 100);
   }

   private void runPressKey(Minecraft client, MacroModel.Node node) {
      InputConstants.Key key = keyFromText(this.resolveRuntimeText(node.value));
      MacroRunner.PressKeyOptions options = pressKeyOptions(this.resolveRuntimeText(node.value2));
      if (key == null || options == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_press_key_needs_a_key_like_space_shift_ctrl_alt_e"));
      } else if (options.hold()) {
         if (this.activeTicks == 1) {
            this.activePressedKey = key;
            this.setRawKeyDown(client, key, true);
         }

         if (this.activeTicks >= durationTicks(options.durationMs())) {
            this.setRawKeyDown(client, key, false);
            this.activePressedKey = null;
            this.complete(client, node, "completed");
         }
      } else {
         if (this.activeTicks == 1) {
            KeyMapping.click(key);
         }

         if (this.activeTicks >= durationTicks(options.durationMs())) {
            this.setRawKeyDown(client, key, false);
            this.complete(client, node, "completed");
         }
      }
   }

   private void runPlayerMove(Minecraft client, MacroModel.Node node) {
      String targetText = this.resolveRuntimeText(node.value);
      Vec3 target = this.parseResolvedVec3(client, targetText);
      if (target != null) {
         this.lookAt(client, target);
         double distanceSquared = client.player.distanceToSqr(target);
         if (distanceSquared <= 1.0) {
            this.releaseMovementKeys(client);
            this.complete(client, node, "completed");
         } else {
            this.setMacroKeyDown(client, client.options.keyUp, true);
            if (this.activeTicks > 300) {
               this.releaseMovementKeys(client);
               this.fail(client, node, tr("macro_builder.runner.failed_move_timed_out_before_reaching_target"));
            }
         }
      } else {
         Entity targetEntity = this.findTargetEntity(client, targetText);
         if (targetEntity != null) {
            this.lookAt(client, this.entityCenter(targetEntity));
            if (client.player.distanceToSqr(targetEntity) <= 1.25) {
               this.releaseMovementKeys(client);
               this.complete(client, node, "completed");
            } else {
               this.setMacroKeyDown(client, client.options.keyUp, true);
               if (this.activeTicks > 300) {
                  this.releaseMovementKeys(client);
                  this.fail(client, node, tr("macro_builder.runner.failed_move_timed_out_before_reaching_entity"));
               }
            }
         } else {
            String direction = targetText != null && !targetText.isBlank() ? targetText.trim().toLowerCase(Locale.ROOT) : "forward";
            if (!isMoveDirection(direction)) {
               this.releaseMovementKeys(client);
               this.fail(client, node, tr("macro_builder.runner.failed_move_could_not_find_entity_use_id_123_uuid_an_entity", targetText));
               return;
            }

            if (this.activeTicks == 1) {
               this.releaseMovementKeys(client);
               switch (direction) {
                  case "back":
                  case "backward":
                  case "backwards":
                     this.setMacroKeyDown(client, client.options.keyDown, true);
                     break;
                  case "left":
                     this.setMacroKeyDown(client, client.options.keyLeft, true);
                     break;
                  case "right":
                     this.setMacroKeyDown(client, client.options.keyRight, true);
                     break;
                  case "jump":
                  case "up":
                     this.setMacroKeyDown(client, client.options.keyJump, true);
                     break;
                  default:
                     this.setMacroKeyDown(client, client.options.keyUp, true);
               }
            }

            int moveTicks = Math.max(1, (int)Math.ceil(Math.max(0.1, parseDouble(this.resolveRuntimeText(node.value2), 1.0)) * 5.0));
            if (this.activeTicks >= moveTicks) {
               this.releaseMovementKeys(client);
               this.complete(client, node, "completed");
            }
         }
      }
   }

   private static boolean isMoveDirection(String direction) {
      return "forward".equals(direction)
         || "forwards".equals(direction)
         || "back".equals(direction)
         || "backward".equals(direction)
         || "backwards".equals(direction)
         || "left".equals(direction)
         || "right".equals(direction)
         || "jump".equals(direction)
         || "up".equals(direction);
   }

   private void runPlayerLook(Minecraft client, MacroModel.Node node) {
      String targetText = this.resolveRuntimeText(node.value);
      Vec3 target = this.parseResolvedVec3(client, targetText);
      if (target != null) {
         this.lookAt(client, target);
         this.complete(client, node, "completed");
      } else {
         Entity targetEntity = this.findTargetEntity(client, targetText);
         if (targetEntity != null) {
            this.lookAt(client, this.entityCenter(targetEntity));
            this.complete(client, node, "completed");
         } else {
            double[] yawPitch = parseTwoDoubles(targetText);
            if (yawPitch != null) {
               client.player.setYRot((float)yawPitch[0]);
               client.player.setXRot((float)Mth.clamp(yawPitch[1], -90.0, 90.0));
               this.complete(client, node, "completed");
            } else {
               this.fail(client, node, tr("macro_builder.runner.failed_look_needs_yaw_pitch_a_target_location_or_a_target_entity"));
            }
         }
      }
   }

   private boolean tickActionRecording(Minecraft client) {
      if (!this.actionRecording) {
         return false;
      }

      if (client == null || client.player == null || client.level == null) {
         this.finishActionRecording(client, false, tr("macro_builder.runner.recording_canceled_because_the_world_closed"));
         return true;
      }

      boolean stopKeyDown = this.actionRecordingStopKey > 0 && InputConstants.isKeyDown(this.actionRecordingStopKey);
      if (!stopKeyDown) {
         this.actionRecordingStopKeyArmed = true;
      }

      if (this.actionRecordingStopKeyArmed && stopKeyDown && !this.actionRecordingStopKeyWasDown) {
         this.finishActionRecording(client, true, "");
         return true;
      }

      this.actionRecordingStopKeyWasDown = stopKeyDown;
      this.captureRecordedActionFrame(client);
      this.actionRecordingTicks++;
      if (this.actionRecordingTicks >= MAX_RECORDED_ACTION_TICKS) {
         this.finishActionRecording(client, true, tr("macro_builder.runner.recording_reached_the_10_minute_limit"));
      }

      return true;
   }

   private void captureRecordedActionFrame(Minecraft client) {
      int flags = 0;
      flags |= client.options.keyUp.isDown() ? RECORDED_FORWARD : 0;
      flags |= client.options.keyDown.isDown() ? RECORDED_BACK : 0;
      flags |= client.options.keyLeft.isDown() ? RECORDED_LEFT : 0;
      flags |= client.options.keyRight.isDown() ? RECORDED_RIGHT : 0;
      flags |= client.options.keyJump.isDown() ? RECORDED_JUMP : 0;
      flags |= client.options.keyShift.isDown() ? RECORDED_CROUCH : 0;
      flags |= client.options.keySprint.isDown() ? RECORDED_SPRINT : 0;
      flags |= client.options.keyAttack.isDown() ? RECORDED_ATTACK : 0;
      flags |= client.options.keyUse.isDown() ? RECORDED_USE : 0;
      flags |= client.options.keyInventory.isDown() ? RECORDED_INVENTORY : 0;
      flags |= client.options.keyDrop.isDown() ? RECORDED_DROP : 0;
      flags |= client.options.keySwapOffhand.isDown() ? RECORDED_SWAP_HANDS : 0;
      int yawTenths = Math.round(Mth.wrapDegrees(client.player.getYRot()) * 10.0F);
      int pitchTenths = Math.round(Mth.clamp(client.player.getXRot(), -90.0F, 90.0F) * 10.0F);
      int slot = Mth.clamp(client.player.getInventory().getSelectedSlot(), 0, 8);
      RecordedActionFrame frame = new RecordedActionFrame(1, yawTenths, pitchTenths, flags, slot);
      int lastIndex = this.actionRecordingFrames.size() - 1;
      if (lastIndex >= 0 && this.actionRecordingFrames.get(lastIndex).sameState(frame)) {
         this.actionRecordingFrames.get(lastIndex).ticks++;
      } else {
         this.actionRecordingFrames.add(frame);
      }
   }

   private void finishActionRecording(Minecraft client, boolean saveRecording, String detail) {
      MacroModel recordingModel = this.actionRecordingModel;
      String nodeId = this.actionRecordingNodeId;
      int macroNumber = this.actionRecordingMacroNumber;
      int stopKey = this.actionRecordingStopKey;
      int ticks = this.actionRecordingTicks;
      List<RecordedActionFrame> frames = new ArrayList<>(this.actionRecordingFrames);
      List<RecordedActionEvent> events = new ArrayList<>(this.actionRecordingEvents);
      this.actionRecording = false;
      this.actionRecordingModel = null;
      this.actionRecordingNodeId = "";
      this.actionRecordingMacroNumber = 0;
      this.actionRecordingStopKey = -1;
      this.actionRecordingStopKeyArmed = false;
      this.actionRecordingStopKeyWasDown = false;
      this.actionRecordingTicks = 0;
      this.actionRecordingFrames.clear();
      if (macroNumber > 0 && macroNumber <= this.macroKeyWasDown.length) {
         this.macroKeyWasDown[macroNumber - 1] = stopKey > 0 && client != null && InputConstants.isKeyDown(stopKey);
      }

      MacroModel.Node node = recordingModel == null ? null : recordingModel.node(nodeId);
      if (saveRecording && node != null && !frames.isEmpty()) {
         node.value = encodeRecordedActions(frames, events);
         try {
            this.model = recordingModel;
            this.ensureConfigFiles();
            recordingModel.save(this.macroPath(macroNumber));
            this.lastStatus = Component.translatable("macro_builder.runner.saved_recorded_ticks_and_inventory_chat_events_seconds", ticks, events.size(), String.format(Locale.ROOT, "%.1f", ticks / 20.0));
         } catch (RuntimeException | IOException error) {
            MacroBuilderClient.LOGGER.error("Failed to save recorded actions", error);
            this.lastStatus = Component.translatable("macro_builder.runner.recording_save_failed", error.getMessage());
         }
      } else {
         this.lastStatus = detail == null || detail.isBlank() ? Component.translatable("macro_builder.runner.recording_canceled_or_empty") : Component.literal(detail);
      }

      if (detail != null && !detail.isBlank() && saveRecording) {
         this.lastStatus = Component.literal(this.lastStatus.getString() + " " + detail);
      }

      this.send(client, this.lastStatus.getString());
      if (client != null && recordingModel != null) {
         client.setScreenAndShow(new MacroBuilderScreen(this, recordingModel, nodeId));
      }
   }

   private void runRecordedActions(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         RecordedActionData data = parseRecordedActionData(node.value);
         this.activeRecordedActionFrames = data.frames();
         this.activeRecordedActionEvents = data.events();
         this.activeRecordedActionFrameIndex = 0;
         this.activeRecordedActionFrameTicks = 0;
         this.activeRecordedActionPreviousFlags = 0;
         this.activeRecordedActionEventIndex = 0;
         this.activeRecordedActionPlaybackTick = 0;
         this.activeRecordedActionEventWaitTicks = 0;
      }

      if (this.activeRecordedActionFrames.isEmpty()) {
         this.fail(client, node, tr("macro_builder.runner.failed_record_actions_with_the_component_s_record_button_first"));
         return;
      }

      if (this.activeRecordedActionFrameIndex >= this.activeRecordedActionFrames.size()) {
         if (!this.applyRecordedActionEvents(client, node)) {
            return;
         }

         this.releaseRecordedActionInputs(client);
         this.complete(client, node, "completed");
         return;
      }

      RecordedActionFrame frame = this.activeRecordedActionFrames.get(this.activeRecordedActionFrameIndex);
      this.applyRecordedActionFrame(client, frame);
      if (!this.applyRecordedActionEvents(client, node)) {
         return;
      }

      this.activeRecordedActionFrameTicks++;
      this.activeRecordedActionPlaybackTick++;
      if (this.activeRecordedActionFrameTicks >= frame.ticks) {
         this.activeRecordedActionPreviousFlags = frame.flags;
         this.activeRecordedActionFrameIndex++;
         this.activeRecordedActionFrameTicks = 0;
         if (this.activeRecordedActionFrameIndex >= this.activeRecordedActionFrames.size()
            && this.activeRecordedActionEventIndex >= this.activeRecordedActionEvents.size()) {
            this.releaseRecordedActionInputs(client);
            this.complete(client, node, "completed");
         }
      }
   }

   private boolean applyRecordedActionEvents(Minecraft client, MacroModel.Node node) {
      while (this.activeRecordedActionEventIndex < this.activeRecordedActionEvents.size()) {
         RecordedActionEvent event = this.activeRecordedActionEvents.get(this.activeRecordedActionEventIndex);
         if (event.tick > this.activeRecordedActionPlaybackTick) {
            this.activeRecordedActionEventWaitTicks = 0;
            return true;
         }

         if (event.containerInput) {
            AbstractContainerMenu menu = client.player.containerMenu;
            String currentMenuType = menu == null ? "" : menu.getClass().getName();
            if (menu == null || menu.slots.size() != event.menuSlotCount || !currentMenuType.equals(event.menuType)) {
               this.activeRecordedActionEventWaitTicks++;
               if (this.activeRecordedActionEventWaitTicks == 1 || this.activeRecordedActionEventWaitTicks % 10 == 0) {
                  KeyMapping.click(
                     (event.menuType.contains("InventoryMenu") || event.menuType.contains("CreativeModeInventoryScreen"))
                        ? client.options.keyInventory.getDefaultKey()
                        : client.options.keyUse.getDefaultKey()
                  );
               }

               if (this.activeRecordedActionEventWaitTicks > SCREEN_TIMEOUT_TICKS) {
                  this.releaseRecordedActionInputs(client);
                  this.fail(client, node, tr("macro_builder.runner.failed_recorded_inventory_action_expected_with_slots_open_the_same_inventory_or", simpleMenuName(event.menuType), event.menuSlotCount));
               }

               return false;
            }

            if ("CLOSE".equals(event.inputType)) {
               client.player.closeContainer();
               this.activeRecordedActionEventIndex++;
               this.activeRecordedActionEventWaitTicks = 0;
               continue;
            }

            if (client.gui.screen() instanceof CreativeModeInventoryScreen creativeScreen
               && creativeScreen instanceof CreativeSlotClickInvoker invoker) {
               Slot slot = event.slotId >= 0 && event.slotId < menu.slots.size() ? menu.getSlot(event.slotId) : null;
               ContainerInput input;
               try {
                  input = ContainerInput.valueOf(event.inputType);
               } catch (IllegalArgumentException error) {
                  this.releaseRecordedActionInputs(client);
                  this.fail(client, node, tr("macro_builder.runner.failed_recorded_creative_inventory_action_type_is_not_supported", event.inputType));
                  return false;
               }

               invoker.litemacro$invokeSlotClicked(slot, event.slotId, event.button, input);
               this.activeRecordedActionEventIndex++;
               this.activeRecordedActionEventWaitTicks = 0;
               continue;
            }

            ContainerInput input;
            try {
               input = ContainerInput.valueOf(event.inputType);
            } catch (IllegalArgumentException error) {
               this.releaseRecordedActionInputs(client);
               this.fail(client, node, tr("macro_builder.runner.failed_recorded_inventory_action_type_is_not_supported", event.inputType));
               return false;
            }

            client.gameMode.handleContainerInput(menu.containerId, event.slotId, event.button, input, client.player);
         } else if (event.command) {
            client.player.connection.sendCommand(event.message);
         } else {
            client.player.connection.sendChat(event.message);
         }

         this.activeRecordedActionEventIndex++;
         this.activeRecordedActionEventWaitTicks = 0;
      }

      return true;
   }

   private static String simpleMenuName(String menuType) {
      int separator = menuType == null ? -1 : menuType.lastIndexOf('.');
      return separator >= 0 ? menuType.substring(separator + 1) : String.valueOf(menuType);
   }

   private void applyRecordedActionFrame(Minecraft client, RecordedActionFrame frame) {
      client.player.setYRot(frame.yawTenths / 10.0F);
      client.player.setXRot(frame.pitchTenths / 10.0F);
      client.player.getInventory().setSelectedSlot(Mth.clamp(frame.slot, 0, 8));
      this.setMacroKeyDown(client, client.options.keyUp, hasRecordedFlag(frame.flags, RECORDED_FORWARD));
      this.setMacroKeyDown(client, client.options.keyDown, hasRecordedFlag(frame.flags, RECORDED_BACK));
      this.setMacroKeyDown(client, client.options.keyLeft, hasRecordedFlag(frame.flags, RECORDED_LEFT));
      this.setMacroKeyDown(client, client.options.keyRight, hasRecordedFlag(frame.flags, RECORDED_RIGHT));
      this.setMacroKeyDown(client, client.options.keyJump, hasRecordedFlag(frame.flags, RECORDED_JUMP));
      this.setMacroKeyDown(client, client.options.keyShift, hasRecordedFlag(frame.flags, RECORDED_CROUCH));
      this.setMacroKeyDown(client, client.options.keySprint, hasRecordedFlag(frame.flags, RECORDED_SPRINT));
      this.setMacroKeyDown(client, client.options.keyAttack, hasRecordedFlag(frame.flags, RECORDED_ATTACK));
      this.setMacroKeyDown(client, client.options.keyUse, hasRecordedFlag(frame.flags, RECORDED_USE));
      this.clickRecordedKeyOnRise(client.options.keyInventory, frame.flags, RECORDED_INVENTORY);
      this.clickRecordedKeyOnRise(client.options.keyDrop, frame.flags, RECORDED_DROP);
      this.clickRecordedKeyOnRise(client.options.keySwapOffhand, frame.flags, RECORDED_SWAP_HANDS);
      this.activeRecordedActionPreviousFlags = frame.flags;
   }

   private void clickRecordedKeyOnRise(KeyMapping mapping, int flags, int flag) {
      if (hasRecordedFlag(flags, flag) && !hasRecordedFlag(this.activeRecordedActionPreviousFlags, flag)) {
         KeyMapping.click(mapping.getDefaultKey());
      }
   }

   private void releaseRecordedActionInputs(Minecraft client) {
      this.releaseMovementKeys(client);
      this.setMacroKeyDown(client, client.options.keyShift, false);
      this.setMacroKeyDown(client, client.options.keySprint, false);
      this.setMacroKeyDown(client, client.options.keyAttack, false);
      this.setMacroKeyDown(client, client.options.keyUse, false);
      this.activeRecordedActionFrames = List.of();
      this.activeRecordedActionEvents = List.of();
      this.activeRecordedActionFrameIndex = 0;
      this.activeRecordedActionFrameTicks = 0;
      this.activeRecordedActionPreviousFlags = 0;
      this.activeRecordedActionEventIndex = 0;
      this.activeRecordedActionPlaybackTick = 0;
      this.activeRecordedActionEventWaitTicks = 0;
   }

   static String recordedActionSummary(String encoded) {
      RecordedActionData data = parseRecordedActionData(encoded);
      List<RecordedActionFrame> frames = data.frames();
      int ticks = frames.stream().mapToInt(frame -> frame.ticks).sum();
      return ticks <= 0 ? tr("macro_builder.runner.not_recorded") : tr("macro_builder.runner.steps_events", frames.size(), data.events().size(), String.format(Locale.ROOT, "%.1fs", ticks / 20.0));
   }

   private static String encodeRecordedActions(List<RecordedActionFrame> frames, List<RecordedActionEvent> events) {
      StringBuilder encoded = new StringBuilder("v2|");
      for (int index = 0; index < frames.size(); index++) {
         RecordedActionFrame frame = frames.get(index);
         if (index > 0) {
            encoded.append(';');
         }

         encoded.append(frame.ticks)
            .append(',').append(frame.yawTenths)
            .append(',').append(frame.pitchTenths)
            .append(',').append(frame.flags)
            .append(',').append(frame.slot);
      }

      encoded.append('|');
      for (int index = 0; index < events.size(); index++) {
         RecordedActionEvent event = events.get(index);
         if (index > 0) {
            encoded.append(';');
         }

         if (event.containerInput) {
            encoded.append(event.tick).append(",s,")
               .append(event.slotId).append(',').append(event.button).append(',')
               .append(event.inputType).append(',').append(event.menuSlotCount).append(',')
               .append(encodeRecordedText(event.menuType));
         } else {
            encoded.append(event.tick).append(event.command ? ",m," : ",c,").append(encodeRecordedText(event.message));
         }
      }

      return encoded.toString();
   }

   private static RecordedActionData parseRecordedActionData(String encoded) {
      if (encoded == null || (!encoded.startsWith("v1|") && !encoded.startsWith("v2|"))) {
         return new RecordedActionData(List.of(), List.of());
      }

      String[] sections = encoded.split("\\|", -1);
      if (sections.length < 2) {
         return new RecordedActionData(List.of(), List.of());
      }

      List<RecordedActionFrame> frames = new ArrayList<>();
      String body = sections[1];
      for (String token : body.split(";")) {
         if (token.isBlank()) {
            continue;
         }

         String[] values = token.split(",");
         if (values.length != 5) {
            return new RecordedActionData(List.of(), List.of());
         }

         try {
            int ticks = Math.max(1, Integer.parseInt(values[0]));
            frames.add(new RecordedActionFrame(ticks, Integer.parseInt(values[1]), Integer.parseInt(values[2]), Integer.parseInt(values[3]), Integer.parseInt(values[4])));
         } catch (NumberFormatException error) {
            return new RecordedActionData(List.of(), List.of());
         }
      }

      if (!encoded.startsWith("v2|") || sections.length < 3 || sections[2].isBlank()) {
         return new RecordedActionData(frames, List.of());
      }

      List<RecordedActionEvent> events = new ArrayList<>();
      for (String token : sections[2].split(";")) {
         String[] values = token.split(",", -1);
         try {
            if (values.length == 7 && "s".equals(values[1])) {
               events.add(RecordedActionEvent.container(
                  Integer.parseInt(values[0]), Integer.parseInt(values[2]), Integer.parseInt(values[3]),
                  values[4], Integer.parseInt(values[5]), decodeRecordedText(values[6])
               ));
            } else if (values.length == 3 && ("c".equals(values[1]) || "m".equals(values[1]))) {
               events.add(RecordedActionEvent.message(Integer.parseInt(values[0]), "m".equals(values[1]), decodeRecordedText(values[2])));
            } else {
               return new RecordedActionData(List.of(), List.of());
            }
         } catch (IllegalArgumentException error) {
            return new RecordedActionData(List.of(), List.of());
         }
      }

      return new RecordedActionData(frames, events);
   }

   private static String encodeRecordedText(String value) {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
   }

   private static String decodeRecordedText(String value) {
      return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
   }

   private static boolean hasRecordedFlag(int flags, int flag) {
      return (flags & flag) != 0;
   }

   private void runSetMouseButton(Minecraft client, MacroModel.Node node) {
      Boolean pressed = parseBooleanOrAction(this.resolveRuntimeText(node.value));
      Integer button = parseMouseButton(this.resolveRuntimeText(node.value2));
      if (pressed != null && button != null && button >= 0 && button <= 1) {
         if (button == 1) {
            this.setMacroKeyDown(client, client.options.keyUse, pressed);
         } else {
            this.setMacroKeyDown(client, client.options.keyAttack, pressed);
         }

         this.complete(client, node, "completed");
      } else {
         this.fail(client, node, tr("macro_builder.runner.failed_mouse_button_needs_state_press_release_and_button_left_right"));
      }
   }

   private void runShootBow(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         Boolean equipBow = parseBooleanOrDefault(this.resolveRuntimeText(node.value2), true);
         if (equipBow == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_shoot_bow_equip_value_must_be_true_or_false"));
            return;
         }

         if (equipBow && !this.selectHotbarItem(client, "minecraft:bow")) {
            this.fail(client, node, tr("macro_builder.runner.failed_shoot_bow_could_not_find_a_bow_in_the_hotbar"));
            return;
         }

         String targetText = this.resolveRuntimeText(node.value);
         Vec3 target = this.parseResolvedVec3(client, targetText);
         Entity targetEntity = target == null ? this.findTargetEntity(client, targetText) : null;
         if (target != null) {
            this.lookAt(client, target);
         } else if (targetEntity != null) {
            this.lookAt(client, this.entityCenter(targetEntity));
         }

         if (client.gameMode == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_shoot_bow_could_not_use_the_held_item"));
            return;
         }

         client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
         this.setMacroKeyDown(client, client.options.keyUse, true);
      }

      if (this.activeTicks >= 25) {
         this.setMacroKeyDown(client, client.options.keyUse, false);
         if (client.gameMode != null) {
            client.gameMode.releaseUsingItem(client.player);
         }

         this.complete(client, node, "completed");
      }
   }

   private void runChat(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         String message = this.resolveRuntimeText(node.value).trim();
         if (message.isBlank()) {
            this.fail(client, node, tr("macro_builder.runner.failed_chat_command_needs_text"));
            return;
         }

         if (message.length() > 256) {
            this.fail(client, node, tr("macro_builder.runner.failed_chat_command_is_over_256_characters"));
            return;
         }

         if (message.equals("/")) {
            this.fail(client, node, tr("macro_builder.runner.failed_chat_command_needs_a_command_after"));
            return;
         }

         if (message.startsWith("/")) {
            client.player.connection.sendCommand(message.substring(1));
         } else {
            client.player.connection.sendChat(message);
         }
      }

      this.complete(client, node, "completed");
   }

   private void runLocalMessage(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1 && node.value != null && !node.value.isBlank()) {
         this.send(client, this.resolveRuntimeText(node.value).trim());
      }

      this.complete(client, node, "completed");
   }

   private void runSelectHotbarSlot(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         String rawSlotText = node.value == null ? "" : node.value.trim();
         String slotText = this.resolveRuntimeText(rawSlotText).trim();
         Integer slotValue = slotText.isBlank() && rawSlotText.isBlank() ? 1 : parseIntOrNull(slotText);
         if (slotValue == null || !this.selectInventorySlotIntoHotbar(client, slotValue - 1)) {
            this.fail(client, node, tr("macro_builder.runner.failed_select_hotbar_slot_needs_a_slot_from_1_to_36"));
            return;
         }
      }

      this.complete(client, node, "completed");
   }

   private void runHotbarSelect(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1 && !this.selectHotbarSlot(client, node)) {
         this.fail(client, node, tr("macro_builder.runner.failed_hotbar_select_could_not_find_the_item"));
      } else {
         this.complete(client, node, "completed");
      }
   }

   private void runHotbarUse(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         if (!this.selectHotbarSlot(client, node)) {
            this.fail(client, node, tr("macro_builder.runner.failed_hotbar_use_could_not_find_the_item"));
            return;
         }

         if (client.gameMode == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_hotbar_use_could_not_use_the_selected_item"));
            return;
         }

         client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
      }

      this.complete(client, node, "completed");
   }

   private void runDropItems(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         Boolean dropStack = parseBooleanOrDefault(this.resolveRuntimeText(node.value2), false);
         if (dropStack == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_drop_items_stack_value_must_be_true_or_false"));
            return;
         }

         Set<Integer> excludedSlots = parseInventorySlotSet(this.resolveRuntimeText(node.value3));
         if (excludedSlots == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_drop_items_exclude_slots_must_be_numbers_or_ranges"));
            return;
         }

         String target = this.resolveRuntimeText(node.value).trim();
         if (target.isBlank()) {
            if (client.gameMode == null) {
               this.fail(client, node, tr("macro_builder.runner.failed_no_game_mode_available"));
               return;
            }

            client.gameMode.dropItem(client.player, dropStack);
            this.complete(client, node, "completed");
            return;
         }

         Integer targetSlot = inventorySlotNumber(target);
         if (targetSlot != null) {
            if (excludedSlots.contains(targetSlot)) {
               this.fail(client, node, tr("macro_builder.runner.failed_drop_items_target_slot_is_excluded"));
               return;
            }

            if (client.player.getInventory().getItem(targetSlot).isEmpty()) {
               this.fail(client, node, tr("macro_builder.runner.failed_drop_items_target_slot_is_empty"));
               return;
            }

            if (!this.dropPlayerInventorySlot(client, targetSlot, dropStack)) {
               this.fail(client, node, tr("macro_builder.runner.failed_drop_items_could_not_drop_from_inventory"));
               return;
            }

            this.complete(client, node, "completed");
            return;
         }

         String itemId = MacroModel.normalizeItemId(target);
         if (!isKnownItemId(itemId)) {
            this.fail(client, node, tr("macro_builder.runner.failed_drop_items_needs_a_valid_item_id_or_inventory_slot"));
            return;
         }

         if (dropAllMatchingDropItems(node)) {
            int dropped = this.dropMatchingPlayerInventorySlots(client, itemId, dropStack, excludedSlots);
            if (dropped <= 0) {
               this.fail(client, node, tr("macro_builder.runner.failed_drop_items_could_not_find_the_item"));
               return;
            }

            this.complete(client, node, "completed");
            return;
         }

         Integer inventorySlot = this.firstMatchingPlayerInventorySlot(client, itemId, excludedSlots);
         if (inventorySlot == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_drop_items_could_not_find_the_item"));
            return;
         }

         if (!this.dropPlayerInventorySlot(client, inventorySlot, dropStack)) {
            this.fail(client, node, tr("macro_builder.runner.failed_drop_items_could_not_drop_from_inventory"));
            return;
         }

         this.complete(client, node, "completed");
      }
   }

   private void runDropSelectedItem(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         Boolean dropStack = parseBooleanOrDefault(this.resolveRuntimeText(node.value), false);
         if (dropStack == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_drop_selected_item_stack_value_must_be_true_or_false"));
            return;
         }

         client.player.getInventory().removeFromSelected(dropStack);
      }

      this.complete(client, node, "completed");
   }

   private void runClickOpenContainerSlot(Minecraft client, MacroModel.Node node) {
      String target = this.resolveRuntimeText(node.value).trim();
      String itemId = clickTargetItemId(target);
      Boolean shiftClick = parseBooleanOrDefault(this.resolveRuntimeText(node.value2), false);
      Integer button = parseMouseButton(this.resolveRuntimeText(node.value3));
      if (target.isBlank()) {
         this.fail(client, node, tr("macro_builder.runner.failed_click_gui_item_needs_an_item_id_or_gui_slot"));
      } else if (!looksLikeOpenContainerSlotTarget(target) && !clickTargetHasValidFilter(target, itemId)) {
         this.fail(client, node, tr("macro_builder.runner.failed_click_gui_item_needs_a_valid_item_id_or_gui_slot"));
      } else if (shiftClick == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_click_gui_item_shift_click_value_must_be_true_or_false"));
      } else if (button == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_click_gui_item_button_must_be_left_or_right"));
      } else if (this.clickOpenContainerTarget(client, target, button, shiftClick)) {
         this.complete(client, node, "completed");
      } else {
         if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
            this.fail(client, node, tr("macro_builder.runner.failed_could_not_click_in_the_open_gui", target));
         }
      }
   }

   private TextNumberObservation.Spec numberSpec(MacroModel.Node node) {
      if (node == null || !node.enabled
         || !MacroModel.SCOREBOARD_NUMBER.equals(node.type) && !MacroModel.CHAT_NUMBER.equals(node.type)) {
         throw new IllegalArgumentException(tr("macro_builder.runner.reference_must_point_to_an_enabled_number_comparison_node"));
      }
      return TextNumberObservation.Spec.parse(MacroModel.CHAT_NUMBER.equals(node.type),
         this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2));
   }

   private TextNumberObservation numberObservation(Minecraft client, TextNumberObservation.Spec spec) {
      TextNumberObservation observation = this.numberObservations.get(spec.key());
      if (observation == null) {
         observation = new TextNumberObservation(spec);
         if (!spec.chat()) {
            observation.observeCurrent(this.sidebarNumberLines(client), numberTimeMs());
         }
         this.numberObservations.put(spec.key(), observation);
      }
      return observation;
   }

   private boolean numberCondition(Minecraft client, MacroModel.Node node) {
      TextNumberObservation.Spec spec = this.numberSpec(node);
      TextNumberObservation.Snapshot snapshot = this.numberObservation(client, spec).snapshot(numberTimeMs());
      boolean valid = snapshot.present() && (spec.staleSeconds() == 0 || snapshot.ageSeconds() <= spec.staleSeconds());
      this.putRuntimeValue("last.number", valid ? Double.toString(snapshot.value()) : "");
      this.putRuntimeValue("last.number_age", snapshot.present() ? Double.toString(snapshot.ageSeconds()) : "");
      this.putRuntimeValue("last.number_valid", Boolean.toString(valid));
      return spec.matches(snapshot);
   }

   private boolean referencedConditions(Minecraft client, String references) {
      String text = this.resolveRuntimeText(references).trim();
      if (text.isBlank()) {
         throw new IllegalArgumentException(tr("macro_builder.runner.at_least_one_condition_node_id_is_required"));
      }
      boolean result = true;
      for (String id : text.split("[,\\s]+")) {
         MacroModel.Node condition = this.model.node(id);
         if (condition == null || !condition.enabled) {
            throw new IllegalArgumentException(tr("macro_builder.runner.missing_or_disabled_condition", id));
         }
         // 只读取条件，不执行其连线或动作，避免点击前检查产生额外副作用。
         Boolean matched = switch (condition.type) {
            case MacroModel.SCOREBOARD_NUMBER, MacroModel.CHAT_NUMBER -> this.numberCondition(client, condition);
            case MacroModel.SCOREBOARD_CONTAINS -> this.scoreboardContains(client, condition);
            case MacroModel.ACTIONBAR_CONTAINS -> this.actionbarContains(condition);
            case MacroModel.BOSSBAR_CONTAINS -> this.bossbarContains(client, condition);
            case MacroModel.HAS_STATUS_EFFECT -> this.statusEffectMatches(client, condition);
            case MacroModel.EVENT_IF_CHAT_SAME -> this.chatSameMatches(condition);
            case MacroModel.PLAYER_IS_ALIVE -> client.player.getHealth() > 0.0F && !client.player.isDeadOrDying();
            case MacroModel.PLAYER_IS_ACCOUNT -> this.isAccount(client, condition);
            case MacroModel.PLAYER_IN_ACTION -> this.isInAction(client, condition);
            case MacroModel.INVENTORY_FULL -> this.isInventoryFull(client);
            case MacroModel.ITEM_IN_INVENTORY, MacroModel.LEGACY_ITEM_IN_INVENTORY -> this.inventoryHasItem(client, condition);
            case MacroModel.HEALTH_BELOW -> doubleAtOrBelow(client.player.getHealth(), this.resolveRuntimeText(condition.value));
            case MacroModel.FOOD_BELOW -> intAtOrBelow(client.player.getFoodData().getFoodLevel(), this.resolveRuntimeText(condition.value));
            case MacroModel.XP_LEVEL_AT_LEAST -> intAtLeast(client.player.experienceLevel, this.resolveRuntimeText(condition.value));
            case MacroModel.PLAYER_ON_GROUND -> client.player.onGround();
            case MacroModel.PLAYER_IN_WATER -> client.player.isInWater();
            case MacroModel.PLAYER_AT_LOCATION -> this.isAtLocation(client, condition);
            case MacroModel.PLAYER_NEARBY -> this.playerNearby(client, condition);
            case MacroModel.HELD_ITEM_IS -> this.heldItemIs(client, condition);
            case MacroModel.SLOT_HAS_ITEM -> this.slotHasItem(client, condition);
            case MacroModel.ITEM_DURABILITY -> this.itemDurabilityMatches(client, condition);
            case MacroModel.EMPTY_SLOTS_AT_LEAST -> this.emptySlotsAtLeast(client, condition);
            case MacroModel.OPEN_CONTAINER_HAS_ITEM -> this.openContainerHasItem(client, condition);
            case MacroModel.HAS_OPEN_CONTAINER -> activeContainerHandler(client) != null;
            case MacroModel.IS_OPEN_CONTAINER_FULL -> this.isOpenContainerFull(client);
            case MacroModel.WORLD_BLOCK_IS -> this.blockAtMatches(client, condition);
            case MacroModel.LOOKING_AT_BLOCK -> this.lookingAtBlockMatches(client, condition);
            case MacroModel.ENTITY_NEARBY -> this.entityNearby(client, condition);
            default -> throw new IllegalArgumentException(tr("macro_builder.runner.unsupported_referenced_condition", condition.displayLabel()));
         };
         if (matched == null) {
            throw new IllegalArgumentException(tr("macro_builder.runner.invalid_condition_settings", condition.displayLabel()));
         }
         result &= matched;
      }
      return result;
   }

   private void runWaitForCondition(Minecraft client, MacroModel.Node node) {
      Integer interval = parseIntOrNull(this.resolveRuntimeText(node.value2));
      if (interval == null || interval < 1) {
         throw new IllegalArgumentException(tr("macro_builder.runner.condition_polling_interval_must_be_a_positive_number_of_milliseconds"));
      }
      long intervalTicks = (interval + 49L) / 50L;
      if ((this.activeTicks - 1) % intervalTicks != 0) {
         return;
      }
      if (this.referencedConditions(client, node.value)) {
         if (this.conditionWaitAnnounced) {
            this.send(client, tr("macro_builder.runner.conditions_satisfied_continuing_the_current_macro"));
            this.conditionWaitAnnounced = false;
         }
         this.complete(client, node, "completed");
      } else {
         this.lastStatus = Component.translatable("macro_builder.runner.waiting_for_conditions", node.value);
         if (!this.conditionWaitAnnounced) {
            this.send(client, this.lastStatus.getString());
            this.conditionWaitAnnounced = true;
         }
      }
   }

   private void runClickWithCondition(Minecraft client, MacroModel.Node node) {
      String target = this.resolveRuntimeText(node.value).trim();
      if (target.isBlank() || !looksLikeOpenContainerSlotTarget(target)
         && !clickTargetHasValidFilter(target, clickTargetItemId(target))) {
         throw new IllegalArgumentException(tr("macro_builder.runner.conditional_click_needs_a_valid_item_id_or_gui_slot"));
      }
      // 按钮查找可跨多个 tick，每次发送点击前重新检查全部条件。
      if (!this.referencedConditions(client, node.value2)) {
         this.complete(client, node, "blocked");
      } else if (this.clickOpenContainerTarget(client, target, 0, false)) {
         this.complete(client, node, "completed");
      } else if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
         this.fail(client, node, tr("macro_builder.runner.failed_could_not_click_in_the_open_gui", target));
      }
   }

   private void clearNumberObservation(Minecraft client, MacroModel.Node node) {
      MacroModel.Node source = this.model.node(this.resolveRuntimeText(node.value).trim());
      this.numberObservation(client, this.numberSpec(source)).clear();
      this.complete(client, node, "completed");
   }

   private void refreshNumberObservations(Minecraft client) {
      Object world = client == null ? null : client.level;
      if (world != this.numberObservationWorld) {
         this.numberObservations.clear();
         this.numberObservationWorld = world;
      }
      if (world == null || !this.running || this.model == null) {
         return;
      }
      List<String> lines = this.sidebarNumberLines(client);
      long nowMs = numberTimeMs();
      Set<TextNumberObservation.Key> refreshed = new HashSet<>();
      for (MacroModel.Node node : this.model.nodes()) {
         if ((MacroModel.SCOREBOARD_NUMBER.equals(node.type) || MacroModel.CHAT_NUMBER.equals(node.type)) && node.enabled) {
            try {
               TextNumberObservation.Spec spec = this.numberSpec(node);
               TextNumberObservation observation = this.numberObservation(client, spec);
               if (!spec.chat() && refreshed.add(spec.key())) {
                  observation.observeCurrent(lines, nowMs);
               }
            } catch (IllegalArgumentException error) {
               this.lastStatus = Component.translatable("macro_builder.runner.invalid_number_comparison", node.id, error.getMessage());
            }
         }
      }
   }

   void recordNumberServerMessage(String message) {
      long nowMs = numberTimeMs();
      for (Map.Entry<TextNumberObservation.Key, TextNumberObservation> entry : this.numberObservations.entrySet()) {
         if (entry.getKey().chat()) {
            entry.getValue().recordMessage(message, nowMs);
         }
      }
      for (MacroRunner child : this.childRunners) {
         child.recordNumberServerMessage(message);
      }
      for (MacroRunner child : this.pendingChildRunners) {
         child.recordNumberServerMessage(message);
      }
   }

   private static long numberTimeMs() {
      return System.nanoTime() / 1_000_000L;
   }

   private List<String> sidebarNumberLines(Minecraft client) {
      if (client == null || client.level == null || client.player == null) {
         return List.of();
      }
      Scoreboard scoreboard = client.level.getScoreboard();
      Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
      PlayerTeam playerTeam = scoreboard.getPlayersTeam(client.player.getScoreboardName());
      if (playerTeam != null && playerTeam.getColor().isPresent()) {
         Objective colored = scoreboard.getDisplayObjective(playerTeam.getColor().get().displaySlot());
         if (colored != null) {
            objective = colored;
         }
      }
      if (objective == null) {
         return List.of();
      }
      List<String> lines = new ArrayList<>();
      Objective objectiveForLines = objective;
      // 与侧栏一致地选择可见条目并组合队伍前后缀，不读取其它目标中的旧数值。
      scoreboard.listPlayerScores(objective).stream()
         .filter(entry -> !entry.isHidden())
         .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
         .limit(15)
         .forEach(entry -> {
            String line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).getString();
            lines.add(line);
            String score = entry.formatValue(objectiveForLines.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)).getString();
            if (!score.isBlank()) {
               lines.add(line + " " + score);
            }
         });
      return lines;
   }

   private void runChestDepositItems(Minecraft client, MacroModel.Node node) {
      MacroRunner.DepositResult result = this.depositNextInventoryStack(client, node);
      if (result == MacroRunner.DepositResult.INVALID_ITEM) {
         this.fail(client, node, tr("macro_builder.runner.failed_deposit_items_specific_item_is_not_a_valid_item_id"));
      } else if (result == MacroRunner.DepositResult.INVALID_SLOTS) {
         this.fail(client, node, tr("macro_builder.runner.failed_deposit_items_exclude_slots_must_be_numbers_or_ranges"));
      } else if (result != MacroRunner.DepositResult.MOVED) {
         if (result == MacroRunner.DepositResult.DONE) {
            this.complete(client, node, "completed");
         } else {
            if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
               this.fail(client, node, tr("macro_builder.runner.failed_no_open_gui_was_available_for_deposit_items"));
            }
         }
      }
   }

   private void runChestWithdrawItems(Minecraft client, MacroModel.Node node) {
      MacroRunner.DepositResult result = this.withdrawNextContainerStack(client, node);
      if (result == MacroRunner.DepositResult.INVALID_ITEM) {
         this.fail(client, node, tr("macro_builder.runner.failed_withdraw_items_specific_item_is_not_a_valid_item_id"));
      } else if (result != MacroRunner.DepositResult.MOVED) {
         if (result == MacroRunner.DepositResult.DONE) {
            this.complete(client, node, "completed");
         } else {
            if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
               this.fail(client, node, tr("macro_builder.runner.failed_no_open_gui_was_available_for_withdraw_items"));
            }
         }
      }
   }

   private void runAutoEnchant(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         this.activeEnchantClicked = false;
         this.activeEnchantClickTick = 0;
      }

      if (this.activeEnchantClicked) {
         if (this.activeTicks - this.activeEnchantClickTick >= CLOSE_SETTLE_TICKS) {
            MacroRunner.AutoEnchantOptions options = this.autoEnchantOptions(node);
            if (options != null && options.closeGui() && client.player != null) {
               client.player.closeContainer();
            }

            this.complete(client, node, "completed");
         } else {
            this.lastStatus = Component.translatable("macro_builder.runner.auto_enchant_finishing_enchant");
         }

         return;
      }

      AbstractContainerMenu handler = activeContainerHandler(client);
      if (!(handler instanceof EnchantmentMenu menu)) {
         this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_needs_an_open_enchanting_table_gui"));
      } else if (client.gameMode == null || client.player == null || client.player.connection == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_cannot_use_the_current_game_mode"));
      } else {
         MacroRunner.AutoEnchantOptions options = this.autoEnchantOptions(node);
         if (options == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_needs_option_1_3_and_xp_level_0_or"));
         } else {
            int buttonId = options.option() - 1;
            int cost = menu.costs[buttonId];
            int lapisCost = buttonId + 1;
            boolean creative = client.player.getAbilities().instabuild;
            if (!handler.getCarried().isEmpty()) {
               ItemStack carried = handler.getCarried();
               if (!creative && stackMatches(carried, options.lapisItem()) && !containerSlotHasAtLeast(handler, 1, lapisCost) && this.moveCarriedToMenuSlot(client, handler, 1)) {
                  this.lastStatus = Component.translatable("macro_builder.runner.auto_enchant_placed_carried_lapis");
               } else if (options.autoLoad()
                  && !containerSlotHasItem(handler, 0)
                  && this.isFreshEnchantingInput(client, handler, carried, options.itemSelector(), true)
                  && this.moveCarriedToMenuSlot(client, handler, 0)) {
                  this.lastStatus = Component.translatable("macro_builder.runner.auto_enchant_placed_carried_item");
               } else if (this.moveCarriedToPlayerInventory(client, handler)) {
                  this.lastStatus = Component.translatable("macro_builder.runner.auto_enchant_cleared_carried_item");
               } else {
                  this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_needs_inventory_space_to_clear_the_cursor"));
               }
            } else if (options.autoLoad() && isAlreadyEnchantedForAutoEnchant(handler.getSlot(0).getItem())) {
               if (this.moveMenuSlotToPlayerInventory(client, handler, 0)) {
                  this.lastStatus = Component.translatable("macro_builder.runner.auto_enchant_moved_enchanted_item_to_inventory");
               } else {
                  this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_needs_inventory_space_for_the_enchanted_item"));
               }
            } else if (options.autoLoad() && !containerSlotHasItem(handler, 0)) {
               if (this.moveEnchantingInputToTable(client, handler, options.itemSelector())) {
                  this.lastStatus = Component.translatable("macro_builder.runner.auto_enchant_loaded_item");
               } else {
                  this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_needs_an_unenchanted_enchantable_item_in_inventory"));
               }
            } else if (options.autoLoad() && !creative && !containerSlotHasAtLeast(handler, 1, lapisCost)) {
               if (this.quickMoveItemToMenuSlot(client, handler, options.lapisItem(), 1)) {
                  this.lastStatus = Component.translatable("macro_builder.runner.auto_enchant_loaded_lapis");
               } else {
                  this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_needs_in_inventory", options.lapisItem()));
               }
            } else if (!containerSlotHasItem(handler, 0)) {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_needs_an_item_in_the_enchanting_slot"));
            } else if (!creative && !containerSlotHasAtLeast(handler, 1, lapisCost)) {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_option_needs_lapis", options.option(), lapisCost));
            } else if (cost <= 0) {
               if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
                  this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_option_is_not_available", options.option()));
               } else {
                  this.lastStatus = Component.translatable("macro_builder.runner.auto_enchant_waiting_for_option", options.option());
               }
            } else if (!creative && menu.getGoldCount() < lapisCost) {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_option_needs_lapis", options.option(), lapisCost));
            } else if (!creative && client.player.experienceLevel < Math.max(cost, options.minimumLevel())) {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_enchant_needs_xp_level", Math.max(cost, options.minimumLevel())));
            } else {
               if (!this.activeEnchantClicked) {
                  client.player.connection.send(new ServerboundContainerButtonClickPacket(handler.containerId, buttonId));
                  this.activeEnchantClicked = true;
                  this.activeEnchantClickTick = this.activeTicks;
               }

               if (this.activeEnchantClicked && this.activeTicks - this.activeEnchantClickTick >= CLOSE_SETTLE_TICKS) {
                  if (options.closeGui()) {
                     client.player.closeContainer();
                  }

                  this.complete(client, node, "completed");
               } else {
                  this.lastStatus = Component.translatable("macro_builder.runner.auto_enchant_option", options.option());
               }
            }
         }
      }
   }

   private void runAutoGrindstone(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         this.activeGrindstoneResultClicked = false;
         this.activeGrindstoneResultReleased = false;
         this.activeGrindstoneResultClickTick = 0;
      }

      AbstractContainerMenu handler = activeContainerHandler(client);
      if (!(handler instanceof GrindstoneMenu)) {
         this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_needs_an_open_grindstone_gui"));
      } else if (client.gameMode == null || client.player == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_cannot_use_the_current_game_mode"));
      } else {
      MacroRunner.AutoGrindstoneOptions options = this.autoGrindstoneOptions(node);
         if (options == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_needs_mode_repair_remove_and_valid_item_settings"));
         } else if (!containerSlotHasItem(handler, 0)) {
            if (this.moveGrindstoneInputToSlot(client, handler, options.inputSelector(), 0, ItemStack.EMPTY, -1)) {
               this.lastStatus = Component.translatable("macro_builder.runner.auto_grindstone_loaded_input_item");
            } else {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_needs_the_input_item_in_inventory_or_item_held"));
            }
         } else if (options.repair() && !containerSlotHasItem(handler, 1)) {
            ItemStack firstInput = handler.getSlot(0).getItem();
            if (this.moveGrindstoneInputToSlot(client, handler, options.repairSelector(), 1, firstInput, -1)) {
               this.lastStatus = Component.translatable("macro_builder.runner.auto_grindstone_loaded_repair_item");
            } else {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_repair_needs_a_second_matching_item"));
            }
         } else if (!options.repair() && containerSlotHasItem(handler, 1)) {
            if (this.activeTicks % 5 == 1) {
               client.gameMode.handleContainerInput(handler.containerId, 1, 0, ContainerInput.QUICK_MOVE, client.player);
            }

            if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_could_not_clear_the_repair_slot"));
            } else {
               this.lastStatus = Component.translatable("macro_builder.runner.auto_grindstone_clearing_repair_slot");
            }
         } else if (!containerSlotHasItem(handler, 2)) {
            if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_needs_a_result_item_in_the_output_slot"));
            } else {
               this.lastStatus = Component.translatable("macro_builder.runner.auto_grindstone_waiting_for_result");
            }
         } else {
            if (!this.activeGrindstoneResultClicked) {
               Slot result = handler.getSlot(2);
               ItemStack resultStack = result.getItem();
               this.setLastItemOutput(node, resultStack);
               if (!"drop".equals(options.resultAction()) && !this.playerInventoryCanAccept(handler, firstPlayerInventorySlot(handler), resultStack)) {
                  this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_needs_inventory_space_for_the_result_or_result_drop"));
                  return;
               }

               client.gameMode.handleContainerInput(handler.containerId, result.index, 0, ContainerInput.PICKUP, client.player);
               this.lastStatus = "drop".equals(options.resultAction()) ? Component.translatable("macro_builder.runner.auto_grindstone_taking_result_to_drop") : Component.translatable("macro_builder.runner.auto_grindstone_taking_one_result");

               this.activeGrindstoneResultClicked = true;
               this.activeGrindstoneResultClickTick = this.activeTicks;
            }

            if (this.activeGrindstoneResultClicked && this.activeTicks - this.activeGrindstoneResultClickTick >= CLOSE_SETTLE_TICKS) {
               if (!handler.getCarried().isEmpty()) {
                  if ("drop".equals(options.resultAction())) {
                     client.gameMode.handleContainerInput(handler.containerId, OUTSIDE_CONTAINER_SLOT, 0, ContainerInput.PICKUP, client.player);
                     this.clearLastSlotOutput(node);
                     this.putRuntimeValue("last.action", "drop");
                     this.lastStatus = Component.translatable("macro_builder.runner.auto_grindstone_clearing_carried_result");
                     this.activeGrindstoneResultReleased = true;
                  } else {
                     int targetSlotIndex = this.moveCarriedToPlayerInventorySlot(client, handler);
                     if (targetSlotIndex >= 0) {
                        this.setLastSlotOutput(node, handler, targetSlotIndex);
                        this.putRuntimeValue("last.action", "inventory");
                        this.lastStatus = Component.translatable("macro_builder.runner.auto_grindstone_result_moved_to_slot", this.runtimeValues.getOrDefault("last.slot", "?"));
                        this.activeGrindstoneResultReleased = true;
                     } else {
                        this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_could_not_put_the_carried_result_away"));
                     }
                  }
               } else if (this.activeGrindstoneResultReleased) {
                  if (options.closeGui()) {
                     client.player.closeContainer();
                  }

                  this.complete(client, node, "completed");
               } else if (containerSlotHasItem(handler, 2)) {
                  if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
                     this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_result_was_not_picked_up"));
                  } else {
                     this.lastStatus = Component.translatable("macro_builder.runner.auto_grindstone_waiting_to_pick_up_result");
                  }
               } else if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
                  this.fail(client, node, tr("macro_builder.runner.failed_auto_grindstone_result_did_not_move"));
               } else {
                  this.lastStatus = Component.translatable("macro_builder.runner.auto_grindstone_waiting_for_carried_result");
               }
            }
         }
      }
   }

   private void runStopAtXpLevel(Minecraft client, MacroModel.Node node) {
      Integer targetLevel = parseIntOrNull(this.resolveRuntimeText(node.value));
      if (targetLevel == null || targetLevel < 0) {
         this.fail(client, node, tr("macro_builder.runner.failed_stop_at_xp_level_needs_a_level_of_0_or_higher"));
      } else if (client.player.experienceLevel >= targetLevel) {
         this.releaseHeldKeys(client);
         if (shouldStopMacroAtXpLevel(this.resolveRuntimeText(node.value2))) {
            this.stop(client, tr("macro_builder.runner.stopped_xp_level_reached", targetLevel));
         } else {
            this.complete(client, node, "completed");
         }
      } else {
         this.lastStatus = Component.translatable("macro_builder.runner.waiting_for_xp_level", targetLevel, client.player.experienceLevel);
      }
   }

   private void runCloseOpenContainer(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         if (hasOpenContainer(client) || handledScreen(client) != null) {
            client.player.closeContainer();
         } else if (client.gui.screen() != null) {
            client.setScreenAndShow(null);
         }
         this.complete(client, node, "completed");
      }
   }

   private void runOpenInventory(Minecraft client, MacroModel.Node node) {
      if (client.player == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_open_inventory_needs_a_player"));
      } else if (client.gui.screen() instanceof InventoryScreen) {
         this.complete(client, node, "completed");
      } else if (client.gui.screen() == null && !hasOpenContainer(client)) {
         client.setScreenAndShow(new InventoryScreen(client.player));
         this.complete(client, node, "completed");
      } else {
         if (this.activeTicks == 1) {
            if (hasOpenContainer(client) || handledScreen(client) != null) {
               client.player.closeContainer();
            } else {
               client.setScreenAndShow(null);
            }
         }

         if (this.activeTicks > CLOSE_SETTLE_TICKS && client.gui.screen() == null && !hasOpenContainer(client)) {
            client.setScreenAndShow(new InventoryScreen(client.player));
            this.complete(client, node, "completed");
         } else if (this.activeTicks > SCREEN_TIMEOUT_TICKS) {
            this.fail(client, node, tr("macro_builder.runner.failed_open_inventory_could_not_open_the_player_inventory"));
         }
      }
   }

   private void runWorldInteract(Minecraft client, MacroModel.Node node) {
      BlockHitResult hit = this.targetBlockHit(client, node.value);
      if (hit != null && client.gameMode != null) {
         if (parseInt(this.resolveRuntimeText(node.value2), 1) == 0) {
            client.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
         } else {
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
         }

         client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
         this.complete(client, node, "completed");
      } else {
         this.fail(client, node, tr("macro_builder.runner.failed_interact_with_block_needs_a_target_block"));
      }
   }

   private void runWorldMine(Minecraft client, MacroModel.Node node) {
      BlockHitResult hit = this.targetBlockHit(client, node.value);
      if (hit != null && client.gameMode != null) {
         Boolean selectTool = parseBooleanOrDefault(this.resolveRuntimeText(node.value2), false);
         if (selectTool != null && selectTool) {
            this.selectBestHotbarTool(client, client.level.getBlockState(hit.getBlockPos()));
         }

         if (this.activeTicks == 1) {
            client.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
         } else {
            client.gameMode.continueDestroyBlock(hit.getBlockPos(), hit.getDirection());
         }

         client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
         if (client.level.getBlockState(hit.getBlockPos()).isAir()) {
            this.setMacroKeyDown(client, client.options.keyAttack, false);
            this.complete(client, node, "completed");
         } else {
            if (this.activeTicks > 300) {
               this.setMacroKeyDown(client, client.options.keyAttack, false);
               this.fail(client, node, tr("macro_builder.runner.failed_mine_block_timed_out_before_the_block_broke"));
            }
         }
      } else {
         this.fail(client, node, tr("macro_builder.runner.failed_mine_block_needs_a_target_block"));
      }
   }

   private void runWorldPlace(Minecraft client, MacroModel.Node node) {
      BlockHitResult hit = this.targetBlockHit(client, node.value);
      if (hit == null || client.gameMode == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_place_block_needs_a_target_block"));
      } else if (!this.selectItemIfProvided(client, this.resolveRuntimeText(node.value2))) {
         this.fail(client, node, tr("macro_builder.runner.failed_place_block_could_not_find_the_selected_block"));
      } else {
         InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
         if (result.consumesAction()) {
            client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            this.complete(client, node, "completed");
         } else {
            this.fail(client, node, tr("macro_builder.runner.failed_place_block_was_not_accepted_by_the_world"));
         }
      }
   }

   private void runJumpAndPlaceBlock(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks == 1) {
         if (!this.selectItemIfProvided(client, this.resolveRuntimeText(node.value))) {
            this.fail(client, node, tr("macro_builder.runner.failed_jump_and_place_block_could_not_find_the_selected_block"));
         } else {
            this.activeJumpPlaceSupportPos = this.blockUnderPlayer(client);
            this.setMacroKeyDown(client, client.options.keyJump, true);
            client.player.jumpFromGround();
         }
      } else {
         if (this.activeTicks >= 3) {
            this.setMacroKeyDown(client, client.options.keyJump, false);
            if (client.gameMode == null) {
               this.fail(client, node, tr("macro_builder.runner.failed_jump_and_place_block_could_not_place"));
               return;
            }

            BlockPos pos = this.activeJumpPlaceSupportPos != null ? this.activeJumpPlaceSupportPos : this.blockUnderPlayer(client);
            BlockHitResult hit = this.blockTopHit(pos);
            InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction()) {
               client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
               this.complete(client, node, "completed");
               return;
            }

            if (this.activeTicks > 18 || this.activeTicks > 6 && client.player.onGround()) {
               this.fail(client, node, tr("macro_builder.runner.failed_jump_and_place_block_was_not_accepted_by_the_world"));
            }
         }
      }
   }

   private void runFarmArea(Minecraft client, MacroModel.Node node) {
      MacroRunner.FarmOptions options = this.farmOptions(client, node);
      if (options == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_farm_area_needs_radius_1_8_or_two_xyz_points_within"));
      } else if (this.activeFarmCropPos == null && options.depositWhenFull() && this.isInventoryFull(client)) {
         this.runFarmDeposit(client, node, options);
      } else {
         if (this.activeFarmCropPos == null) {
            this.activeFarmCropPos = this.nearestMatureCrop(client, options);
            this.activeFarmHarvested = false;
            if (this.activeFarmCropPos == null) {
               if (this.moveToUnloadedArea(client, node, options)) {
                  return;
               }

               this.releaseMovementKeys(client);
               this.complete(client, node, "completed");
               return;
            }

            BlockState cropState = client.level.getBlockState(this.activeFarmCropPos);
            this.activeFarmSeedItem = seedForFarmCrop(cropState);
            if (options.replant() && this.activeFarmSeedItem.isBlank()) {
               this.fail(client, node, tr("macro_builder.runner.failed_farm_area_found_a_crop_but_no_replant_item"));
               return;
            }
         }

         BlockState currentState = client.level.getBlockState(this.activeFarmCropPos);
         if (this.canReachFarmTarget(client, node, options, this.activeFarmCropPos)) {
            if (!this.activeFarmHarvested) {
               if (isMatureFarmCrop(currentState)) {
                  if (client.gameMode == null) {
                     this.fail(client, node, tr("macro_builder.runner.failed_farm_area_cannot_harvest_in_the_current_game_mode"));
                     return;
                  }

                  this.releaseMovementKeys(client);
                  this.lookAt(client, Vec3.atCenterOf(this.activeFarmCropPos));
                  if (this.activeTicks == 1) {
                     client.gameMode.startDestroyBlock(this.activeFarmCropPos, Direction.UP);
                  } else {
                     client.gameMode.continueDestroyBlock(this.activeFarmCropPos, Direction.UP);
                  }

                  client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                  if (this.activeTicks > 300) {
                     this.fail(client, node, tr("macro_builder.runner.failed_farm_area_timed_out_while_harvesting"));
                  }

                  return;
               }

               this.activeFarmHarvested = true;
            }

            if (!options.replant()) {
               this.releaseMovementKeys(client);
               this.complete(client, node, "completed");
            } else {
               currentState = client.level.getBlockState(this.activeFarmCropPos);
               if (isSupportedFarmCrop(currentState)) {
                  this.releaseMovementKeys(client);
                  this.complete(client, node, "completed");
               } else if (!currentState.isAir()) {
                  this.fail(client, node, tr("macro_builder.runner.failed_farm_area_cannot_replant_because_the_crop_space_is_blocked"));
               } else if (!this.selectHotbarItem(client, this.activeFarmSeedItem)) {
                  this.fail(client, node, tr("macro_builder.runner.failed_farm_area_needs_in_the_hotbar_to_replant", this.activeFarmSeedItem));
               } else if (client.gameMode == null) {
                  this.fail(client, node, tr("macro_builder.runner.failed_farm_area_cannot_replant_in_the_current_game_mode"));
               } else {
                  this.releaseMovementKeys(client);
                  BlockPos base = this.activeFarmCropPos.below();
                  BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false);
                  InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                  if (!result.consumesAction() && !isSupportedFarmCrop(client.level.getBlockState(this.activeFarmCropPos))) {
                     this.fail(client, node, tr("macro_builder.runner.failed_farm_area_could_not_replant", this.activeFarmSeedItem));
                  } else {
                     client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                     this.releaseMovementKeys(client);
                     this.complete(client, node, "completed");
                  }
               }
            }
         }
      }
   }

   private void runFarmDeposit(Minecraft client, MacroModel.Node node, MacroRunner.FarmOptions options) {
      if (activeContainerHandler(client) != null) {
         MacroRunner.DepositResult result = this.depositAnyInventoryStack(client);
         if (result == MacroRunner.DepositResult.MOVED) {
            return;
         }

         if (result == MacroRunner.DepositResult.NO_SPACE) {
            this.fail(client, node, tr("macro_builder.runner.failed_nearest_container_has_no_room"));
            return;
         }

         if (result == MacroRunner.DepositResult.DONE) {
            client.player.closeContainer();
            this.releaseMovementKeys(client);
            this.complete(client, node, "completed");
            return;
         }
      }

      int radius = options.radius() > 0 ? options.radius() : 10;
      BlockPos container = this.nearestContainer(client, Math.min(10, radius + 2));
      if (container == null || client.gameMode == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_inventory_full_and_no_nearby_container_was_found"));
      } else if (canReachBlock(client, container)) {
         this.releaseMovementKeys(client);
         this.lookAt(client, Vec3.atCenterOf(container));
         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(container), Direction.UP, container, false);
         client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
         client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
         if (this.activeTicks > 300) {
            this.fail(client, node, tr("macro_builder.runner.failed_nearest_container_did_not_open"));
         }
      } else if (!options.move()) {
         this.fail(client, node, tr("macro_builder.runner.failed_nearest_container_is_out_of_reach"));
      } else {
         this.moveTowardBlock(client, container);
         if (this.activeTicks > 300) {
            this.releaseMovementKeys(client);
            this.fail(client, node, tr("macro_builder.runner.failed_nearest_container_stayed_out_of_reach"));
         }
      }
   }

   private void runOpenNearestContainer(Minecraft client, MacroModel.Node node) {
      MacroRunner.ContainerSearchOptions options = this.containerSearchOptions(this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2));
      if (options == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_open_nearest_container_needs_radius_1_16_and_type_any_chest"));
      } else {
         this.runOpenNearestContainer(client, node, options, tr("macro_builder.runner.open_nearest_container"));
      }
   }

   private void runOpenNearestContainer(Minecraft client, MacroModel.Node node, String type, String label) {
      MacroRunner.ContainerSearchOptions options = this.containerSearchOptions(this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2));
      if (options == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_needs_radius_1_16", label));
      } else {
         this.runOpenNearestContainer(client, node, new MacroRunner.ContainerSearchOptions(options.radius(), options.move(), type), label);
      }
   }

   private void runOpenNearestContainer(Minecraft client, MacroModel.Node node, MacroRunner.ContainerSearchOptions options, String label) {
      if (activeContainerHandler(client) != null) {
         this.releaseMovementKeys(client);
         this.complete(client, node, "completed");
      } else {
         BlockPos container = this.nearestContainer(client, options.radius(), options.type());
         if (container != null && client.gameMode != null) {
            this.lookAt(client, Vec3.atCenterOf(container));
            if (canReachBlock(client, container)) {
               this.releaseMovementKeys(client);
               if (this.activeTicks % 5 == 1) {
                  this.openBlockAt(client, container);
               }

               if (this.activeTicks > 300) {
                  this.fail(client, node, tr("macro_builder.runner.failed_nearest_container_did_not_open"));
               }
            } else if (!options.move()) {
               this.fail(client, node, tr("macro_builder.runner.failed_nearest_container_is_out_of_reach"));
            } else {
               this.setMacroKeyDown(client, client.options.keyUp, true);
               if (this.activeTicks > 300) {
                  this.releaseMovementKeys(client);
                  this.fail(client, node, tr("macro_builder.runner.failed_nearest_container_stayed_out_of_reach"));
               }
            }
         } else {
            this.fail(client, node, tr("macro_builder.runner.failed_no_nearby_was_found", containerTypeLabel(options.type())));
         }
      }
   }

   private void runOpenNearestEnchantingTable(Minecraft client, MacroModel.Node node) {
      MacroRunner.ContainerSearchOptions options = this.containerSearchOptions(this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2));
      if (options == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_open_nearest_enchanting_table_needs_radius_1_16_and_move_true"));
      } else if (activeContainerHandler(client) instanceof EnchantmentMenu) {
         this.releaseMovementKeys(client);
         this.complete(client, node, "completed");
      } else if (activeContainerHandler(client) != null) {
         this.fail(client, node, tr("macro_builder.runner.failed_close_the_current_gui_before_opening_an_enchanting_table"));
      } else {
         BlockPos table = this.nearestBlockWithId(client, options.radius(), "minecraft:enchanting_table");
         if (table != null && client.gameMode != null) {
            this.lookAt(client, Vec3.atCenterOf(table));
            if (canReachBlock(client, table)) {
               this.releaseMovementKeys(client);
               if (this.activeTicks % 5 == 1) {
                  this.openBlockAt(client, table);
               }

               if (this.activeTicks > 300) {
                  this.fail(client, node, tr("macro_builder.runner.failed_nearest_enchanting_table_did_not_open"));
               }
            } else if (!options.move()) {
               this.fail(client, node, tr("macro_builder.runner.failed_nearest_enchanting_table_is_out_of_reach"));
            } else {
               this.moveTowardBlock(client, table);
               if (this.activeTicks > 300) {
                  this.releaseMovementKeys(client);
                  this.fail(client, node, tr("macro_builder.runner.failed_nearest_enchanting_table_stayed_out_of_reach"));
               }
            }
         } else {
            this.fail(client, node, tr("macro_builder.runner.failed_no_nearby_enchanting_table_was_found"));
         }
      }
   }

   private void runOpenNearestGrindstone(Minecraft client, MacroModel.Node node) {
      MacroRunner.ContainerSearchOptions options = this.containerSearchOptions(this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2));
      if (options == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_open_nearest_grindstone_needs_radius_1_16_and_move_true_false"));
      } else if (activeContainerHandler(client) instanceof GrindstoneMenu) {
         this.releaseMovementKeys(client);
         this.complete(client, node, "completed");
      } else if (activeContainerHandler(client) != null) {
         this.fail(client, node, tr("macro_builder.runner.failed_close_the_current_gui_before_opening_a_grindstone"));
      } else {
         BlockPos grindstone = this.nearestBlockWithId(client, options.radius(), "minecraft:grindstone");
         if (grindstone != null && client.gameMode != null) {
            this.lookAt(client, Vec3.atCenterOf(grindstone));
            if (canReachBlock(client, grindstone)) {
               this.releaseMovementKeys(client);
               if (this.activeTicks % 5 == 1) {
                  this.openBlockAt(client, grindstone);
               }

               if (this.activeTicks > 300) {
                  this.fail(client, node, tr("macro_builder.runner.failed_nearest_grindstone_did_not_open"));
               }
            } else if (!options.move()) {
               this.fail(client, node, tr("macro_builder.runner.failed_nearest_grindstone_is_out_of_reach"));
            } else {
               this.moveTowardBlock(client, grindstone);
               if (this.activeTicks > 300) {
                  this.releaseMovementKeys(client);
                  this.fail(client, node, tr("macro_builder.runner.failed_nearest_grindstone_stayed_out_of_reach"));
               }
            }
         } else {
            this.fail(client, node, tr("macro_builder.runner.failed_no_nearby_grindstone_was_found"));
         }
      }
   }

   private void runAutoBoneMeal(Minecraft client, MacroModel.Node node) {
      String itemId = MacroModel.normalizeItemId(this.resolveRuntimeText(node.value));
      if (!isKnownItemId(itemId)) {
         this.fail(client, node, tr("macro_builder.runner.failed_auto_bone_meal_needs_a_valid_item_id"));
      } else {
         MacroRunner.BoneMealOptions options = this.boneMealOptions(client, node.value2);
         if (options == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_auto_bone_meal_options_need_radius_1_16"));
         } else if (activeContainerHandler(client) != null) {
            if (this.selectHotbarItem(client, itemId)) {
               client.player.closeContainer();
            } else {
               MacroRunner.DepositResult result = this.withdrawSpecificFromOpenContainer(client, itemId);
               if (result == MacroRunner.DepositResult.MOVED) {
                  client.player.closeContainer();
               } else if (result == MacroRunner.DepositResult.NO_SPACE) {
                  this.fail(client, node, tr("macro_builder.runner.failed_inventory_has_no_room_for", itemId));
               } else {
                  this.fail(client, node, tr("macro_builder.runner.failed_nearby_container_does_not_have", itemId));
               }
            }
         } else if (!this.selectHotbarItem(client, itemId)) {
            if (!options.refill()) {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_bone_meal_needs_in_the_hotbar", itemId));
            } else {
               BlockPos container = this.nearestContainer(client, options.radius());
               if (container == null || client.gameMode == null) {
                  this.fail(client, node, tr("macro_builder.runner.failed_no_nearby_refill_container_was_found"));
               } else if (!canReachBlock(client, container)) {
                  this.fail(client, node, tr("macro_builder.runner.failed_refill_container_is_out_of_reach"));
               } else {
                  this.openBlockAt(client, container);
                  if (this.activeTicks > 300) {
                     this.fail(client, node, tr("macro_builder.runner.failed_refill_container_did_not_open"));
                  }
               }
            }
         } else {
            BlockHitResult hit = this.boneMealTargetHit(client, options);
            if (hit != null && client.gameMode != null) {
               InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
               if (result.consumesAction()) {
                  client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                  this.complete(client, node, "completed");
               } else {
                  this.fail(client, node, tr("macro_builder.runner.failed_auto_bone_meal_was_not_accepted_by_the_target_block"));
               }
            } else {
               this.fail(client, node, tr("macro_builder.runner.failed_auto_bone_meal_needs_you_to_look_at_a_block_or"));
            }
         }
      }
   }

   private void runMineArea(Minecraft client, MacroModel.Node node) {
      MacroRunner.MineAreaOptions options = this.mineAreaOptions(client, node);
      if (options == null) {
         this.fail(client, node, this.mineAreaOptionsError(client, node));
      } else if (client.player.isInWater() || client.player.isInLava()) {
         this.releaseHeldKeys(client);
         if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
         }

         this.activeMineAreaTicks = 0;
         this.activeMineAreaTravelTicks = 0;
         this.fail(client, node, client.player.isInLava() ? tr("macro_builder.runner.failed_mine_area_stopped_because_the_player_entered_lava") : tr("macro_builder.runner.failed_mine_area_stopped_because_the_player_entered_water"));
      } else if (!options.autoTool() && toolIsLow(client, options.toolLowThreshold())) {
         this.releaseHeldKeys(client);
         this.complete(client, node, "tool_low");
      } else if (client.gameMode == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_mine_area_cannot_mine_in_the_current_game_mode"));
      } else {
         if (this.activeMineAreaPos == null || !isMineAreaBreakTarget(client.level.getBlockState(this.activeMineAreaPos))) {
            this.activeMineAreaPos = this.nextMineAreaBlock(client, options);
            this.activeMineAreaDescentTarget = null;
            this.activeMineAreaTicks = 0;
            this.activeMineAreaTravelTicks = 0;
            if (this.activeMineAreaPos == null) {
               if (this.moveToUnloadedArea(client, node, options)) {
                  return;
               }

               this.releaseHeldKeys(client);
               this.complete(client, node, "completed");
               return;
            }
         }

          BlockState selectedState = client.level.getBlockState(this.activeMineAreaPos);
          String selectedBlockId = blockId(selectedState);
          if (!options.stopBeforeBlock().isBlank() && options.stopBeforeBlock().equals(selectedBlockId)) {
             this.putRuntimeValue("mine.protected_block.id", selectedBlockId);
             this.putRuntimeValue("mine.protected_block.pos", blockPosText(this.activeMineAreaPos));
             this.releaseHeldKeys(client);
             this.stop(client, tr("macro_builder.runner.stopped_mine_area_found_protected_block_at", selectedBlockId, blockPosText(this.activeMineAreaPos)));
             return;
          }

         if (this.activeMineAreaDescentTarget != null) {
            if (client.player.onGround() && client.player.blockPosition().getY() < this.activeMineAreaDescentStartY) {
               this.releaseMovementKeys(client);
               this.activeMineAreaDescentTarget = null;
               this.activeMineAreaTravelTicks = 0;
               this.lastStatus = Component.translatable("macro_builder.runner.mine_area_safe_descent_complete");
            } else {
               this.activeMineAreaTravelTicks++;
               this.moveTowardBlock(client, this.activeMineAreaDescentTarget);
               if (this.activeMineAreaTravelTicks > MINE_AREA_MOVE_TIMEOUT_TICKS) {
                  this.releaseHeldKeys(client);
                  this.activeMineAreaDescentTarget = null;
                  this.activeMineAreaTravelTicks = 0;
                  this.fail(client, node, tr("macro_builder.runner.failed_mine_area_could_not_finish_the_safe_descent"));
               } else {
                  this.lastStatus = Component.translatable("macro_builder.runner.mine_area_stepping_down_before_removing_the_old_support_block");
               }

               return;
            }
         }

         if (this.activeMineAreaPos.equals(client.player.blockPosition().below())) {
            this.activeMineAreaTicks = 0;
            if (!options.move()) {
               this.skipMineAreaBlock(client, tr("macro_builder.runner.mine_area_left_the_support_block_because_move_is_off"));
               return;
            }

            BlockPos descentStep = this.mineAreaDescentStep(client, options);
            if (descentStep == null) {
               this.releaseHeldKeys(client);
               this.activeMineAreaTravelTicks = 0;
               this.fail(client, node, tr("macro_builder.runner.failed_mine_area_could_not_find_a_supported_step_down_before_mining"));
               return;
            }

            this.activeMineAreaDescentTarget = descentStep;
            this.activeMineAreaDescentStartY = client.player.blockPosition().getY();
            this.activeMineAreaTravelTicks++;
            this.moveTowardBlock(client, descentStep);
            if (this.activeMineAreaTravelTicks > MINE_AREA_MOVE_TIMEOUT_TICKS) {
               this.releaseHeldKeys(client);
               this.activeMineAreaTravelTicks = 0;
               this.fail(client, node, tr("macro_builder.runner.failed_mine_area_could_not_finish_the_safe_descent"));
            } else {
               this.lastStatus = Component.translatable("macro_builder.runner.mine_area_stepping_down_before_removing_the_old_support_block");
            }

            return;
         }

         if (!canReachBlock(client, this.activeMineAreaPos)) {
            this.activeMineAreaTicks = 0;
            if (!options.move()) {
               this.skipMineAreaBlock(client, tr("macro_builder.runner.mine_area_skipped_out_of_reach_block"));
            } else {
               BlockPos movementTarget = options.safePath() ? this.mineAreaSafeStepToward(client, this.activeMineAreaPos, options.sliceHeight()) : this.activeMineAreaPos;
               if (movementTarget == null) {
                  this.releaseHeldKeys(client);
                  this.activeMineAreaTicks = 0;
                  this.activeMineAreaTravelTicks = 0;
                  this.fail(client, node, tr("macro_builder.runner.failed_mine_area_safe_path_could_not_walk_around_a_hole_or", options.sliceHeight()));
                  return;
               }

               this.activeMineAreaTravelTicks++;
               this.moveTowardBlock(client, movementTarget);
               if (this.activeMineAreaTravelTicks > MINE_AREA_MOVE_TIMEOUT_TICKS) {
                  this.skipMineAreaBlock(client, tr("macro_builder.runner.mine_area_skipped_unreachable_block"));
               } else {
                  this.lastStatus = Component.translatable("macro_builder.runner.mine_area_moving_to", blockPosText(this.activeMineAreaPos));
               }
            }
         } else {
            this.releaseMovementKeys(client);
            this.activeMineAreaTravelTicks = 0;
            this.activeMineAreaTicks++;
            BlockState targetState = client.level.getBlockState(this.activeMineAreaPos);
            if (!isMineAreaBreakTarget(targetState)) {
               this.activeMineAreaPos = null;
               this.activeMineAreaTicks = 0;
               return;
            }

            if (options.autoTool()) {
               this.selectBestHotbarTool(client, targetState);
               if (toolIsLow(client, options.toolLowThreshold())) {
                  this.releaseHeldKeys(client);
                  this.complete(client, node, "tool_low");
                  return;
               }
            }

            this.lookAt(client, Vec3.atCenterOf(this.activeMineAreaPos));
            Direction hitDirection = this.mineAreaHitDirection(client, this.activeMineAreaPos);
            if (this.activeMineAreaTicks == 1) {
               client.gameMode.startDestroyBlock(this.activeMineAreaPos, hitDirection);
            } else {
               client.gameMode.continueDestroyBlock(this.activeMineAreaPos, hitDirection);
            }

            client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            if (!isMineAreaBreakTarget(client.level.getBlockState(this.activeMineAreaPos))) {
               this.activeMineAreaPos = null;
               this.activeMineAreaTicks = 0;
            } else {
               if (this.activeMineAreaTicks > MINE_AREA_BREAK_TIMEOUT_TICKS) {
                  this.skipMineAreaBlock(client, tr("macro_builder.runner.mine_area_skipped_block_that_would_not_break"));
               }
            }
         }
      }
   }

   private void skipMineAreaBlock(Minecraft client, String status) {
      this.releaseMovementKeys(client);
      if (this.activeMineAreaPos != null) {
         this.activeMineAreaSkippedBlocks.add(this.activeMineAreaPos);
      }

      this.activeMineAreaPos = null;
      this.activeMineAreaDescentTarget = null;
      this.activeMineAreaTicks = 0;
      this.activeMineAreaTravelTicks = 0;
      this.lastStatus = Component.literal(status);
   }

   private void runEntityAction(Minecraft client, MacroModel.Node node, boolean attack) {
      Entity target = this.findTargetEntity(client, this.resolveRuntimeText(node.value));
      if (target == null) {
         if (this.activeTicks > 300) {
            this.fail(client, node, tr("macro_builder.runner.failed_no_matching_entity_found"));
         }
      } else {
         this.lookAt(client, this.entityCenter(target));
         Boolean moveToTarget = parseBooleanOrDefault(this.resolveRuntimeText(node.value2), false);
         if (moveToTarget == null) {
            this.fail(client, node, tr("macro_builder.runner.failed_entity_move_value_must_be_true_or_false"));
         } else if (client.player.distanceToSqr(target) > (double)(ENTITY_REACH_DISTANCE * ENTITY_REACH_DISTANCE)) {
            if (moveToTarget) {
               this.setMacroKeyDown(client, client.options.keyUp, true);
               if (this.activeTicks > 300) {
                  this.releaseMovementKeys(client);
                  this.fail(client, node, tr("macro_builder.runner.failed_target_entity_was_out_of_reach"));
               }
            } else {
               this.releaseMovementKeys(client);
               this.fail(client, node, tr("macro_builder.runner.failed_target_is_out_of_reach_turn_move_on_or_stand_within"));
            }
         } else {
            this.releaseMovementKeys(client);
            if (client.gameMode == null) {
               this.fail(client, node, tr("macro_builder.runner.failed_entity_interaction_was_not_available"));
            } else {
               if (attack) {
                  client.gameMode.attack(client.player, target);
               } else {
                  client.gameMode.interact(client.player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
               }

               client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
               this.complete(client, node, "completed");
            }
         }
      }
   }

   private void runDisconnect(Minecraft client, MacroModel.Node node) {
      this.releaseHeldKeys(client);
      this.running = false;
      this.activeNodeId = null;
      this.activeTicks = 0;
      this.lastStatus = Component.translatable("macro_builder.runner.disconnected_by_macro");
      if (client != null) {
         client.disconnectFromWorld(Component.translatable("macro_builder.runner.disconnected_by_macro"));
      }
   }

   private void runRejoinServer(Minecraft client, MacroModel.Node node) {
      ServerData server = client == null ? null : client.getCurrentServer();
      if (server != null && server.ip != null && !server.ip.isBlank()) {
         this.lastServerData = server;
      }

      ServerData targetServer = this.rejoinTargetServer(node);
      if (targetServer == null || targetServer.ip == null || targetServer.ip.isBlank()) {
         this.fail(client, node, tr("macro_builder.runner.failed_rejoin_server_needs_a_last_server_or_a_server_ip"));
      } else {
         int delayMs = Math.max(0, parseInt(this.resolveRuntimeText(node.value), 3000));
         this.scheduleServerConnection(client, node, targetServer, delayMs, "Rejoin Server", tr("macro_builder.runner.rejoining_by_macro"));
      }
   }

   private void runJoinServer(Minecraft client, MacroModel.Node node) {
      String configuredIp = this.resolveRuntimeText(node.value).trim();
      if (configuredIp.isBlank()) {
         this.fail(client, node, tr("macro_builder.runner.failed_join_server_needs_a_server_ip"));
      } else {
         ServerData targetServer = new ServerData(configuredIp, configuredIp, ServerData.Type.OTHER);
         int delayMs = Math.max(0, parseInt(this.resolveRuntimeText(node.value2), 1000));
         this.scheduleServerConnection(client, node, targetServer, delayMs, "Join Server", tr("macro_builder.runner.joining_server_by_macro"));
      }
   }

   private void scheduleServerConnection(
      Minecraft client, MacroModel.Node node, ServerData targetServer, int delayMs, String label, String disconnectMessage
   ) {
      if (this.activeTicks == 1) {
         this.pendingRejoinServer = targetServer;
         this.pendingRejoinNodeId = node.id;
         this.pendingRejoinLabel = label;
         this.pendingRejoinDelayTicks = Math.max(1, (int)Math.ceil(delayMs / 50.0));
         this.pendingRejoinConnectTicks = 0;
         this.pendingRejoinConnecting = false;
         this.releaseHeldKeys(client);
         this.clearDiscordRequest();
         this.clearFarmState();
         this.clearMineAreaState();
         this.activeJumpPlaceSupportPos = null;
         this.waitingForWorld = false;
         this.lastStatus = Component.translatable("macro_builder.runner.in_ms", label, delayMs);
         if (client != null && client.player != null && client.level != null) {
            client.disconnectFromWorld(Component.literal(disconnectMessage));
         }
      }
   }

   private ServerData rejoinTargetServer(MacroModel.Node node) {
      String configuredIp = this.resolveRuntimeText(node.value2).trim();
      if (!configuredIp.isBlank()) {
         ServerData target = this.lastServerData == null ? new ServerData(configuredIp, configuredIp, ServerData.Type.OTHER) : this.copyServerData(this.lastServerData);
         target.name = configuredIp;
         target.ip = configuredIp;
         return target;
      }

      return this.lastServerData == null ? null : this.copyServerData(this.lastServerData);
   }

   private void runIdleUntil(Minecraft client, MacroModel.Node node) {
      Boolean triggered = this.idleConditionMatches(
         client, this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2), this.nodeChatBaseline, this.nodeActionbarBaseline, this.nodeKickedBaseline
      );
      if (triggered == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_idle_until_condition_must_be_chat_actionbar_player_full_kicked_item"));
      } else if (triggered) {
         this.complete(client, node, "triggered");
      } else {
         this.lastStatus = Component.translatable("macro_builder.runner.idle_waiting_for", this.idleConditionLabel(this.resolveRuntimeText(node.value)));
      }
   }

   private void runUpdateSign(Minecraft client, MacroModel.Node node) {
      BlockHitResult hit = this.targetBlockHit(client, "");
      if (hit == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_update_sign_needs_you_to_look_at_a_sign"));
      } else {
         String[] lines = signLines(this.resolveRuntimeText(node.value));
         client.player.connection.send(new ServerboundSignUpdatePacket(hit.getBlockPos(), List.of(lines[0], lines[1], lines[2], lines[3]), SignTextSlot.FRONT));
         this.complete(client, node, "completed");
      }
   }

   private void runRandom(Minecraft client, MacroModel.Node node) {
      Double percent = parseDoubleOrNull(this.resolveRuntimeText(node.value));
      if (percent != null && !(percent < 0.0) && !(percent > 100.0)) {
         this.complete(client, node, Math.random() * 100.0 < percent ? "true" : "false");
      } else {
         this.fail(client, node, tr("macro_builder.runner.failed_random_true_false_needs_a_number_from_0_to_100"));
      }
   }

   private void runRandomOutput3(Minecraft client, MacroModel.Node node) {
      String output = switch ((int)(Math.random() * 3.0)) {
         case 0 -> "one";
         case 1 -> "two";
         default -> "three";
      };
      this.complete(client, node, output);
   }

   private void runRepeatMacro(Minecraft client, MacroModel.Node node) {
      String repeatText = this.resolveRuntimeText(node.value);
      if (isRepeatIndefinitely(repeatText)) {
         MacroModel.Node start = this.model == null ? null : this.model.startNode();
         if (start == null) {
            this.stop(client, tr("macro_builder.runner.stopped_repeat_macro_could_not_find_the_entry_point"));
         } else {
            this.releaseHeldKeys(client);
            this.currentNodeId = start.id;
            this.activeNodeId = null;
            this.activeTicks = 0;
            this.stepDelayTicksRemaining = this.stepDelayTicks(node);
            this.lastStatus = Component.translatable("macro_builder.runner.repeating_macro_forever", this.currentMacroNumber);
         }

         return;
      }

      int targetRuns = parseInt(repeatText, 1);
      if (targetRuns < 1 || targetRuns > 100000) {
         this.fail(client, node, tr("macro_builder.runner.failed_repeat_macro_needs_a_repeat_count_from_1_to_100000_or"));
      } else {
         int completedRuns = this.repeatCounters.getOrDefault(node.id, 0) + 1;
         if (completedRuns >= targetRuns) {
            this.repeatCounters.remove(node.id);
            this.stop(client, tr("macro_builder.runner.stopped_repeat_macro_finished_run", targetRuns, (targetRuns == 1 ? "" : "s")));
         } else {
            MacroModel.Node start = this.model == null ? null : this.model.startNode();
            if (start == null) {
               this.stop(client, tr("macro_builder.runner.stopped_repeat_macro_could_not_find_the_entry_point"));
            } else {
               this.releaseHeldKeys(client);
               this.repeatCounters.put(node.id, completedRuns);
               this.currentNodeId = start.id;
               this.activeNodeId = null;
               this.activeTicks = 0;
               this.stepDelayTicksRemaining = this.stepDelayTicks(node);
               this.lastStatus = Component.translatable("macro_builder.runner.repeating_macro_run_of", this.currentMacroNumber, (completedRuns + 1), targetRuns);
            }
         }
      }
   }

   private void runRepeatSection(Minecraft client, MacroModel.Node node) {
      String repeatText = this.resolveRuntimeText(node.value);
      if (isRepeatIndefinitely(repeatText)) {
         this.complete(client, node, "repeat");
         this.lastStatus = Component.translatable("macro_builder.runner.repeating_section_forever");
         return;
      }

      int targetRuns = parseInt(repeatText, 1);
      if (targetRuns < 1 || targetRuns > 100000) {
         this.fail(client, node, tr("macro_builder.runner.failed_repeat_needs_a_repeat_count_from_1_to_100000_or_forever"));
      } else {
         int completedRuns = this.repeatCounters.getOrDefault(node.id, 0) + 1;
         if (completedRuns >= targetRuns) {
            this.repeatCounters.remove(node.id);
            this.complete(client, node, "completed");
         } else {
            this.repeatCounters.put(node.id, completedRuns);
            this.complete(client, node, "repeat");
            this.lastStatus = Component.translatable("macro_builder.runner.repeating_section_loop_of", (completedRuns + 1), targetRuns);
         }
      }
   }

   private void runRepeatUntil(Minecraft client, MacroModel.Node node) {
      Boolean done = this.idleConditionMatches(
         client,
         this.resolveRuntimeText(node.value),
         this.resolveRuntimeText(node.value2),
         this.autoStartChatBaseline,
         this.autoStartActionbarBaseline,
         this.autoStartKickedBaseline
      );
      if (done == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_repeat_until_condition_must_be_chat_actionbar_player_full_kicked_item"));
      } else if (done) {
         this.complete(client, node, "completed");
      } else {
         this.complete(client, node, "repeat");
         this.lastStatus = Component.translatable("macro_builder.runner.repeating_until", this.idleConditionLabel(this.resolveRuntimeText(node.value)));
      }
   }

   private static boolean isRepeatIndefinitely(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      return normalized.equals("forever")
         || normalized.equals("infinite")
         || normalized.equals("indefinite")
         || normalized.equals("always")
         || normalized.equals("loop")
         || normalized.equals("loops")
         || normalized.equals("-1");
   }

   private void skipDisabledNode(Minecraft client, MacroModel.Node node) {
      List<String> outputs = node.descriptor().outputs();
      if (outputs.isEmpty()) {
         this.endBranch(client, tr("macro_builder.runner.skipped_disabled", node.displayLabel()));
      } else {
         String output = outputs.contains("completed") ? "completed" : outputs.contains("started") ? "started" : outputs.get(0);
         this.lastStatus = Component.translatable("macro_builder.runner.skipped_disabled", node.displayLabel());
         this.complete(client, node, output);
      }
   }

   private void runSkipIfTrue(Minecraft client, MacroModel.Node node) {
      Boolean condition = parseBooleanOrDefault(this.resolveRuntimeText(node.value), true);
      if (condition == null) {
         this.fail(client, node, tr("macro_builder.runner.failed_skip_if_true_needs_true_or_false"));
      } else if (!condition) {
         this.complete(client, node, "false");
      } else {
         String skipTarget = this.resolveRuntimeText(node.value2);
         MacroModel.Node skipped = this.findNodeForSkip(node, skipTarget);
         if (skipped == null) {
            if (!skipTarget.isBlank()) {
               this.fail(client, node, tr("macro_builder.runner.failed_skip_if_true_could_not_find", skipTarget.trim()));
            } else {
               this.complete(client, node, "true");
            }
         } else {
            String outputKey = this.skipOutputKey(skipped.descriptor());
            List<String> nextTargets = skipped.nextTargets(outputKey);
            if (nextTargets.isEmpty()) {
               this.complete(client, node, "true");
            } else {
               this.jumpToTargets(client, node, nextTargets, tr("macro_builder.runner.skipped", skipped.displayLabel()));
            }
         }
      }
   }

   private MacroModel.Node findNodeForSkip(MacroModel.Node source, String text) {
      String filter = cleanSkipTarget(text);
      if (filter.isBlank() || this.model == null) {
         return null;
      }

      String normalized = filter.toLowerCase(Locale.ROOT);
      for (MacroModel.Node node : this.model.nodes()) {
         if (node != source
            && (node.id.equals(filter)
               || node.displayLabel().equalsIgnoreCase(filter)
               || node.descriptor().legacyLabel().equalsIgnoreCase(filter)
               || node.descriptor().label().equalsIgnoreCase(filter)
               || node.type.equalsIgnoreCase(filter))) {
            return node;
         }
      }

      for (MacroModel.Node node : this.model.nodes()) {
         if (node != source
            && (node.displayLabel().toLowerCase(Locale.ROOT).contains(normalized)
               || node.descriptor().legacyLabel().toLowerCase(Locale.ROOT).contains(normalized)
               || node.descriptor().label().toLowerCase(Locale.ROOT).contains(normalized))) {
            return node;
         }
      }

      return null;
   }

   private static String cleanSkipTarget(String text) {
      String value = text == null ? "" : text.trim();
      for (String prefix : List.of("component=", "name=", "id=", "skip=", "target=")) {
         if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            return value.substring(prefix.length()).trim();
         }
      }

      return value;
   }

   private String skipOutputKey(MacroModel.Descriptor descriptor) {
      List<String> outputs = descriptor.outputs();
      for (String preferred : List.of("completed", "started", "true", "triggered", "repeat", "failed", "false")) {
         if (outputs.contains(preferred)) {
            return preferred;
         }
      }

      return outputs.isEmpty() ? "completed" : outputs.get(0);
   }

   private void jumpToTargets(Minecraft client, MacroModel.Node node, List<String> nextTargets, String message) {
      if (nextTargets.isEmpty()) {
         this.stop(client, tr("macro_builder.runner.stopped_has_no_next_connection", message));
      } else if (this.model != null && nextTargets.stream().allMatch(target -> this.model.node(target) != null)) {
         this.currentNodeId = nextTargets.get(0);
         this.activeNodeId = null;
         this.activeTicks = 0;
         this.stepDelayTicksRemaining = this.stepDelayTicks(node);
         this.lastStatus = Component.literal(message + ".");

         for (int index = 1; index < nextTargets.size(); index++) {
            this.rootRunner().queueChildRunner(this.spawnBranch(nextTargets.get(index), node));
         }
      } else {
         this.stop(client, tr("macro_builder.runner.stopped_has_a_broken_link", message));
      }
   }

   private void endBranch(Minecraft client, String reason) {
      this.releaseHeldKeys(client);
      this.clearDiscordRequest();
      this.clearFarmState();
      this.clearMineAreaState();
      this.running = false;
      this.waitingForWorld = false;
      this.currentNodeId = null;
      this.activeNodeId = null;
      this.activeTicks = 0;
      this.stepDelayTicksRemaining = 0;
      this.repeatCounters.clear();
      this.clearPendingRejoin();
      this.lastStatus = Component.literal(reason);
   }

   private void completeCondition(Minecraft client, MacroModel.Node node, Boolean result, String failReason) {
      if (result != null) {
         this.complete(client, node, result ? "true" : "false");
      } else {
         this.fail(client, node, failReason != null && !failReason.isBlank() ? failReason : tr("macro_builder.runner.failed_could_not_run", node.displayLabel()));
      }
   }

   private void complete(Minecraft client, MacroModel.Node node, String outputKey) {
      List<String> nextTargets = node.nextTargets(outputKey);
      if (nextTargets.isEmpty()) {
         this.stop(client, tr("macro_builder.runner.stopped_has_no_connection", node.displayLabel(), MacroText.output(outputKey)));
      } else if (this.model != null && nextTargets.stream().allMatch(target -> this.model.node(target) != null)) {
         String next = nextTargets.get(0);
         this.currentNodeId = next;
         this.activeNodeId = null;
         this.activeTicks = 0;
         this.stepDelayTicksRemaining = this.stepDelayTicks(node);
         this.lastStatus = Component.translatable("macro_builder.runner.running_macro_2", this.currentMacroNumber, node.displayLabel());

         for (int index = 1; index < nextTargets.size(); index++) {
            this.rootRunner().queueChildRunner(this.spawnBranch(nextTargets.get(index), node));
         }
      } else {
         this.stop(client, tr("macro_builder.runner.stopped_has_a_broken_link_2", node.displayLabel(), outputKey));
      }
   }

   private void fail(Minecraft client, MacroModel.Node node, String reason) {
      this.releaseHeldKeys(client);
      List<String> nextTargets = node.nextTargets("failed");
      this.lastStatus = Component.literal(reason);
      if (nextTargets.isEmpty()) {
         this.stop(client, reason);
      } else if (this.model != null && nextTargets.stream().allMatch(target -> this.model.node(target) != null)) {
         String next = nextTargets.get(0);
         this.currentNodeId = next;
         this.activeNodeId = null;
         this.activeTicks = 0;
         this.stepDelayTicksRemaining = this.stepDelayTicks(node);

         for (int index = 1; index < nextTargets.size(); index++) {
            this.rootRunner().queueChildRunner(this.spawnBranch(nextTargets.get(index), node));
         }
      } else {
         this.stop(client, tr("macro_builder.runner.broken_failed_link", reason));
      }
   }

   private int stepDelayTicks(MacroModel.Node node) {
      int delayMs = node != null && node.delayMs >= 0 ? node.delayMs : (this.model == null ? 0 : this.model.stepDelayMs());
      if (this.model != null && this.model.randomDelayEnabled()) {
         delayMs = randomizedDelayMs(this.model.randomDelayMinMs(), this.model.randomDelayMaxMs());
      }

      return Math.max(0, (int)Math.ceil(delayMs / 50.0));
   }

   private static int randomizedDelayMs(int minMs, int maxMs) {
      int min = Math.max(0, Math.min(minMs, maxMs));
      int max = Math.max(min, Math.max(minMs, maxMs));
      return min + (int)Math.floor(Math.random() * (double)(max - min + 1));
   }

   private String resolveRuntimeText(String text) {
      if (text == null || text.isBlank()) {
         return "";
      }

      String directKey = runtimeKey(text);
      if (directKey != null) {
         return this.runtimeValues.getOrDefault(directKey, "");
      }

      if (text.indexOf('$') < 0 && text.indexOf("{{") < 0) {
         return text;
      }

      String resolved = text;
      for (Map.Entry<String, String> entry : this.runtimeValues.entrySet()) {
         resolved = resolved.replace("${" + entry.getKey() + "}", entry.getValue());
         resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
         resolved = resolved.replace("$" + entry.getKey(), entry.getValue());
      }

      return resolved;
   }

   private static String runtimeKey(String text) {
      String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
      if (normalized.startsWith("${") && normalized.endsWith("}")) {
         normalized = normalized.substring(2, normalized.length() - 1).trim();
      } else if (normalized.startsWith("{{") && normalized.endsWith("}}")) {
         normalized = normalized.substring(2, normalized.length() - 2).trim();
      } else if (normalized.startsWith("$")) {
         normalized = normalized.substring(1).trim();
      }

      normalized = normalized.replace('-', '_');
      return isRuntimeKey(normalized) ? normalized : null;
   }

   private static boolean isRuntimeKey(String key) {
      if (key == null || key.isBlank()) {
         return false;
      }

      return key.startsWith("last.")
         || key.startsWith("last_")
         || key.startsWith("player.")
         || key.startsWith("target.")
         || key.startsWith("world.")
         || key.startsWith("macro.")
         || key.startsWith("server.")
         || key.startsWith("screen.")
         || key.endsWith(".slot")
         || key.endsWith(".inventory_slot")
         || key.endsWith(".hotbar_slot")
         || key.endsWith(".item")
         || key.endsWith(".action");
   }

   private void putRuntimeValue(String key, String value) {
      String normalized = runtimeKey(key);
      if (normalized == null) {
         normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('-', '_');
      }

      if (normalized.isBlank()) {
         return;
      }

      if (value == null || value.isBlank()) {
         this.runtimeValues.remove(normalized);
      } else {
         this.runtimeValues.put(normalized, value.trim());
      }
   }

   private Vec3 parseResolvedVec3(Minecraft client, String value) {
      return parseVec3(this.resolveRuntimeText(value), relativeBase(client));
   }

   private BlockPos parseResolvedBlockPos(Minecraft client, String value) {
      Vec3 vec = this.parseResolvedVec3(client, value);
      return vec == null ? null : BlockPos.containing(vec);
   }

   private BlockPos parseResolvedFirstBlockPos(Minecraft client, String value) {
      return parseFirstBlockPos(this.resolveRuntimeText(value), relativeBase(client));
   }

   private void clearLastSlotOutput(MacroModel.Node node) {
      this.putRuntimeValue("last.slot", "");
      this.putRuntimeValue("last.inventory_slot", "");
      this.putRuntimeValue("last.hotbar_slot", "");
      this.putRuntimeValue(nodeScopedRuntimeKey(node, "slot"), "");
      this.putRuntimeValue(nodeScopedRuntimeKey(node, "inventory_slot"), "");
      this.putRuntimeValue(nodeScopedRuntimeKey(node, "hotbar_slot"), "");
   }

   private void setLastSlotOutput(MacroModel.Node node, AbstractContainerMenu handler, int menuSlotIndex) {
      int inventorySlot = playerInventorySlotNumber(handler, menuSlotIndex);
      if (inventorySlot < 0) {
         this.clearLastSlotOutput(node);
         return;
      }

      String slot = Integer.toString(inventorySlot + 1);
      this.putRuntimeValue("last.slot", slot);
      this.putRuntimeValue("last.inventory_slot", slot);
      this.putRuntimeValue(nodeScopedRuntimeKey(node, "slot"), slot);
      this.putRuntimeValue(nodeScopedRuntimeKey(node, "inventory_slot"), slot);
      if (inventorySlot < 9) {
         String hotbarSlot = Integer.toString(inventorySlot + 1);
         this.putRuntimeValue("last.hotbar_slot", hotbarSlot);
         this.putRuntimeValue(nodeScopedRuntimeKey(node, "hotbar_slot"), hotbarSlot);
      } else {
         this.putRuntimeValue("last.hotbar_slot", "");
         this.putRuntimeValue(nodeScopedRuntimeKey(node, "hotbar_slot"), "");
      }
   }

   private void setLastItemOutput(MacroModel.Node node, ItemStack stack) {
      String itemId = stack == null || stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
      this.putRuntimeValue("last.item", itemId);
      this.putRuntimeValue(nodeScopedRuntimeKey(node, "item"), itemId);
   }

   private static String nodeScopedRuntimeKey(MacroModel.Node node, String key) {
      if (node == null) {
         return "";
      }

      String name = node.displayName == null || node.displayName.isBlank() ? node.id : node.displayName;
      String cleaned = name.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
      StringBuilder builder = new StringBuilder();
      for (int index = 0; index < cleaned.length(); index++) {
         char ch = cleaned.charAt(index);
         if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.') {
            builder.append(ch);
         }
      }

      return builder.isEmpty() ? "" : builder + "." + key;
   }

   private void runMacroStart(Minecraft client, MacroModel.Node node) {
      int macroNumber = parseInt(this.resolveRuntimeText(node.value), this.currentMacroNumber);
      if (macroNumber >= 1 && macroNumber <= 100) {
         this.rootRunner().startMacroSlot(client, macroNumber);
         this.complete(client, node, "completed");
      } else {
         this.fail(client, node, tr("macro_builder.runner.failed_start_macro_slot_needs_a_macro_number_from_1_to_100"));
      }
   }

   private void runMacroStop(Minecraft client, MacroModel.Node node) {
      int macroNumber = parseInt(this.resolveRuntimeText(node.value), this.currentMacroNumber);
      if (macroNumber >= 1 && macroNumber <= 100) {
         boolean stopped = this.rootRunner().stopMacroRuns(client, macroNumber, tr("macro_builder.runner.stopped_macro_by_macro_component", macroNumber));
         if (this.childRunner && macroNumber == this.currentMacroNumber) {
            this.stopSelf(client, tr("macro_builder.runner.stopped_by_macro_component"));
         } else if (stopped || macroNumber != this.currentMacroNumber) {
            this.complete(client, node, "completed");
         } else {
            this.complete(client, node, "completed");
         }
      } else {
         this.fail(client, node, tr("macro_builder.runner.failed_stop_macro_slot_needs_a_macro_number_from_1_to_100"));
      }
   }

   private void runNotification(Minecraft client, MacroModel.Node node) {
      if (this.activeTicks != 1 && this.activeDiscordRequest != null && node.id.equals(this.activeDiscordNodeId)) {
         if (this.activeDiscordRequest.isDone()) {
            MacroRunner.DiscordResult result = this.activeDiscordRequest.getNow(new MacroRunner.DiscordResult(false, tr("macro_builder.runner.discord_failed")));
            this.clearDiscordRequest();
            if (result.success()) {
               this.complete(client, node, "completed");
            } else {
               this.fail(client, node, tr("macro_builder.runner.failed", result.message()));
            }

            this.send(client, result.message());
         } else {
            if (this.activeTicks > 300) {
               this.clearDiscordRequest();
               this.fail(client, node, tr("macro_builder.runner.failed_discord_webhook_timed_out"));
            }
         }
      } else {
         String message = node.value2 != null && !node.value2.isBlank() ? this.resolveRuntimeText(node.value2).trim() : "Notification";
         String webhook = node.value == null ? "" : node.value.trim();
         if (webhook.isBlank()) {
            this.clearDiscordRequest();
            this.fail(client, node, tr("macro_builder.runner.failed_discord_notification_needs_a_webhook_url"));
         } else {
            URI webhookUri = this.parseHttpUri(webhook);
            if (webhookUri == null) {
               this.clearDiscordRequest();
               this.fail(client, node, tr("macro_builder.runner.failed_discord_notification_needs_a_valid_webhook_url"));
            } else {
               this.activeDiscordNodeId = node.id;
               this.activeDiscordRequest = this.postDiscordWebhook(webhookUri, message);
               this.send(client, tr("macro_builder.runner.discord_notification_sending"));
            }
         }
      }
   }

   private void runEventChat(Minecraft client, MacroModel.Node node) {
      if (this.chatSequence > this.nodeChatBaseline && eventTextMatches(this.lastChatMessage, this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2))) {
         this.complete(client, node, "triggered");
      }
   }

   private void runEventActionbar(Minecraft client, MacroModel.Node node) {
      if (this.actionbarSequence > this.nodeActionbarBaseline
         && eventTextMatches(this.lastActionbarMessage, this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2))) {
         this.complete(client, node, "triggered");
      }
   }

   private void runEventStatusEffect(Minecraft client, MacroModel.Node node) {
      if (Boolean.TRUE.equals(this.statusEffectMatches(client, node))) {
         this.complete(client, node, "triggered");
      }
   }

   private void runEventBossbar(Minecraft client, MacroModel.Node node) {
      if (Boolean.TRUE.equals(this.bossbarContains(client, node))) {
         this.complete(client, node, "triggered");
      }
   }

   private void runEventKicked(Minecraft client, MacroModel.Node node) {
      if (this.kickedSequence > this.nodeKickedBaseline && eventTextMatches(this.lastKickReason, this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2))) {
         this.complete(client, node, "triggered");
      }
   }

   private Boolean chatSameMatches(MacroModel.Node node) {
      String latest = this.lastChatMessage == null ? "" : this.lastChatMessage;
      if (latest.isBlank()) {
         return false;
      } else {
         String filter = this.resolveRuntimeText(node.value).trim();
         if (filter.isBlank()) {
            return true;
         } else {
            String mode = this.resolveRuntimeText(node.value2);
            mode = mode.isBlank() ? "same" : mode.trim().toLowerCase(Locale.ROOT);

            return switch (mode) {
               case "", "same", "exact", "equals" -> latest.equalsIgnoreCase(filter);
               case "contains", "any" -> latest.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
               case "regex" -> this.chatRegexMatches(latest, filter);
               default -> null;
            };
         }
      }
   }

   private Boolean actionbarContains(MacroModel.Node node) {
      String latest = this.lastActionbarMessage == null ? "" : this.lastActionbarMessage;
      if (latest.isBlank()) {
         return false;
      }

      String filter = this.resolveRuntimeText(node.value).trim();
      if (filter.isBlank()) {
         return true;
      }

      String mode = this.resolveRuntimeText(node.value2);
      mode = mode.isBlank() ? "contains" : mode.trim().toLowerCase(Locale.ROOT);
      return matchesTextMode(latest, filter, mode);
   }

   private Boolean statusEffectMatches(Minecraft client, MacroModel.Node node) {
      return this.statusEffectMatches(client, this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2));
   }

   private Boolean statusEffectMatches(Minecraft client, String effectText, String minimumLevelText) {
      if (client == null || client.player == null) {
         return false;
      }

      String filter = effectText == null ? "" : effectText.trim().toLowerCase(Locale.ROOT);
      int minimumLevel = minimumLevelText == null || minimumLevelText.isBlank() ? 1 : parseInt(minimumLevelText, -1);
      if (filter.isBlank() || minimumLevel < 1) {
         return null;
      }

      for (MobEffectInstance effect : client.player.getActiveEffects()) {
         String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString().toLowerCase(Locale.ROOT);
         String descriptionId = effect.getDescriptionId().toLowerCase(Locale.ROOT);
         boolean idMatches = id.equals(filter) || (!filter.contains(":") && id.endsWith(":" + filter)) || descriptionId.endsWith("." + filter);
         if (idMatches && effect.getAmplifier() + 1 >= minimumLevel) {
            this.putRuntimeValue("effect.id", id);
            this.putRuntimeValue("effect.level", Integer.toString(effect.getAmplifier() + 1));
            this.putRuntimeValue("effect.duration_ticks", Integer.toString(effect.getDuration()));
            this.putRuntimeValue("effect.duration_seconds", Integer.toString(Math.max(0, effect.getDuration() / 20)));
            return true;
         }
      }

      return false;
   }

   private Boolean bossbarContains(Minecraft client, MacroModel.Node node) {
      return this.bossbarContains(client, this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2));
   }

   private Boolean bossbarContains(Minecraft client, String filterText, String modeText) {
      if (client == null || client.gui == null) {
         return false;
      }

      String filter = filterText == null ? "" : filterText.trim();
      String mode = modeText == null || modeText.isBlank() ? "contains" : modeText.trim().toLowerCase(Locale.ROOT);
      if (!"contains".equals(mode) && !"any".equals(mode) && !"exact".equals(mode) && !"equals".equals(mode) && !"regex".equals(mode)) {
         return null;
      }

      BossHealthOverlay overlay = client.gui.hud.getBossOverlay();
      for (LerpingBossEvent event : ((BossHealthOverlayAccessor)overlay).litemacro$getEvents().values()) {
         String text = event.getName().getString();
         if (Boolean.TRUE.equals(matchesTextMode(text, filter, mode))) {
            this.putRuntimeValue("bossbar.text", text);
            this.putRuntimeValue("bossbar.progress", Float.toString(event.getProgress()));
            this.putRuntimeValue("bossbar.percent", Integer.toString(Math.round(event.getProgress() * 100.0F)));
            return true;
         }
      }

      return false;
   }

   private Boolean chatRegexMatches(String text, String regexText) {
      try {
         return text.matches(regexText);
      } catch (PatternSyntaxException var4) {
         return null;
      }
   }

   private void runEventDeath(Minecraft client, MacroModel.Node node) {
      if (client.player.isDeadOrDying() || client.player.getHealth() <= 0.0F) {
         this.complete(client, node, "triggered");
      }
   }

   private void runEventDamage(Minecraft client, MacroModel.Node node) {
      if (this.damageSequence > this.nodeDamageBaseline) {
         this.complete(client, node, "triggered");
      }
   }

   private void runEventTeleport(Minecraft client, MacroModel.Node node) {
      if (this.teleportSequence > this.nodeTeleportBaseline) {
         this.complete(client, node, "triggered");
      }
   }

   private void runEventSchedule(Minecraft client, MacroModel.Node node) {
      String schedule = this.resolveRuntimeText(node.value).trim();
      if (!schedule.isBlank() && !schedule.equals("* * * * *")) {
         int delayTicks = parseScheduleDelayTicks(schedule);
         if (delayTicks > 0) {
            if (this.activeTicks >= delayTicks) {
               this.complete(client, node, "triggered");
            }
         } else {
            LocalTime target = parseTimeOfDay(schedule);
            if (target != null) {
               LocalTime now = LocalTime.now();
               if (now.getHour() == target.getHour() && now.getMinute() == target.getMinute()) {
                  this.complete(client, node, "triggered");
               }
            }
         }
      } else {
         long minute = System.currentTimeMillis() / 60000L;
         if (minute != this.scheduleMinute) {
            this.scheduleMinute = minute;
            this.complete(client, node, "triggered");
         }
      }
   }

   private void runEventPlayerChange(Minecraft client, MacroModel.Node node, boolean spawned) {
      long sequence = spawned ? this.playerSpawnSequence : this.playerDespawnSequence;
      long baseline = spawned ? this.nodeSpawnBaseline : this.nodeDespawnBaseline;
      String name = spawned ? this.lastPlayerSpawnName : this.lastPlayerDespawnName;
      if (sequence > baseline && eventTextMatches(name, this.resolveRuntimeText(node.value), this.resolveRuntimeText(node.value2))) {
         this.complete(client, node, "triggered");
      }
   }

   private void captureEventBaselines() {
      this.nodeChatBaseline = this.chatSequence;
      this.nodeActionbarBaseline = this.actionbarSequence;
      this.nodeDamageBaseline = this.damageSequence;
      this.nodeTeleportBaseline = this.teleportSequence;
      this.nodeSpawnBaseline = this.playerSpawnSequence;
      this.nodeDespawnBaseline = this.playerDespawnSequence;
      this.nodeKickedBaseline = this.kickedSequence;
   }

   private void updatePolledEvents(Minecraft client) {
      float health = client.player.getHealth();
      if (!Float.isNaN(this.lastHealth) && health < this.lastHealth && health > 0.0F) {
         this.damageSequence++;
      }

      this.lastHealth = health;
      Vec3 pos = this.entityCenter(client.player);
      String worldId = client.level.dimension().identifier().toString();
      if (this.lastPlayerPos != null && (!worldId.equals(this.lastWorldId) || pos.distanceToSqr(this.lastPlayerPos) > 64.0)) {
         this.teleportSequence++;
      }

      this.lastPlayerPos = pos;
      this.lastWorldId = worldId;
      this.currentPlayerIds.clear();

      for (AbstractClientPlayer player : client.level.players()) {
         UUID id = player.getUUID();
         String name = player.getName().getString();
         this.currentPlayerIds.add(id);
         if (!this.knownPlayerIds.isEmpty() && !this.knownPlayerIds.contains(id)) {
            this.lastPlayerSpawnName = name;
            this.playerSpawnSequence++;
         }

         this.knownPlayerNames.put(id, name);
      }

      if (!this.knownPlayerIds.isEmpty()) {
         for (UUID previous : this.knownPlayerIds) {
            if (!this.currentPlayerIds.contains(previous)) {
               this.lastPlayerDespawnName = this.knownPlayerNames.getOrDefault(previous, previous.toString());
               this.playerDespawnSequence++;
            }
         }
      }

      this.knownPlayerIds.clear();
      this.knownPlayerIds.addAll(this.currentPlayerIds);
      this.knownPlayerNames.keySet().retainAll(this.currentPlayerIds);
   }

   private URI parseHttpUri(String url) {
      try {
         URI uri = URI.create(url);
         String scheme = uri.getScheme();
         String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
         return "https".equalsIgnoreCase(scheme)
               && (host.equals("discord.com") || host.endsWith(".discord.com") || host.equals("discordapp.com") || host.endsWith(".discordapp.com"))
            ? uri
            : null;
      } catch (IllegalArgumentException var5) {
         return null;
      }
   }

   private CompletableFuture<MacroRunner.DiscordResult> postDiscordWebhook(URI webhook, String message) {
      try {
         JsonObject payload = new JsonObject();
         payload.addProperty("content", discordContent(message));
         JsonArray parse = new JsonArray();
         parse.add("users");
         parse.add("roles");
         parse.add("everyone");
         JsonObject allowedMentions = new JsonObject();
         allowedMentions.add("parse", parse);
         payload.add("allowed_mentions", allowedMentions);
         HttpRequest request = HttpRequest.newBuilder(discordWaitUri(webhook))
            .timeout(Duration.ofSeconds(12L))
            .header("Content-Type", "application/json")
            .header("User-Agent", "Litemacro/1.0.0")
            .POST(BodyPublishers.ofString(payload.toString()))
            .build();
         return DISCORD_HTTP.sendAsync(request, BodyHandlers.ofString()).handle((response, throwable) -> {
            if (throwable != null) {
               String errorx = tr("macro_builder.runner.discord_failed_2", throwable.getMessage());
               this.lastStatus = Component.literal(errorx);
               MacroBuilderClient.LOGGER.warn("Discord webhook failed", throwable);
               return new MacroRunner.DiscordResult(false, errorx);
            } else {
               int statusCode = response.statusCode();
               if (statusCode >= 200 && statusCode < 300) {
                  String success = tr("macro_builder.runner.discord_notification_sent");
                  this.lastStatus = Component.literal(success);
                  return new MacroRunner.DiscordResult(true, success);
               } else {
                  String body = response.body() == null ? "" : response.body().trim();
                  String errorxx = tr("macro_builder.runner.discord_failed_http", statusCode, (body.isBlank() ? "" : " " + body));
                  this.lastStatus = Component.literal(errorxx);
                  MacroBuilderClient.LOGGER.warn("Discord webhook returned HTTP {} {}", statusCode, body);
                  return new MacroRunner.DiscordResult(false, errorxx);
               }
            }
         });
      } catch (URISyntaxException | IllegalArgumentException var7) {
         String error = tr("macro_builder.runner.discord_failed_bad_webhook_url");
         this.lastStatus = Component.literal(error);
         MacroBuilderClient.LOGGER.warn("Invalid Discord webhook URL", var7);
         return CompletableFuture.completedFuture(new MacroRunner.DiscordResult(false, error));
      }
   }

   private static URI discordWaitUri(URI webhook) throws URISyntaxException {
      String query = webhook.getRawQuery();
      String waitQuery = query != null && !query.isBlank() ? query + "&wait=true" : "wait=true";
      return new URI(
         webhook.getScheme(), webhook.getRawUserInfo(), webhook.getHost(), webhook.getPort(), webhook.getRawPath(), waitQuery, webhook.getRawFragment()
      );
   }

   private static String discordContent(String message) {
      String content = message != null && !message.isBlank() ? message : "Notification";
      return content.length() <= 2000 ? content : content.substring(0, 1997) + "...";
   }

   private void clearDiscordRequest() {
      this.activeDiscordRequest = null;
      this.activeDiscordNodeId = null;
   }

   private void ensureConfigFiles() throws IOException {
      this.migrateLegacyConfigDirectory();
      Files.createDirectories(this.configDirectory);
      Path legacyPath = this.configDirectory.resolve("macro.json");
      Path macro1Path = this.macroPath(1);
      if (Files.notExists(macro1Path)) {
         if (Files.exists(legacyPath)) {
            Files.copy(legacyPath, macro1Path, StandardCopyOption.REPLACE_EXISTING);
         } else {
            this.saveDefaultExample(macro1Path);
         }
      } else {
         this.migrateOldMacro2ExampleToMacro1(macro1Path);
      }

      this.ensureMacroFile(this.currentMacroNumber);
      if (Files.notExists(this.settingsPath)) {
         this.settings.save(this.settingsPath);
      }
   }

   private void migrateLegacyConfigDirectory() throws IOException {
      if (!Files.exists(this.configDirectory)) {
         if (Files.exists(this.previousBrandConfigDirectory)) {
            this.copyConfigDirectory(this.previousBrandConfigDirectory);
         } else if (!Files.notExists(this.legacyConfigDirectory)) {
            this.copyConfigDirectory(this.legacyConfigDirectory);
         }
      }
   }

   private void copyConfigDirectory(Path sourceDirectory) throws IOException {
      Files.createDirectories(this.configDirectory);

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDirectory)) {
         for (Path source : stream) {
            if (Files.isRegularFile(source)) {
               Files.copy(source, this.configDirectory.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
         }
      }
   }

   private void ensureMacroFile(int macroNumber) throws IOException {
      Path path = this.macroPath(macroNumber);
      if (!Files.exists(path)) {
         MacroModel macro = MacroModel.createDefault();
         macro.setName("Macro " + macroNumber);
         macro.save(path);
      }
   }

   private void saveDefaultExample(Path path) throws IOException {
      MacroModel macro;
      try (InputStream input = MacroRunner.class.getResourceAsStream("/macro_builder/default_macro2_export.json")) {
         if (input == null) {
            macro = MacroModel.createDefault();
            macro.setName("Example");
         } else {
            macro = MacroModel.fromMineBotExport(input, "Example");
         }
      }

      macro.save(path);
   }

   private void migrateOldMacro2ExampleToMacro1(Path macro1Path) throws IOException {
      Path macro2Path = this.macroPath(2);
      if (!Files.notExists(macro2Path)) {
         MacroModel macro1 = MacroModel.load(macro1Path);
         MacroModel macro2 = MacroModel.load(macro2Path);
         if (this.isBlankMacro(macro1) && this.looksLikeBundledExample(macro2)) {
            macro2.setName("Example");
            macro2.save(macro1Path);
            MacroModel blankMacro2 = MacroModel.createDefault();
            blankMacro2.setName("Macro 2");
            blankMacro2.save(macro2Path);
         }
      }
   }

   private boolean isBlankMacro(MacroModel macro) {
      MacroModel.Node start = macro.startNode();
      return macro.nodes().size() == 1 && start != null && start.outputs.isEmpty();
   }

   private boolean looksLikeBundledExample(MacroModel macro) {
      if ("Macro 2".equals(macro.name()) && macro.nodes().size() >= 4) {
         boolean hasInventoryFull = false;
         boolean hasChat = false;
         boolean hasDeposit = false;

         for (MacroModel.Node node : macro.nodes()) {
            if ("official:inventory.isFull".equals(node.type)) {
               hasInventoryFull = true;
            }

            if ("official:misc.chat".equals(node.type)) {
               hasChat = true;
            }

            if ("official:inventory.chestDepositItems".equals(node.type)) {
               hasDeposit = true;
            }
         }

         return hasInventoryFull && hasChat && hasDeposit;
      } else {
         return false;
      }
   }

   private MacroModel readMacroImport(Path source, int macroNumber) throws IOException {
      JsonObject root;
      try (Reader reader = Files.newBufferedReader(source)) {
         root = JsonParser.parseReader(reader).getAsJsonObject();
      }

      if (root.has("components")) {
         MacroModel var5;
         try (Reader reader = Files.newBufferedReader(source)) {
            var5 = MacroModel.fromMineBotExport(reader, "Imported Macro " + macroNumber);
         }

         return var5;
      } else if (!root.has("nodes") && !root.has("format")) {
         throw new IOException(tr("macro_builder.runner.json_file_is_not_a_litemacro_or_minebot_macro"));
      } else {
         return MacroModel.load(source);
      }
   }

   List<Path> downloadsJsonFiles() throws IOException {
      Path downloads = this.downloadsDirectory();
      if (Files.notExists(downloads)) {
         throw new IOException(tr("macro_builder.runner.downloads_folder_was_not_found"));
      } else {
         List<Path> files = new ArrayList<>();

         try (DirectoryStream<Path> stream = Files.newDirectoryStream(downloads, "*.json")) {
            for (Path path : stream) {
               files.add(path);
            }
         }

         files.sort(Comparator.comparing(this::lastModifiedOrOldest).reversed());
         return files;
      }
   }

   List<Path> marketplaceJsonFiles() throws IOException {
      Path marketplace = this.marketplaceDirectory();
      Files.createDirectories(marketplace);
      List<Path> files = new ArrayList<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(marketplace, "*.json")) {
         for (Path path : stream) {
            files.add(path);
         }
      }

      files.sort(Comparator.comparing(this::lastModifiedOrOldest).reversed());
      return files;
   }

   private Path newestJsonInDownloads() throws IOException {
      List<Path> files = this.downloadsJsonFiles();
      if (files.isEmpty()) {
         throw new IOException(tr("macro_builder.runner.no_json_macro_file_found_in_downloads"));
      } else {
         return files.get(0);
      }
   }

   Path downloadsDirectory() {
      return Path.of(System.getProperty("user.home"), "Downloads");
   }

   Path marketplaceDirectory() {
      return this.downloadsDirectory().resolve("Litemacro-Marketplace");
   }

   private String safeMarketplaceName(String name) {
      String value = name == null || name.isBlank() ? "Macro" : name.trim();
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < value.length(); i++) {
         char ch = value.charAt(i);
         if (Character.isLetterOrDigit(ch)) {
            builder.append(ch);
         } else if (ch == '-' || ch == '_') {
            builder.append(ch);
         } else if (ch == ' ' && (builder.length() == 0 || builder.charAt(builder.length() - 1) != '-')) {
            builder.append('-');
         }
      }

      if (builder.isEmpty()) {
         return "Macro";
      } else {
         return builder.substring(0, Math.min(48, builder.length()));
      }
   }

   private FileTime lastModifiedOrOldest(Path path) {
      try {
         return Files.getLastModifiedTime(path);
      } catch (IOException var3) {
         return FileTime.fromMillis(0L);
      }
   }

   private Path macroPath(int macroNumber) {
      return this.configDirectory.resolve("macro" + macroNumber + ".json");
   }

   private boolean isInventoryFull(Minecraft client) {
      for (int index = 0; index < 36 && index < client.player.getInventory().getContainerSize(); index++) {
         if (client.player.getInventory().getItem(index).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   private Boolean isOpenContainerFull(Minecraft client) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      if (handler == null) {
         return null;
      } else {
         int firstPlayerSlot = firstPlayerInventorySlot(handler);

         for (int index = 0; index < firstPlayerSlot; index++) {
            if (((Slot)handler.slots.get(index)).getItem().isEmpty()) {
               return false;
            }
         }

         return firstPlayerSlot > 0;
      }
   }

   private Boolean inventoryHasItem(Minecraft client, MacroModel.Node node) {
      String itemId = MacroModel.normalizeItemId(this.resolveRuntimeText(node.value));
      if (!isKnownItemId(itemId)) {
         return null;
      } else {
         int minCount = Math.max(1, parseInt(this.resolveRuntimeText(node.value2), 1));
         int count = 0;

         for (int index = 0; index < client.player.getInventory().getContainerSize(); index++) {
            ItemStack stack = client.player.getInventory().getItem(index);
            if (stackMatches(stack, itemId)) {
               count += stack.getCount();
               if (count >= minCount) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private Boolean heldItemIs(Minecraft client, MacroModel.Node node) {
      String itemId = MacroModel.normalizeItemId(this.resolveRuntimeText(node.value));
      return !isKnownItemId(itemId) ? null : stackMatches(client.player.getMainHandItem(), itemId);
   }

   private Boolean slotHasItem(Minecraft client, MacroModel.Node node) {
      ItemStack stack = playerInventoryStack(client, this.resolveRuntimeText(node.value));
      String itemId = MacroModel.normalizeItemId(this.resolveRuntimeText(node.value2));
      return stack != null && isKnownItemId(itemId) ? stackMatches(stack, itemId) : null;
   }

   private Boolean itemOrSlotHasTag(Minecraft client, MacroModel.Node node) {
      String sourceText = this.resolveRuntimeText(node.value);
      String tagText = this.resolveRuntimeText(node.value2).trim();
      boolean openContainerSource = isOpenContainerTagSource(sourceText);
      if (tagText.isBlank()) {
         if (openContainerSource) {
            this.clearOpenContainerTagMatch();
         }

         return null;
      } else if (openContainerSource) {
         Boolean result = this.openContainerSourceHasTag(client, node, tagText);
         if (result == null || !result) {
            this.clearOpenContainerTagMatch();
         }

         return result;
      } else {
         List<ItemStack> stacks = stacksFromItemOrSlot(client, sourceText);
         if (stacks == null) {
            return null;
         }

         for (ItemStack stack : stacks) {
            if (stackHasConfiguredTag(stack, tagText)) {
               return true;
            }
         }

         return false;
      }
   }

   private Boolean itemDurabilityMatches(Minecraft client, MacroModel.Node node) {
      MacroRunner.DurabilityCheck check = durabilityCheck(this.resolveRuntimeText(node.value2));
      List<ItemStack> stacks = stacksFromItemOrSlot(client, this.resolveRuntimeText(node.value));
      if (check == null || stacks == null) {
         return null;
      }

      boolean sawDamageableItem = false;
      for (ItemStack stack : stacks) {
         if (!stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            sawDamageableItem = true;
            double remainingPercent = (stack.getMaxDamage() - stack.getDamageValue()) * 100.0 / stack.getMaxDamage();
            if (check.lower() ? remainingPercent <= check.percent() : remainingPercent >= check.percent()) {
               return true;
            }
         }
      }

      return sawDamageableItem ? false : false;
   }

   private Boolean emptySlotsAtLeast(Minecraft client, MacroModel.Node node) {
      Integer minimum = parseIntOrNull(this.resolveRuntimeText(node.value));
      return minimum != null && minimum >= 0 ? this.emptyInventorySlots(client) >= minimum : null;
   }

   private int emptyInventorySlots(Minecraft client) {
      int empty = 0;

      for (int index = 0; index < 36 && index < client.player.getInventory().getContainerSize(); index++) {
         if (client.player.getInventory().getItem(index).isEmpty()) {
            empty++;
         }
      }

      return empty;
   }

   private Boolean openContainerHasItem(Minecraft client, MacroModel.Node node) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      if (handler == null) {
         return null;
      } else {
         String itemId = MacroModel.normalizeItemId(this.resolveRuntimeText(node.value));
         if (!isKnownItemId(itemId)) {
            return null;
         } else {
            int minCount = Math.max(1, parseInt(this.resolveRuntimeText(node.value2), 1));
            int count = 0;
            int firstPlayerSlot = firstPlayerInventorySlot(handler);

            for (int index = 0; index < firstPlayerSlot; index++) {
               ItemStack stack = ((Slot)handler.slots.get(index)).getItem();
               if (stackMatches(stack, itemId)) {
                  count += stack.getCount();
                  if (count >= minCount) {
                     return true;
                  }
               }
            }

            return false;
         }
      }
   }

   private Boolean isLobbyMatch(Minecraft client, MacroModel.Node node) {
      String player = this.resolveRuntimeText(node.value).trim();
      return player.isBlank() ? null : client.player.getName().getString().equalsIgnoreCase(player);
   }

   private Boolean isAtLocation(Minecraft client, MacroModel.Node node) {
      Vec3 target = this.parseResolvedVec3(client, node.value);
      Double maxDistance = parseDoubleOrNull(this.resolveRuntimeText(node.value2));
      if (target == null || maxDistance == null || maxDistance < 0.0) {
         return null;
      } else {
         double x = client.player.getX() - target.x;
         double y = client.player.getY() - target.y;
         double z = client.player.getZ() - target.z;
         return x * x + y * y + z * z <= maxDistance * maxDistance;
      }
   }

   private Boolean playerNearby(Minecraft client, MacroModel.Node node) {
      String filterText = this.resolveRuntimeText(node.value).trim();
      String mode = this.resolveRuntimeText(node.value2);
      mode = mode.isBlank() ? "any" : mode.trim().toLowerCase(Locale.ROOT);
      if (!mode.equals("any") && !mode.equals("allow") && !mode.equals("block")) {
         return null;
      } else {
         for (AbstractClientPlayer player : client.level.players()) {
            if (!player.getUUID().equals(client.player.getUUID())) {
               String playerName = player.getName().getString();
               boolean listed = filterText.isBlank() || nameMatchesList(playerName, filterText) || nameMatchesRegex(playerName, filterText);
               if ("allow".equals(mode) && listed) {
                  return true;
               }

               if ("block".equals(mode) && !listed) {
                  return true;
               }

               if (!"allow".equals(mode) && !"block".equals(mode)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private Boolean scoreboardContains(Minecraft client, MacroModel.Node node) {
      String filter = this.resolveRuntimeText(node.value).trim();
      if (filter.isBlank()) {
         return true;
      }

      String mode = this.resolveRuntimeText(node.value2);
      mode = mode.isBlank() ? "contains" : mode.trim().toLowerCase(Locale.ROOT);
      if (!"contains".equals(mode) && !"any".equals(mode) && !"exact".equals(mode) && !"equals".equals(mode) && !"regex".equals(mode)) {
         return null;
      }

      String text = this.scoreboardText(client);
      return matchesTextMode(text, filter, mode);
   }

   private String scoreboardText(Minecraft client) {
      if (client == null || client.level == null) {
         return "";
      }

      Scoreboard scoreboard = client.level.getScoreboard();
      StringBuilder text = new StringBuilder();
      for (Objective objective : scoreboard.getObjectives()) {
         appendText(text, objective.getName());
         appendText(text, objective.getDisplayName().getString());

         for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
            appendText(text, entry.owner());
            appendText(text, entry.ownerName().getString());
            if (entry.display() != null) {
               appendText(text, entry.display().getString());
            }

            appendText(text, Integer.toString(entry.value()));
         }
      }

      for (PlayerTeam team : scoreboard.getPlayerTeams()) {
         appendText(text, team.getName());
         appendText(text, team.getDisplayName().getString());
         appendText(text, team.getPlayerPrefix().getString());
         appendText(text, team.getPlayerSuffix().getString());

         for (String playerName : team.getPlayers()) {
            appendText(text, playerName);
         }
      }

      return text.toString();
   }

   private static void appendText(StringBuilder text, String value) {
      if (value != null && !value.isBlank()) {
         text.append(value).append('\n');
      }
   }

   private static Boolean matchesTextMode(String text, String filter, String mode) {
      String normalizedText = text == null ? "" : text;
      String normalizedFilter = filter == null ? "" : filter.trim();
      if (normalizedFilter.isBlank()) {
         return true;
      }

      return switch (mode) {
         case "contains", "any" -> normalizedText.toLowerCase(Locale.ROOT).contains(normalizedFilter.toLowerCase(Locale.ROOT));
         case "exact", "equals" -> normalizedText.lines().anyMatch(line -> line.trim().equalsIgnoreCase(normalizedFilter));
         case "regex" -> {
            try {
               yield Pattern.compile(normalizedFilter, Pattern.CASE_INSENSITIVE).matcher(normalizedText).find();
            } catch (PatternSyntaxException error) {
               yield null;
            }
         }
         default -> null;
      };
   }

   private Boolean blockAtMatches(Minecraft client, MacroModel.Node node) {
      BlockPos pos = this.parseResolvedBlockPos(client, node.value);
      if (pos != null && client.level.isLoaded(pos)) {
         String blockId = MacroModel.normalizeItemId(this.resolveRuntimeText(node.value2));
         return !isKnownBlockId(blockId) ? null : BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString().equals(blockId);
      } else {
         return null;
      }
   }

   private Boolean lookingAtBlockMatches(Minecraft client, MacroModel.Node node) {
      if (client.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
         String blockId = this.resolveRuntimeText(node.value).trim();
         if (blockId.isBlank()) {
            return true;
         } else {
            return !isKnownBlockId(blockId)
               ? null
               : BuiltInRegistries.BLOCK
                  .getKey(client.level.getBlockState(hit.getBlockPos()).getBlock())
                  .toString()
                  .equals(MacroModel.normalizeItemId(blockId));
         }
      } else {
         return null;
      }
   }

   private Boolean entityNearby(Minecraft client, MacroModel.Node node) {
      Double maxDistance = parseDoubleOrNull(this.resolveRuntimeText(node.value2));
      return maxDistance != null && !(maxDistance <= 0.0) ? this.findTargetEntityWithin(client, this.resolveRuntimeText(node.value), maxDistance) != null : null;
   }

   private boolean clickOpenContainerTarget(Minecraft client, String target, int button, boolean shiftClick) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      if (handler != null && client.gameMode != null) {
         ContainerInput clickType = shiftClick ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
         Slot targetSlot = openContainerSlot(handler, target);
         if (targetSlot != null) {
            client.gameMode.handleContainerInput(handler.containerId, targetSlot.index, button, clickType, client.player);
            return true;
         }

         PriceRange priceRange = priceRange(target);
         String tagText = clickTargetTagText(target);
         String itemId = clickTargetItemId(target);
         if (priceRange == null && tagText.isBlank() && this.activeNodeId != null && this.activeNodeId.equals(this.lastOpenContainerTagNextNodeId)) {
            tagText = this.lastOpenContainerTagFilter;
            priceRange = priceRange(tagText);
         }

         int firstPlayerSlot = firstPlayerInventorySlot(handler);

         for (int index = 0; index < firstPlayerSlot; index++) {
            Slot slot = (Slot)handler.slots.get(index);
            if (stackMatchesClickTarget(slot.getItem(), itemId, tagText, priceRange)) {
               client.gameMode.handleContainerInput(handler.containerId, slot.index, button, clickType, client.player);
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean selectHotbarSlot(Minecraft client, MacroModel.Node node) {
      String rawItem = node.value == null ? "" : node.value.trim();
      String item = this.resolveRuntimeText(rawItem).trim();
      if (item.isBlank()) {
         if (!rawItem.isBlank() && runtimeKey(rawItem) != null) {
            return false;
         }

         String rawSlotText = node.value2 == null ? "" : node.value2.trim();
         String slotText = this.resolveRuntimeText(rawSlotText).trim();
         Integer slotValue = slotText.isBlank() ? parseIntOrNull(this.resolveRuntimeText(node.value)) : parseIntOrNull(slotText);
         if (slotValue == null && rawSlotText.isBlank()) {
            slotValue = 1;
         }

         return slotValue != null && this.selectInventorySlotIntoHotbar(client, slotValue - 1);
      } else {
         Integer slotValue = parseIntOrNull(item);
         if (slotValue != null) {
            return this.selectInventorySlotIntoHotbar(client, slotValue - 1);
         }

         String itemId = MacroModel.normalizeItemId(item);

         for (int slot = 0; slot < 9 && slot < client.player.getInventory().getContainerSize(); slot++) {
            if (stackMatches(client.player.getInventory().getItem(slot), itemId)) {
               client.player.getInventory().setSelectedSlot(slot);
               return true;
            }
         }

         return false;
      }
   }

   private boolean selectHotbarItem(Minecraft client, String itemId) {
      String normalized = MacroModel.normalizeItemId(this.resolveRuntimeText(itemId));

      for (int slot = 0; slot < 9 && slot < client.player.getInventory().getContainerSize(); slot++) {
         if (stackMatches(client.player.getInventory().getItem(slot), normalized)) {
            client.player.getInventory().setSelectedSlot(slot);
            return true;
         }
      }

      return false;
   }

   private boolean selectItemIfProvided(Minecraft client, String itemText) {
      return itemText != null && !itemText.isBlank() && !truthy(itemText) && !"false".equalsIgnoreCase(itemText.trim())
         ? this.selectHotbarItem(client, itemText)
         : true;
   }

   private Entity findTargetEntity(Minecraft client, String filter) {
      return this.findTargetEntityWithin(client, filter, Double.POSITIVE_INFINITY);
   }

   private Entity findTargetEntityWithin(Minecraft client, String filter, double maxDistance) {
      String normalized = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
      Entity best = null;
      double bestDistance = maxDistance == Double.POSITIVE_INFINITY ? Double.MAX_VALUE : maxDistance * maxDistance;

      for (Entity entity : client.level.entitiesForRendering()) {
         if (entity != client.player && entity instanceof LivingEntity && entity.isAlive() && (normalized.isBlank() || this.entityMatches(entity, normalized))) {
            double distance = client.player.distanceToSqr(entity);
            if (distance < bestDistance) {
               bestDistance = distance;
               best = entity;
            }
         }
      }

      return best;
   }

   private boolean entityMatches(Entity entity, String filter) {
      String selector = filter == null ? "" : filter.trim();
      String selectorValue = selector;
      boolean runtimeIdSelector = false;
      if (selector.startsWith("id=")) {
         runtimeIdSelector = true;
         selectorValue = selector.substring(3).trim();
      } else if (selector.startsWith("entity_id=")) {
         runtimeIdSelector = true;
         selectorValue = selector.substring("entity_id=".length()).trim();
      } else if (!selector.isEmpty() && selector.chars().allMatch(Character::isDigit)) {
         runtimeIdSelector = true;
      }

      if (runtimeIdSelector) {
         try {
            return entity.getId() == Integer.parseInt(selectorValue);
         } catch (NumberFormatException ignored) {
            return false;
         }
      }

      if (selector.startsWith("uuid=") || selector.length() == 36 && selector.indexOf('-') >= 0) {
         try {
            String uuidValue = selector.startsWith("uuid=") ? selector.substring(5).trim() : selector;
            return entity.getUUID().equals(UUID.fromString(uuidValue));
         } catch (IllegalArgumentException ignored) {
            return false;
         }
      }

      if (selector.startsWith("name=")) {
         return entity.getName().getString().equalsIgnoreCase(selector.substring(5).trim());
      }

      String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
      if (filter.startsWith("#")) {
         TagKey<EntityType<?>> tag = entityTypeTagKey(filter);
         if (tag != null && entity.getType().builtInRegistryHolder().is(tag)) {
            return true;
         }

         return "#minecraft:raiders".equals(filter) && VANILLA_RAIDER_ENTITY_IDS.contains(type);
      }

      String name = entity.getName().getString().toLowerCase(Locale.ROOT);
      return name.contains(filter) || type.contains(filter) || nameMatchesList(name, filter) || nameMatchesRegex(name, filter);
   }

   private BlockHitResult targetBlockHit(Minecraft client, String targetText) {
      Vec3 target = this.parseResolvedVec3(client, targetText);
      if (target != null) {
         BlockPos pos = BlockPos.containing(target);
         return this.blockFaceHit(client, pos);
      } else {
         return client.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK ? hit : null;
      }
   }

   private BlockHitResult blockFaceHit(Minecraft client, BlockPos pos) {
      Direction face = Direction.UP;
      if (client != null && client.player != null) {
         Vec3 center = Vec3.atCenterOf(pos);
         face = this.nearestBlockFace(client.player.getX() - center.x, client.player.getEyeY() - center.y, client.player.getZ() - center.z);
      }

      Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
      return new BlockHitResult(hit, face, pos, false);
   }

   private Direction nearestBlockFace(double dx, double dy, double dz) {
      double ax = Math.abs(dx);
      double ay = Math.abs(dy);
      double az = Math.abs(dz);
      if (ay >= ax && ay >= az) {
         return dy >= 0.0 ? Direction.UP : Direction.DOWN;
      } else if (ax >= az) {
         return dx >= 0.0 ? Direction.EAST : Direction.WEST;
      } else {
         return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
      }
   }

   private BlockHitResult blockTopHit(BlockPos pos) {
      return new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), Direction.UP, pos, false);
   }

   private BlockPos blockUnderPlayer(Minecraft client) {
      return BlockPos.containing(client.player.getX(), client.player.getY() - 0.05, client.player.getZ());
   }

   private Vec3 entityCenter(Entity entity) {
      return new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
   }

   private void lookAt(Minecraft client, Vec3 target) {
      Vec3 eye = client.player.getEyePosition();
      double dx = target.x - eye.x;
      double dy = target.y - eye.y;
      double dz = target.z - eye.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
      client.player.setYRot(Mth.wrapDegrees(yaw));
      client.player.setXRot((float)Mth.clamp(pitch, -90.0, 90.0));
   }

   private void releaseHeldKeys(Minecraft client) {
      this.releaseMovementKeys(client);
      if (this.activePressedKey != null) {
         this.setRawKeyDown(client, this.activePressedKey, false);
         this.activePressedKey = null;
      }

      if (client != null) {
         this.setMacroKeyDown(client, client.options.keyAttack, false);
         this.setMacroKeyDown(client, client.options.keyUse, false);
         this.setMacroKeyDown(client, client.options.keyShift, false);
         this.setMacroKeyDown(client, client.options.keySprint, false);
      }
   }

   private void releaseMovementKeys(Minecraft client) {
      if (client != null) {
         this.setMacroKeyDown(client, client.options.keyUp, false);
         this.setMacroKeyDown(client, client.options.keyDown, false);
         this.setMacroKeyDown(client, client.options.keyLeft, false);
         this.setMacroKeyDown(client, client.options.keyRight, false);
         this.setMacroKeyDown(client, client.options.keyJump, false);
      }
   }

   private void maintainBackgroundMacroState(Minecraft client) {
      if (this.running()) {
         this.disablePauseOnLostFocus(client);
         this.refreshHeldMacroInputs();
      } else {
         this.restorePauseOnLostFocus(client);
      }
   }

   private void disablePauseOnLostFocus(Minecraft client) {
      if (client == null || client.options == null) {
         return;
      }

      if (this.previousPauseOnLostFocus == null) {
         this.previousPauseOnLostFocus = client.options.pauseOnLostFocus;
      }

      client.options.pauseOnLostFocus = false;
   }

   private void restorePauseOnLostFocus(Minecraft client) {
      if (client == null || client.options == null || this.previousPauseOnLostFocus == null) {
         return;
      }

      client.options.pauseOnLostFocus = this.previousPauseOnLostFocus;
      this.previousPauseOnLostFocus = null;
   }

   private void refreshHeldMacroInputs() {
      for (Map.Entry<KeyMapping, Integer> entry : this.rootHeldKeyMappings.entrySet()) {
         if (entry.getValue() > 0) {
            KeyMapping keyMapping = entry.getKey();
            keyMapping.setDown(true);
            KeyMapping.set(keyMapping.getDefaultKey(), true);
         }
      }

      for (Map.Entry<InputConstants.Key, Integer> entry : this.rootHeldRawKeys.entrySet()) {
         if (entry.getValue() > 0) {
            KeyMapping.set(entry.getKey(), true);
         }
      }
   }

   private void setMacroKeyDown(Minecraft client, KeyMapping keyMapping, boolean down) {
      if (keyMapping == null) {
         return;
      }

      if (down) {
         if (this.ownedKeyMappings.add(keyMapping)) {
            Map<KeyMapping, Integer> rootKeys = this.rootRunner().rootHeldKeyMappings;
            rootKeys.put(keyMapping, rootKeys.getOrDefault(keyMapping, 0) + 1);
         }

         keyMapping.setDown(true);
         KeyMapping.set(keyMapping.getDefaultKey(), true);
      } else {
         this.releaseMacroKey(client, keyMapping);
      }
   }

   private void releaseMacroKey(Minecraft client, KeyMapping keyMapping) {
      if (keyMapping == null || !this.ownedKeyMappings.remove(keyMapping)) {
         return;
      }

      MacroRunner root = this.rootRunner();
      Map<KeyMapping, Integer> rootKeys = root.rootHeldKeyMappings;
      int remaining = Math.max(0, rootKeys.getOrDefault(keyMapping, 0) - 1);
      if (remaining > 0) {
         rootKeys.put(keyMapping, remaining);
         return;
      }

      rootKeys.remove(keyMapping);
      InputConstants.Key defaultKey = keyMapping.getDefaultKey();
      if (!root.hasMacroOwnerForRawKey(defaultKey) && !this.isPhysicalKeyDown(client, defaultKey)) {
         keyMapping.setDown(false);
         KeyMapping.set(defaultKey, false);
      }
   }

   private void setRawKeyDown(Minecraft client, InputConstants.Key key, boolean down) {
      if (key == null || key == InputConstants.UNKNOWN) {
         return;
      }

      if (down) {
         if (this.ownedRawKeys.add(key)) {
            Map<InputConstants.Key, Integer> rootKeys = this.rootRunner().rootHeldRawKeys;
            rootKeys.put(key, rootKeys.getOrDefault(key, 0) + 1);
         }

         KeyMapping.set(key, true);
      } else {
         this.releaseRawKey(client, key);
      }
   }

   private void releaseRawKey(Minecraft client, InputConstants.Key key) {
      if (key == null || key == InputConstants.UNKNOWN || !this.ownedRawKeys.remove(key)) {
         return;
      }

      MacroRunner root = this.rootRunner();
      Map<InputConstants.Key, Integer> rootKeys = root.rootHeldRawKeys;
      int remaining = Math.max(0, rootKeys.getOrDefault(key, 0) - 1);
      if (remaining > 0) {
         rootKeys.put(key, remaining);
         return;
      }

      rootKeys.remove(key);
      if (!root.hasMacroOwnerForKeyMapping(key) && !this.isPhysicalKeyDown(client, key)) {
         KeyMapping.set(key, false);
      }
   }

   private boolean hasMacroOwnerForRawKey(InputConstants.Key key) {
      return key != null && this.rootRunner().rootHeldRawKeys.getOrDefault(key, 0) > 0;
   }

   private boolean hasMacroOwnerForKeyMapping(InputConstants.Key key) {
      if (key == null) {
         return false;
      }

      for (Map.Entry<KeyMapping, Integer> entry : this.rootRunner().rootHeldKeyMappings.entrySet()) {
         if (entry.getValue() > 0 && key.equals(entry.getKey().getDefaultKey())) {
            return true;
         }
      }

      return false;
   }

   private boolean isPhysicalKeyDown(Minecraft client, KeyMapping keyMapping) {
      return keyMapping != null && this.isPhysicalKeyDown(client, keyMapping.getDefaultKey());
   }

   private boolean isPhysicalKeyDown(Minecraft client, InputConstants.Key key) {
      if (client == null || key == null || key == InputConstants.UNKNOWN || key.getValue() <= 0) {
         return false;
      }

      if (key.getType() == InputConstants.Type.MOUSE) {
         return false;
      }

      return InputConstants.isKeyDown(key.getValue());
   }

   private void clearFarmState() {
      this.activeFarmCropPos = null;
      this.activeFarmSeedItem = "";
      this.activeFarmHarvested = false;
   }

   private void clearMineAreaState() {
      this.activeMineAreaPos = null;
      this.activeMineAreaDescentTarget = null;
      this.activeMineAreaDescentStartY = 0;
      this.activeMineAreaTicks = 0;
      this.activeMineAreaTravelTicks = 0;
      this.activeMineAreaSkippedBlocks.clear();
   }

   private void clearAutoFishState() {
      this.activeFishPhase = 0;
      this.activeFishPhaseTick = 0;
   }

   private MacroRunner.FarmOptions farmOptions(Minecraft client, MacroModel.Node node) {
      Integer radius = parseIntOrNull(this.resolveRuntimeText(node.value));
      BlockPos min = null;
      BlockPos max = null;
      if (radius == null) {
         BlockPos from = this.parseResolvedBlockPos(client, node.value);
         BlockPos to = this.parseResolvedFirstBlockPos(client, node.value2);
         if (from == null || to == null) {
            return null;
         }

         min = minPos(from, to);
         max = maxPos(from, to);
         if (!areaWithinLimit(min, max, 16)) {
            return null;
         }

         radius = -1;
      } else if (radius < 1 || radius > 8) {
         return null;
      }

      String options = this.resolveRuntimeText(node.value2).trim().toLowerCase(Locale.ROOT);
      boolean disabled = options.equals("false") || options.equals("off") || options.equals("harvest only");
      boolean replant = !disabled && !options.contains("no replant") && !options.contains("noreplant");
      boolean deposit = !disabled
         && (options.isBlank() || options.equals("true") || options.contains("deposit") || options.contains("container") || options.contains("drop"))
         && !options.contains("no deposit")
         && !options.contains("nodeposit");
      boolean move = moveOption(options, true);
      return new MacroRunner.FarmOptions(radius, min, max, replant, deposit, move);
   }

   private MacroRunner.ContainerSearchOptions containerSearchOptions(String radiusText, String optionText) {
      Integer radius = parseIntOrNull(radiusText);
      if (radius != null && radius >= 1 && radius <= 16) {
         String options = optionText == null ? "" : optionText.trim().toLowerCase(Locale.ROOT);
         boolean move = true;
         String type = "any";

         for (String token : options.split("[,\\s]+")) {
            String normalized = token.trim();
            if (!normalized.isBlank()) {
               if (normalized.startsWith("move=")) {
                  Boolean parsedMove = parseBooleanOrDefault(normalized.substring("move=".length()), true);
                  if (parsedMove == null) {
                     return null;
                  }

                  move = parsedMove;
               } else {
                  if (normalized.startsWith("type=")) {
                     normalized = normalized.substring("type=".length());
                  }

                  Boolean parsedMove = parseBooleanOrDefault(normalized, true);
                  if (parsedMove == null
                     || !normalized.equals("true")
                        && !normalized.equals("false")
                        && !normalized.equals("on")
                        && !normalized.equals("off")
                        && !normalized.equals("yes")
                        && !normalized.equals("no")) {
                     String parsedType = containerType(normalized);
                     if (parsedType == null) {
                        return null;
                     }

                     type = parsedType;
                  } else {
                     move = parsedMove;
                  }
               }
            }
         }

         return new MacroRunner.ContainerSearchOptions(radius, move, type);
      } else {
         return null;
      }
   }

   private static String containerType(String token) {
      String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);

      return switch (normalized) {
         case "", "any", "all", "container" -> "any";
         case "chest", "chests", "normal_chest", "trapped_chest" -> "chest";
         case "barrel", "barrels" -> "barrel";
         case "ender", "enderchest", "ender_chest" -> "ender_chest";
         case "shulker", "shulkers", "shulker_box", "shulker_boxes" -> "shulker";
         case "furnace", "furnaces" -> "furnace";
         case "blast", "blast_furnace", "blast_furnaces", "blastfurnace" -> "blast_furnace";
         case "smoker", "smokers" -> "smoker";
         case "brewing", "brewing_stand", "brewing_stands", "brewingstand" -> "brewing_stand";
         case "hopper", "hoppers" -> "hopper";
         case "dispenser", "dispensers" -> "dispenser";
         case "dropper", "droppers" -> "dropper";
         default -> null;
      };
   }

   private String containerTypeLabel(String type) {
      return tr("macro_builder.container." + (type == null ? "any" : type));
   }

   private MacroRunner.BoneMealOptions boneMealOptions(Minecraft client, String value) {
      String options = this.resolveRuntimeText(value).trim().toLowerCase(Locale.ROOT);
      boolean refill = options.isBlank() || options.equals("true") || options.contains("refill") || options.contains("container") || options.contains("chest");
      if (options.equals("false") || options.equals("off") || options.contains("no refill") || options.contains("norefill")) {
         refill = false;
      }

      int radius = optionInt(options, "radius=", 8);
      radius = optionInt(options, "r=", radius);
      if (radius >= 1 && radius <= 16) {
         BlockPos target = null;
         int targetIndex = options.indexOf("target=");
         if (targetIndex >= 0) {
            target = this.parseResolvedFirstBlockPos(client, options.substring(targetIndex + "target=".length()));
         } else {
            target = this.parseResolvedBlockPos(client, value);
         }

         return new MacroRunner.BoneMealOptions(refill, radius, target);
      } else {
         return null;
      }
   }

   private BlockHitResult boneMealTargetHit(Minecraft client, MacroRunner.BoneMealOptions options) {
      if (options.target() != null) {
         BlockPos pos = options.target();
         return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      } else {
         return this.targetBlockHit(client, "");
      }
   }

   private MacroRunner.MineAreaOptions mineAreaOptions(Minecraft client, MacroModel.Node node) {
      BlockPos from = this.parseResolvedBlockPos(client, node.value);
      BlockPos to = this.parseResolvedFirstBlockPos(client, node.value2);
      if (from != null && to != null) {
         String options = this.resolveRuntimeText(node.value2).trim().toLowerCase(Locale.ROOT);
         BlockPos min = minPos(from, to);
         BlockPos max = maxPos(from, to);
         if (!areaWithinLimit(min, max, 16)) {
            return null;
         } else {
             int toolLowThreshold = mineToolLowThreshold(options);
             boolean move = moveOption(options, true);
             boolean autoTool = mineAreaAutoTool(options);
             String stopBeforeBlockText = this.resolveRuntimeText(node.value3).trim();
             String stopBeforeBlock = stopBeforeBlockText.isBlank() ? "" : MacroModel.normalizeItemId(stopBeforeBlockText);
             int sliceHeight = clampMineAreaSliceHeight(node.value4);
             boolean safePath = node.value5 == null || node.value5.isBlank() || optionBoolean(node.value5, true);
             if (!stopBeforeBlock.isBlank() && !isKnownBlockId(stopBeforeBlock)) {
                return null;
             }

             return new MacroRunner.MineAreaOptions(min, max, toolLowThreshold, move, autoTool, stopBeforeBlock, sliceHeight, safePath);
          }
       } else {
         return null;
      }
   }

   private String mineAreaOptionsError(Minecraft client, MacroModel.Node node) {
      BlockPos from = this.parseResolvedBlockPos(client, node.value);
      BlockPos to = this.parseResolvedFirstBlockPos(client, node.value2);
      if (from == null && to == null) {
         return tr("macro_builder.runner.failed_mine_area_needs_from_and_to_coordinates_examples_10_64_10");
      } else if (from == null) {
         return tr("macro_builder.runner.failed_mine_area_needs_from_x_y_z_in_the_first_field");
      } else if (to == null) {
         return tr("macro_builder.runner.failed_mine_area_needs_to_x_y_z_in_the_second_field");
       } else if (node.value3 != null && !node.value3.isBlank() && !isKnownBlockId(this.resolveRuntimeText(node.value3))) {
          return tr("macro_builder.runner.failed_mine_area_stop_before_block_needs_a_valid_block_id_such");
      } else {
         BlockPos min = minPos(from, to);
         BlockPos max = maxPos(from, to);
         if (!areaWithinLimit(min, max, 16)) {
            return tr("macro_builder.runner.failed_mine_area_is_max_is_16x16x16_split_bigger_areas_into_smaller", areaSizeText(min, max));
         } else {
            return tr("macro_builder.runner.failed_mine_area_settings_are_invalid");
         }
      }
   }

   private static String areaSizeText(BlockPos min, BlockPos max) {
      return max.getX() - min.getX() + 1 + "x" + (max.getY() - min.getY() + 1) + "x" + (max.getZ() - min.getZ() + 1);
   }

   private static String blockPosText(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   private static String coordinateText(double value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private BlockPos nextMineAreaBlock(Minecraft client, MacroRunner.MineAreaOptions options) {
      for (int sliceTop = options.max().getY(); sliceTop >= options.min().getY(); sliceTop -= options.sliceHeight()) {
         int sliceBottom = Math.max(options.min().getY(), sliceTop - options.sliceHeight() + 1);
         BlockPos nearest = this.nearestMineAreaBlockInSlice(client, options, sliceBottom, sliceTop, false);
         if (nearest != null) {
            return nearest;
         }

         nearest = this.nearestMineAreaBlockInSlice(client, options, sliceBottom, sliceTop, true);
         if (nearest != null) {
            return nearest;
         }
      }

      return null;
   }

   private BlockPos nearestMineAreaBlockInSlice(Minecraft client, MacroRunner.MineAreaOptions options, int sliceBottom, int sliceTop, boolean allowPlayerSupport) {
      BlockPos playerSupport = client.player.blockPosition().below();
      int playerFeetY = client.player.blockPosition().getY();
      BlockPos nearest = null;
      double nearestScore = Double.MAX_VALUE;
      BlockPos sliceMin = new BlockPos(options.min().getX(), sliceBottom, options.min().getZ());
      BlockPos sliceMax = new BlockPos(options.max().getX(), sliceTop, options.max().getZ());

      for (BlockPos mutable : BlockPos.betweenClosed(sliceMin, sliceMax)) {
         BlockPos pos = mutable.immutable();
         if (!client.level.isLoaded(pos)
            || this.activeMineAreaSkippedBlocks.contains(pos)
            || !isMineAreaBreakTarget(client.level.getBlockState(pos))
            || !allowPlayerSupport && pos.equals(playerSupport)) {
            continue;
         }

         double score = client.player.distanceToSqr(Vec3.atCenterOf(pos));
         if (pos.getY() < playerFeetY) {
            score += 64.0;
         }

         if (options.safePath() && !canReachBlock(client, pos) && this.mineAreaSafeStepToward(client, pos, options.sliceHeight()) == null) {
            score += 1000000.0;
         }

         if (score < nearestScore) {
            nearestScore = score;
            nearest = pos;
         }
      }

      return nearest;
   }

   private BlockPos mineAreaSafeStepToward(Minecraft client, BlockPos target, int maxDrop) {
      BlockPos start = client.player.blockPosition();
      List<BlockPos> queue = new ArrayList<>();
      Map<BlockPos, BlockPos> previous = new HashMap<>();
      queue.add(start);
      previous.put(start, null);
      BlockPos best = start;
      double bestDistance = start.distSqr(target);

      for (int index = 0; index < queue.size() && queue.size() < 512; index++) {
         BlockPos current = queue.get(index);
         for (int stepX = -1; stepX <= 1; stepX++) {
            for (int stepZ = -1; stepZ <= 1; stepZ++) {
               if (stepX == 0 && stepZ == 0) {
                  continue;
               }

               BlockPos nextFeet = current.offset(stepX, 0, stepZ);
               if (previous.containsKey(nextFeet)
                  || Math.abs(nextFeet.getX() - start.getX()) > 16
                  || Math.abs(nextFeet.getZ() - start.getZ()) > 16
                  || !this.mineAreaStepIsSafe(client, nextFeet, maxDrop)) {
                  continue;
               }

               previous.put(nextFeet, current);
               queue.add(nextFeet);
               double distance = nextFeet.distSqr(target);
               if (distance < bestDistance) {
                  bestDistance = distance;
                  best = nextFeet;
               }
            }
         }
      }

      if (best.equals(start)) {
         return null;
      }

      BlockPos step = best;
      while (previous.get(step) != null && !previous.get(step).equals(start)) {
         step = previous.get(step);
      }

      return step;
   }

   private boolean mineAreaStepIsSafe(Minecraft client, BlockPos feetPos, int maxDrop) {
      return client.level.isLoaded(feetPos)
         && this.mineAreaWalkSpaceIsClear(client, feetPos)
         && this.mineAreaWalkSpaceIsClear(client, feetPos.above())
         && this.mineAreaHasSafeSupport(client, feetPos, maxDrop);
   }

   private BlockPos mineAreaDescentStep(Minecraft client, MacroRunner.MineAreaOptions options) {
      BlockPos start = client.player.blockPosition();
      BlockPos best = null;
      int bestScore = Integer.MAX_VALUE;

      for (int stepX = -1; stepX <= 1; stepX++) {
         for (int stepZ = -1; stepZ <= 1; stepZ++) {
            if (stepX == 0 && stepZ == 0) {
               continue;
            }

            BlockPos nextFeet = start.offset(stepX, 0, stepZ);
            if (!client.level.isLoaded(nextFeet)
               || !this.mineAreaWalkSpaceIsClear(client, nextFeet)
               || !this.mineAreaWalkSpaceIsClear(client, nextFeet.above())) {
               continue;
            }

            int supportDepth = this.mineAreaSupportDepth(client, nextFeet, options.sliceHeight());
            if (supportDepth <= 1) {
               continue;
            }

            int desiredDepth = options.sliceHeight() + 1;
            int score = Math.abs(supportDepth - desiredDepth) * 100;
            if (stepX != 0 && stepZ != 0) {
               score += 10;
            }

            if (nextFeet.getX() < options.min().getX()
               || nextFeet.getX() > options.max().getX()
               || nextFeet.getZ() < options.min().getZ()
               || nextFeet.getZ() > options.max().getZ()) {
               score += 50;
            }

            if (score < bestScore) {
               bestScore = score;
               best = nextFeet;
            }
         }
      }

      return best;
   }

   private boolean mineAreaWalkSpaceIsClear(Minecraft client, BlockPos pos) {
      BlockState state = client.level.getBlockState(pos);
      return !state.is(Blocks.WATER)
         && !state.is(Blocks.LAVA)
         && !state.is(Blocks.BUBBLE_COLUMN)
         && state.getCollisionShape(client.level, pos).isEmpty();
   }

   private boolean mineAreaHasSafeSupport(Minecraft client, BlockPos feetPos, int maxDrop) {
      return this.mineAreaSupportDepth(client, feetPos, maxDrop) > 0;
   }

   private int mineAreaSupportDepth(Minecraft client, BlockPos feetPos, int maxDrop) {
      for (int depth = 1; depth <= maxDrop + 1; depth++) {
         BlockState support = client.level.getBlockState(feetPos.below(depth));
         if (isMineAreaBreakTarget(support)) {
            return depth;
         }
      }

      return -1;
   }

   private static int clampMineAreaSliceHeight(String value) {
      return Math.max(1, Math.min(8, parseInt(value == null ? "" : value.trim(), 3)));
   }

   private static boolean optionBoolean(String value, boolean fallback) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return fallback;
      }

      return switch (normalized) {
         case "true", "on", "yes", "1" -> true;
         case "false", "off", "no", "0" -> false;
         default -> fallback;
      };
   }

   private static boolean isMineAreaBreakTarget(BlockState state) {
      return !state.isAir() && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA) && !state.is(Blocks.BUBBLE_COLUMN);
   }

   private static int mineToolLowThreshold(String options) {
      int toolLowThreshold = 10;

      for (String token : options.split("[,\\s]+")) {
         String normalized = token.trim();
         if (normalized.startsWith("tool=")) {
            toolLowThreshold = Math.max(0, parseInt(normalized.substring("tool=".length()), toolLowThreshold));
         } else if (normalized.startsWith("low=")) {
            toolLowThreshold = Math.max(0, parseInt(normalized.substring("low=".length()), toolLowThreshold));
         } else if (normalized.startsWith("durability=")) {
            toolLowThreshold = Math.max(0, parseInt(normalized.substring("durability=".length()), toolLowThreshold));
         }
      }

      return toolLowThreshold;
   }

   private static boolean mineAreaAutoTool(String options) {
      boolean autoTool = false;

      for (String token : options.split("[,\\s]+")) {
         String normalized = token.trim().toLowerCase(Locale.ROOT).replace('-', '_');
         if (normalized.equals("auto_tool") || normalized.equals("autotool") || normalized.equals("tool_auto") || normalized.equals("select_tool") || normalized.equals("smart_tool")) {
            autoTool = true;
         } else if (normalized.startsWith("auto_tool=")) {
            autoTool = truthyWithDefault(normalized.substring("auto_tool=".length()), autoTool);
         } else if (normalized.startsWith("autotool=")) {
            autoTool = truthyWithDefault(normalized.substring("autotool=".length()), autoTool);
         } else if (normalized.startsWith("tool_auto=")) {
            autoTool = truthyWithDefault(normalized.substring("tool_auto=".length()), autoTool);
         } else if (normalized.startsWith("select_tool=")) {
            autoTool = truthyWithDefault(normalized.substring("select_tool=".length()), autoTool);
         } else if (normalized.startsWith("smart_tool=")) {
            autoTool = truthyWithDefault(normalized.substring("smart_tool=".length()), autoTool);
         }
      }

      return autoTool;
   }

   private static boolean toolIsLow(Minecraft client, int threshold) {
      if (threshold <= 0) {
         return false;
      } else {
         ItemStack stack = client.player.getMainHandItem();
         return !stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= threshold;
      }
   }

   private boolean selectBestHotbarTool(Minecraft client, BlockState state) {
      if (client.player == null || state == null || state.isAir()) {
         return false;
      }

      int selectedSlot = client.player.getInventory().getSelectedSlot();
      double selectedScore = selectedSlot >= 0 && selectedSlot < client.player.getInventory().getContainerSize()
         ? this.toolScore(client.player.getInventory().getItem(selectedSlot), state)
         : 1.0;
      int bestSlot = selectedSlot;
      double bestScore = selectedScore;

      for (int slot = 0; slot < 9 && slot < client.player.getInventory().getContainerSize(); slot++) {
         ItemStack stack = client.player.getInventory().getItem(slot);
         double score = this.toolScore(stack, state);
         if (score > bestScore + 0.0001) {
            bestScore = score;
            bestSlot = slot;
         }
      }

      if (bestSlot >= 0 && bestSlot != selectedSlot && bestScore > selectedScore + 0.0001 && bestScore > 1.0001) {
         client.player.getInventory().setSelectedSlot(bestSlot);
         return true;
      }

      return false;
   }

   private double toolScore(ItemStack stack, BlockState state) {
      if (stack == null || stack.isEmpty()) {
         return 1.0;
      }

      double score = stack.getDestroySpeed(state);
      if (stack.isCorrectToolForDrops(state)) {
         score += 1000.0;
      }

      if (stack.isDamageableItem()) {
         score += Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) / 100000.0;
      }

      return score;
   }

   private static boolean canReachBlock(Minecraft client, BlockPos pos) {
      return client.player.distanceToSqr(Vec3.atCenterOf(pos)) <= 25.0;
   }

   private Direction mineAreaHitDirection(Minecraft client, BlockPos pos) {
      Vec3 center = Vec3.atCenterOf(pos);
      return this.nearestBlockFace(client.player.getX() - center.x, client.player.getEyeY() - center.y, client.player.getZ() - center.z);
   }

   private boolean canReachFarmTarget(Minecraft client, MacroModel.Node node, MacroRunner.FarmOptions options, BlockPos target) {
      if (canReachBlock(client, target)) {
         this.releaseMovementKeys(client);
         return true;
      } else if (!options.move()) {
         this.fail(client, node, tr("macro_builder.runner.failed_farm_area_target_is_out_of_reach"));
         return false;
      } else {
         this.moveTowardBlock(client, target);
         if (this.activeTicks > 300) {
            this.releaseMovementKeys(client);
            this.fail(client, node, tr("macro_builder.runner.failed_farm_area_could_not_reach_the_crop"));
         }

         return false;
      }
   }

   private boolean moveToUnloadedArea(Minecraft client, MacroModel.Node node, MacroRunner.FarmOptions options) {
      if (options.radius() > 0 || this.areaHasLoadedPosition(client, options.min(), options.max())) {
         return false;
      } else if (!options.move()) {
         this.fail(client, node, tr("macro_builder.runner.failed_farm_area_is_not_loaded_and_move_is_false"));
         return true;
      } else {
         this.moveTowardBlock(client, areaCenter(options.min(), options.max()));
         if (this.activeTicks > 300) {
            this.releaseMovementKeys(client);
            this.fail(client, node, tr("macro_builder.runner.failed_farm_area_could_not_reach_the_area"));
         }

         return true;
      }
   }

   private boolean moveToUnloadedArea(Minecraft client, MacroModel.Node node, MacroRunner.MineAreaOptions options) {
      if (this.areaHasLoadedPosition(client, options.min(), options.max())) {
         return false;
      } else if (!options.move()) {
         this.fail(client, node, tr("macro_builder.runner.failed_mine_area_is_not_loaded_and_move_is_false"));
         return true;
      } else {
         this.moveTowardBlock(client, areaCenter(options.min(), options.max()));
         this.activeMineAreaTravelTicks++;
         if (this.activeMineAreaTravelTicks > MINE_AREA_MOVE_TIMEOUT_TICKS) {
            this.releaseMovementKeys(client);
            this.fail(client, node, tr("macro_builder.runner.failed_mine_area_could_not_reach_the_area"));
         } else {
            this.lastStatus = Component.translatable("macro_builder.runner.mine_area_moving_to_load_the_area");
         }

         return true;
      }
   }

   private void moveTowardBlock(Minecraft client, BlockPos pos) {
      this.lookAt(client, Vec3.atCenterOf(pos));
      this.setMacroKeyDown(client, client.options.keyUp, true);
   }

   private boolean areaHasLoadedPosition(Minecraft client, BlockPos min, BlockPos max) {
      for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
         if (client.level.isLoaded(mutable)) {
            return true;
         }
      }

      return false;
   }

   private static BlockPos areaCenter(BlockPos min, BlockPos max) {
      return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
   }

   private static BlockPos minPos(BlockPos first, BlockPos second) {
      return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
   }

   private static BlockPos maxPos(BlockPos first, BlockPos second) {
      return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
   }

   private static boolean areaWithinLimit(BlockPos min, BlockPos max, int maxBlocksPerSide) {
      return max.getX() - min.getX() < maxBlocksPerSide && max.getY() - min.getY() < maxBlocksPerSide && max.getZ() - min.getZ() < maxBlocksPerSide;
   }

   private void openBlockAt(Minecraft client, BlockPos pos) {
      if (client.gameMode != null) {
         this.lookAt(client, Vec3.atCenterOf(pos));
         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
         client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
         client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
      }
   }

   private BlockPos nearestMatureCrop(Minecraft client, int radius) {
      return this.nearestMatureCrop(client, client.player.blockPosition().offset(-radius, -1, -radius), client.player.blockPosition().offset(radius, 1, radius));
   }

   private BlockPos nearestMatureCrop(Minecraft client, MacroRunner.FarmOptions options) {
      return options.radius() > 0 ? this.nearestMatureCrop(client, options.radius()) : this.nearestMatureCrop(client, options.min(), options.max());
   }

   private BlockPos nearestMatureCrop(Minecraft client, BlockPos min, BlockPos max) {
      BlockPos nearest = null;
      double nearestDistance = Double.MAX_VALUE;

      for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
         BlockPos pos = mutable.immutable();
         if (client.level.isLoaded(pos) && isMatureFarmCrop(client.level.getBlockState(pos))) {
            double distance = client.player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < nearestDistance) {
               nearestDistance = distance;
               nearest = pos;
            }
         }
      }

      return nearest;
   }

   private static boolean isSupportedFarmCrop(BlockState state) {
      return !seedForFarmCrop(state).isBlank();
   }

   private static boolean isMatureFarmCrop(BlockState state) {
      String id = blockId(state);
      Integer age = ageValue(state);
      if (age == null) {
         return false;
      } else {
         return switch (id) {
            case "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes" -> age >= 7;
            case "minecraft:beetroots", "minecraft:nether_wart" -> age >= 3;
            default -> false;
         };
      }
   }

   private static String seedForFarmCrop(BlockState state) {
      String var1 = blockId(state);

      return switch (var1) {
         case "minecraft:wheat" -> "minecraft:wheat_seeds";
         case "minecraft:carrots" -> "minecraft:carrot";
         case "minecraft:potatoes" -> "minecraft:potato";
         case "minecraft:beetroots" -> "minecraft:beetroot_seeds";
         case "minecraft:nether_wart" -> "minecraft:nether_wart";
         default -> "";
      };
   }

   private static Integer ageValue(BlockState state) {
      for (Property<?> property : state.getProperties()) {
         if (property instanceof IntegerProperty ageProperty && "age".equals(property.getName())) {
            return (Integer)state.getValue(ageProperty);
         }
      }

      return null;
   }

   private BlockPos nearestContainer(Minecraft client, int radius) {
      return this.nearestContainer(client, radius, "any");
   }

   private BlockPos nearestContainer(Minecraft client, int radius, String type) {
      BlockPos center = client.player.blockPosition();
      BlockPos nearest = null;
      double nearestDistance = Double.MAX_VALUE;

      for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
         BlockPos pos = mutable.immutable();
         if (client.level.isLoaded(pos) && isContainerBlock(client.level.getBlockState(pos), type)) {
            double distance = client.player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < nearestDistance) {
               nearestDistance = distance;
               nearest = pos;
            }
         }
      }

      return nearest;
   }

   private BlockPos nearestBlockWithId(Minecraft client, int radius, String targetBlockId) {
      BlockPos center = client.player.blockPosition();
      BlockPos nearest = null;
      double nearestDistance = Double.MAX_VALUE;

      for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
         BlockPos pos = mutable.immutable();
         if (client.level.isLoaded(pos) && blockId(client.level.getBlockState(pos)).equals(targetBlockId)) {
            double distance = client.player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < nearestDistance) {
               nearestDistance = distance;
               nearest = pos;
            }
         }
      }

      return nearest;
   }

   private static boolean isContainerBlock(BlockState state) {
      return isContainerBlock(state, "any");
   }

   private static boolean isContainerBlock(BlockState state, String type) {
      String id = blockId(state);
      String normalizedType = type == null ? "any" : type;
      boolean chest = id.equals("minecraft:chest") || id.equals("minecraft:trapped_chest");
      boolean barrel = id.equals("minecraft:barrel");
      boolean enderChest = id.equals("minecraft:ender_chest");
      boolean shulker = id.endsWith("_shulker_box");
      boolean furnace = id.equals("minecraft:furnace");
      boolean blastFurnace = id.equals("minecraft:blast_furnace");
      boolean smoker = id.equals("minecraft:smoker");
      boolean brewingStand = id.equals("minecraft:brewing_stand");
      boolean hopper = id.equals("minecraft:hopper");
      boolean dispenser = id.equals("minecraft:dispenser");
      boolean dropper = id.equals("minecraft:dropper");

      return switch (normalizedType) {
         case "chest" -> chest;
         case "barrel" -> barrel;
         case "ender_chest" -> enderChest;
         case "shulker" -> shulker;
         case "furnace" -> furnace;
         case "blast_furnace" -> blastFurnace;
         case "smoker" -> smoker;
         case "brewing_stand" -> brewingStand;
         case "hopper" -> hopper;
         case "dispenser" -> dispenser;
         case "dropper" -> dropper;
         default -> chest || barrel || enderChest || shulker || furnace || blastFurnace || smoker || brewingStand || hopper || dispenser || dropper;
      };
   }

   private static String blockId(BlockState state) {
      return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
   }

   private MacroRunner.DepositResult depositAnyInventoryStack(Minecraft client) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      if (handler != null && client.gameMode != null) {
         int firstPlayerSlot = firstPlayerInventorySlot(handler);
         boolean hadStack = false;

         for (int index = firstPlayerSlot; index < handler.slots.size(); index++) {
            Slot slot = (Slot)handler.slots.get(index);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
               hadStack = true;
               if (this.containerCanAccept(handler, firstPlayerSlot, stack)) {
                  client.gameMode.handleContainerInput(handler.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, client.player);
                  return MacroRunner.DepositResult.MOVED;
               }
            }
         }

         return hadStack ? MacroRunner.DepositResult.NO_SPACE : MacroRunner.DepositResult.DONE;
      } else {
         return MacroRunner.DepositResult.NO_CONTAINER;
      }
   }

   private MacroRunner.DepositResult depositNextInventoryStack(Minecraft client, MacroModel.Node node) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      if (handler != null && client.gameMode != null) {
         int firstPlayerSlot = firstPlayerInventorySlot(handler);
         boolean depositAll = !"specific".equalsIgnoreCase(this.resolveRuntimeText(node.value).trim());
         String specificItem = MacroModel.normalizeItemId(this.resolveRuntimeText(node.value2));
         boolean shiftClick = depositWithdrawShiftClick(node);
         boolean fastDeposit = fastDepositEnabled(node);
         Set<Integer> excludedSlots = parseInventorySlotSet(this.resolveRuntimeText(node.value5));
         if (!depositAll && !isKnownItemId(specificItem)) {
            return MacroRunner.DepositResult.INVALID_ITEM;
         } else if (excludedSlots == null) {
            return MacroRunner.DepositResult.INVALID_SLOTS;
         } else {
            boolean moved = false;
            boolean blocked = false;

            for (int index = firstPlayerSlot; index < handler.slots.size(); index++) {
               int playerSlotNumber = playerInventorySlotNumber(handler, index);
               if (excludedSlots.contains(playerSlotNumber)) {
                  continue;
               }

               Slot slot = (Slot)handler.slots.get(index);
               ItemStack stack = slot.getItem();
               if (!stack.isEmpty() && (depositAll || stackMatches(stack, specificItem))) {
                  if (!this.containerCanAccept(handler, firstPlayerSlot, stack)) {
                     blocked = true;
                     continue;
                  }

                  if (shiftClick) {
                     client.gameMode.handleContainerInput(handler.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, client.player);
                  } else if (!this.pickupMoveStack(client, handler, index, 0, firstPlayerSlot, stack)) {
                     blocked = true;
                     continue;
                  }

                  moved = true;
                  if (!fastDeposit) {
                     return MacroRunner.DepositResult.MOVED;
                  }
               }
            }

            if (moved) {
               return MacroRunner.DepositResult.MOVED;
            } else {
               return blocked ? MacroRunner.DepositResult.NO_SPACE : MacroRunner.DepositResult.DONE;
            }
         }
      } else {
         return MacroRunner.DepositResult.NO_CONTAINER;
      }
   }

   private MacroRunner.DepositResult withdrawNextContainerStack(Minecraft client, MacroModel.Node node) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      if (handler != null && client.gameMode != null) {
         int firstPlayerSlot = firstPlayerInventorySlot(handler);
         boolean withdrawAll = !"specific".equalsIgnoreCase(this.resolveRuntimeText(node.value).trim());
         String specificItem = MacroModel.normalizeItemId(this.resolveRuntimeText(node.value2));
         boolean shiftClick = depositWithdrawShiftClick(node);
         if (!withdrawAll && !isKnownItemId(specificItem)) {
            return MacroRunner.DepositResult.INVALID_ITEM;
         } else {
            for (int index = 0; index < firstPlayerSlot; index++) {
               Slot slot = (Slot)handler.slots.get(index);
               ItemStack stack = slot.getItem();
               if (!stack.isEmpty() && (withdrawAll || stackMatches(stack, specificItem)) && this.playerInventoryCanAccept(handler, firstPlayerSlot, stack)) {
                  if (shiftClick) {
                     client.gameMode.handleContainerInput(handler.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, client.player);
                  } else if (!this.pickupMoveStack(client, handler, index, firstPlayerSlot, handler.slots.size(), stack)) {
                     return MacroRunner.DepositResult.NO_SPACE;
                  }

                  return MacroRunner.DepositResult.MOVED;
               }
            }

            return MacroRunner.DepositResult.DONE;
         }
      } else {
         return MacroRunner.DepositResult.NO_CONTAINER;
      }
   }

   private MacroRunner.DepositResult withdrawSpecificFromOpenContainer(Minecraft client, String itemId) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      if (handler != null && client.gameMode != null) {
         int firstPlayerSlot = firstPlayerInventorySlot(handler);

         for (int index = 0; index < firstPlayerSlot; index++) {
            Slot slot = (Slot)handler.slots.get(index);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stackMatches(stack, itemId)) {
               if (!this.playerInventoryCanAccept(handler, firstPlayerSlot, stack)) {
                  return MacroRunner.DepositResult.NO_SPACE;
               }

               client.gameMode.handleContainerInput(handler.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, client.player);
               return MacroRunner.DepositResult.MOVED;
            }
         }

         return MacroRunner.DepositResult.DONE;
      } else {
         return MacroRunner.DepositResult.NO_CONTAINER;
      }
   }

   private static boolean depositWithdrawShiftClick(MacroModel.Node node) {
      return node == null || !"false".equalsIgnoreCase(node.value3 == null ? "" : node.value3.trim());
   }

   private static boolean fastDepositEnabled(MacroModel.Node node) {
      return Boolean.parseBoolean(node == null || node.value4 == null ? "" : node.value4.trim());
   }

   private static boolean containerSlotHasItem(AbstractContainerMenu handler, int slotIndex) {
      return handler != null && slotIndex >= 0 && slotIndex < handler.slots.size() && !handler.getSlot(slotIndex).getItem().isEmpty();
   }

   private static boolean containerSlotHasAtLeast(AbstractContainerMenu handler, int slotIndex, int count) {
      return handler != null
         && slotIndex >= 0
         && slotIndex < handler.slots.size()
         && !handler.getSlot(slotIndex).getItem().isEmpty()
         && handler.getSlot(slotIndex).getItem().getCount() >= count;
   }

   private boolean moveEnchantingInputToTable(Minecraft client, AbstractContainerMenu handler, String selector) {
      int sourceSlotIndex = this.firstEnchantingInputMenuSlot(client, handler, selector);
      return sourceSlotIndex >= 0 && this.pickupMoveStack(client, handler, sourceSlotIndex, 0, 1, handler.getSlot(sourceSlotIndex).getItem());
   }

   private boolean moveGrindstoneInputToSlot(
      Minecraft client, AbstractContainerMenu handler, String selector, int targetSlotIndex, ItemStack matchingStack, int ignoredSourceSlotIndex
   ) {
      int sourceSlotIndex = this.firstGrindstoneInputMenuSlot(client, handler, selector, matchingStack, targetSlotIndex, ignoredSourceSlotIndex);
      return sourceSlotIndex >= 0
         && this.pickupMoveStack(client, handler, sourceSlotIndex, targetSlotIndex, targetSlotIndex + 1, handler.getSlot(sourceSlotIndex).getItem());
   }

   private boolean quickMoveItemToMenuSlot(Minecraft client, AbstractContainerMenu handler, String itemId, int targetSlotIndex) {
      if (client.gameMode == null || client.player == null || handler == null || targetSlotIndex < 0 || targetSlotIndex >= handler.slots.size() || !isKnownItemId(itemId)) {
         return false;
      }

      Slot targetSlot = handler.getSlot(targetSlotIndex);
      int sourceSlotIndex = this.firstMatchingPlayerMenuSlot(handler, itemId);
      if (sourceSlotIndex < 0) {
         return false;
      }

      ItemStack stack = handler.getSlot(sourceSlotIndex).getItem();
      if (stack.isEmpty() || !targetSlot.mayPlace(stack)) {
         return false;
      }

      client.gameMode.handleContainerInput(handler.containerId, sourceSlotIndex, 0, ContainerInput.QUICK_MOVE, client.player);
      return true;
   }

   private int firstEnchantingInputMenuSlot(Minecraft client, AbstractContainerMenu handler, String selector) {
      if (client.player == null || handler.slots.isEmpty()) {
         return -1;
      } else {
         String normalizedSelector = selector == null ? "" : selector.trim().toLowerCase(Locale.ROOT);
         boolean anyItem = normalizedSelector.isBlank() || normalizedSelector.equals("any") || normalizedSelector.equals("all") || normalizedSelector.equals("auto");
         boolean heldItem = normalizedSelector.equals("held") || normalizedSelector.equals("hand") || normalizedSelector.equals("mainhand") || normalizedSelector.equals("main_hand");
         String itemId = anyItem || heldItem ? "" : MacroModel.normalizeItemId(normalizedSelector);
         if (!itemId.isBlank() && !isKnownItemId(itemId)) {
            return -1;
         } else {
            ItemStack heldStack = client.player.getMainHandItem();
            Slot targetSlot = handler.getSlot(0);
            int firstPlayerSlot = firstPlayerInventorySlot(handler);

            for (int index = firstPlayerSlot; index < handler.slots.size(); index++) {
               ItemStack stack = handler.getSlot(index).getItem();
               if (this.isFreshEnchantingInput(client, handler, stack, selector, false)) {
                  if (anyItem || heldItem && !heldStack.isEmpty() && stack.getItem() == heldStack.getItem() || !itemId.isBlank() && stackMatches(stack, itemId)) {
                     return index;
                  }
               }
            }

            return -1;
         }
      }
   }

   private boolean isFreshEnchantingInput(Minecraft client, AbstractContainerMenu handler, ItemStack stack, String selector, boolean allowHeldCursorFallback) {
      return stack != null
         && !stack.isEmpty()
         && handler != null
         && handler.getSlot(0).mayPlace(stack)
         && !isAlreadyEnchantedForAutoEnchant(stack)
         && this.enchantingInputMatchesSelector(client, stack, selector, allowHeldCursorFallback);
   }

   private boolean enchantingInputMatchesSelector(Minecraft client, ItemStack stack, String selector, boolean allowHeldCursorFallback) {
      String normalizedSelector = selector == null ? "" : selector.trim().toLowerCase(Locale.ROOT);
      boolean anyItem = normalizedSelector.isBlank() || normalizedSelector.equals("any") || normalizedSelector.equals("all") || normalizedSelector.equals("auto");
      if (anyItem) {
         return true;
      }

      boolean heldItem = normalizedSelector.equals("held")
         || normalizedSelector.equals("hand")
         || normalizedSelector.equals("mainhand")
         || normalizedSelector.equals("main_hand");
      if (heldItem) {
         ItemStack heldStack = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem();
         return heldStack.isEmpty() ? allowHeldCursorFallback : stack.getItem() == heldStack.getItem();
      }

      String itemId = MacroModel.normalizeItemId(normalizedSelector);
      return isKnownItemId(itemId) && stackMatches(stack, itemId);
   }

   private static boolean isAlreadyEnchantedForAutoEnchant(ItemStack stack) {
      return stack != null && !stack.isEmpty() && (stack.isEnchanted() || stackMatches(stack, "minecraft:enchanted_book"));
   }

   private int firstGrindstoneInputMenuSlot(
      Minecraft client, AbstractContainerMenu handler, String selector, ItemStack matchingStack, int targetSlotIndex, int ignoredSourceSlotIndex
   ) {
      if (client.player == null || handler.slots.isEmpty() || targetSlotIndex < 0 || targetSlotIndex >= handler.slots.size()) {
         return -1;
      } else {
         String normalizedSelector = selector == null ? "" : selector.trim().toLowerCase(Locale.ROOT);
         boolean anyItem = normalizedSelector.isBlank() || normalizedSelector.equals("any") || normalizedSelector.equals("all") || normalizedSelector.equals("auto");
         boolean heldItem = normalizedSelector.equals("held")
            || normalizedSelector.equals("hand")
            || normalizedSelector.equals("mainhand")
            || normalizedSelector.equals("main_hand")
            || normalizedSelector.equals("selected");
         boolean sameItem = normalizedSelector.equals("same")
            || normalizedSelector.equals("match")
            || normalizedSelector.equals("matching")
            || normalizedSelector.equals("input");
         String itemId = anyItem || heldItem || sameItem ? "" : MacroModel.normalizeItemId(normalizedSelector);
         if (!itemId.isBlank() && !isKnownItemId(itemId)) {
            return -1;
         } else {
            ItemStack heldStack = client.player.getMainHandItem();
            Slot targetSlot = handler.getSlot(targetSlotIndex);
            int firstPlayerSlot = firstPlayerInventorySlot(handler);

            for (int index = firstPlayerSlot; index < handler.slots.size(); index++) {
               if (index == ignoredSourceSlotIndex) {
                  continue;
               }

               ItemStack stack = handler.getSlot(index).getItem();
               if (!stack.isEmpty() && targetSlot.mayPlace(stack)) {
                  boolean matchesAny = anyItem;
                  boolean matchesHeld = heldItem && !heldStack.isEmpty() && ItemStack.isSameItemSameComponents(stack, heldStack);
                  boolean matchesSame = sameItem && matchingStack != null && !matchingStack.isEmpty() && ItemStack.isSameItemSameComponents(stack, matchingStack);
                  boolean matchesId = !itemId.isBlank() && stackMatches(stack, itemId);
                  if (matchesAny || matchesHeld || matchesSame || matchesId) {
                     return index;
                  }
               }
            }

            return -1;
         }
      }
   }

   private int firstMatchingPlayerMenuSlot(AbstractContainerMenu handler, String itemId) {
      if (!isKnownItemId(itemId)) {
         return -1;
      } else {
         int firstPlayerSlot = firstPlayerInventorySlot(handler);

         for (int index = firstPlayerSlot; index < handler.slots.size(); index++) {
            ItemStack stack = handler.getSlot(index).getItem();
            if (!stack.isEmpty() && stackMatches(stack, itemId)) {
               return index;
            }
         }

         return -1;
      }
   }

   private boolean pickupMoveStack(Minecraft client, AbstractContainerMenu handler, int sourceSlotIndex, int targetStart, int targetEnd, ItemStack stack) {
      int targetSlotIndex = this.firstAcceptingSlotIndex(handler, targetStart, targetEnd, stack);
      if (targetSlotIndex < 0) {
         return false;
      } else {
         client.gameMode.handleContainerInput(handler.containerId, sourceSlotIndex, 0, ContainerInput.PICKUP, client.player);
         client.gameMode.handleContainerInput(handler.containerId, targetSlotIndex, 0, ContainerInput.PICKUP, client.player);
         if (!handler.getCarried().isEmpty()) {
            client.gameMode.handleContainerInput(handler.containerId, sourceSlotIndex, 0, ContainerInput.PICKUP, client.player);
         }

         return true;
      }
   }

   private boolean moveMenuSlotToPlayerInventory(Minecraft client, AbstractContainerMenu handler, int sourceSlotIndex) {
      if (client.gameMode == null || client.player == null || handler == null || sourceSlotIndex < 0 || sourceSlotIndex >= handler.slots.size()) {
         return false;
      }

      ItemStack stack = handler.getSlot(sourceSlotIndex).getItem();
      return !stack.isEmpty() && this.pickupMoveStack(client, handler, sourceSlotIndex, firstPlayerInventorySlot(handler), handler.slots.size(), stack);
   }

   private boolean moveCarriedToPlayerInventory(Minecraft client, AbstractContainerMenu handler) {
      return this.moveCarriedToPlayerInventorySlot(client, handler) >= 0;
   }

   private int moveCarriedToPlayerInventorySlot(Minecraft client, AbstractContainerMenu handler) {
      if (client.gameMode == null || client.player == null || handler == null) {
         return -1;
      }

      ItemStack carried = handler.getCarried();
      if (carried.isEmpty()) {
         return -1;
      }

      int targetSlotIndex = this.firstAcceptingSlotIndex(handler, firstPlayerInventorySlot(handler), handler.slots.size(), carried);
      if (targetSlotIndex < 0) {
         return -1;
      }

      client.gameMode.handleContainerInput(handler.containerId, targetSlotIndex, 0, ContainerInput.PICKUP, client.player);
      return targetSlotIndex;
   }

   private boolean moveCarriedToMenuSlot(Minecraft client, AbstractContainerMenu handler, int targetSlotIndex) {
      if (client.gameMode == null || client.player == null || handler == null || targetSlotIndex < 0 || targetSlotIndex >= handler.slots.size()) {
         return false;
      }

      ItemStack carried = handler.getCarried();
      if (carried.isEmpty()) {
         return true;
      }

      Slot targetSlot = handler.getSlot(targetSlotIndex);
      if (!targetSlot.mayPlace(carried)) {
         return false;
      }

      ItemStack existing = targetSlot.getItem();
      int maxCount = Math.min(carried.getMaxStackSize(), targetSlot.getMaxStackSize(carried));
      if (!existing.isEmpty() && (!ItemStack.isSameItemSameComponents(existing, carried) || existing.getCount() >= maxCount)) {
         return false;
      }

      client.gameMode.handleContainerInput(handler.containerId, targetSlotIndex, 0, ContainerInput.PICKUP, client.player);
      return true;
   }

   private int firstAcceptingSlotIndex(AbstractContainerMenu handler, int start, int end, ItemStack stack) {
      for (int index = Math.max(0, start); index < Math.min(end, handler.slots.size()); index++) {
         Slot slot = (Slot)handler.slots.get(index);
         if (slot.mayPlace(stack)) {
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) {
               return index;
            }

            int maxCount = Math.min(existing.getMaxStackSize(), slot.getMaxStackSize(existing));
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < maxCount) {
               return index;
            }
         }
      }

      return -1;
   }

   private boolean containerCanAccept(AbstractContainerMenu handler, int firstPlayerSlot, ItemStack stack) {
      for (int index = 0; index < firstPlayerSlot; index++) {
         Slot slot = (Slot)handler.slots.get(index);
         if (slot.mayPlace(stack)) {
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) {
               return true;
            }

            int maxCount = Math.min(existing.getMaxStackSize(), slot.getMaxStackSize(existing));
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < maxCount) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean playerInventoryCanAccept(AbstractContainerMenu handler, int firstPlayerSlot, ItemStack stack) {
      for (int index = firstPlayerSlot; index < handler.slots.size(); index++) {
         Slot slot = (Slot)handler.slots.get(index);
         if (slot.mayPlace(stack)) {
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) {
               return true;
            }

            int maxCount = Math.min(existing.getMaxStackSize(), slot.getMaxStackSize(existing));
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < maxCount) {
               return true;
            }
         }
      }

      return false;
   }

   private static int firstPlayerInventorySlot(AbstractContainerMenu handler) {
      return Math.max(0, handler.slots.size() - 36);
   }

   private static int playerInventorySlotNumber(AbstractContainerMenu handler, int menuIndex) {
      int firstPlayerSlot = firstPlayerInventorySlot(handler);
      int offset = menuIndex - firstPlayerSlot;
      if (offset < 0 || offset >= PLAYER_INVENTORY_SLOT_COUNT) {
         return -1;
      } else {
         return offset >= 27 ? offset - 27 : offset + 9;
      }
   }

   private static int playerInventoryMenuSlot(int inventorySlot) {
      return inventorySlot >= 0 && inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
   }

   private boolean selectInventorySlotIntoHotbar(Minecraft client, int inventorySlot) {
      if (client == null || client.player == null || inventorySlot < 0 || inventorySlot >= PLAYER_INVENTORY_SLOT_COUNT) {
         return false;
      }

      if (inventorySlot < 9) {
         client.player.getInventory().setSelectedSlot(inventorySlot);
         return true;
      }

      if (client.gameMode == null || client.player.inventoryMenu == null) {
         return false;
      }

      int hotbarSlot = this.firstEmptyHotbarSlot(client);
      if (hotbarSlot < 0) {
         hotbarSlot = this.selectedHotbarSlot(client);
      }

      if (hotbarSlot < 0 || hotbarSlot >= 9) {
         hotbarSlot = 0;
      }

      client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, playerInventoryMenuSlot(inventorySlot), hotbarSlot, ContainerInput.SWAP, client.player);
      client.player.getInventory().setSelectedSlot(hotbarSlot);
      return true;
   }

   private int firstEmptyHotbarSlot(Minecraft client) {
      if (client == null || client.player == null) {
         return -1;
      }

      int max = Math.min(9, client.player.getInventory().getContainerSize());
      for (int slot = 0; slot < max; slot++) {
         if (client.player.getInventory().getItem(slot).isEmpty()) {
            return slot;
         }
      }

      return -1;
   }

   private int selectedHotbarSlot(Minecraft client) {
      if (client == null || client.player == null) {
         return -1;
      }

      int selected = client.player.getInventory().getSelectedSlot();
      return selected >= 0 && selected < 9 ? selected : -1;
   }

   private boolean dropPlayerInventorySlot(Minecraft client, int inventorySlot, boolean dropStack) {
      if (client.gameMode == null || client.player == null || client.player.inventoryMenu == null) {
         return false;
      } else {
         client.gameMode
            .handleContainerInput(client.player.inventoryMenu.containerId, playerInventoryMenuSlot(inventorySlot), dropStack ? 1 : 0, ContainerInput.THROW, client.player);
         return true;
      }
   }

   private Integer firstMatchingPlayerInventorySlot(Minecraft client, String itemId, Set<Integer> excludedSlots) {
      int size = Math.min(PLAYER_INVENTORY_SLOT_COUNT, client.player.getInventory().getContainerSize());

      for (int slot = 0; slot < size; slot++) {
         if (!excludedSlots.contains(slot) && stackMatches(client.player.getInventory().getItem(slot), itemId)) {
            return slot;
         }
      }

      return null;
   }

   private int dropMatchingPlayerInventorySlots(Minecraft client, String itemId, boolean dropStack, Set<Integer> excludedSlots) {
      int dropped = 0;
      int size = Math.min(PLAYER_INVENTORY_SLOT_COUNT, client.player.getInventory().getContainerSize());

      for (int slot = 0; slot < size; slot++) {
         if (!excludedSlots.contains(slot) && stackMatches(client.player.getInventory().getItem(slot), itemId) && this.dropPlayerInventorySlot(client, slot, dropStack)) {
            dropped++;
         }
      }

      return dropped;
   }

   private static boolean dropAllMatchingDropItems(MacroModel.Node node) {
      return Boolean.parseBoolean(node == null || node.value4 == null ? "" : node.value4.trim());
   }

   private static Set<Integer> parseInventorySlotSet(String slotText) {
      Set<Integer> slots = new HashSet<>();
      String normalized = slotText == null ? "" : slotText.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return slots;
      } else {
         for (String rawToken : normalized.split("[,;\\s]+")) {
            String token = cleanInventorySlotToken(rawToken);
            if (token.isBlank()) {
               continue;
            }

            int dash = token.indexOf('-');
            if (dash >= 0) {
               Integer start = parseIntOrNull(token.substring(0, dash).trim());
               Integer end = parseIntOrNull(token.substring(dash + 1).trim());
               if (start == null || end == null || !addInventorySlotRange(slots, start, end)) {
                  return null;
               }
            } else {
               Integer slot = parseIntOrNull(token);
               if (slot == null || !addInventorySlot(slots, slot)) {
                  return null;
               }
            }
         }

         return slots;
      }
   }

   private static String cleanInventorySlotToken(String token) {
      String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
      if (normalized.equals("exclude") || normalized.equals("skip") || normalized.equals("slot") || normalized.equals("slots") || normalized.equals("inv") || normalized.equals("inventory")) {
         return "";
      }

      for (String prefix : List.of("exclude=", "skip=", "slots=", "slot=", "inv=", "inventory=", "exclude:", "skip:", "slots:", "slot:", "inv:", "inventory:")) {
         if (normalized.startsWith(prefix)) {
            return normalized.substring(prefix.length()).trim();
         }
      }

      return normalized;
   }

   private static boolean addInventorySlotRange(Set<Integer> slots, int start, int end) {
      int min = Math.min(start, end);
      int max = Math.max(start, end);
      if (min < 0 || max >= PLAYER_INVENTORY_SLOT_COUNT) {
         return false;
      }

      for (int slot = min; slot <= max; slot++) {
         slots.add(slot);
      }

      return true;
   }

   private static boolean addInventorySlot(Set<Integer> slots, int slot) {
      if (slot < 0 || slot >= PLAYER_INVENTORY_SLOT_COUNT) {
         return false;
      } else {
         slots.add(slot);
         return true;
      }
   }

   private static Integer inventorySlotNumber(String slotText) {
      String normalized = cleanInventorySlotToken(slotText);
      if (normalized.isBlank()) {
         return null;
      } else {
         for (String prefix : List.of("slots ", "slot ", "inventory ", "inv ", "hotbar ")) {
            if (normalized.startsWith(prefix)) {
               normalized = normalized.substring(prefix.length()).trim();
               break;
            }
         }
      }

      Integer slot = parseIntOrNull(normalized);
      return slot != null && slot >= 0 && slot < PLAYER_INVENTORY_SLOT_COUNT ? slot : null;
   }

   private static Slot openContainerSlot(AbstractContainerMenu handler, String slotText) {
      Integer index = openContainerSlotIndex(handler, slotText);
      return index != null ? (Slot)handler.slots.get(index) : null;
   }

   private static ItemStack openContainerStack(Minecraft client, String slotText) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      Slot slot = handler == null ? null : openContainerSlot(handler, slotText);
      return slot == null ? null : slot.getItem();
   }

   private static List<ItemStack> openContainerStacks(Minecraft client, SlotRange range) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      if (handler == null) {
         return null;
      } else {
         List<ItemStack> stacks = new ArrayList<>();
         int start = Math.max(0, Math.min(range.start(), range.end()));
         int end = Math.min(handler.slots.size() - 1, Math.max(range.start(), range.end()));

         for (int index = start; index <= end; index++) {
            stacks.add(((Slot)handler.slots.get(index)).getItem());
         }

         return stacks;
      }
   }

   private static Integer openContainerSlotIndex(AbstractContainerMenu handler, String slotText) {
      Integer slot = openContainerSlotNumber(slotText);
      if (slot == null) {
         return null;
      } else {
         int firstPlayerSlot = firstPlayerInventorySlot(handler);
         if (slot >= 1 && slot <= firstPlayerSlot) {
            return slot - 1;
         } else {
            return slot >= 0 && slot < firstPlayerSlot ? slot : null;
         }
      }
   }

   private static boolean looksLikeOpenContainerSlotTarget(String slotText) {
      return openContainerSlotNumber(slotText) != null;
   }

   private static boolean isExplicitOpenContainerSlotTarget(String slotText) {
      String normalized = slotText == null ? "" : slotText.trim().toLowerCase(Locale.ROOT);
      return normalized.startsWith("gui") || normalized.startsWith("open") || normalized.startsWith("container") || normalized.startsWith("screen");
   }

   private static Integer openContainerSlotNumber(String slotText) {
      String normalized = slotText == null ? "" : slotText.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return null;
      } else {
         normalized = normalized.replace('=', ' ');
         for (String prefix : List.of("gui", "open", "container", "screen")) {
            if (normalized.startsWith(prefix)) {
               normalized = normalized.substring(prefix.length()).trim();
               break;
            }
         }

         if (normalized.startsWith("slot")) {
            normalized = normalized.substring("slot".length()).trim();
         }

         return parseIntOrNull(normalized);
      }
   }

   private static AbstractContainerMenu activeContainerHandler(Minecraft client) {
      AbstractContainerScreen<?> screen = handledScreen(client);
      return screen != null ? screen.getMenu() : openContainerHandler(client);
   }

   private static AbstractContainerMenu openContainerHandler(Minecraft client) {
      return client != null && client.player != null && client.player.containerMenu != null && client.player.containerMenu != client.player.inventoryMenu
         ? client.player.containerMenu
         : null;
   }

   private static boolean hasOpenContainer(Minecraft client) {
      return openContainerHandler(client) != null;
   }

   private static AbstractContainerScreen<?> handledScreen(Minecraft client) {
      return client.gui.screen() instanceof AbstractContainerScreen<?> handledScreen ? handledScreen : null;
   }

   private static ItemStack playerInventoryStack(Minecraft client, String slotText) {
      Integer slot = playerInventorySlotIndex(slotText);
      return slot != null && slot < client.player.getInventory().getContainerSize() ? client.player.getInventory().getItem(slot) : null;
   }

   private static List<ItemStack> playerInventoryStacks(Minecraft client, SlotRange range) {
      int start = Math.min(range.start(), range.end());
      int end = Math.max(range.start(), range.end());
      if (start >= 1 && end <= 36) {
         start--;
         end--;
      }

      if (start < 0 || end >= 36) {
         return null;
      } else {
         List<ItemStack> stacks = new ArrayList<>();
         int maxSlot = Math.min(35, client.player.getInventory().getContainerSize() - 1);

         for (int index = start; index <= end && index <= maxSlot; index++) {
            stacks.add(client.player.getInventory().getItem(index));
         }

         return stacks;
      }
   }

   private static Integer playerInventorySlotIndex(String slotText) {
      String normalized = slotText == null ? "" : slotText.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return null;
      } else {
         boolean hotbarOnly = false;
         if (normalized.startsWith("hotbar")) {
            hotbarOnly = true;
            normalized = normalized.substring("hotbar".length()).trim();
         }

         if (normalized.startsWith("slot")) {
            normalized = normalized.substring("slot".length()).trim();
         }

         Integer slot = parseIntOrNull(normalized);
         if (slot == null) {
            return null;
         } else if (!hotbarOnly) {
            if (slot >= 1 && slot <= 36) {
               return slot - 1;
            } else {
               return slot >= 0 && slot < 36 ? slot : null;
            }
         } else {
            return slot >= 1 && slot <= 9 ? slot - 1 : null;
         }
      }
   }

   private static ItemStack stackFromItemOrSlot(Minecraft client, String sourceText) {
      String source = sourceText == null ? "" : sourceText.trim();
      if (!source.isBlank() && !source.equalsIgnoreCase("held") && !source.equalsIgnoreCase("mainhand") && !source.equalsIgnoreCase("main_hand")) {
         if (isExplicitOpenContainerSlotTarget(source)) {
            return openContainerStack(client, source);
         }

         ItemStack slotStack = playerInventoryStack(client, source);
         if (slotStack != null) {
            return slotStack;
         } else {
            Item item = itemById(MacroModel.normalizeItemId(source));
            return item == null ? null : new ItemStack(item);
         }
      } else {
         return client.player.getMainHandItem();
      }
   }

   private static List<ItemStack> stacksFromItemOrSlot(Minecraft client, String sourceText) {
      String source = sourceText == null ? "" : sourceText.trim();
      SlotRange range = slotRange(source);
      if (range != null) {
         return range.openContainer() ? openContainerStacks(client, range) : playerInventoryStacks(client, range);
      } else {
         ItemStack stack = stackFromItemOrSlot(client, source);
         return stack == null ? null : List.of(stack);
      }
   }

   private void rememberOpenContainerTagMatch(MacroModel.Node node, String tagText) {
      String next = node.next("true");
      if (next == null || next.isBlank()) {
         this.clearOpenContainerTagMatch();
      } else {
         this.lastOpenContainerTagFilter = tagText;
         this.lastOpenContainerTagNextNodeId = next;
      }
   }

   private void clearOpenContainerTagMatch() {
      this.lastOpenContainerTagFilter = "";
      this.lastOpenContainerTagNextNodeId = "";
   }

   private static boolean isOpenContainerTagSource(String sourceText) {
      SlotRange range = slotRange(sourceText);
      return range != null && range.openContainer() || isExplicitOpenContainerSlotTarget(sourceText);
   }

   private Boolean openContainerSourceHasTag(Minecraft client, MacroModel.Node node, String tagText) {
      AbstractContainerMenu handler = activeContainerHandler(client);
      if (handler == null) {
         return null;
      } else {
         String sourceText = this.resolveRuntimeText(node.value);
         SlotRange range = slotRange(sourceText);
         if (range != null && range.openContainer()) {
            int firstPlayerSlot = firstPlayerInventorySlot(handler);
            int start = Math.max(0, Math.min(range.start(), range.end()));
            int end = Math.min(firstPlayerSlot - 1, Math.max(range.start(), range.end()));
            return start > end ? false : this.openContainerSlotsHaveTag(client, node, handler, start, end, tagText);
         } else {
            Integer slotIndex = isExplicitOpenContainerSlotTarget(sourceText) ? openContainerSlotIndex(handler, sourceText) : null;
            return slotIndex == null ? null : this.openContainerSlotsHaveTag(client, node, handler, slotIndex, slotIndex, tagText);
         }
      }
   }

   private Boolean openContainerSlotsHaveTag(Minecraft client, MacroModel.Node node, AbstractContainerMenu handler, int start, int end, String tagText) {
      for (int index = start; index <= end && index < handler.slots.size(); index++) {
         Slot slot = (Slot)handler.slots.get(index);
         if (stackHasConfiguredTag(slot.getItem(), tagText)) {
            this.rememberOpenContainerTagMatch(node, tagText);
            if (clickMatchedTagEnabled(node)) {
               if (client.gameMode == null) {
                  return null;
               }

               client.gameMode.handleContainerInput(handler.containerId, slot.index, 0, ContainerInput.PICKUP, client.player);
            }

            return true;
         }
      }

      return false;
   }

   private static boolean clickMatchedTagEnabled(MacroModel.Node node) {
      return Boolean.parseBoolean(node.value3 == null ? "" : node.value3.trim());
   }

   private static boolean stackHasConfiguredTag(ItemStack stack, String tagText) {
      if (stack.isEmpty()) {
         return false;
      } else {
         PriceRange priceRange = priceRange(tagText);
         String remainingTagText = priceRange == null ? tagText : tagText.replace(priceRange.source(), " ").trim();
         if (priceRange != null && !stackHasPriceInRange(stack, priceRange)) {
            return false;
         }

         if (remainingTagText.isBlank()) {
            return priceRange != null;
         }

         TagKey<Item> itemTag = itemTagKey(remainingTagText);
         if (itemTag != null && stack.is(itemTag)) {
            return true;
         }

         String needle = tagSearchText(remainingTagText);
         return !needle.isBlank() && stackSearchText(stack).contains(needle);
      }
   }

   private static String stackSearchText(ItemStack stack) {
      StringBuilder text = new StringBuilder();
      text.append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append(' ');
      text.append(stack.getHoverName().getString()).append(' ');
      try {
         for (Component line : stack.getTooltipLines(Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL)) {
            text.append(line.getString()).append(' ');
         }
      } catch (RuntimeException ignored) {
      }

      text.append(stack.getComponents());
      return text.toString().toLowerCase(Locale.ROOT);
   }

   private static String tagSearchText(String tagText) {
      String normalized = tagText == null ? "" : tagText.trim().toLowerCase(Locale.ROOT);
      if (normalized.startsWith("#")) {
         normalized = normalized.substring(1);
      }

      return normalized;
   }

   private static boolean stackHasPriceInRange(ItemStack stack, PriceRange range) {
      Matcher matcher = PRICE_PATTERN.matcher(stackSearchText(stack));
      while (matcher.find()) {
         double price = parsePriceValue(matcher.group(1), matcher.group(2));
         if (!Double.isNaN(price) && price <= range.max()) {
            return true;
         }
      }

      return false;
   }

   private static boolean stackMatchesClickTarget(ItemStack stack, String itemId, String tagText, PriceRange priceRange) {
      if (stack.isEmpty()) {
         return false;
      } else if (!itemId.isBlank() && !stackMatches(stack, itemId)) {
         return false;
      } else if (priceRange != null && !stackHasPriceInRange(stack, priceRange)) {
         return false;
      } else {
         return tagText.isBlank() || stackHasConfiguredTag(stack, tagText);
      }
   }

   private static boolean clickTargetHasValidFilter(String target, String itemId) {
      return !itemId.isBlank() && isKnownItemId(itemId) || priceRange(target) != null || !clickTargetTagText(target).isBlank();
   }

   private static String clickTargetItemText(String target) {
      String text = removePriceRange(target);
      String[] parts = text.trim().split("\\s+", 2);
      return parts.length == 0 ? "" : parts[0];
   }

   private static String clickTargetItemId(String target) {
      String itemText = clickTargetItemText(target);
      return itemText.isBlank() ? "" : MacroModel.normalizeItemId(itemText);
   }

   private static String clickTargetTagText(String target) {
      String text = removePriceRange(target).trim();
      int firstSpace = text.indexOf(' ');
      return firstSpace < 0 ? "" : text.substring(firstSpace + 1).trim();
   }

   private static String removePriceRange(String text) {
      Matcher matcher = PRICE_RANGE_PATTERN.matcher(text == null ? "" : text);
      return matcher.find() ? matcher.replaceFirst(" ").trim() : (text == null ? "" : text.trim());
   }

   private static PriceRange priceRange(String text) {
      Matcher matcher = PRICE_RANGE_PATTERN.matcher(text == null ? "" : text);
      if (!matcher.find()) {
         return null;
      } else {
         double first = parsePriceValue(matcher.group(1), matcher.group(2));
         double second = parsePriceValue(matcher.group(3), matcher.group(4));
         return Double.isNaN(first) || Double.isNaN(second)
            ? null
            : new PriceRange(matcher.group(), Math.min(first, second), Math.max(first, second));
      }
   }

   private static double parsePriceValue(String numberText, String suffixText) {
      try {
         double value = Double.parseDouble(numberText.replace(",", ""));
         return value * switch ((suffixText == null ? "" : suffixText).toLowerCase(Locale.ROOT)) {
            case "k" -> 1_000.0;
            case "m" -> 1_000_000.0;
            case "b" -> 1_000_000_000.0;
            case "t" -> 1_000_000_000_000.0;
            default -> 1.0;
         };
      } catch (NumberFormatException var3) {
         return Double.NaN;
      }
   }

   private static SlotRange slotRange(String sourceText) {
      String normalized = sourceText == null ? "" : sourceText.trim().toLowerCase(Locale.ROOT).replace('=', ' ');
      if (normalized.isBlank()) {
         return null;
      } else {
         boolean openContainer = false;
         for (String prefix : List.of("gui", "open", "container", "screen")) {
            if (normalized.startsWith(prefix)) {
               openContainer = true;
               normalized = normalized.substring(prefix.length()).trim();
               break;
            }
         }

         if (normalized.startsWith("slots")) {
            normalized = normalized.substring("slots".length()).trim();
         } else if (normalized.startsWith("slot")) {
            normalized = normalized.substring("slot".length()).trim();
         }

         int dash = normalized.indexOf('-');
         if (dash < 0) {
            return null;
         } else {
            Integer start = parseIntOrNull(normalized.substring(0, dash).trim());
            Integer end = parseIntOrNull(normalized.substring(dash + 1).trim());
            if (start == null || end == null || start < 0 || end < 0) {
               return null;
            } else {
               return new SlotRange(openContainer || start > 35 || end > 35, start, end);
            }
         }
      }
   }

   private static boolean stackMatches(ItemStack stack, String itemId) {
      if (stack.isEmpty()) {
         return false;
      } else {
         Identifier stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
         return stackId.toString().equals(MacroModel.normalizeItemId(itemId));
      }
   }

   private static String itemId(ItemStack stack) {
      return stack == null || stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
   }

   private static boolean isKnownItemId(String itemId) {
      return itemById(itemId) != null;
   }

   private static Item itemById(String itemId) {
      String normalized = MacroModel.normalizeItemId(itemId);
      return BuiltInRegistries.ITEM.stream().filter(item -> BuiltInRegistries.ITEM.getKey(item).toString().equals(normalized)).findFirst().orElse(null);
   }

   private static boolean isKnownBlockId(String blockId) {
      String normalized = MacroModel.normalizeItemId(blockId);
      return BuiltInRegistries.BLOCK.stream().anyMatch(block -> BuiltInRegistries.BLOCK.getKey(block).toString().equals(normalized));
   }

   private static TagKey<Item> itemTagKey(String tagText) {
      Identifier id = identifierFromText(tagText);
      return id == null ? null : TagKey.create(Registries.ITEM, id);
   }

   private static TagKey<EntityType<?>> entityTypeTagKey(String tagText) {
      Identifier id = identifierFromText(tagText);
      return id == null ? null : TagKey.create(Registries.ENTITY_TYPE, id);
   }

   private static Identifier identifierFromText(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (normalized.startsWith("#")) {
         normalized = normalized.substring(1);
      }

      if (normalized.isBlank()) {
         return null;
      } else {
         String namespace = "minecraft";
         String path = normalized;
         int separator = normalized.indexOf(58);
         if (separator >= 0) {
            namespace = normalized.substring(0, separator);
            path = normalized.substring(separator + 1);
         }

         if (!namespace.isBlank() && !path.isBlank() && !path.contains(" ")) {
            try {
               return Identifier.fromNamespaceAndPath(namespace, path);
            } catch (RuntimeException var6) {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   private record PriceRange(String source, double min, double max) {
   }

   private record DurabilityCheck(boolean lower, double percent) {
   }

   private record SlotRange(boolean openContainer, int start, int end) {
   }

   private static DurabilityCheck durabilityCheck(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return null;
      }

      boolean higher = normalized.contains("higher")
         || normalized.contains("above")
         || normalized.contains("over")
         || normalized.contains(">=")
         || normalized.contains(">");
      boolean lower = !higher;
      String numberText = normalized.replaceAll("[^0-9.]+", " ").trim();
      if (numberText.isBlank()) {
         return null;
      }

      Double percent = parseDoubleOrNull(numberText.split("\\s+")[0]);
      if (percent == null || percent < 0.0 || percent > 100.0) {
         return null;
      }

      return new DurabilityCheck(lower, percent);
   }

   private static Vec3 parseVec3(String value) {
      return parseVec3(value, null);
   }

   private static Vec3 parseVec3(String value, BlockPos relativeBase) {
      if (value != null && !value.isBlank()) {
         String[] parts = value.trim().split("[,\\s]+");
         if (parts.length < 3) {
            return null;
         } else {
            Double x = parseCoordinate(parts[0], relativeBase == null ? null : relativeBase.getX());
            Double y = parseCoordinate(parts[1], relativeBase == null ? null : relativeBase.getY());
            Double z = parseCoordinate(parts[2], relativeBase == null ? null : relativeBase.getZ());
            if (x == null || y == null || z == null) {
               return null;
            }

            return new Vec3(x, y, z);
         }
      } else {
         return null;
      }
   }

   private static BlockPos parseBlockPos(String value) {
      Vec3 vec = parseVec3(value);
      return vec == null ? null : BlockPos.containing(vec);
   }

   private static BlockPos parseFirstBlockPos(String value) {
      return parseFirstBlockPos(value, null);
   }

   private static BlockPos parseFirstBlockPos(String value, BlockPos relativeBase) {
      if (value != null && !value.isBlank()) {
         String[] parts = value.trim().split("[,\\s]+");
         if (parts.length < 3) {
            return null;
         } else {
            Double x = parseCoordinate(parts[0], relativeBase == null ? null : relativeBase.getX());
            Double y = parseCoordinate(parts[1], relativeBase == null ? null : relativeBase.getY());
            Double z = parseCoordinate(parts[2], relativeBase == null ? null : relativeBase.getZ());
            if (x == null || y == null || z == null) {
               return null;
            }

            return BlockPos.containing(x, y, z);
         }
      } else {
         return null;
      }
   }

   private static Double parseCoordinate(String token, Integer relativeBase) {
      if (token == null || token.isBlank()) {
         return null;
      }

      String trimmed = token.trim();
      if (trimmed.startsWith("~")) {
         if (relativeBase == null) {
            return null;
         }

         String offsetText = trimmed.substring(1);
         if (offsetText.isBlank()) {
            return relativeBase.doubleValue();
         }

         try {
            return relativeBase + Double.parseDouble(offsetText);
         } catch (NumberFormatException error) {
            return null;
         }
      }

      try {
         return Double.parseDouble(trimmed);
      } catch (NumberFormatException error) {
         return null;
      }
   }

   private static BlockPos relativeBase(Minecraft client) {
      return client == null || client.player == null ? null : client.player.blockPosition();
   }

   private static double[] parseTwoDoubles(String value) {
      if (value != null && !value.isBlank()) {
         String[] parts = value.trim().split("[,\\s]+");
         if (parts.length < 2) {
            return null;
         } else {
            try {
               return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
            } catch (NumberFormatException var3) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static boolean truthy(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      return normalized.equals("1")
         || normalized.equals("true")
         || normalized.equals("on")
         || normalized.equals("yes")
         || normalized.equals("press")
         || normalized.equals("pressed")
         || normalized.equals("down");
   }

   private static boolean truthyWithDefault(String value, boolean fallback) {
      if (value != null && !value.isBlank()) {
         String normalized = value.trim().toLowerCase(Locale.ROOT);
         return !normalized.equals("0") && !normalized.equals("false") && !normalized.equals("off") && !normalized.equals("no") ? truthy(normalized) : false;
      } else {
         return fallback;
      }
   }

   private static Boolean parseBooleanOrDefault(String value, boolean fallback) {
      if (value != null && !value.isBlank()) {
         String normalized = value.trim().toLowerCase(Locale.ROOT);
         if (normalized.equals("1")
            || normalized.equals("true")
            || normalized.equals("on")
            || normalized.equals("yes")
            || normalized.equals("press")
            || normalized.equals("pressed")
            || normalized.equals("down")) {
            return true;
         } else {
            return !normalized.equals("0")
                  && !normalized.equals("false")
                  && !normalized.equals("off")
                  && !normalized.equals("no")
                  && !normalized.equals("release")
                  && !normalized.equals("released")
                  && !normalized.equals("up")
               ? null
               : false;
         }
      } else {
         return fallback;
      }
   }

   private static Boolean parseBooleanOrAction(String value) {
      return parseBooleanOrDefault(value, false);
   }

   private static Integer parseMouseButton(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank() || "0".equals(normalized) || "left".equals(normalized) || "attack".equals(normalized) || "left click".equals(normalized)) {
         return 0;
      } else {
         return "1".equals(normalized) || "right".equals(normalized) || "use".equals(normalized) || "right click".equals(normalized) ? 1 : null;
      }
   }

   private static Integer parseEnchantOption(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      normalized = switch (normalized) {
         case "", "best", "highest", "top", "max" -> "3";
         case "middle", "mid" -> "2";
         case "low", "lowest", "bottom" -> "1";
         default -> normalized;
      };
      Integer option = parseIntOrNull(normalized);
      return option != null && option >= 1 && option <= 3 ? option : null;
   }

   private MacroRunner.AutoEnchantOptions autoEnchantOptions(MacroModel.Node node) {
      Integer option = parseEnchantOption(this.resolveRuntimeText(node.value));
      if (option == null) {
         return null;
      } else {
         String options = this.resolveRuntimeText(node.value2).trim();
         Integer minimumLevel = leadingInteger(options);
         if (minimumLevel == null) {
            minimumLevel = optionInt(options, "level=", optionInt(options, "min=", optionInt(options, "xp=", 1)));
         }

         if (minimumLevel < 0) {
            return null;
         } else {
            String itemSelector = optionPresent(options, "item=") ? "" : this.resolveRuntimeText(node.value3).trim();
            if (itemSelector.isBlank()) {
               itemSelector = optionString(options, "item=", "held").trim();
            }

            String lapisText = optionPresent(options, "lapis=") ? "" : this.resolveRuntimeText(node.value4).trim();
            String lapisItem = MacroModel.normalizeItemId(lapisText.isBlank() ? optionString(options, "lapis=", "minecraft:lapis_lazuli") : lapisText);
            String normalizedOptions = options.toLowerCase(Locale.ROOT);
            boolean autoLoad = optionBoolean(options, "load=", true)
               && !normalizedOptions.contains("manual")
               && !normalizedOptions.contains("no_load")
               && !normalizedOptions.contains("noload");
            Boolean closeSetting = parseBooleanOrDefault(this.resolveRuntimeText(node.value5), false);
            boolean closeGui = optionPresent(options, "close=") ? optionBoolean(options, "close=", false) : closeSetting != null && closeSetting;
            if (!isKnownItemId(lapisItem)) {
               return null;
            } else if (enchantItemSelectorIsSpecial(itemSelector)) {
               return new MacroRunner.AutoEnchantOptions(option, minimumLevel, itemSelector, lapisItem, autoLoad, closeGui);
            } else {
               String itemId = MacroModel.normalizeItemId(itemSelector);
               return isKnownItemId(itemId) ? new MacroRunner.AutoEnchantOptions(option, minimumLevel, itemId, lapisItem, autoLoad, closeGui) : null;
            }
         }
      }
   }

   private MacroRunner.AutoGrindstoneOptions autoGrindstoneOptions(MacroModel.Node node) {
      Boolean legacyShiftClick = parseExplicitBoolean(this.resolveRuntimeText(node.value));
      if (legacyShiftClick != null) {
         Boolean legacyClose = parseBooleanOrDefault(this.resolveRuntimeText(node.value2), false);
         return legacyClose == null
            ? null
            : new MacroRunner.AutoGrindstoneOptions(false, "held", "same", "inventory", legacyClose);
      } else {
         String mode = grindstoneMode(this.resolveRuntimeText(node.value));
         if (mode == null) {
            return null;
         } else {
            String inputSelector = cleanGrindstoneInputSelector(this.resolveRuntimeText(node.value2), "held", false);
            String repairSelector = cleanGrindstoneInputSelector(this.resolveRuntimeText(node.value3), "same", true);
            String resultAction = grindstoneResultAction(this.resolveRuntimeText(node.value4));
            Boolean closeGui = parseBooleanOrDefault(this.resolveRuntimeText(node.value5), false);
            if (inputSelector == null || repairSelector == null || resultAction == null || closeGui == null) {
               return null;
            }

            return new MacroRunner.AutoGrindstoneOptions("repair".equals(mode), inputSelector, repairSelector, resultAction, closeGui);
         }
      }
   }

   private static String grindstoneResultAction(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
      return switch (normalized) {
         case "", "true", "false", "on", "off", "yes", "no", "shift", "shift_click", "quick_move", "inventory", "inv", "keep", "back", "move_back", "pickup", "cursor", "hold" -> "inventory";
         case "drop", "throw", "toss" -> "drop";
         default -> null;
      };
   }

   private static String grindstoneMode(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      normalized = normalized.replace('-', '_').replace(' ', '_');
      return switch (normalized) {
         case "", "disenchant", "remove", "remove_enchant", "remove_enchants", "unenchant", "clean" -> "disenchant";
         case "repair", "combine", "fix" -> "repair";
         default -> null;
      };
   }

   private static String cleanGrindstoneInputSelector(String value, String fallback, boolean allowSame) {
      String selector = value == null || value.isBlank() ? fallback : value.trim();
      String normalized = selector.toLowerCase(Locale.ROOT);
      if (grindstoneInputSelectorIsSpecial(normalized, allowSame)) {
         return normalized;
      }

      String itemId = MacroModel.normalizeItemId(selector);
      return isKnownItemId(itemId) ? itemId : null;
   }

   private static boolean grindstoneInputSelectorIsSpecial(String selector, boolean allowSame) {
      String normalized = selector == null ? "" : selector.trim().toLowerCase(Locale.ROOT);
      return normalized.isBlank()
         || normalized.equals("held")
         || normalized.equals("hand")
         || normalized.equals("mainhand")
         || normalized.equals("main_hand")
         || normalized.equals("selected")
         || normalized.equals("any")
         || normalized.equals("all")
         || normalized.equals("auto")
         || allowSame
            && (normalized.equals("same") || normalized.equals("match") || normalized.equals("matching") || normalized.equals("input"));
   }

   private static Boolean parseExplicitBoolean(String value) {
      if (value == null || value.isBlank()) {
         return null;
      }

      return parseBooleanOrDefault(value, false);
   }

   private static boolean enchantItemSelectorIsSpecial(String selector) {
      String normalized = selector == null ? "" : selector.trim().toLowerCase(Locale.ROOT);
      return normalized.isBlank()
         || normalized.equals("held")
         || normalized.equals("hand")
         || normalized.equals("mainhand")
         || normalized.equals("main_hand")
         || normalized.equals("any")
         || normalized.equals("all")
         || normalized.equals("auto");
   }

   private static Integer leadingInteger(String value) {
      if (value != null && !value.isBlank()) {
         for (String token : value.split("[,\\s]+")) {
            if (!token.isBlank()) {
               return parseIntOrNull(token);
            }
         }
      }

      return null;
   }

   private static boolean shouldStopMacroAtXpLevel(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      return normalized.equals("stop") || normalized.equals("macro") || normalized.equals("stop macro");
   }

   private void setMouseButtonDown(Minecraft client, int button, boolean down) {
      if (button == 1) {
         this.setMacroKeyDown(client, client.options.keyUse, down);
      } else {
         this.setMacroKeyDown(client, client.options.keyAttack, down);
      }
   }

   private void clickMouseButton(Minecraft client, int button) {
      if (this.performMouseButtonClick(client, button)) {
         return;
      }

      InputConstants.Key key = button == 1 ? client.options.keyUse.getDefaultKey() : client.options.keyAttack.getDefaultKey();
      KeyMapping.click(key);
      this.setMouseButtonDown(client, button, true);
      this.setMouseButtonDown(client, button, false);
   }

   private boolean performMouseButtonClick(Minecraft client, int button) {
      if (client.player == null || client.gameMode == null) {
         return false;
      } else {
         return button == 1 ? this.performUseClick(client) : this.performAttackClick(client);
      }
   }

   private boolean performAttackClick(Minecraft client) {
      if (client.hitResult instanceof EntityHitResult hit && hit.getType() == Type.ENTITY) {
         client.gameMode.attack(client.player, hit.getEntity());
         client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
         return true;
      } else if (client.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
         client.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
         client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
         return true;
      } else {
         return false;
      }
   }

   private boolean performUseClick(Minecraft client) {
      if (client.hitResult instanceof EntityHitResult hit && hit.getType() == Type.ENTITY) {
         InteractionResult result = client.gameMode.interact(client.player, hit.getEntity(), hit, InteractionHand.MAIN_HAND);
         if (result.consumesAction()) {
            client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
         }

         return true;
      } else if (client.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
         InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
         if (result.consumesAction()) {
            client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
         }

         return true;
      } else {
         InteractionResult result = client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
         if (result.consumesAction()) {
            client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
         }

         return true;
      }
   }

   private static MacroRunner.AutoClickOptions autoClickOptions(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return new MacroRunner.AutoClickOptions(false, 1, 125, 50);
      }

      String[] tokens = normalized.split("[,\\s]+");
      boolean hold = false;
      int count = 1;
      int durationMs = 50;
      int intervalMs = 125;
      Double cps = null;

      for (int index = 0; index < tokens.length; index++) {
         String token = tokens[index];
         if (token.isBlank()) {
            continue;
         }

         String key = token;
         String parsedValue = "";
         int equals = token.indexOf('=');
         if (equals > 0) {
            key = token.substring(0, equals);
            parsedValue = token.substring(equals + 1);
         } else if (index + 1 < tokens.length && isAutoClickOptionName(token)) {
            parsedValue = tokens[++index];
         }

         switch (key) {
            case "hold", "held", "down" -> {
               hold = true;
               Integer parsed = parseDurationMs(parsedValue);
               if (parsed != null) {
                  durationMs = parsed;
               }
            }
            case "duration", "time", "ms", "holdms" -> {
               Integer parsed = parseDurationMs(parsedValue);
               if (parsed != null) {
                  durationMs = parsed;
               }
            }
            case "count", "clicks", "times" -> {
               Integer parsed = parseIntOrNull(parsedValue);
               if (parsed != null) {
                  count = parsed;
               }
            }
            case "cps" -> {
               Double parsed = parseDoubleOrNull(parsedValue);
               if (parsed != null) {
                  cps = parsed;
               }
            }
            case "interval", "delay" -> {
               Integer parsed = parseDurationMs(parsedValue);
               if (parsed != null) {
                  intervalMs = parsed;
               }
            }
            default -> {
               Integer parsedDuration = parseDurationMs(token);
               Integer parsedInt = parseIntOrNull(token);
               if (hold && parsedDuration != null) {
                  durationMs = parsedDuration;
               } else if (parsedInt != null) {
                  count = parsedInt;
               }
            }
         }
      }

      if (cps != null && cps > 0.0) {
         intervalMs = Math.max(1, (int)Math.round(1000.0 / cps));
      }

      return hold
         ? new MacroRunner.AutoClickOptions(true, 1, Math.max(1, durationMs), Math.max(1, intervalMs))
         : new MacroRunner.AutoClickOptions(false, Math.max(1, count), Math.max(1, durationMs), Math.max(1, intervalMs));
   }

   private static boolean isAutoClickOptionName(String value) {
      return switch (value) {
         case "hold", "held", "down", "duration", "time", "ms", "holdms", "count", "clicks", "times", "cps", "interval", "delay" -> true;
         default -> false;
      };
   }

   private static MacroRunner.PressKeyOptions pressKeyOptions(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return new MacroRunner.PressKeyOptions(false, 50);
      }

      String[] tokens = normalized.split("[,\\s]+");
      boolean hold = false;
      int durationMs = 50;

      for (int index = 0; index < tokens.length; index++) {
         String token = tokens[index];
         if (token.isBlank()) {
            continue;
         }

         String key = token;
         String parsedValue = "";
         int equals = token.indexOf('=');
         if (equals > 0) {
            key = token.substring(0, equals);
            parsedValue = token.substring(equals + 1);
         } else if (index + 1 < tokens.length && isPressKeyOptionName(token)) {
            parsedValue = tokens[++index];
         }

         switch (key) {
            case "hold", "held", "down" -> {
               hold = true;
               Integer parsed = parseDurationMs(parsedValue);
               if (parsed != null) {
                  durationMs = parsed;
               }
            }
            case "tap", "press", "click" -> {
               hold = false;
               Integer parsed = parseDurationMs(parsedValue);
               if (parsed != null) {
                  durationMs = parsed;
               }
            }
            case "duration", "time", "ms" -> {
               Integer parsed = parseDurationMs(parsedValue);
               if (parsed != null) {
                  durationMs = parsed;
               }
            }
            default -> {
               Integer parsed = parseDurationMs(token);
               if (parsed != null) {
                  durationMs = parsed;
               }
            }
         }
      }

      return new MacroRunner.PressKeyOptions(hold, Math.max(1, durationMs));
   }

   private static boolean isPressKeyOptionName(String value) {
      return switch (value) {
         case "hold", "held", "down", "tap", "press", "click", "duration", "time", "ms" -> true;
         default -> false;
      };
   }

   private static int durationTicks(int durationMs) {
      return Math.max(1, (int)Math.ceil(durationMs / 50.0));
   }

   private static Integer parseDurationMs(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return null;
      }

      try {
         if (normalized.endsWith("ms")) {
            return Math.max(1, (int)Math.round(Double.parseDouble(normalized.substring(0, normalized.length() - 2).trim())));
         } else if (normalized.endsWith("s")) {
            return Math.max(1, (int)Math.round(Double.parseDouble(normalized.substring(0, normalized.length() - 1).trim()) * 1000.0));
         } else {
            return Math.max(1, (int)Math.round(Double.parseDouble(normalized)));
         }
      } catch (NumberFormatException var2) {
         return null;
      }
   }

   private static InputConstants.Key keyFromText(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return null;
      }

      Integer keyCode = keyCodeFromName(normalized);
      InputConstants.Key key = keyCode == null
         ? InputConstants.getKey(normalized.startsWith("key.") ? normalized : "key.keyboard." + normalized.replace(' ', '.'))
         : InputConstants.getKey(new KeyEvent(keyCode, 0, 0));
      return key == InputConstants.UNKNOWN ? null : key;
   }

   private static Integer keyCodeFromName(String value) {
      if (value.length() == 1) {
         char character = value.charAt(0);
         if (character >= 'a' && character <= 'z') {
            return InputConstants.KEY_A + character - 'a';
         }

         if (character >= '0' && character <= '9') {
            return InputConstants.KEY_0 + character - '0';
         }
      }

      if (value.startsWith("f")) {
         Integer number = parseIntOrNull(value.substring(1));
         if (number != null && number >= 1 && number <= 25) {
            return InputConstants.KEY_F1 + number - 1;
         }
      }

      Integer numeric = parseIntOrNull(value);
      if (numeric != null) {
         return numeric;
      }

      return switch (value.replace("_", "").replace("-", "").replace(" ", "")) {
         case "space" -> InputConstants.KEY_SPACE;
         case "tab" -> InputConstants.KEY_TAB;
         case "enter", "return" -> InputConstants.KEY_RETURN;
         case "esc", "escape" -> InputConstants.KEY_ESCAPE;
         case "backspace" -> InputConstants.KEY_BACKSPACE;
         case "delete", "del" -> InputConstants.KEY_DELETE;
         case "insert", "ins" -> InputConstants.KEY_INSERT;
         case "home" -> InputConstants.KEY_HOME;
         case "end" -> InputConstants.KEY_END;
         case "pageup", "pgup" -> InputConstants.KEY_PAGEUP;
         case "pagedown", "pgdn" -> InputConstants.KEY_PAGEDOWN;
         case "up", "arrowup" -> InputConstants.KEY_UP;
         case "down", "arrowdown" -> InputConstants.KEY_DOWN;
         case "left", "arrowleft" -> InputConstants.KEY_LEFT;
         case "right", "arrowright" -> InputConstants.KEY_RIGHT;
         case "shift", "leftshift", "lshift" -> InputConstants.KEY_LSHIFT;
         case "rightshift", "rshift" -> InputConstants.KEY_RSHIFT;
         case "ctrl", "control", "leftctrl", "leftcontrol", "lctrl", "lcontrol" -> InputConstants.KEY_LCONTROL;
         case "rightctrl", "rightcontrol", "rctrl", "rcontrol" -> InputConstants.KEY_RCONTROL;
         case "alt", "leftalt", "lalt" -> InputConstants.KEY_LALT;
         case "rightalt", "ralt" -> InputConstants.KEY_RALT;
         case "minus" -> InputConstants.KEY_MINUS;
         case "equals", "equal" -> InputConstants.KEY_EQUALS;
         case "comma" -> InputConstants.KEY_COMMA;
         case "period", "dot" -> InputConstants.KEY_PERIOD;
         case "slash" -> InputConstants.KEY_SLASH;
         case "backslash" -> InputConstants.KEY_BACKSLASH;
         case "semicolon" -> InputConstants.KEY_SEMICOLON;
         case "apostrophe", "quote" -> InputConstants.KEY_APOSTROPHE;
         case "grave", "backtick" -> InputConstants.KEY_GRAVE;
         case "leftbracket", "lbracket" -> InputConstants.KEY_LBRACKET;
         case "rightbracket", "rbracket" -> InputConstants.KEY_RBRACKET;
         default -> null;
      };
   }

   private static String[] signLines(String value) {
      String[] split = (value == null ? "" : value).split("\\|", -1);
      String[] lines = new String[]{"", "", "", ""};

      for (int index = 0; index < lines.length && index < split.length; index++) {
         lines[index] = split[index].trim();
      }

      return lines;
   }

   private static boolean eventTextMatches(String text, String filterText, String modeText) {
      String normalizedText = text == null ? "" : text;
      String filter = filterText == null ? "" : filterText.trim();
      String mode = modeText == null ? "any" : modeText.trim().toLowerCase(Locale.ROOT);
      boolean listed = filter.isBlank()
         || normalizedText.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))
         || nameMatchesList(normalizedText, filter)
         || nameMatchesRegex(normalizedText, filter);
      if ("allow".equals(mode)) {
         return listed;
      } else {
         return "block".equals(mode) ? !listed : listed;
      }
   }

   private static int parseScheduleDelayTicks(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
         return -1;
      } else {
         try {
            if (normalized.endsWith("ms")) {
               return Math.max(1, (int)Math.ceil(Integer.parseInt(normalized.substring(0, normalized.length() - 2).trim()) / 50.0));
            } else if (normalized.endsWith("s")) {
               return Math.max(1, Integer.parseInt(normalized.substring(0, normalized.length() - 1).trim()) * 20);
            } else {
               return normalized.endsWith("m")
                  ? Math.max(1, Integer.parseInt(normalized.substring(0, normalized.length() - 1).trim()) * 20 * 60)
                  : Math.max(1, Integer.parseInt(normalized) * 20);
            }
         } catch (NumberFormatException var3) {
            return -1;
         }
      }
   }

   private static LocalTime parseTimeOfDay(String value) {
      if (value != null && !value.isBlank() && value.contains(":")) {
         String[] parts = value.trim().split(":");
         if (parts.length < 2) {
            return null;
         } else {
            try {
               int hour = Math.max(0, Math.min(23, Integer.parseInt(parts[0])));
               int minute = Math.max(0, Math.min(59, Integer.parseInt(parts[1])));
               return LocalTime.of(hour, minute);
            } catch (NumberFormatException var4) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static int parseInt(String value, int fallback) {
      try {
         return Integer.parseInt(value == null ? "" : value.trim());
      } catch (NumberFormatException var3) {
         return fallback;
      }
   }

   private static int optionInt(String options, String prefix, int fallback) {
      if (options != null && !options.isBlank()) {
         for (String token : options.split("[,\\s]+")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith(prefix)) {
               return parseInt(normalized.substring(prefix.length()), fallback);
            }
         }

         return fallback;
      } else {
         return fallback;
      }
   }

   private static boolean optionBoolean(String options, String prefix, boolean fallback) {
      if (options != null && !options.isBlank()) {
         for (String token : options.split("[,\\s]+")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith(prefix)) {
               Boolean parsed = parseBooleanOrDefault(normalized.substring(prefix.length()), fallback);
               return parsed == null ? fallback : parsed;
            }
         }

         return fallback;
      } else {
         return fallback;
      }
   }

   private static String optionString(String options, String prefix, String fallback) {
      if (options != null && !options.isBlank()) {
         for (String token : options.split("[,\\s]+")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith(prefix)) {
               return token.substring(prefix.length());
            }
         }
      }

      return fallback;
   }

   private static boolean optionPresent(String options, String prefix) {
      if (options != null && !options.isBlank()) {
         for (String token : options.split("[,\\s]+")) {
            if (token.trim().toLowerCase(Locale.ROOT).startsWith(prefix)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean moveOption(String options, boolean fallback) {
      boolean prefixed = optionBoolean(options, "move=", fallback);
      if (prefixed == fallback && options != null && !options.isBlank()) {
         for (String token : options.split("[,\\s]+")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("true") || normalized.equals("false")) {
               Boolean parsed = parseBooleanOrDefault(normalized, fallback);
               return parsed == null ? fallback : parsed;
            }
         }

         return fallback;
      } else {
         return prefixed;
      }
   }

   private static Integer parseIntOrNull(String value) {
      try {
         return Integer.parseInt(value == null ? "" : value.trim());
      } catch (NumberFormatException var2) {
         return null;
      }
   }

   private static double parseDouble(String value, double fallback) {
      try {
         return Double.parseDouble(value == null ? "" : value.trim());
      } catch (NumberFormatException var4) {
         return fallback;
      }
   }

   private static Double parseDoubleOrNull(String value) {
      try {
         return Double.parseDouble(value == null ? "" : value.trim());
      } catch (NumberFormatException var2) {
         return null;
      }
   }

   private static Integer firstInteger(String value) {
      Matcher matcher = FIRST_INTEGER_PATTERN.matcher(value == null ? "" : value);
      return matcher.find() ? parseIntOrNull(matcher.group()) : null;
   }

   private static Double firstDouble(String value) {
      Matcher matcher = FIRST_DOUBLE_PATTERN.matcher(value == null ? "" : value);
      return matcher.find() ? parseDoubleOrNull(matcher.group()) : null;
   }

   private static Boolean doubleAtOrBelow(double actual, String value) {
      Double threshold = parseDoubleOrNull(value);
      return threshold == null ? null : actual <= threshold;
   }

   private static Boolean intAtOrBelow(int actual, String value) {
      Integer threshold = parseIntOrNull(value);
      return threshold == null ? null : actual <= threshold;
   }

   private static Boolean intAtLeast(int actual, String value) {
      Integer threshold = parseIntOrNull(value);
      return threshold == null ? null : actual >= threshold;
   }

   private static boolean nameMatchesList(String playerName, String filterText) {
      for (String token : filterText.split("[,\\s]+")) {
         if (!token.isBlank() && playerName.equalsIgnoreCase(token.trim())) {
            return true;
         }
      }

      return false;
   }

   private static boolean nameMatchesRegex(String playerName, String regexText) {
      String regex = regexText.trim();
      if (regex.isEmpty()) {
         return false;
      } else {
         try {
            return playerName.matches(regex);
         } catch (PatternSyntaxException var4) {
            return false;
         }
      }
   }

   private void send(Minecraft client, String message) {
      if (this.settings.chatNotifications()) {
         if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Litemacro] " + message));
         }
      }
   }

   private void saveSettings() {
      try {
         this.settings.save(this.settingsPath);
      } catch (IOException var2) {
         MacroBuilderClient.LOGGER.error("Failed to save Litemacro settings", var2);
      }
   }

   private static String keyName(int keyCode) {
      return keyCode != -1 && keyCode >= 0 ? InputConstants.getKey(new KeyEvent(keyCode, 0, 0)).getDisplayName().getString() : tr("macro_builder.runner.unbound");
   }

   record OverlaySnapshot(int macroNumber, String macroName, String componentName, String status, int ticks, int activeCount) {
   }

   private record AutoClickOptions(boolean hold, int count, int durationMs, int intervalMs) {
   }

   private record AutoEnchantOptions(int option, int minimumLevel, String itemSelector, String lapisItem, boolean autoLoad, boolean closeGui) {
   }

   private record AutoGrindstoneOptions(boolean repair, String inputSelector, String repairSelector, String resultAction, boolean closeGui) {
   }

   private record BoneMealOptions(boolean refill, int radius, BlockPos target) {
   }

   private record ContainerSearchOptions(int radius, boolean move, String type) {
   }

   private static enum DepositResult {
      MOVED,
      DONE,
      NO_CONTAINER,
      INVALID_ITEM,
      INVALID_SLOTS,
      NO_SPACE;
   }

   private record DiscordResult(boolean success, String message) {
   }

   private record FarmOptions(int radius, BlockPos min, BlockPos max, boolean replant, boolean depositWhenFull, boolean move) {
   }

   private record MineAreaOptions(
      BlockPos min,
      BlockPos max,
      int toolLowThreshold,
      boolean move,
      boolean autoTool,
      String stopBeforeBlock,
      int sliceHeight,
      boolean safePath
   ) {
   }

   private record PressKeyOptions(boolean hold, int durationMs) {
   }

   private record RecordedActionData(List<RecordedActionFrame> frames, List<RecordedActionEvent> events) {
   }

   private static final class RecordedActionEvent {
      final int tick;
      final boolean containerInput;
      final int slotId;
      final int button;
      final String inputType;
      final int menuSlotCount;
      final String menuType;
      final boolean command;
      final String message;

      private RecordedActionEvent(
         int tick,
         boolean containerInput,
         int slotId,
         int button,
         String inputType,
         int menuSlotCount,
         String menuType,
         boolean command,
         String message
      ) {
         this.tick = Math.max(0, tick);
         this.containerInput = containerInput;
         this.slotId = slotId;
         this.button = button;
         this.inputType = inputType == null ? "" : inputType;
         this.menuSlotCount = menuSlotCount;
         this.menuType = menuType == null ? "" : menuType;
         this.command = command;
         this.message = message == null ? "" : message;
      }

      static RecordedActionEvent container(int tick, int slotId, int button, String inputType, int menuSlotCount, String menuType) {
         return new RecordedActionEvent(tick, true, slotId, button, inputType, menuSlotCount, menuType, false, "");
      }

      static RecordedActionEvent message(int tick, boolean command, String message) {
         return new RecordedActionEvent(tick, false, 0, 0, "", 0, "", command, message);
      }
   }

   private static final class RecordedActionFrame {
      int ticks;
      final int yawTenths;
      final int pitchTenths;
      final int flags;
      final int slot;

      RecordedActionFrame(int ticks, int yawTenths, int pitchTenths, int flags, int slot) {
         this.ticks = ticks;
         this.yawTenths = yawTenths;
         this.pitchTenths = pitchTenths;
         this.flags = flags;
         this.slot = slot;
      }

      boolean sameState(RecordedActionFrame other) {
         return other != null
            && this.yawTenths == other.yawTenths
            && this.pitchTenths == other.pitchTenths
            && this.flags == other.flags
            && this.slot == other.slot;
      }
   }
}
