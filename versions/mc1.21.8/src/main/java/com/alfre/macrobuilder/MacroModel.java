package com.alfre.macrobuilder;

import static com.alfre.macrobuilder.MacroText.tr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

final class MacroModel {
   static final String START = "official:start";
   static final String WAIT = "official:misc.wait";
   static final String CHAT = "official:misc.chat";
   static final String PLAYER_RESPAWN = "official:player.respawn";
   static final String PLAYER_MOVE = "official:player.move";
   static final String PLAYER_LOOK = "official:player.look";
   static final String PLAYER_RECORDED_ACTIONS = "builder:player.recordedActions";
   static final String PLAYER_SET_MOUSE_BUTTON = "official:player.setMouseButton";
   static final String PLAYER_SHOOT_BOW = "official:player.shootBow";
   static final String PLAYER_IS_ACCOUNT = "official:player.isAccount";
   static final String PLAYER_IN_ACTION = "official:player.inAction";
   static final String PLAYER_IS_ALIVE = "official:player.isAlive";
   static final String INVENTORY_FULL = "official:inventory.isFull";
   static final String ITEM_IN_INVENTORY = "official:inventory.hasItem";
   static final String LEGACY_ITEM_IN_INVENTORY = "builder:inventory.hasItem";
   static final String HEALTH_BELOW = "builder:player.healthBelow";
   static final String XP_LEVEL_AT_LEAST = "builder:player.xpLevelAtLeast";
   static final String STOP_AT_XP_LEVEL = "builder:player.stopAtXpLevel";
   static final String PLAYER_AT_LOCATION = "official:player.isAtLocation";
   static final String PLAYER_SET_CROUCH = "official:player.setCrouch";
   static final String PLAYER_JUMP = "official:player.jump";
   static final String PLAYER_SET_SPRINT = "builder:player.setSprint";
   static final String PLAYER_AUTO_CLICK = "builder:player.autoClick";
   static final String PLAYER_PRESS_KEY = "builder:player.pressKey";
   static final String FOOD_BELOW = "builder:player.foodBelow";
   static final String PLAYER_ON_GROUND = "builder:player.onGround";
   static final String PLAYER_IN_WATER = "builder:player.inWater";
   static final String PLAYER_NEARBY = "official:entity.player_nearby";
   static final String HELD_ITEM_IS = "builder:inventory.heldItemIs";
   static final String SLOT_HAS_ITEM = "builder:inventory.slotHasItem";
   static final String ITEM_OR_SLOT_HAS_TAG = "builder:inventory.itemOrSlotHasTag";
   static final String ITEM_DURABILITY = "builder:inventory.itemDurability";
   static final String EMPTY_SLOTS_AT_LEAST = "builder:inventory.emptySlotsAtLeast";
   static final String OPEN_CONTAINER_HAS_ITEM = "builder:inventory.openContainerHasItem";
   static final String AUTO_ENCHANT = "builder:inventory.autoEnchant";
   static final String AUTO_GRINDSTONE = "builder:inventory.autoGrindstone";
   static final String CLICK_OPEN_CONTAINER_SLOT = "official:inventory.clickOpenContainerSlot";
   static final String HOTBAR_SELECT = "official:inventory.hotbarSelect";
   static final String HOTBAR_USE = "official:inventory.hotbarUse";
   static final String DROP_ITEMS = "official:inventory.dropItems";
   static final String CHEST_WITHDRAW_ITEMS = "official:inventory.chestWithdrawItems";
   static final String CHEST_DEPOSIT_ITEMS = "official:inventory.chestDepositItems";
   static final String HAS_OPEN_CONTAINER = "official:inventory.hasOpenContainer";
   static final String IS_OPEN_CONTAINER_FULL = "official:inventory.isOpenContainerFull";
   static final String OPEN_PLAYER_INVENTORY = "official:inventory.openInventory";
   static final String CLOSE_OPEN_CONTAINER = "official:inventory.closeOpenContainer";
   static final String SELECT_HOTBAR_SLOT = "builder:inventory.selectHotbarSlot";
   static final String DROP_SELECTED_ITEM = "builder:inventory.dropSelectedItem";
   static final String WORLD_INTERACT_WITH_BLOCK = "official:world.interactWithBlock";
   static final String WORLD_MINE_BLOCK = "official:world.mineBlock";
   static final String WORLD_MINE_AREA = "builder:world.mineArea";
   static final String WORLD_PLACE_BLOCK = "official:world.placeBlock";
   static final String WORLD_JUMP_AND_PLACE_BLOCK = "official:world.jumpAndPlaceBlock";
   static final String WORLD_FARM_AREA = "builder:world.farmArea";
   static final String WORLD_OPEN_NEAREST_CONTAINER = "builder:world.openNearestContainer";
   static final String WORLD_OPEN_NEAREST_CHEST = "builder:world.openNearestChest";
   static final String WORLD_OPEN_NEAREST_BARREL = "builder:world.openNearestBarrel";
   static final String WORLD_OPEN_NEAREST_SHULKER = "builder:world.openNearestShulker";
   static final String WORLD_OPEN_NEAREST_ENCHANTING_TABLE = "builder:world.openNearestEnchantingTable";
   static final String WORLD_OPEN_NEAREST_GRINDSTONE = "builder:world.openNearestGrindstone";
   static final String WORLD_AUTO_BONE_MEAL = "builder:world.autoBoneMeal";
   static final String WORLD_BLOCK_IS = "builder:world.blockIs";
   static final String LOOKING_AT_BLOCK = "builder:world.lookingAtBlock";
   static final String ENTITY_ATTACK = "official:entity.attack";
   static final String ENTITY_INTERACT = "official:entity.interact";
   static final String ENTITY_NEARBY = "builder:entity.nearby";
   static final String SCOREBOARD_CONTAINS = "builder:logic.scoreboardContains";
   static final String ACTIONBAR_CONTAINS = "builder:logic.actionbarContains";
   static final String HAS_STATUS_EFFECT = "builder:logic.hasStatusEffect";
   static final String BOSSBAR_CONTAINS = "builder:logic.bossbarContains";
   static final String MACRO_ACTIVE_STATE = "builder:logic.macroActiveState";
   static final String LOCAL_MESSAGE = "builder:localMessage";
   static final String STOP_MACRO = "builder:stop";
   static final String END_CONNECTION = "builder:flow.endConnection";
   static final String BUILDER_NOTE = "builder:flow.note";
   static final String REPEAT_MACRO = "builder:misc.repeatMacro";
   static final String REPEAT_SECTION = "builder:misc.repeatSection";
   static final String REPEAT_UNTIL = "builder:misc.repeatUntil";
   static final String MISC_IDLE_UNTIL = "builder:misc.idleUntil";
   static final String MISC_DISCONNECT = "official:misc.disconnect";
   static final String MISC_REJOIN_SERVER = "official:misc.rejoinServer";
   static final String MISC_JOIN_SERVER = "official:misc.joinServer";
   static final String MISC_MACRO_STOP = "official:misc.macroStop";
   static final String MISC_MACRO_START = "official:misc.macroStart";
   static final String MISC_RANDOM = "official:misc.random";
   static final String MISC_RANDOM_OUTPUT_3 = "builder:misc.randomOutput3";
   static final String MISC_UPDATE_SIGN = "official:misc.updateSign";
   static final String MISC_IS_IN_LOBBY = "official:misc.isInLobby";
   static final String NOTIFICATION_DISCORD = "official:notification.discord";
   static final String LOGIN_REPEAT = "official:login.repeat";
   static final String EVENT_IF_CHAT_SAME = "builder:event.ifChatSame";
   static final String EVENT_CHAT = "official:event.chat";
   static final String EVENT_ACTIONBAR = "builder:event.actionbar";
   static final String EVENT_STATUS_EFFECT = "builder:event.statusEffect";
   static final String EVENT_BOSSBAR = "builder:event.bossbar";
   static final String EVENT_KICKED = "builder:event.kicked";
   static final String EVENT_DEATH = "official:event.death";
   static final String EVENT_DAMAGE_RECEIVED = "official:event.damageReceived";
   static final String EVENT_TELEPORT = "official:event.teleport";
   static final String EVENT_SCHEDULE = "official:event.schedule";
   static final String EVENT_PLAYER_SPAWNED = "official:event.player_spawned";
   static final String EVENT_PLAYER_DESPAWNED = "official:event.player_despawned";
   // 物品字段能力由稳定的组件类型定义，不依赖当前语言的显示标签。
   private static final java.util.Set<String> PRIMARY_ITEM_FIELDS = java.util.Set.of("official:inventory.hasItem", "builder:inventory.hasItem", "official:inventory.clickOpenContainerSlot", "official:inventory.hotbarSelect", "official:inventory.hotbarUse", "official:inventory.dropItems", "builder:inventory.heldItemIs", "builder:inventory.itemOrSlotHasTag", "builder:inventory.itemDurability", "builder:inventory.openContainerHasItem", "builder:world.autoBoneMeal");
   private static final java.util.Set<String> SECONDARY_ITEM_FIELDS = java.util.Set.of("official:inventory.chestWithdrawItems", "official:inventory.chestDepositItems", "builder:inventory.slotHasItem", "builder:inventory.autoGrindstone");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final List<String> COMPLETED_OR_FAILED = List.of("completed", "failed");
   private static final List<String> TRUE_FALSE_OR_FAILED = List.of("true", "false", "failed");
   private static final List<String> COMPLETED_TOOL_LOW_OR_FAILED = List.of("completed", "tool_low", "failed");
   private static final List<String> AUTO_FISH_OUTPUTS = List.of("caught", "inventory_full", "rod_low", "failed");
   private static final List<String> RANDOM_OUTPUT_3 = List.of("one", "two", "three");
   private static final List<String> REPEAT_COMPLETED_OR_FAILED = List.of("repeat", "completed", "failed");
   private static final List<MacroModel.Descriptor> DESCRIPTORS = List.of(
      new MacroModel.Descriptor("official:start", "macro_builder.component.official.start.name", "Macro Entry Point", "Entry", -11961857, List.of("started"), true, "", "", ""),
      new MacroModel.Descriptor("official:player.respawn", "macro_builder.component.official.player.respawn.name", "Respawn", "Player", -6765703, COMPLETED_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor("official:player.move", "macro_builder.component.official.player.move.name", "Move", "Player", -6765703, COMPLETED_OR_FAILED, true, "macro_builder.component.official.player.move.primary", "macro_builder.component.official.player.move.secondary", "forward"),
      new MacroModel.Descriptor("official:player.look", "macro_builder.component.official.player.look.name", "Look", "Player", -6765703, COMPLETED_OR_FAILED, true, "macro_builder.component.official.player.look.primary", "", "0 0"),
      new MacroModel.Descriptor("builder:player.recordedActions", "macro_builder.component.builder.player.recordedActions.name", "Recorded Actions", "Player", -6765703, COMPLETED_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor(
         "official:player.setMouseButton", "macro_builder.component.official.player.setMouseButton.name", "Mouse Button", "Player", -6765703, COMPLETED_OR_FAILED, true, "macro_builder.component.official.player.setMouseButton.primary", "macro_builder.component.official.player.setMouseButton.secondary", "press"
      ),
      new MacroModel.Descriptor("official:player.shootBow", "macro_builder.component.official.player.shootBow.name", "Shoot Bow", "Player", -6765703, COMPLETED_OR_FAILED, true, "macro_builder.component.official.player.shootBow.primary", "macro_builder.component.official.player.shootBow.secondary", ""),
      new MacroModel.Descriptor("official:player.isAccount", "macro_builder.component.official.player.isAccount.name", "Is Account", "Player", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.official.player.isAccount.primary", "", ""),
      new MacroModel.Descriptor("official:player.inAction", "macro_builder.component.official.player.inAction.name", "Player In Action", "Player", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.official.player.inAction.primary", "", "sneaking"),
      new MacroModel.Descriptor("official:player.isAlive", "macro_builder.component.official.player.isAlive.name", "Player Is Alive", "Player", -1726398, TRUE_FALSE_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor("official:player.setCrouch", "macro_builder.component.official.player.setCrouch.name", "Set Crouch", "Player", -6765703, COMPLETED_OR_FAILED, true, "macro_builder.component.official.player.setCrouch.primary", "", "true"),
      new MacroModel.Descriptor("builder:player.setSprint", "macro_builder.component.builder.player.setSprint.name", "Set Sprint", "Player", -6765703, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.player.setSprint.primary", "", "true"),
      new MacroModel.Descriptor("official:player.jump", "macro_builder.component.official.player.jump.name", "Jump", "Player", -6765703, COMPLETED_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor(
         "builder:player.autoClick", "macro_builder.component.builder.player.autoClick.name", "Auto Click", "Player", -6765703, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.player.autoClick.primary", "macro_builder.component.builder.player.autoClick.secondary", "left"
      ),
      new MacroModel.Descriptor(
         "builder:player.autoFish", "macro_builder.component.builder.player.autoFish.name", "Auto Fish", "Player", -6765703, AUTO_FISH_OUTPUTS, true, "macro_builder.component.builder.player.autoFish.primary", "macro_builder.component.builder.player.autoFish.secondary", "10"
      ),
      new MacroModel.Descriptor(
         "builder:player.pressKey", "macro_builder.component.builder.player.pressKey.name", "Press Key", "Player", -6765703, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.player.pressKey.primary", "macro_builder.component.builder.player.pressKey.secondary", "space"
      ),
      new MacroModel.Descriptor("official:inventory.isFull", "macro_builder.component.official.inventory.isFull.name", "Inventory Is Full", "Logic", -1726398, TRUE_FALSE_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor(
         "official:inventory.hasItem", "macro_builder.component.official.inventory.hasItem.name", "Inventory Has Item", "Logic", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.official.inventory.hasItem.primary", "macro_builder.component.official.inventory.hasItem.secondary", "minecraft:bone_block"
      ),
      new MacroModel.Descriptor(
         "builder:inventory.hasItem", "macro_builder.component.builder.inventory.hasItem.name", "Inventory Has Item", "Logic", -1726398, TRUE_FALSE_OR_FAILED, false, "macro_builder.component.builder.inventory.hasItem.primary", "macro_builder.component.builder.inventory.hasItem.secondary", "minecraft:bone_block"
      ),
      new MacroModel.Descriptor("builder:player.healthBelow", "macro_builder.component.builder.player.healthBelow.name", "Health Below", "Logic", -2069387, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.player.healthBelow.primary", "", "10"),
      new MacroModel.Descriptor("builder:player.foodBelow", "macro_builder.component.builder.player.foodBelow.name", "Food Below", "Logic", -2069387, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.player.foodBelow.primary", "", "6"),
      new MacroModel.Descriptor("builder:player.xpLevelAtLeast", "macro_builder.component.builder.player.xpLevelAtLeast.name", "XP Level At Least", "Logic", -4794025, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.player.xpLevelAtLeast.primary", "", "1"),
      new MacroModel.Descriptor(
         "builder:player.stopAtXpLevel", "macro_builder.component.builder.player.stopAtXpLevel.name", "Stop At XP Level", "Flow", -7366491, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.player.stopAtXpLevel.primary", "macro_builder.component.builder.player.stopAtXpLevel.secondary", "30"
      ),
      new MacroModel.Descriptor("builder:player.onGround", "macro_builder.component.builder.player.onGround.name", "Player On Ground", "Logic", -1726398, TRUE_FALSE_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor("builder:player.inWater", "macro_builder.component.builder.player.inWater.name", "Player In Water", "Logic", -1726398, TRUE_FALSE_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor(
         "official:player.isAtLocation", "macro_builder.component.official.player.isAtLocation.name", "Player At Location", "Logic", -10375185, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.official.player.isAtLocation.primary", "macro_builder.component.official.player.isAtLocation.secondary", "0 64 0"
      ),
      new MacroModel.Descriptor(
         "official:entity.player_nearby", "macro_builder.component.official.entity.player_nearby.name", "Player Nearby", "Logic", -10375185, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.official.entity.player_nearby.primary", "macro_builder.component.official.entity.player_nearby.secondary", ""
      ),
      new MacroModel.Descriptor(
         "builder:logic.scoreboardContains", "macro_builder.component.builder.logic.scoreboardContains.name", "Scoreboard Contains", "Logic", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.logic.scoreboardContains.primary", "macro_builder.component.builder.logic.scoreboardContains.secondary", ""
      ),
      new MacroModel.Descriptor(
         "builder:logic.actionbarContains", "macro_builder.component.builder.logic.actionbarContains.name", "Actionbar Contains", "Logic", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.logic.actionbarContains.primary", "macro_builder.component.builder.logic.actionbarContains.secondary", ""
      ),
      new MacroModel.Descriptor(
         "builder:logic.hasStatusEffect", "macro_builder.component.builder.logic.hasStatusEffect.name", "Has Status Effect", "Logic", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.logic.hasStatusEffect.primary", "macro_builder.component.builder.logic.hasStatusEffect.secondary", "minecraft:speed"
      ),
      new MacroModel.Descriptor(
         "builder:logic.bossbarContains", "macro_builder.component.builder.logic.bossbarContains.name", "Bossbar Contains", "Logic", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.logic.bossbarContains.primary", "macro_builder.component.builder.logic.bossbarContains.secondary", ""
      ),
      new MacroModel.Descriptor(
         "builder:logic.macroActiveState", "macro_builder.component.builder.logic.macroActiveState.name", "Macro Active State", "Logic", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.logic.macroActiveState.primary", "macro_builder.component.builder.logic.macroActiveState.secondary", "1"
      ),
      new MacroModel.Descriptor("official:misc.wait", "macro_builder.component.official.misc.wait.name", "Wait", "Flow", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.official.misc.wait.primary", "", "10000"),
      new MacroModel.Descriptor("official:misc.chat", "macro_builder.component.official.misc.chat.name", "Chat / Command", "Action", -6765703, COMPLETED_OR_FAILED, true, "macro_builder.component.official.misc.chat.primary", "", "/say Macro running"),
      new MacroModel.Descriptor(
         "official:inventory.clickOpenContainerSlot",
         "macro_builder.component.official.inventory.clickOpenContainerSlot.name", "Click GUI Item",
         "Inventory",
         -3770147,
         COMPLETED_OR_FAILED,
         true,
         "macro_builder.component.official.inventory.clickOpenContainerSlot.primary",
         "macro_builder.component.official.inventory.clickOpenContainerSlot.secondary",
         "minecraft:bone_block"
      ),
      new MacroModel.Descriptor(
         "official:inventory.hotbarSelect", "macro_builder.component.official.inventory.hotbarSelect.name", "Hotbar Select", "Inventory", -3040666, COMPLETED_OR_FAILED, true, "macro_builder.component.official.inventory.hotbarSelect.primary", "macro_builder.component.official.inventory.hotbarSelect.secondary", ""
      ),
      new MacroModel.Descriptor(
         "official:inventory.hotbarUse", "macro_builder.component.official.inventory.hotbarUse.name", "Hotbar Use", "Inventory", -3040666, COMPLETED_OR_FAILED, true, "macro_builder.component.official.inventory.hotbarUse.primary", "macro_builder.component.official.inventory.hotbarUse.secondary", ""
      ),
      new MacroModel.Descriptor(
         "official:inventory.dropItems", "macro_builder.component.official.inventory.dropItems.name", "Drop Items", "Inventory", -3040666, COMPLETED_OR_FAILED, true, "macro_builder.component.official.inventory.dropItems.primary", "macro_builder.component.official.inventory.dropItems.secondary", ""
      ),
      new MacroModel.Descriptor(
         "official:inventory.chestWithdrawItems",
         "macro_builder.component.official.inventory.chestWithdrawItems.name", "Withdraw Items",
         "Inventory",
         -3040666,
         COMPLETED_OR_FAILED,
         true,
         "macro_builder.component.official.inventory.chestWithdrawItems.primary",
         "macro_builder.component.official.inventory.chestWithdrawItems.secondary",
         "all"
      ),
      new MacroModel.Descriptor(
         "official:inventory.chestDepositItems",
         "macro_builder.component.official.inventory.chestDepositItems.name", "Deposit Items",
         "Inventory",
         -3040666,
         COMPLETED_OR_FAILED,
         true,
         "macro_builder.component.official.inventory.chestDepositItems.primary",
         "macro_builder.component.official.inventory.chestDepositItems.secondary",
         "all"
      ),
      new MacroModel.Descriptor(
         "builder:inventory.heldItemIs", "macro_builder.component.builder.inventory.heldItemIs.name", "Held Item Is", "Inventory", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.inventory.heldItemIs.primary", "", "minecraft:bone_block"
      ),
      new MacroModel.Descriptor(
         "builder:inventory.slotHasItem", "macro_builder.component.builder.inventory.slotHasItem.name", "Slot Has Item", "Inventory", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.inventory.slotHasItem.primary", "macro_builder.component.builder.inventory.slotHasItem.secondary", "1"
      ),
      new MacroModel.Descriptor(
         "builder:inventory.itemOrSlotHasTag",
         "macro_builder.component.builder.inventory.itemOrSlotHasTag.name", "Item/Slot Has Tag",
         "Inventory",
         -1726398,
         TRUE_FALSE_OR_FAILED,
         true,
         "macro_builder.component.builder.inventory.itemOrSlotHasTag.primary",
         "macro_builder.component.builder.inventory.itemOrSlotHasTag.secondary",
         "held"
      ),
      new MacroModel.Descriptor(
         "builder:inventory.itemDurability",
         "macro_builder.component.builder.inventory.itemDurability.name", "Item Durability",
         "Inventory",
         -1726398,
         TRUE_FALSE_OR_FAILED,
         true,
         "macro_builder.component.builder.inventory.itemDurability.primary",
         "macro_builder.component.builder.inventory.itemDurability.secondary",
         "held"
      ),
      new MacroModel.Descriptor(
         "builder:inventory.emptySlotsAtLeast", "macro_builder.component.builder.inventory.emptySlotsAtLeast.name", "Empty Slots At Least", "Inventory", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.inventory.emptySlotsAtLeast.primary", "", "1"
      ),
      new MacroModel.Descriptor("official:inventory.hasOpenContainer", "macro_builder.component.official.inventory.hasOpenContainer.name", "Has Open GUI", "Inventory", -1726398, TRUE_FALSE_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor("official:inventory.isOpenContainerFull", "macro_builder.component.official.inventory.isOpenContainerFull.name", "Open GUI Is Full", "Inventory", -1726398, TRUE_FALSE_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor(
         "builder:inventory.openContainerHasItem",
         "macro_builder.component.builder.inventory.openContainerHasItem.name", "Open GUI Has Item",
         "Inventory",
         -1726398,
         TRUE_FALSE_OR_FAILED,
         true,
         "macro_builder.component.builder.inventory.openContainerHasItem.primary",
         "macro_builder.component.builder.inventory.openContainerHasItem.secondary",
         "minecraft:bone_block"
      ),
      new MacroModel.Descriptor(
         "builder:inventory.autoEnchant",
         "macro_builder.component.builder.inventory.autoEnchant.name", "Auto Enchant",
         "Inventory",
         -3040666,
         COMPLETED_OR_FAILED,
         true,
         "macro_builder.component.builder.inventory.autoEnchant.primary",
         "macro_builder.component.builder.inventory.autoEnchant.secondary",
         "3"
      ),
      new MacroModel.Descriptor(
         "builder:inventory.autoGrindstone",
         "macro_builder.component.builder.inventory.autoGrindstone.name", "Auto Grindstone",
         "Inventory",
         -3040666,
         COMPLETED_OR_FAILED,
         true,
         "macro_builder.component.builder.inventory.autoGrindstone.primary",
         "macro_builder.component.builder.inventory.autoGrindstone.secondary",
         "disenchant"
      ),
      new MacroModel.Descriptor("official:inventory.openInventory", "macro_builder.component.official.inventory.openInventory.name", "Open Inventory", "Inventory", -3040666, COMPLETED_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor("official:inventory.closeOpenContainer", "macro_builder.component.official.inventory.closeOpenContainer.name", "Close GUI", "Inventory", -2069387, COMPLETED_OR_FAILED, true, "", "", ""),
      new MacroModel.Descriptor(
         "builder:inventory.selectHotbarSlot", "macro_builder.component.builder.inventory.selectHotbarSlot.name", "Select Hotbar Slot", "Inventory", -3040666, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.inventory.selectHotbarSlot.primary", "", "1"
      ),
      new MacroModel.Descriptor(
         "builder:inventory.dropSelectedItem", "macro_builder.component.builder.inventory.dropSelectedItem.name", "Drop Selected Item", "Inventory", -3040666, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.inventory.dropSelectedItem.primary", "", "false"
      ),
      new MacroModel.Descriptor(
         "official:world.interactWithBlock", "macro_builder.component.official.world.interactWithBlock.name", "Interact With Block", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.official.world.interactWithBlock.primary", "macro_builder.component.official.world.interactWithBlock.secondary", ""
      ),
      new MacroModel.Descriptor(
         "official:world.mineBlock", "macro_builder.component.official.world.mineBlock.name", "Mine Block", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.official.world.mineBlock.primary", "macro_builder.component.official.world.mineBlock.secondary", ""
      ),
      new MacroModel.Descriptor(
         "builder:world.mineArea", "macro_builder.component.builder.world.mineArea.name", "Mine Area", "World", -11094334, COMPLETED_TOOL_LOW_OR_FAILED, true, "macro_builder.component.builder.world.mineArea.primary", "macro_builder.component.builder.world.mineArea.secondary", "0 64 0"
      ),
      new MacroModel.Descriptor("official:world.placeBlock", "macro_builder.component.official.world.placeBlock.name", "Place Block", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.official.world.placeBlock.primary", "macro_builder.component.official.world.placeBlock.secondary", ""),
      new MacroModel.Descriptor(
         "official:world.jumpAndPlaceBlock", "macro_builder.component.official.world.jumpAndPlaceBlock.name", "Jump And Place Block", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.official.world.jumpAndPlaceBlock.primary", "", ""
      ),
      new MacroModel.Descriptor(
         "builder:world.farmArea", "macro_builder.component.builder.world.farmArea.name", "Farm Area", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.world.farmArea.primary", "macro_builder.component.builder.world.farmArea.secondary", "0 64 0"
      ),
      new MacroModel.Descriptor(
         "builder:world.openNearestContainer", "macro_builder.component.builder.world.openNearestContainer.name", "Open Nearest Container", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.world.openNearestContainer.primary", "macro_builder.component.builder.world.openNearestContainer.secondary", "8"
      ),
      new MacroModel.Descriptor(
         "builder:world.openNearestChest", "macro_builder.component.builder.world.openNearestChest.name", "Open Nearest Chest", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.world.openNearestChest.primary", "macro_builder.component.builder.world.openNearestChest.secondary", "8"
      ),
      new MacroModel.Descriptor(
         "builder:world.openNearestBarrel", "macro_builder.component.builder.world.openNearestBarrel.name", "Open Nearest Barrel", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.world.openNearestBarrel.primary", "macro_builder.component.builder.world.openNearestBarrel.secondary", "8"
      ),
      new MacroModel.Descriptor(
         "builder:world.openNearestShulker", "macro_builder.component.builder.world.openNearestShulker.name", "Open Nearest Shulker", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.world.openNearestShulker.primary", "macro_builder.component.builder.world.openNearestShulker.secondary", "8"
      ),
      new MacroModel.Descriptor(
         "builder:world.openNearestEnchantingTable",
         "macro_builder.component.builder.world.openNearestEnchantingTable.name", "Open Nearest Enchanting Table",
         "World",
         -11094334,
         COMPLETED_OR_FAILED,
         true,
         "macro_builder.component.builder.world.openNearestEnchantingTable.primary",
         "macro_builder.component.builder.world.openNearestEnchantingTable.secondary",
         "8"
      ),
      new MacroModel.Descriptor(
         "builder:world.openNearestGrindstone",
         "macro_builder.component.builder.world.openNearestGrindstone.name", "Open Nearest Grindstone",
         "World",
         -11094334,
         COMPLETED_OR_FAILED,
         true,
         "macro_builder.component.builder.world.openNearestGrindstone.primary",
         "macro_builder.component.builder.world.openNearestGrindstone.secondary",
         "8"
      ),
      new MacroModel.Descriptor(
         "builder:world.autoBoneMeal", "macro_builder.component.builder.world.autoBoneMeal.name", "Auto Bone Meal", "World", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.world.autoBoneMeal.primary", "macro_builder.component.builder.world.autoBoneMeal.secondary", "minecraft:bone_meal"
      ),
      new MacroModel.Descriptor("builder:world.blockIs", "macro_builder.component.builder.world.blockIs.name", "Block At Location Is", "World", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.world.blockIs.primary", "macro_builder.component.builder.world.blockIs.secondary", "0 64 0"),
      new MacroModel.Descriptor("builder:world.lookingAtBlock", "macro_builder.component.builder.world.lookingAtBlock.name", "Looking At Block", "World", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.world.lookingAtBlock.primary", "", ""),
      new MacroModel.Descriptor(
         "official:entity.attack", "macro_builder.component.official.entity.attack.name", "Attack Entity", "Entity", -3770147, COMPLETED_OR_FAILED, true, "macro_builder.component.official.entity.attack.primary", "macro_builder.component.official.entity.attack.secondary", "minecraft:zombie"
      ),
      new MacroModel.Descriptor(
         "official:entity.interact", "macro_builder.component.official.entity.interact.name", "Interact Entity", "Entity", -3770147, COMPLETED_OR_FAILED, true, "macro_builder.component.official.entity.interact.primary", "macro_builder.component.official.entity.interact.secondary", "minecraft:villager"
      ),
      new MacroModel.Descriptor(
         "builder:entity.nearby", "macro_builder.component.builder.entity.nearby.name", "Entity Nearby", "Entity", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.entity.nearby.primary", "macro_builder.component.builder.entity.nearby.secondary", "minecraft:zombie"
      ),
      new MacroModel.Descriptor("builder:localMessage", "macro_builder.component.builder.localMessage.name", "Local Message", "Action", -10375185, COMPLETED_OR_FAILED, true, "macro_builder.component.builder.localMessage.primary", "", "Macro message"),
      new MacroModel.Descriptor("builder:misc.repeatMacro", "macro_builder.component.builder.misc.repeatMacro.name", "Repeat Macro", "Flow", -7366491, List.of(), true, "macro_builder.component.builder.misc.repeatMacro.primary", "", "3"),
      new MacroModel.Descriptor("builder:misc.repeatSection", "macro_builder.component.builder.misc.repeatSection.name", "Repeat", "Flow", -7366491, REPEAT_COMPLETED_OR_FAILED, true, "macro_builder.component.builder.misc.repeatSection.primary", "", "3"),
      new MacroModel.Descriptor(
         "builder:misc.repeatUntil", "macro_builder.component.builder.misc.repeatUntil.name", "Repeat Until", "Flow", -7366491, REPEAT_COMPLETED_OR_FAILED, true, "macro_builder.component.builder.misc.repeatUntil.primary", "macro_builder.component.builder.misc.repeatUntil.secondary", "actionbar"
      ),
      new MacroModel.Descriptor(
         "builder:misc.idleUntil", "macro_builder.component.builder.misc.idleUntil.name", "Idle Until", "Flow", -7366491, List.of("triggered", "failed"), true, "macro_builder.component.builder.misc.idleUntil.primary", "macro_builder.component.builder.misc.idleUntil.secondary", "chat"
      ),
      new MacroModel.Descriptor(
         "builder:flow.skipIfTrue", "macro_builder.component.builder.flow.skipIfTrue.name", "Skip If True", "Flow", -7366491, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.flow.skipIfTrue.primary", "macro_builder.component.builder.flow.skipIfTrue.secondary", "true"
      ),
      new MacroModel.Descriptor("builder:flow.endConnection", "macro_builder.component.builder.flow.endConnection.name", "End Connection", "Flow", -7366491, List.of(), true, "", "", ""),
      new MacroModel.Descriptor("builder:flow.note", "macro_builder.component.builder.flow.note.name", "Note", "Flow", -7366491, List.of(), true, "macro_builder.component.builder.flow.note.primary", "", "Write notes here"),
      new MacroModel.Descriptor("builder:stop", "macro_builder.component.builder.stop.name", "Stop Macro", "Flow", -7366491, List.of(), true, "", "", ""),
      new MacroModel.Descriptor("official:misc.disconnect", "macro_builder.component.official.misc.disconnect.name", "Disconnect", "Misc", -2069387, COMPLETED_OR_FAILED, true, "macro_builder.component.official.misc.disconnect.primary", "", "true"),
      new MacroModel.Descriptor("official:misc.rejoinServer", "macro_builder.component.official.misc.rejoinServer.name", "Rejoin Server", "Misc", -2069387, COMPLETED_OR_FAILED, true, "macro_builder.component.official.misc.rejoinServer.primary", "macro_builder.component.official.misc.rejoinServer.secondary", "3000"),
      new MacroModel.Descriptor("official:misc.joinServer", "macro_builder.component.official.misc.joinServer.name", "Join Server", "Misc", -2069387, COMPLETED_OR_FAILED, true, "macro_builder.component.official.misc.joinServer.primary", "macro_builder.component.official.misc.joinServer.secondary", ""),
      new MacroModel.Descriptor("official:misc.macroStop", "macro_builder.component.official.misc.macroStop.name", "Stop Macro Slot", "Misc", -7366491, COMPLETED_OR_FAILED, true, "macro_builder.component.official.misc.macroStop.primary", "", ""),
      new MacroModel.Descriptor("official:misc.macroStart", "macro_builder.component.official.misc.macroStart.name", "Start Macro Slot", "Misc", -7366491, COMPLETED_OR_FAILED, true, "macro_builder.component.official.misc.macroStart.primary", "", ""),
      new MacroModel.Descriptor("official:misc.random", "macro_builder.component.official.misc.random.name", "Random True/False", "Misc", -11094334, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.official.misc.random.primary", "", "50"),
      new MacroModel.Descriptor("builder:misc.randomOutput3", "macro_builder.component.builder.misc.randomOutput3.name", "Random Output 3", "Misc", -11094334, RANDOM_OUTPUT_3, true, "", "", ""),
      new MacroModel.Descriptor(
         "official:misc.updateSign", "macro_builder.component.official.misc.updateSign.name", "Update Sign", "Misc", -11094334, COMPLETED_OR_FAILED, true, "macro_builder.component.official.misc.updateSign.primary", "", ""
      ),
      new MacroModel.Descriptor("official:misc.isInLobby", "macro_builder.component.official.misc.isInLobby.name", "Is In Lobby", "Misc", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.official.misc.isInLobby.primary", "", ""),
      new MacroModel.Descriptor(
         "official:notification.discord", "macro_builder.component.official.notification.discord.name", "Discord Notification", "Notification", -10983950, COMPLETED_OR_FAILED, true, "macro_builder.component.official.notification.discord.primary", "macro_builder.component.official.notification.discord.secondary", ""
      ),
      new MacroModel.Descriptor("official:login.repeat", "macro_builder.component.official.login.repeat.name", "Login Repeat", "Login", -4794025, COMPLETED_OR_FAILED, true, "macro_builder.component.official.login.repeat.primary", "", "2"),
      new MacroModel.Descriptor(
         "builder:event.ifChatSame", "macro_builder.component.builder.event.ifChatSame.name", "If Chat Same", "Event", -1726398, TRUE_FALSE_OR_FAILED, true, "macro_builder.component.builder.event.ifChatSame.primary", "macro_builder.component.builder.event.ifChatSame.secondary", ""
      ),
      new MacroModel.Descriptor(
         "official:event.chat", "macro_builder.component.official.event.chat.name", "Event: Chat", "Event", -10375185, List.of("triggered"), true, "macro_builder.component.official.event.chat.primary", "macro_builder.component.official.event.chat.secondary", ""
      ),
      new MacroModel.Descriptor(
         "builder:event.actionbar", "macro_builder.component.builder.event.actionbar.name", "Event: Actionbar", "Event", -10375185, List.of("triggered"), true, "macro_builder.component.builder.event.actionbar.primary", "macro_builder.component.builder.event.actionbar.secondary", ""
      ),
      new MacroModel.Descriptor(
         "builder:event.statusEffect", "macro_builder.component.builder.event.statusEffect.name", "Event: Status Effect", "Event", -10375185, List.of("triggered"), true, "macro_builder.component.builder.event.statusEffect.primary", "macro_builder.component.builder.event.statusEffect.secondary", "minecraft:speed"
      ),
      new MacroModel.Descriptor(
         "builder:event.bossbar", "macro_builder.component.builder.event.bossbar.name", "Event: Bossbar", "Event", -10375185, List.of("triggered"), true, "macro_builder.component.builder.event.bossbar.primary", "macro_builder.component.builder.event.bossbar.secondary", "Litemacro Test"
      ),
      new MacroModel.Descriptor(
         "builder:event.kicked", "macro_builder.component.builder.event.kicked.name", "If Kicked", "Event", -10375185, List.of("triggered"), true, "macro_builder.component.builder.event.kicked.primary", "macro_builder.component.builder.event.kicked.secondary", ""
      ),
      new MacroModel.Descriptor("official:event.death", "macro_builder.component.official.event.death.name", "Event: Death", "Event", -10375185, List.of("triggered"), true, "", "", ""),
      new MacroModel.Descriptor("official:event.damageReceived", "macro_builder.component.official.event.damageReceived.name", "Event: Damage", "Event", -10375185, List.of("triggered"), true, "", "", ""),
      new MacroModel.Descriptor("official:event.teleport", "macro_builder.component.official.event.teleport.name", "Event: Teleport", "Event", -10375185, List.of("triggered"), true, "", "", ""),
      new MacroModel.Descriptor(
         "official:event.schedule", "macro_builder.component.official.event.schedule.name", "Event: Schedule", "Event", -10375185, List.of("triggered"), true, "macro_builder.component.official.event.schedule.primary", "macro_builder.component.official.event.schedule.secondary", "* * * * *"
      ),
      new MacroModel.Descriptor(
         "official:event.player_spawned",
         "macro_builder.component.official.event.player_spawned.name", "Event: Player Spawned",
         "Event",
         -10375185,
         List.of("triggered"),
         true,
         "macro_builder.component.official.event.player_spawned.primary",
         "macro_builder.component.official.event.player_spawned.secondary",
         ""
      ),
      new MacroModel.Descriptor(
         "official:event.player_despawned",
         "macro_builder.component.official.event.player_despawned.name", "Event: Player Despawned",
         "Event",
         -10375185,
         List.of("triggered"),
         true,
         "macro_builder.component.official.event.player_despawned.primary",
         "macro_builder.component.official.event.player_despawned.secondary",
         ""
      )
   );
   private final List<MacroModel.Node> nodes = new ArrayList<>();
   private final Map<String, MacroModel.Node> nodesById = new HashMap<>();
   private int nextIndex = 1;
   private String name = "Macro 1";
   private int stepDelayMs;
   private boolean alwaysOn;
   private String autoStartMode = "off";
   private String autoStartFilter = "";
   private boolean randomDelayEnabled;
   private int randomDelayMinMs = 250;
   private int randomDelayMaxMs = 1250;

   static MacroModel createDefault() {
      MacroModel model = new MacroModel();
      MacroModel.Node start = model.addNode("official:start", 220, 120);
      start.outputs.clear();
      return model;
   }

   static MacroModel fromMineBotExport(InputStream input, String name) throws IOException {
      MacroModel var3;
      try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
         var3 = fromMineBotExport(reader, name);
      }

      return var3;
   }

   static MacroModel fromMineBotExport(Reader reader, String name) {
      JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
      JsonArray componentArray = root.getAsJsonArray("components");
      if (componentArray != null && !componentArray.isEmpty()) {
         MacroModel model = new MacroModel();
         model.setName(name);

         for (JsonElement componentElement : componentArray) {
            JsonObject componentObject = componentElement.getAsJsonObject();
            String type = componentType(componentObject);
            MacroModel.Descriptor descriptor = descriptor(type);
            if (descriptor.type().equals(type) || "official:misc.wait".equals(type)) {
               JsonObject data = componentObject.getAsJsonObject("data");
               String id = string(data, "instanceReferenceId", model.nextId());
               JsonObject position = componentObject.getAsJsonObject("render").getAsJsonObject("position");
               MacroModel.Node node = new MacroModel.Node(id, type, integer(position, "x", 240), integer(position, "y", 140));
               applyMineBotVariables(node, data);
               readMineBotOutputs(node, data);
               model.addLoadedNode(node);
            }
         }

         if (model.startNode() == null) {
            model.addNode("official:start", 220, 120);
         }

         model.repairStartOutputKey();
         model.repairOutputKeys();
         model.bumpNextIndexPastExistingIds();
         return model;
      } else {
         throw new IllegalArgumentException("MineBot export has no components.");
      }
   }

   static MacroModel load(Path path) throws IOException {
      if (Files.notExists(path)) {
         MacroModel model = createDefault();
         model.save(path);
         return model;
      } else {
         MacroModel var15;
         try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            MacroModel model = new MacroModel();
            model.name = string(root, "name", "Macro 1");
            model.nextIndex = Math.max(1, integer(root, "nextIndex", 1));
            model.stepDelayMs = Math.max(0, integer(root, "stepDelayMs", 0));
            model.alwaysOn = booleanValue(root.get("alwaysOn"), false);
            model.autoStartMode = normalizeAutoStartMode(string(root, "autoStartMode", "off"));
            model.autoStartFilter = string(root, "autoStartFilter", "");
            model.randomDelayEnabled = booleanValue(root.get("randomDelayEnabled"), false);
            model.randomDelayMinMs = Math.max(0, integer(root, "randomDelayMinMs", 250));
            model.randomDelayMaxMs = Math.max(model.randomDelayMinMs, integer(root, "randomDelayMaxMs", 1250));
            JsonArray nodeArray = root.getAsJsonArray("nodes");
            if (nodeArray != null) {
               for (JsonElement nodeElement : nodeArray) {
                  JsonObject nodeObject = nodeElement.getAsJsonObject();
                  MacroModel.Node node = new MacroModel.Node(
                     string(nodeObject, "id", model.nextId()),
                     string(nodeObject, "type", "official:misc.wait"),
                     integer(nodeObject, "x", 240),
                     integer(nodeObject, "y", 140)
                  );
                  node.displayName = string(nodeObject, "displayName", "");
                  node.value = string(nodeObject, "value", descriptor(node.type).defaultValue());
                  node.value2 = string(nodeObject, "value2", descriptor(node.type).defaultValue2());
                  node.value3 = string(nodeObject, "value3", defaultValue3(node.type));
                  node.value4 = string(nodeObject, "value4", defaultValue4(node.type));
                  node.value5 = string(nodeObject, "value5", defaultValue5(node.type));
                  node.delayMs = Math.max(-1, integer(nodeObject, "delayMs", -1));
                  node.enabled = booleanValue(nodeObject.get("enabled"), true);
                  if (BUILDER_NOTE.equals(node.type)) {
                     node.value = string(nodeObject, "stickyNoteText", node.value);
                     node.noteWidth = Math.max(0, integer(nodeObject, "stickyNoteWidth", 0));
                     node.noteHeight = Math.max(0, integer(nodeObject, "stickyNoteHeight", 0));
                  }

                  JsonObject outputsObject = nodeObject.getAsJsonObject("outputs");
                  if (outputsObject != null) {
                     for (String key : outputsObject.keySet()) {
                        node.outputs.put(key, outputString(outputsObject.get(key)));
                     }
                  }

                  JsonObject routesObject = nodeObject.getAsJsonObject("routes");
                  if (routesObject != null) {
                     for (String key : routesObject.keySet()) {
                        String route = string(routesObject, key, "");
                        if (!route.isBlank()) {
                           node.connectionRoutes.put(key, route);
                        }
                     }
                  }

                  JsonObject colorsObject = nodeObject.getAsJsonObject("linkColors");
                  if (colorsObject != null) {
                     for (String key : colorsObject.keySet()) {
                        String color = string(colorsObject, key, "");
                        if (!color.isBlank()) {
                           node.connectionColors.put(key, color);
                        }
                     }
                  }

                  model.addLoadedNode(node);
               }
            }

            if (model.startNode() == null) {
               model.addNode("official:start", 220, 120);
            }

            model.repairStartOutputKey();
            model.repairOutputKeys();
            model.bumpNextIndexPastExistingIds();
            var15 = model;
         }

         return var15;
      }
   }

   static MacroModel fromJson(String json) throws IOException {
      Path temp = Files.createTempFile("litemacro-snapshot-", ".json");

      try {
         Files.writeString(temp, json == null ? "" : json, StandardCharsets.UTF_8);
         return load(temp);
      } finally {
         Files.deleteIfExists(temp);
      }
   }

   void restoreFromJson(String json) throws IOException {
      MacroModel restored = fromJson(json);
      this.nodes.clear();
      this.nodes.addAll(restored.nodes);
      this.rebuildNodeIndex();
      this.nextIndex = restored.nextIndex;
      this.name = restored.name;
      this.stepDelayMs = restored.stepDelayMs;
      this.alwaysOn = restored.alwaysOn;
      this.autoStartMode = restored.autoStartMode;
      this.autoStartFilter = restored.autoStartFilter;
      this.randomDelayEnabled = restored.randomDelayEnabled;
      this.randomDelayMinMs = restored.randomDelayMinMs;
      this.randomDelayMaxMs = restored.randomDelayMaxMs;
   }

   void save(Path path) throws IOException {
      Files.createDirectories(path.getParent());
      Files.writeString(path, this.toJson());
   }

   String toJson() {
      JsonObject root = new JsonObject();
      root.addProperty("format", "litemacro-v1");
      root.addProperty("name", this.name);
      root.addProperty("stepDelayMs", this.stepDelayMs);
      root.addProperty("alwaysOn", this.alwaysOn);
      root.addProperty("autoStartMode", this.autoStartMode);
      root.addProperty("autoStartFilter", this.autoStartFilter);
      root.addProperty("randomDelayEnabled", this.randomDelayEnabled);
      root.addProperty("randomDelayMinMs", this.randomDelayMinMs);
      root.addProperty("randomDelayMaxMs", this.randomDelayMaxMs);
      root.addProperty("nextIndex", this.nextIndex);
      JsonArray nodeArray = new JsonArray();

      for (MacroModel.Node node : this.nodes) {
         JsonObject nodeObject = new JsonObject();
         nodeObject.addProperty("id", node.id);
         nodeObject.addProperty("type", node.type);
         nodeObject.addProperty("x", node.x);
         nodeObject.addProperty("y", node.y);
         nodeObject.addProperty("displayName", node.displayName);
         nodeObject.addProperty("value", node.value);
         nodeObject.addProperty("value2", node.value2);
         nodeObject.addProperty("value3", node.value3);
         nodeObject.addProperty("value4", node.value4);
         nodeObject.addProperty("value5", node.value5);
         nodeObject.addProperty("delayMs", node.delayMs);
         nodeObject.addProperty("enabled", node.enabled);
         if (BUILDER_NOTE.equals(node.type)) {
            nodeObject.addProperty("stickyNoteText", node.value == null ? "" : node.value);
            nodeObject.addProperty("stickyNoteWidth", node.noteWidth);
            nodeObject.addProperty("stickyNoteHeight", node.noteHeight);
         }

         JsonObject outputsObject = new JsonObject();

         for (Entry<String, String> output : node.outputs.entrySet()) {
            outputsObject.addProperty(output.getKey(), output.getValue());
         }

         nodeObject.add("outputs", outputsObject);
         if (!node.connectionRoutes.isEmpty()) {
            JsonObject routesObject = new JsonObject();

            for (Entry<String, String> route : node.connectionRoutes.entrySet()) {
               routesObject.addProperty(route.getKey(), route.getValue());
            }

            nodeObject.add("routes", routesObject);
         }

         if (!node.connectionColors.isEmpty()) {
            JsonObject colorsObject = new JsonObject();

            for (Entry<String, String> color : node.connectionColors.entrySet()) {
               colorsObject.addProperty(color.getKey(), color.getValue());
            }

            nodeObject.add("linkColors", colorsObject);
         }

         nodeArray.add(nodeObject);
      }

      root.add("nodes", nodeArray);
      return GSON.toJson(root);
   }

   MacroModel.Node addNode(String type, int x, int y) {
      MacroModel.Descriptor descriptor = descriptor(type);
      MacroModel.Node node = new MacroModel.Node(this.nextId(), descriptor.type(), x, y);
      node.value = descriptor.defaultValue();
      node.value2 = descriptor.defaultValue2();
      node.value3 = defaultValue3(descriptor.type());
      node.value4 = defaultValue4(descriptor.type());
      node.value5 = defaultValue5(descriptor.type());
      node.delayMs = -1;
      this.addLoadedNode(node);
      return node;
   }

   private void addLoadedNode(MacroModel.Node node) {
      this.nodes.add(node);
      this.nodesById.put(node.id, node);
   }

   private void rebuildNodeIndex() {
      this.nodesById.clear();

      for (MacroModel.Node node : this.nodes) {
         this.nodesById.put(node.id, node);
      }
   }

   boolean removeNode(MacroModel.Node node) {
      if (node != null && (!"official:start".equals(node.type) || this.startNodes().size() > 1)) {
         boolean removed = this.nodes.remove(node);
         if (removed) {
            this.rebuildNodeIndex();
            for (MacroModel.Node existing : this.nodes) {
               Iterator<Entry<String, String>> iterator = existing.outputs.entrySet().iterator();

               while (iterator.hasNext()) {
                  Entry<String, String> output = iterator.next();
                  String remainingTargets = removeOutputTarget(output.getValue(), node.id);
                     if (remainingTargets.isBlank()) {
                        iterator.remove();
                        existing.clearConnectionRoutesForOutput(output.getKey());
                        existing.clearConnectionColorsForOutput(output.getKey());
                     } else {
                        if (!remainingTargets.equals(output.getValue())) {
                           existing.removeConnectionRoute(output.getKey(), node.id);
                           existing.removeConnectionColor(output.getKey(), node.id);
                        }

                        output.setValue(remainingTargets);
                  }
               }
            }
         }

         return removed;
      } else {
         return false;
      }
   }

   MacroModel.Node startNode() {
      for (MacroModel.Node node : this.nodes) {
         if ("official:start".equals(node.type)) {
            return node;
         }
      }

      return null;
   }

   List<MacroModel.Node> startNodes() {
      List<MacroModel.Node> starts = new ArrayList<>();

      for (MacroModel.Node node : this.nodes) {
         if ("official:start".equals(node.type)) {
            starts.add(node);
         }
      }

      return starts;
   }

   MacroModel.Node node(String id) {
      return id == null ? null : this.nodesById.get(id);
   }

   List<MacroModel.Node> nodes() {
      return this.nodes;
   }

   boolean hasAnyConnection(MacroModel.Node node) {
      return node != null && (!node.outputs.isEmpty() || this.hasIncomingConnection(node));
   }

   boolean clearConnections(MacroModel.Node node) {
      if (node == null) {
         return false;
      }

      boolean changed = !node.outputs.isEmpty() || !node.connectionRoutes.isEmpty() || !node.connectionColors.isEmpty();
      node.outputs.clear();
      node.connectionRoutes.clear();
      node.connectionColors.clear();

      for (MacroModel.Node existing : this.nodes) {
         if (existing == node) {
            continue;
         }

         Iterator<Entry<String, String>> iterator = existing.outputs.entrySet().iterator();
         while (iterator.hasNext()) {
            Entry<String, String> output = iterator.next();
            String before = output.getValue();
            String remainingTargets = removeOutputTarget(before, node.id);
            if (!remainingTargets.equals(before)) {
               changed = true;
               existing.removeConnectionRoute(output.getKey(), node.id);
               existing.removeConnectionColor(output.getKey(), node.id);
               if (remainingTargets.isBlank()) {
                  iterator.remove();
                  existing.clearConnectionRoutesForOutput(output.getKey());
                  existing.clearConnectionColorsForOutput(output.getKey());
               } else {
                  output.setValue(remainingTargets);
               }
            }
         }
      }

      return changed;
   }

   boolean removeConnection(MacroModel.Node source, String outputKey, String targetId) {
      if (source == null || outputKey == null || targetId == null) {
         return false;
      }

      String before = source.outputs.get(outputKey);
      if (before == null || before.isBlank()) {
         source.removeConnectionRoute(outputKey, targetId);
         source.removeConnectionColor(outputKey, targetId);
         return false;
      }

      String remainingTargets = removeOutputTarget(before, targetId);
      boolean changed = !remainingTargets.equals(before);
      source.removeConnectionRoute(outputKey, targetId);
      source.removeConnectionColor(outputKey, targetId);
      if (remainingTargets.isBlank()) {
         source.outputs.remove(outputKey);
         source.clearConnectionRoutesForOutput(outputKey);
         source.clearConnectionColorsForOutput(outputKey);
      } else {
         source.outputs.put(outputKey, remainingTargets);
      }

      return changed;
   }

   private boolean hasIncomingConnection(MacroModel.Node node) {
      for (MacroModel.Node existing : this.nodes) {
         if (existing != node) {
            for (String targetList : existing.outputs.values()) {
               if (outputTargets(targetList).contains(node.id)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   String name() {
      return this.name;
   }

   void setName(String name) {
      String trimmed = name == null ? "" : name.trim();
      this.name = trimmed.isEmpty() ? "Macro 1" : trimmed;
   }

   int stepDelayMs() {
      return this.stepDelayMs;
   }

   void setStepDelayMs(int stepDelayMs) {
      this.stepDelayMs = Math.max(0, stepDelayMs);
   }

   boolean alwaysOn() {
      return this.alwaysOn;
   }

   void setAlwaysOn(boolean alwaysOn) {
      this.alwaysOn = alwaysOn;
   }

   String autoStartMode() {
      return this.autoStartMode;
   }

   void setAutoStartMode(String autoStartMode) {
      this.autoStartMode = normalizeAutoStartMode(autoStartMode);
   }

   String autoStartFilter() {
      return this.autoStartFilter;
   }

   void setAutoStartFilter(String autoStartFilter) {
      this.autoStartFilter = autoStartFilter == null ? "" : autoStartFilter.trim();
   }

   boolean randomDelayEnabled() {
      return this.randomDelayEnabled;
   }

   void setRandomDelayEnabled(boolean randomDelayEnabled) {
      this.randomDelayEnabled = randomDelayEnabled;
   }

   int randomDelayMinMs() {
      return this.randomDelayMinMs;
   }

   int randomDelayMaxMs() {
      return this.randomDelayMaxMs;
   }

   void setRandomDelayRangeMs(int minMs, int maxMs) {
      int safeMin = Math.max(0, minMs);
      int safeMax = Math.max(0, maxMs);
      this.randomDelayMinMs = Math.min(safeMin, safeMax);
      this.randomDelayMaxMs = Math.max(safeMin, safeMax);
   }

   static List<MacroModel.Descriptor> paletteDescriptors() {
      List<MacroModel.Descriptor> palette = new ArrayList<>();

      for (MacroModel.Descriptor descriptor : DESCRIPTORS) {
         if (descriptor.inPalette()) {
            palette.add(descriptor);
         }
      }

      return palette;
   }

   static MacroModel.Descriptor descriptor(String type) {
      for (MacroModel.Descriptor descriptor : DESCRIPTORS) {
         if (descriptor.type().equals(type)) {
            return descriptor;
         }
      }

      return DESCRIPTORS.stream().filter(descriptorx -> "official:misc.wait".equals(descriptorx.type())).findFirst().orElse(DESCRIPTORS.get(0));
   }

   static boolean runsInMainMenu(String type) {
      return switch (type) {
         case "official:start",
            "official:misc.wait",
            "builder:flow.endConnection",
            "builder:flow.note",
            "builder:localMessage",
            "builder:misc.repeatMacro",
            "builder:misc.repeatSection",
            "builder:misc.repeatUntil",
            "builder:misc.idleUntil",
            "builder:flow.skipIfTrue",
            "builder:stop",
            "official:misc.rejoinServer",
            "official:misc.joinServer",
            "official:misc.macroStart",
            "official:misc.macroStop",
            "official:misc.random",
            "builder:misc.randomOutput3",
            "official:notification.discord",
            "builder:event.actionbar",
            "builder:event.kicked" -> true;
         default -> false;
      };
   }

   private static String description(String type) {
      return switch (type) {
         case "official:start" -> tr("macro_builder.description.first_block_every_macro_starts_from");
         case "official:player.respawn" -> tr("macro_builder.description.clicks_respawn_when_the_player_is_dead");
         case "official:player.move" -> tr("macro_builder.description.moves_toward_a_direction_place_or_entity");
         case "official:player.look" -> tr("macro_builder.description.turns_the_player_toward_a_target");
         case "builder:player.recordedActions" -> tr("macro_builder.description.records_movement_gameplay_keys_inventory_moves_and_sent_chat");
         case "official:player.setMouseButton" -> tr("macro_builder.description.presses_or_releases_left_right_click");
         case "official:player.shootBow" -> tr("macro_builder.description.aims_and_fires_a_bow");
         case "official:player.isAccount" -> tr("macro_builder.description.checks_if_your_username_matches");
         case "official:player.inAction" -> tr("macro_builder.description.checks_sneaking_using_dead_or_alive");
         case "official:player.isAlive" -> tr("macro_builder.description.true_when_the_player_is_alive");
         case "official:player.setCrouch" -> tr("macro_builder.description.turns_crouch_on_or_off");
         case "builder:player.setSprint" -> tr("macro_builder.description.turns_sprint_on_or_off");
         case "official:player.jump" -> tr("macro_builder.description.makes_the_player_jump_once");
         case "builder:player.autoClick" -> tr("macro_builder.description.clicks_or_holds_left_right_click_with_a_count_and_interval");
         case "builder:player.autoFish" -> tr("macro_builder.description.casts_detects_a_bite_reels_in_and_switches_fishing_rods_before_they");
         case "builder:player.pressKey" -> tr("macro_builder.description.presses_or_holds_a_keyboard_key_for_a_set_duration");
         case "official:inventory.isFull" -> tr("macro_builder.description.true_when_inventory_has_no_empty_slots");
         case "official:inventory.hasItem", "builder:inventory.hasItem" -> tr("macro_builder.description.checks_for_an_item_and_count");
         case "builder:player.healthBelow" -> tr("macro_builder.description.true_when_health_is_at_or_below_value");
         case "builder:player.foodBelow" -> tr("macro_builder.description.true_when_hunger_is_at_or_below_value");
         case "builder:player.xpLevelAtLeast" -> tr("macro_builder.description.true_when_xp_level_is_high_enough");
         case "builder:player.stopAtXpLevel" -> tr("macro_builder.description.waits_for_xp_level_then_releases_held_actions_or_stops_the_macro");
         case "builder:player.onGround" -> tr("macro_builder.description.true_when_player_is_touching_ground");
         case "builder:player.inWater" -> tr("macro_builder.description.true_when_player_is_in_water");
         case "official:player.isAtLocation" -> tr("macro_builder.description.checks_if_player_is_near_coordinates");
         case "official:entity.player_nearby" -> tr("macro_builder.description.checks_for_nearby_players");
         case "official:misc.wait" -> tr("macro_builder.description.pauses_the_macro_for_a_set_time");
         case "official:misc.chat" -> tr("macro_builder.description.sends_a_chat_message_or_command");
         case "official:inventory.clickOpenContainerSlot" -> tr("macro_builder.description.clicks_a_matching_item_or_numbered_slot_in_open_gui");
         case "official:inventory.hotbarSelect" -> tr("macro_builder.description.selects_a_hotbar_item_or_slot");
         case "official:inventory.hotbarUse" -> tr("macro_builder.description.uses_the_selected_hotbar_item");
         case "official:inventory.dropItems" -> tr("macro_builder.description.drops_matching_inventory_or_hotbar_items");
         case "official:inventory.chestWithdrawItems" -> tr("macro_builder.description.moves_items_from_open_gui_to_inventory");
         case "official:inventory.chestDepositItems" -> tr("macro_builder.description.moves_inventory_items_into_open_gui");
         case "builder:inventory.heldItemIs" -> tr("macro_builder.description.checks_the_item_in_your_main_hand");
         case "builder:inventory.slotHasItem" -> tr("macro_builder.description.checks_an_inventory_slot_for_an_item");
         case "builder:inventory.itemOrSlotHasTag" -> tr("macro_builder.description.checks_item_tags_or_stack_data_text_and_can_click_matching_gui");
         case "builder:inventory.itemDurability" -> tr("macro_builder.description.checks_whether_item_durability_is_lower_or_higher_than_a_percent");
         case "builder:inventory.emptySlotsAtLeast" -> tr("macro_builder.description.checks_free_inventory_slot_count");
         case "official:inventory.hasOpenContainer" -> tr("macro_builder.description.true_when_a_gui_or_chest_is_open");
         case "official:inventory.isOpenContainerFull" -> tr("macro_builder.description.true_when_open_gui_has_no_empty_slots");
         case "builder:inventory.openContainerHasItem" -> tr("macro_builder.description.checks_for_an_item_in_open_gui");
         case "builder:inventory.autoEnchant" -> tr("macro_builder.description.loads_item_lapis_when_possible_and_clicks_an_enchanting_table_option");
         case "builder:inventory.autoGrindstone" -> tr("macro_builder.description.loads_items_handles_one_grindstone_result_and_saves_last_slot_last_item");
         case "official:inventory.openInventory" -> tr("macro_builder.description.opens_the_player_inventory");
         case "official:inventory.closeOpenContainer" -> tr("macro_builder.description.closes_the_current_gui_or_chest");
         case "builder:inventory.selectHotbarSlot" -> tr("macro_builder.description.selects_a_numbered_hotbar_slot");
         case "builder:inventory.dropSelectedItem" -> tr("macro_builder.description.drops_the_held_hotbar_item");
         case "official:world.interactWithBlock" -> tr("macro_builder.description.clicks_or_attacks_a_target_block");
         case "official:world.mineBlock" -> tr("macro_builder.description.mines_a_target_block");
         case "builder:world.mineArea" -> tr("macro_builder.description.mines_a_16x16x16_box_from_the_top_layer_down_and_can_stop");
         case "official:world.placeBlock" -> tr("macro_builder.description.places_a_selected_block_on_target");
         case "official:world.jumpAndPlaceBlock" -> tr("macro_builder.description.jumps_and_places_below_player");
         case "builder:world.farmArea" -> tr("macro_builder.description.harvests_crops_between_two_xyz_points_and_can_move");
         case "builder:world.openNearestContainer" -> tr("macro_builder.description.finds_and_opens_a_selected_container_type");
         case "builder:world.openNearestChest" -> tr("macro_builder.description.finds_and_opens_the_nearest_chest");
         case "builder:world.openNearestBarrel" -> tr("macro_builder.description.finds_and_opens_the_nearest_barrel");
         case "builder:world.openNearestShulker" -> tr("macro_builder.description.finds_and_opens_the_nearest_shulker_box");
         case "builder:world.openNearestEnchantingTable" -> tr("macro_builder.description.finds_and_opens_the_nearest_enchanting_table");
         case "builder:world.openNearestGrindstone" -> tr("macro_builder.description.finds_and_opens_the_nearest_grindstone");
         case "builder:world.autoBoneMeal" -> tr("macro_builder.description.uses_bone_meal_and_can_refill_from_nearby_containers");
         case "builder:world.blockIs" -> tr("macro_builder.description.checks_block_id_at_coordinates");
         case "builder:world.lookingAtBlock" -> tr("macro_builder.description.checks_the_block_you_are_looking_at");
         case "official:entity.attack" -> tr("macro_builder.description.attacks_matching_mob_or_player");
         case "official:entity.interact" -> tr("macro_builder.description.right_clicks_matching_mob_or_player");
         case "builder:entity.nearby" -> tr("macro_builder.description.checks_for_matching_nearby_entities");
         case "builder:logic.scoreboardContains" -> tr("macro_builder.description.checks_sidebar_team_and_objective_scoreboard_text");
         case "builder:logic.actionbarContains" -> tr("macro_builder.description.checks_the_latest_actionbar_text");
         case "builder:logic.hasStatusEffect" -> tr("macro_builder.description.checks_whether_the_player_has_a_matching_status_effect_and_level");
         case "builder:logic.bossbarContains" -> tr("macro_builder.description.checks_all_boss_bars_currently_visible_on_screen");
         case "builder:logic.macroActiveState" -> tr("macro_builder.description.checks_another_macro_and_can_trigger_this_macro_when_its_state_changes");
         case "builder:localMessage" -> tr("macro_builder.description.shows_a_local_chat_notification");
         case "builder:misc.repeatMacro" -> tr("macro_builder.description.repeats_the_whole_macro_a_set_number_of_times_then_stops");
         case "builder:misc.repeatSection" -> tr("macro_builder.description.repeats_a_linked_section_a_set_number_of_times");
         case "builder:misc.repeatUntil" -> tr("macro_builder.description.repeats_a_linked_section_until_a_condition_is_true");
         case "builder:misc.idleUntil" -> tr("macro_builder.description.waits_here_until_chat_actionbar_player_full_inventory_or_disconnect_is_detected");
         case "builder:flow.skipIfTrue" -> tr("macro_builder.description.when_true_jumps_past_a_named_component");
         case "builder:flow.endConnection" -> tr("macro_builder.description.ends_this_connection_path_without_stopping_other_running_branches");
         case "builder:flow.note" -> tr("macro_builder.description.adds_a_saved_note_to_explain_part_of_the_macro");
         case "builder:stop" -> tr("macro_builder.description.stops_the_current_macro_run");
         case "official:misc.disconnect" -> tr("macro_builder.description.disconnects_from_the_server");
         case "official:misc.rejoinServer" -> tr("macro_builder.description.reconnects_to_the_last_multiplayer_server_or_a_typed_server_ip");
         case "official:misc.joinServer" -> tr("macro_builder.description.connects_to_the_typed_multiplayer_server_ip");
         case "official:misc.macroStop" -> tr("macro_builder.description.stops_another_macro_slot");
         case "official:misc.macroStart" -> tr("macro_builder.description.starts_another_macro_slot");
         case "official:misc.random" -> tr("macro_builder.description.randomly_chooses_true_or_false");
         case "builder:misc.randomOutput3" -> tr("macro_builder.description.randomly_chooses_one_of_three_outputs");
         case "official:misc.updateSign" -> tr("macro_builder.description.updates_the_sign_you_are_looking_at");
         case "official:misc.isInLobby" -> tr("macro_builder.description.checks_if_named_player_is_you");
         case "official:notification.discord" -> tr("macro_builder.description.sends_a_discord_webhook_message");
         case "official:login.repeat" -> tr("macro_builder.description.pass_through_login_repeat_marker");
         case "builder:event.ifChatSame" -> tr("macro_builder.description.checks_latest_chat_against_text");
         case "official:event.chat" -> tr("macro_builder.description.triggers_when_matching_chat_appears");
         case "builder:event.actionbar" -> tr("macro_builder.description.triggers_when_matching_actionbar_text_appears");
         case "builder:event.statusEffect" -> tr("macro_builder.description.triggers_once_when_a_matching_status_effect_appears_and_can_start_the");
         case "builder:event.bossbar" -> tr("macro_builder.description.triggers_once_when_a_matching_boss_bar_appears_and_can_start_the");
         case "builder:event.kicked" -> tr("macro_builder.description.triggers_after_a_kick_or_disconnect");
         case "official:event.death" -> tr("macro_builder.description.triggers_when_you_die");
         case "official:event.damageReceived" -> tr("macro_builder.description.triggers_when_you_take_damage");
         case "official:event.teleport" -> tr("macro_builder.description.triggers_after_a_teleport_or_world_change");
         case "official:event.schedule" -> tr("macro_builder.description.triggers_on_a_timer_or_clock_time");
         case "official:event.player_spawned" -> tr("macro_builder.description.triggers_when_matching_player_appears");
         case "official:event.player_despawned" -> tr("macro_builder.description.triggers_when_matching_player_leaves");
         default -> tr("macro_builder.description.macro_component");
      };
   }

   private String nextId() {
      return "node-" + this.nextIndex++;
   }

   private void bumpNextIndexPastExistingIds() {
      int highest = 0;

      for (MacroModel.Node node : this.nodes) {
         if (node.id.startsWith("node-")) {
            try {
               highest = Math.max(highest, Integer.parseInt(node.id.substring("node-".length())));
            } catch (NumberFormatException var5) {
            }
         }
      }

      this.nextIndex = Math.max(this.nextIndex, highest + 1);
   }

   private void repairStartOutputKey() {
      MacroModel.Node start = this.startNode();
      if (start != null && !start.outputs.containsKey("started") && start.outputs.containsKey("completed")) {
         start.outputs.put("started", start.outputs.remove("completed"));
      }
   }

   private void repairOutputKeys() {
      for (MacroModel.Node node : this.nodes) {
         MacroModel.Descriptor descriptor = descriptor(node.type);
         if (descriptor.outputs().isEmpty()) {
            node.outputs.clear();
            node.connectionRoutes.clear();
            node.connectionColors.clear();
         } else {
            boolean hasExpectedOutput = false;
            Iterator target = descriptor.outputs().iterator();

            while (true) {
               if (target.hasNext()) {
                  String expected = (String)target.next();
                  if (!node.outputs.containsKey(expected)) {
                     continue;
                  }

                  hasExpectedOutput = true;
               }

               if (!hasExpectedOutput) {
                  String targetx = firstOutputTarget(node);
                  node.outputs.clear();
                  if (targetx != null && !targetx.isBlank()) {
                     node.outputs.put(primaryRepairOutput(descriptor), targetx);
                  }
               } else {
                  node.outputs.keySet().removeIf(key -> {
                     boolean remove = !descriptor.outputs().contains(key);
                     if (remove) {
                        node.clearConnectionRoutesForOutput(key);
                        node.clearConnectionColorsForOutput(key);
                     }

                     return remove;
                  });
               }
               break;
            }
         }
      }
   }

   private static String primaryRepairOutput(MacroModel.Descriptor descriptor) {
      if (descriptor.outputs().contains("completed")) {
         return "completed";
      } else {
         return descriptor.outputs().contains("started") ? "started" : descriptor.outputs().get(0);
      }
   }

   private static String firstOutputTarget(MacroModel.Node node) {
      for (String target : node.outputs.values()) {
         List<String> targets = outputTargets(target);
         if (!targets.isEmpty()) {
            return targets.get(0);
         }
      }

      return null;
   }

   static List<String> outputTargets(String value) {
      List<String> targets = new ArrayList<>();
      if (value != null && !value.isBlank()) {
         for (String part : value.split("\\R")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank() && !targets.contains(trimmed)) {
               targets.add(trimmed);
            }
         }
      }

      return targets;
   }

   static String addOutputTarget(String value, String targetId) {
      List<String> targets = outputTargets(value);
      String trimmed = targetId == null ? "" : targetId.trim();
      if (!trimmed.isBlank() && !targets.contains(trimmed)) {
         targets.add(trimmed);
      }

      return String.join("\n", targets);
   }

   static String removeOutputTarget(String value, String targetId) {
      List<String> targets = outputTargets(value);
      targets.removeIf(target -> target.equals(targetId));
      return String.join("\n", targets);
   }

   private static String outputString(JsonElement value) {
      if (value == null || value.isJsonNull()) {
         return "";
      } else if (value.isJsonArray()) {
         List<String> targets = new ArrayList<>();

         for (JsonElement element : value.getAsJsonArray()) {
            if (element != null && !element.isJsonNull()) {
               String target = element.getAsString().trim();
               if (!target.isBlank() && !targets.contains(target)) {
                  targets.add(target);
               }
            }
         }

         return String.join("\n", targets);
      } else {
         return value.getAsString();
      }
   }

   private static void applyMineBotVariables(MacroModel.Node node, JsonObject data) {
      Map<String, JsonElement> variables = readMineBotVariables(data);
      if ("official:misc.wait".equals(node.type)) {
         node.value = stringValue(variables.get("duration"), "10000");
      } else if ("official:player.move".equals(node.type)) {
         node.value = firstNonBlank(
            stringValue(variables.get("direction"), ""), locationString(variables.get("location"), ""), stringValue(variables.get("playerTarget"), "")
         );
         node.value2 = stringValue(variables.get("distance"), "1");
      } else if ("builder:misc.idleUntil".equals(node.type)) {
         node.value = stringValue(variables.get("condition"), "chat");
         node.value2 = stringValue(variables.get("filter"), "");
      } else if (!"official:player.look".equals(node.type)) {
         if ("official:player.setMouseButton".equals(node.type)) {
            node.value = stringValue(variables.get("action"), "0");
            node.value2 = stringValue(variables.get("button"), "0");
         } else if ("official:player.shootBow".equals(node.type)) {
            node.value = firstNonBlank(
               stringValue(variables.get("playerTarget"), ""),
               locationString(variables.get("locationClosestTo"), ""),
               stringValue(variables.get("entityFilter"), "")
            );
            node.value2 = stringValue(variables.get("equipBow"), "true");
         } else if ("official:player.isAccount".equals(node.type)) {
            node.value = stringValue(variables.get("list"), "");
         } else if ("official:player.inAction".equals(node.type)) {
            node.value = stringValue(variables.get("state"), "sneaking");
         } else if ("official:player.setCrouch".equals(node.type)) {
            node.value = dropdownStateToBooleanText(variables.get("desiredState"), true);
         } else if ("official:misc.chat".equals(node.type)) {
            node.value = stringValue(variables.get("message"), "");
         } else if ("official:inventory.clickOpenContainerSlot".equals(node.type)) {
            int selector = intValue(variables.get("itemSelector"), 0);
            String slotLocation = stringValue(variables.get("slotLocation"), "");
            node.value = selector == 1 && !slotLocation.isBlank()
               ? "slot " + slotLocation
               : normalizeItemId(stringValue(variables.get("specificItem"), "minecraft:bone_block"));
            node.value2 = Boolean.toString(booleanValue(variables.get("shiftClick"), false));
            node.value3 = stringValue(variables.get("button"), "left");
         } else if ("official:inventory.hotbarSelect".equals(node.type) || "official:inventory.hotbarUse".equals(node.type)) {
            node.value = stringValue(variables.get("specificItem"), "");
            node.value2 = stringValue(variables.get("slotLocation"), "1");
         } else if ("official:inventory.dropItems".equals(node.type)) {
            node.value = stringValue(variables.get("specificItem"), "");
            node.value2 = "false";
            node.value3 = stringValue(variables.get("excludeSlots"), "");
         } else if ("official:inventory.chestWithdrawItems".equals(node.type)) {
            int selector = intValue(variables.get("itemSelector"), 1);
            node.value = selector == 1 ? "all" : "specific";
            node.value2 = normalizeItemId(stringValue(variables.get("specificItem"), "minecraft:bone_block"));
            node.value3 = Boolean.toString(booleanValue(variables.get("shiftClick"), true));
         } else if ("official:inventory.chestDepositItems".equals(node.type)) {
            int selector = intValue(variables.get("itemSelector"), 1);
            node.value = selector == 1 ? "all" : "specific";
            node.value2 = normalizeItemId(stringValue(variables.get("specificItem"), "minecraft:bone_block"));
            node.value3 = Boolean.toString(booleanValue(variables.get("shiftClick"), true));
            node.value5 = stringValue(variables.get("excludeSlots"), "");
         } else if ("official:inventory.hasItem".equals(node.type) || "builder:inventory.hasItem".equals(node.type)) {
            node.value = normalizeItemId(stringValue(variables.get("item"), stringValue(variables.get("specificItem"), "minecraft:bone_block")));
            node.value2 = "1";
         } else if ("official:player.isAtLocation".equals(node.type)) {
            JsonElement location = variables.get("location");
            if (location != null && location.isJsonObject()) {
               JsonObject object = location.getAsJsonObject();
               node.value = doubleString(object.get("x"), "0") + " " + doubleString(object.get("y"), "64") + " " + doubleString(object.get("z"), "0");
            }

            node.value2 = stringValue(variables.get("maxDistance"), "2");
         } else if ("official:entity.player_nearby".equals(node.type)) {
            int filterType = intValue(variables.get("filterType"), 0);
            String filterText = stringValue(variables.get("filterText"), "");
            String regexText = stringValue(variables.get("regexText"), "");
            node.value = filterText.isBlank() ? regexText : filterText;
            node.value2 = filterType == 1 ? "allow" : (filterType == 2 ? "block" : "any");
         } else if ("official:world.interactWithBlock".equals(node.type)
            || "official:world.mineBlock".equals(node.type)
            || "official:world.placeBlock".equals(node.type)) {
            node.value = locationString(variables.get("target"), "");
            node.value2 = firstNonBlank(
               stringValue(variables.get("button"), ""), stringValue(variables.get("selectTool"), ""), stringValue(variables.get("selectBuildingBlock"), "")
            );
         } else if ("official:world.jumpAndPlaceBlock".equals(node.type)) {
            node.value = stringValue(variables.get("selectBuildingBlock"), "");
         } else if ("official:entity.attack".equals(node.type) || "official:entity.interact".equals(node.type)) {
            node.value = firstNonBlank(
               stringValue(variables.get("playerTarget"), ""),
               locationString(variables.get("locationClosestTo"), ""),
               stringValue(variables.get("entityFilter"), "")
            );
            node.value2 = stringValue(variables.get("moveToTarget"), "false");
         } else if ("official:misc.disconnect".equals(node.type)) {
            node.value = stringValue(variables.get("permanent"), "true");
         } else if ("official:misc.macroStart".equals(node.type) || "official:misc.macroStop".equals(node.type)) {
            node.value = stringValue(variables.get("macroId"), "");
         } else if ("official:misc.updateSign".equals(node.type)) {
            node.value = stringValue(variables.get("line1"), "")
               + " | "
               + stringValue(variables.get("line2"), "")
               + " | "
               + stringValue(variables.get("line3"), "")
               + " | "
               + stringValue(variables.get("line4"), "");
         } else if ("official:misc.isInLobby".equals(node.type)) {
            node.value = stringValue(variables.get("player"), "");
         } else if ("official:notification.discord".equals(node.type)) {
            node.value = stringValue(variables.get("target"), "");
            node.value2 = stringValue(variables.get("message"), "");
         } else if ("official:login.repeat".equals(node.type)) {
            node.value = stringValue(variables.get("repeatCount"), "2");
         } else if (!"official:event.chat".equals(node.type)
            && !"official:event.player_spawned".equals(node.type)
            && !"official:event.player_despawned".equals(node.type)) {
            if ("official:event.schedule".equals(node.type)) {
               node.value = firstNonBlank(stringValue(variables.get("time"), ""), stringValue(variables.get("cron"), "* * * * *"));
               node.value2 = stringValue(variables.get("targetType"), "0");
            }
         } else {
            int filterType = intValue(variables.get("filterType"), 0);
            String filterText = stringValue(variables.get("filterText"), "");
            String regexText = stringValue(variables.get("regexText"), "");
            node.value = filterText.isBlank() ? regexText : filterText;
            node.value2 = filterType == 1 ? "allow" : (filterType == 2 ? "block" : "any");
         }
      } else {
         String yaw = stringValue(variables.get("yaw"), "");
         String pitch = stringValue(variables.get("pitch"), "");
         node.value = firstNonBlank(
            yaw.isBlank() && pitch.isBlank() ? "" : yaw + " " + pitch,
            locationString(variables.get("location"), ""),
            stringValue(variables.get("playerTarget"), "")
         );
         node.value2 = stringValue(variables.get("targetType"), "0");
      }
   }

   private static void readMineBotOutputs(MacroModel.Node node, JsonObject data) {
      JsonObject connections = data == null ? null : data.getAsJsonObject("connections");
      JsonArray outputs = connections == null ? null : connections.getAsJsonArray("outputs");
      if (outputs != null) {
         for (JsonElement outputElement : outputs) {
            JsonObject outputObject = outputElement.getAsJsonObject();
            String key = string(outputObject, "key", "");
            String target = string(outputObject, "target", "");
            if (!key.isBlank() && !target.isBlank()) {
               node.outputs.put(key, target);
            }
         }
      }
   }

   private static Map<String, JsonElement> readMineBotVariables(JsonObject data) {
      Map<String, JsonElement> variables = new LinkedHashMap<>();
      JsonObject variablesObject = data == null ? null : data.getAsJsonObject("variables");
      JsonArray values = variablesObject == null ? null : variablesObject.getAsJsonArray("values");
      if (values == null) {
         return variables;
      } else {
         for (JsonElement valueElement : values) {
            JsonObject variableObject = valueElement.getAsJsonObject();
            String id = string(variableObject, "id", "");
            JsonObject wrappedValue = variableObject.getAsJsonObject("value");
            JsonElement value = wrappedValue == null ? null : wrappedValue.get("value");
            if (!id.isBlank() && value != null) {
               variables.put(id, value);
            }
         }

         return variables;
      }
   }

   private static String componentType(JsonObject component) {
      JsonObject metadata = component.getAsJsonObject("metadata");
      return metadata == null ? "" : string(metadata, "type", "");
   }

   private static String stringValue(JsonElement value, String fallback) {
      if (value == null || value.isJsonNull()) {
         return fallback;
      } else {
         return value.isJsonObject() ? fallback : value.getAsString();
      }
   }

   private static String firstNonBlank(String... values) {
      for (String value : values) {
         if (value != null && !value.isBlank()) {
            return value;
         }
      }

      return "";
   }

   private static String dropdownStateToBooleanText(JsonElement value, boolean fallback) {
      int state = intValue(value, fallback ? 1 : 0);
      return Boolean.toString(state != 0);
   }

   private static String locationString(JsonElement value, String fallback) {
      if (value == null || value.isJsonNull()) {
         return fallback;
      } else if (value.isJsonObject()) {
         JsonObject object = value.getAsJsonObject();
         return doubleString(object.get("x"), "0") + " " + doubleString(object.get("y"), "64") + " " + doubleString(object.get("z"), "0");
      } else {
         String text = stringValue(value, fallback);
         return text.isBlank() ? fallback : text;
      }
   }

   private static int intValue(JsonElement value, int fallback) {
      if (value != null && !value.isJsonNull()) {
         try {
            return value.getAsInt();
         } catch (RuntimeException var3) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static boolean booleanValue(JsonElement value, boolean fallback) {
      if (value != null && !value.isJsonNull()) {
         try {
            return value.getAsBoolean();
         } catch (RuntimeException var3) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static String defaultValue3(String type) {
      if ("official:inventory.chestDepositItems".equals(type) || "official:inventory.chestWithdrawItems".equals(type)) {
         return "true";
      } else if ("builder:inventory.autoEnchant".equals(type)) {
         return "held";
      } else if ("builder:inventory.autoGrindstone".equals(type)) {
         return "same";
      } else {
         return "official:inventory.clickOpenContainerSlot".equals(type)
            ? "left"
            : ("builder:inventory.itemOrSlotHasTag".equals(type) ? "false" : "");
      }
   }

   private static String normalizeAutoStartMode(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      return switch (normalized) {
         case "always", "run", "start" -> "always";
         case "chat", "message" -> "chat";
         case "player", "nearby", "player_nearby" -> "player";
         case "inventory", "inventory_full", "full" -> "inventory_full";
         case "kick", "kicked", "disconnect", "disconnected" -> "kicked";
         default -> "off";
      };
   }

   private static String defaultValue4(String type) {
      if ("builder:inventory.autoEnchant".equals(type)) {
         return "minecraft:lapis_lazuli";
      } else if ("builder:inventory.autoGrindstone".equals(type)) {
         return "inventory";
      } else {
         return "official:inventory.chestDepositItems".equals(type) || "official:inventory.dropItems".equals(type) ? "false" : "";
      }
   }

   private static String defaultValue5(String type) {
      return "builder:inventory.autoGrindstone".equals(type) ? "false" : "";
   }

   private static String doubleString(JsonElement value, String fallback) {
      if (value != null && !value.isJsonNull()) {
         try {
            double number = value.getAsDouble();
            return number == Math.rint(number) ? Integer.toString((int)number) : Double.toString(number);
         } catch (RuntimeException var4) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static String string(JsonObject object, String key, String fallback) {
      JsonElement value = object.get(key);
      return value != null && !value.isJsonNull() ? value.getAsString() : fallback;
   }

   private static int integer(JsonObject object, String key, int fallback) {
      JsonElement value = object.get(key);
      if (value != null && !value.isJsonNull()) {
         try {
            return value.getAsInt();
         } catch (RuntimeException var5) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   static String normalizeItemId(String itemId) {
      String normalized = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT).trim();
      if (normalized.isEmpty()) {
         return "minecraft:air";
      } else {
         return normalized.contains(":") ? normalized : "minecraft:" + normalized;
      }
   }

   record Descriptor(
      String type,
      String labelKey,
      String legacyLabel,
      String group,
      int color,
      List<String> outputs,
      boolean inPalette,
      String primaryLabelKey,
      String secondaryLabelKey,
      String defaultValue
   ) {
      String label() {
         return tr(this.labelKey);
      }

      String primaryLabel() {
         return this.primaryLabelKey.isEmpty() ? "" : tr(this.primaryLabelKey);
      }

      String secondaryLabel() {
         return this.secondaryLabelKey.isEmpty() ? "" : tr(this.secondaryLabelKey);
      }

      String groupLabel() {
         return tr("macro_builder.group." + this.group.toLowerCase(Locale.ROOT));
      }

      boolean primaryItemField() {
         return PRIMARY_ITEM_FIELDS.contains(this.type);
      }

      boolean secondaryItemField() {
         return SECONDARY_ITEM_FIELDS.contains(this.type);
      }

      String description() {
         return MacroModel.description(this.type);
      }

      String defaultValue2() {
         if ("official:inventory.clickOpenContainerSlot".equals(this.type)) {
            return "false";
         } else if ("official:inventory.chestDepositItems".equals(this.type)) {
            return "minecraft:bone_block";
         } else if ("official:inventory.chestWithdrawItems".equals(this.type)) {
            return "minecraft:bone_block";
         } else if ("official:inventory.hasItem".equals(this.type)) {
            return "1";
         } else if ("builder:inventory.hasItem".equals(this.type)) {
            return "1";
         } else if ("builder:inventory.openContainerHasItem".equals(this.type)) {
            return "1";
         } else if ("builder:inventory.slotHasItem".equals(this.type)) {
            return "minecraft:bone_block";
         } else if ("builder:inventory.itemOrSlotHasTag".equals(this.type)) {
            return "minecraft:logs";
         } else if ("builder:inventory.itemDurability".equals(this.type)) {
            return "lower 20";
         } else if ("builder:inventory.autoEnchant".equals(this.type)) {
            return "30";
         } else if ("builder:inventory.autoGrindstone".equals(this.type)) {
            return "held";
         } else if ("official:inventory.hotbarSelect".equals(this.type) || "official:inventory.hotbarUse".equals(this.type)) {
            return "1";
         } else if ("official:inventory.dropItems".equals(this.type)) {
            return "false";
         } else if ("official:player.setMouseButton".equals(this.type)) {
            return "left";
         } else if ("builder:player.autoClick".equals(this.type)) {
            return "count=1 cps=8";
         } else if ("builder:player.autoFish".equals(this.type)) {
            return "60";
         } else if ("builder:player.pressKey".equals(this.type)) {
            return "tap 100";
         } else if ("official:player.shootBow".equals(this.type)) {
            return "true";
         } else if ("official:player.look".equals(this.type)) {
            return "0";
         } else if ("official:world.interactWithBlock".equals(this.type)) {
            return "1";
         } else if ("official:world.mineBlock".equals(this.type) || "official:world.placeBlock".equals(this.type)) {
            return "true";
         } else if ("builder:world.mineArea".equals(this.type)) {
            return "10 79 10 tool=10 move=true auto_tool=false";
         } else if ("builder:world.farmArea".equals(this.type)) {
            return "0 64 0 replant deposit move=true";
         } else if ("builder:world.openNearestContainer".equals(this.type)) {
            return "move=true type=chest";
         } else if ("builder:world.openNearestChest".equals(this.type)
            || "builder:world.openNearestBarrel".equals(this.type)
            || "builder:world.openNearestShulker".equals(this.type)) {
            return "move=true";
         } else if ("builder:world.openNearestEnchantingTable".equals(this.type)) {
            return "move=true";
         } else if ("builder:world.openNearestGrindstone".equals(this.type)) {
            return "move=true";
         } else if ("builder:world.autoBoneMeal".equals(this.type)) {
            return "refill radius=8";
         } else if ("builder:world.blockIs".equals(this.type)) {
            return "minecraft:air";
         } else if ("official:entity.attack".equals(this.type) || "official:entity.interact".equals(this.type)) {
            return "false";
         } else if ("builder:entity.nearby".equals(this.type)) {
            return "6";
         } else if ("official:player.isAtLocation".equals(this.type)) {
            return "2";
         } else if ("official:entity.player_nearby".equals(this.type)) {
            return "any";
         } else if ("builder:logic.macroActiveState".equals(this.type)) {
            return "active";
         } else if ("builder:misc.idleUntil".equals(this.type) || "builder:misc.repeatUntil".equals(this.type)) {
            return "";
         } else if ("official:event.chat".equals(this.type)
            || "builder:event.actionbar".equals(this.type)
            || "official:event.player_spawned".equals(this.type)
            || "official:event.player_despawned".equals(this.type)) {
            return "any";
         } else {
            return "builder:event.ifChatSame".equals(this.type) ? "same" : "";
         }
      }
   }

   static final class Node {
      final String id;
      final String type;
      final Map<String, String> outputs = new LinkedHashMap<>();
      final Map<String, String> connectionRoutes = new LinkedHashMap<>();
      final Map<String, String> connectionColors = new LinkedHashMap<>();
      int x;
      int y;
      String value = "";
      String value2 = "";
      String value3 = "";
      String value4 = "";
      String value5 = "";
      String displayName = "";
      int delayMs = -1;
      boolean enabled = true;
      int noteWidth = 0;
      int noteHeight = 0;

      private Node(String id, String type, int x, int y) {
         this.id = id;
         this.type = type;
         this.x = x;
         this.y = y;
      }

      MacroModel.Descriptor descriptor() {
         return MacroModel.descriptor(this.type);
      }

      String displayLabel() {
         String label = this.displayName == null ? "" : this.displayName.trim();
         return label.isBlank() ? this.descriptor().label() : label;
      }

      String next(String outputKey) {
         List<String> targets = this.nextTargets(outputKey);
         return targets.isEmpty() ? null : targets.get(0);
      }

      List<String> nextTargets(String outputKey) {
         return MacroModel.outputTargets(this.outputs.get(outputKey));
      }

      void addTarget(String outputKey, String targetId) {
         this.outputs.put(outputKey, MacroModel.addOutputTarget(this.outputs.get(outputKey), targetId));
      }

      void removeTarget(String outputKey, String targetId) {
         String remainingTargets = MacroModel.removeOutputTarget(this.outputs.get(outputKey), targetId);
         if (remainingTargets.isBlank()) {
            this.outputs.remove(outputKey);
            this.clearConnectionRoutesForOutput(outputKey);
            this.clearConnectionColorsForOutput(outputKey);
         } else {
            this.outputs.put(outputKey, remainingTargets);
            this.removeConnectionRoute(outputKey, targetId);
            this.removeConnectionColor(outputKey, targetId);
         }
      }

      String connectionRoute(String outputKey, String targetId) {
         return this.connectionRoutes.get(connectionRouteKey(outputKey, targetId));
      }

      void setConnectionRoute(String outputKey, String targetId, double bendWorldX) {
         this.connectionRoutes.put(connectionRouteKey(outputKey, targetId), Double.toString(bendWorldX));
      }

      void removeConnectionRoute(String outputKey, String targetId) {
         this.connectionRoutes.remove(connectionRouteKey(outputKey, targetId));
      }

      void clearConnectionRoutesForOutput(String outputKey) {
         this.connectionRoutes.keySet().removeIf(key -> key.startsWith(outputKey + "->"));
      }

      String connectionColor(String outputKey, String targetId) {
         return this.connectionColors.get(connectionRouteKey(outputKey, targetId));
      }

      void setConnectionColor(String outputKey, String targetId, String color) {
         String key = connectionRouteKey(outputKey, targetId);
         if (color == null || color.isBlank()) {
            this.connectionColors.remove(key);
         } else {
            this.connectionColors.put(key, color);
         }
      }

      void removeConnectionColor(String outputKey, String targetId) {
         this.connectionColors.remove(connectionRouteKey(outputKey, targetId));
      }

      void clearConnectionColorsForOutput(String outputKey) {
         this.connectionColors.keySet().removeIf(key -> key.startsWith(outputKey + "->"));
      }

      private static String connectionRouteKey(String outputKey, String targetId) {
         return outputKey + "->" + targetId;
      }
   }
}
