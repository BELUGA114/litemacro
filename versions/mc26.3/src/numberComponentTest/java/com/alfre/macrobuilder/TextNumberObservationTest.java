package com.alfre.macrobuilder;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TextNumberObservationTest {
   private static int checks;

   public static void main(String[] args) throws Exception {
      numericComparison();
      freshnessAndInvalidInput();
      messageReductionAndClear();
      invalidConfiguration();
      for (String file : List.of("cloud-vault-normal.json", "cloud-vault-ominous.json")) {
         MacroModel model = MacroModel.load(Path.of(args[0]).resolve(file));
         validateGraph(model);
         serverSidebarFixture(model);
         simulateVault(model);
      }
      System.out.println("Number component and vault checks passed: " + checks);
   }

   private static TextNumberObservation.Spec spec(boolean chat, String options) {
      return TextNumberObservation.Spec.parse(chat, "Value:(\\S+)", options);
   }

   private static void numericComparison() {
      TextNumberObservation.Spec rule = spec(false, "op=gte value=1.5 stale=5 rate=0 mode=latest missing=false");
      TextNumberObservation observation = new TextNumberObservation(rule);
      observation.observeCurrent(List.of("§aValue:1.5"), -10000);
      check(rule.matches(observation.snapshot(-10000)), "decimal comparison and negative clock origins work");
      for (String operator : List.of("gt", "gte", "lt", "lte", "eq", "ne")) {
         TextNumberObservation.Spec comparison = spec(false, "op=" + operator + " value=1.5 stale=5 rate=0 mode=latest missing=false");
         check(comparison.matches(observation.snapshot(-10000)) == Set.of("gte", "lte", "eq").contains(operator), "operator " + operator);
         check(comparison.key().equals(rule.key()), "different thresholds share an observation");
      }
      observation.observeCurrent(List.of("Value:-2.25"), -9000);
      TextNumberObservation.Spec negative = spec(false, "op=lt value=0 stale=5 rate=0 mode=latest missing=false");
      check(negative.matches(observation.snapshot(-9000)), "negative values are supported");
      TextNumberObservation.Spec decaying = spec(false, "op=gt value=60 stale=0 rate=-1 mode=latest missing=false");
      TextNumberObservation counter = new TextNumberObservation(decaying);
      counter.observeCurrent(List.of("Value:61"), 0);
      check(decaying.matches(counter.snapshot(999)), "configured rate uses elapsed time");
      check(!decaying.matches(counter.snapshot(1000)), "threshold equality blocks without another update");
      TextNumberObservation.Spec growing = spec(false, "op=gte value=12 stale=0 rate=2 mode=latest missing=false");
      TextNumberObservation increasing = new TextNumberObservation(growing);
      increasing.observeCurrent(List.of("Value:10"), 0);
      check(growing.matches(increasing.snapshot(1000)), "positive change rates work too");
   }

   private static void freshnessAndInvalidInput() {
      TextNumberObservation.Spec rule = spec(false, "op=gt value=60 stale=5 rate=0 mode=latest missing=false");
      TextNumberObservation observation = new TextNumberObservation(rule);
      check(!rule.matches(observation.snapshot(0)), "missing input blocks according to configuration");
      observation.observeCurrent(List.of("Value:100"), 0);
      observation.observeCurrent(List.of("Value:100"), 4000);
      check(!rule.matches(observation.snapshot(5001)), "reading a frozen value does not refresh it");
      observation.observeCurrent(List.of("Value:99"), 6000);
      check(rule.matches(observation.snapshot(6000)), "actual changes recover freshness");
      observation.observeCurrent(List.of(), 6100);
      check(!rule.matches(observation.snapshot(6100)), "missing sidebar blocks immediately");
      observation.observeCurrent(List.of("Value:99"), 12000);
      check(!rule.matches(observation.snapshot(12000)), "disappearing and returning frozen data does not reset age");
      observation.observeCurrent(List.of("Value:98", "Value:98"), 13000);
      check(rule.matches(observation.snapshot(13000)), "duplicate matching values are accepted");
      for (List<String> lines : List.of(
         List.of("Value:100", "Value:10"), List.of("Value:NaN"), List.of("Value:Infinity"),
         List.of("Value:word"), List.of("unrelated 100")
      )) {
         observation.observeCurrent(lines, 14000);
         check(!rule.matches(observation.snapshot(14000)), "ambiguous or invalid values block");
      }
   }

   private static void messageReductionAndClear() {
      TextNumberObservation.Spec rule = spec(true, "op=gt value=10 stale=0 rate=-1 mode=min missing=true");
      TextNumberObservation observation = new TextNumberObservation(rule);
      check(rule.matches(observation.snapshot(0)), "the JSON may allow absent optional messages");
      observation.recordMessage("Value:30", 0);
      observation.recordMessage("unrelated message", 1000);
      check(observation.snapshot(1000).value() == 29, "unrelated messages cannot overwrite matching data");
      observation.recordMessage("Value:10", 2000);
      observation.recordMessage("Value:60", 2001);
      check(!rule.matches(observation.snapshot(2001)), "min mode does not extend an earlier limit");
      observation.recordMessage("Value:NaN", 2002);
      check(!rule.matches(observation.snapshot(2002)), "invalid matching data cannot be allowed by missing=true");
      observation.recordMessage("Value:100", 2003);
      check(!rule.matches(observation.snapshot(2003)), "invalid messages cannot erase the previously accumulated minimum");
      observation.clear();
      check(rule.matches(observation.snapshot(3000)), "clearing an observation restores its configured missing behavior");
      observation.recordMessage("Value:100", 3000);
      check(rule.matches(observation.snapshot(3000)), "new data after clearing starts a new observation");
      TextNumberObservation.Spec latest = spec(true, "op=eq value=20 stale=0 rate=0 mode=latest missing=false");
      TextNumberObservation last = new TextNumberObservation(latest);
      last.recordMessage("Value:10", 0);
      last.recordMessage("Value:20", 1000);
      check(latest.matches(last.snapshot(1000)), "latest mode replaces matching data");
      TextNumberObservation.Spec maximum = spec(true, "op=eq value=20 stale=0 rate=0 mode=max missing=false");
      TextNumberObservation max = new TextNumberObservation(maximum);
      max.recordMessage("Value:20", 0);
      max.recordMessage("Value:10", 1000);
      check(maximum.matches(max.snapshot(1000)), "max mode preserves the larger value");
   }

   private static void invalidConfiguration() {
      String valid = "op=gt value=60 stale=5 rate=0 mode=latest missing=false";
      for (String options : List.of("", "op=gt", valid + " op=lt", valid + " unknown=1",
         valid.replace("op=gt", "op=no"), valid.replace("value=60", "value=NaN"),
         valid.replace("stale=5", "stale=-1"), valid.replace("rate=0", "rate=Infinity"),
         valid.replace("mode=latest", "mode=min"), valid.replace("missing=false", "missing=yes"))) {
         boolean rejected = false;
         try {
            spec(false, options);
         } catch (IllegalArgumentException expected) {
            rejected = true;
         }
         check(rejected, "invalid options fail explicitly");
      }
      boolean rejected = false;
      try {
         TextNumberObservation.Spec.parse(false, "Value:", valid);
      } catch (IllegalArgumentException expected) {
         rejected = true;
      }
      check(rejected, "the regex must capture a number");
      check(MacroModel.descriptor(MacroModel.SCOREBOARD_NUMBER).defaultValue().isEmpty()
         && MacroModel.descriptor(MacroModel.SCOREBOARD_NUMBER).defaultValue2().isEmpty(), "components have no server-specific defaults");
   }

   private static void validateGraph(MacroModel model) throws Exception {
      MacroModel copy = MacroModel.fromJson(model.toJson());
      check(copy.nodes().size() == model.nodes().size(), "round trip preserves nodes");
      Set<String> ids = new HashSet<>();
      for (MacroModel.Node node : copy.nodes()) {
         check(ids.add(node.id), "node IDs are unique");
         check(node.type.equals(node.descriptor().type()), "all component types are supported");
         for (String output : node.outputs.keySet()) {
            check(node.descriptor().outputs().contains(output), "output port is valid");
            for (String target : node.nextTargets(output)) {
               check(copy.node(target) != null, "connection target exists");
            }
         }
         if (MacroModel.SCOREBOARD_NUMBER.equals(node.type) || MacroModel.CHAT_NUMBER.equals(node.type)) {
            TextNumberObservation.Spec.parse(MacroModel.CHAT_NUMBER.equals(node.type), node.value, node.value2);
         }
         String references = MacroModel.CLICK_WITH_CONDITION.equals(node.type) ? node.value2
            : MacroModel.WAIT_FOR_CONDITION.equals(node.type) || MacroModel.CLEAR_NUMBER_OBSERVATION.equals(node.type) ? node.value : "";
         for (String reference : references.isEmpty() ? List.<String>of() : List.of(references.split("\\s+"))) {
            check(copy.node(reference) != null, "referenced condition exists");
         }
      }
      check("node-70".equals(copy.startNode().next("started"))
         && "node-70".equals(copy.node("node-58").next("completed")), "startup and each new round check the scoreboard");
      check("node-70 node-85 node-74".equals(copy.node("node-19").value2), "exchange checks scoreboard, valid range and chat immediately before clicking");
      check("node-81".equals(copy.node("node-80").next("completed"))
         && "node-73".equals(copy.node("node-81").next("completed"))
         && "node-20".equals(copy.node("node-73").next("true")), "emergency recovery only returns to claiming");
   }

   private static List<String> serverSidebarLines(int seconds) {
      return List.of("剩余梦币: 19488.01§0§1§r", "剩余点卷: 0§0§2§r",
         "当前称号: 『 杂鱼 』§0§3§r", "当前权限: [普通玩家]§0§4§r",
         "垃圾清理: [" + seconds + "]§0§5§r");
   }

   private static void serverSidebarFixture(MacroModel model) {
      for (String id : List.of("node-70", "node-75", "node-77", "node-82", "node-85")) {
         MacroModel.Node node = model.node(id);
         TextNumberObservation.Spec rule = TextNumberObservation.Spec.parse(false, node.value, node.value2);
         TextNumberObservation observation = new TextNumberObservation(rule);
         observation.observeCurrent(serverSidebarLines(231), 0);
         check(observation.snapshot(0).present() && observation.snapshot(0).value() == 231,
            "every production comparison reads cleanup time, not currency or sorting score");
         check(rule.matches(observation.snapshot(0)) == Set.of("node-70", "node-75", "node-85").contains(id),
            "231 seconds permits production without falsely satisfying cleanup/reset conditions");
         observation.observeCurrent(serverSidebarLines(228), 3000);
         check(observation.snapshot(3000).value() == 228 && observation.snapshot(3000).ageSeconds() == 0,
            "the reported 231-to-228 update refreshes the observation");
         observation.observeCurrent(serverSidebarLines(60), 4000);
         check(rule.matches(observation.snapshot(4000)) == Set.of("node-75", "node-82", "node-85").contains(id),
            "60 seconds blocks new exchanges and confirms the low-time window");
         observation.observeCurrent(serverSidebarLines(10), 5000);
         check(rule.matches(observation.snapshot(5000)) == Set.of("node-82", "node-85").contains(id),
            "10 seconds also blocks claiming");
         observation.observeCurrent(serverSidebarLines(600), 6000);
         check(rule.matches(observation.snapshot(6000)) == Set.of("node-70", "node-75", "node-77", "node-85").contains(id),
            "600 seconds satisfies the configured reset conditions");
      }
   }

   private static void simulateVault(MacroModel model) {
      VaultSimulation initialPause = new VaultSimulation(model, 55, 1, 1000);
      initialPause.runUntil(50000);
      check(initialPause.exchanges == 0, "startup inside the danger window cannot spend keys");
      initialPause.runUntil(70000);
      check(initialPause.exchanges == 1 && initialPause.claims == 1, "confirmed reset and settling resume once");
      VaultSimulation currentRound = new VaultSimulation(model, 70, 1, 20000);
      currentRound.runUntil(30000);
      check(currentRound.exchanges == 1 && currentRound.claims == 1, "the current round finishes after crossing 60 seconds");
      currentRound.runUntil(85000);
      check(currentRound.exchanges == 1, "recovery cannot exchange again with exhausted keys");
      VaultSimulation slowButton = new VaultSimulation(model, 63, 1, 1000);
      slowButton.selectionReadyAtMs = 3500;
      slowButton.runUntil(20000);
      check(slowButton.exchanges == 0, "the final condition blocks while a GUI button is still synchronizing");
      VaultSimulation emergency = new VaultSimulation(model, 25, 0, 20000);
      emergency.current = "node-20";
      emergency.menu = Menu.ROLLING;
      emergency.rewardAtMs = 20000;
      emergency.runUntil(24000);
      check(emergency.claims == 0, "the emergency threshold pauses the pending claim");
      emergency.runUntil(41000);
      check(emergency.claims == 1 && emergency.exchanges == 0, "emergency recovery claims without spending another key");
      VaultSimulation missing = new VaultSimulation(model, 300, 1, 1000);
      missing.boardAvailable = false;
      missing.runUntil(10000);
      check(missing.exchanges == 0, "missing scoreboard data cannot start an exchange");
      VaultSimulation outOfRange = new VaultSimulation(model, 601, 1, 1000);
      outOfRange.runUntil(20000);
      check(outOfRange.exchanges == 0, "the JSON policy rejects values outside the server's declared range");
      VaultSimulation conflictingWarning = new VaultSimulation(model, 600, 1, 1000);
      conflictingWarning.frozenBoard = true;
      conflictingWarning.recordMessage("还剩下 10 秒清理物品和实体");
      conflictingWarning.recordMessage("unrelated server message");
      conflictingWarning.runUntil(20000);
      check(conflictingWarning.exchanges == 0, "a warning plus an unchanged high scoreboard cannot be treated as a reset");
   }

   private static void check(boolean condition, String message) {
      if (!condition) {
         throw new AssertionError(message);
      }
      checks++;
   }

   private enum Menu {
      NONE, MAIN, SELECTION, ROLLING, REWARD
   }

   private static final class VaultSimulation {
      private final MacroModel model;
      private final Map<String, TextNumberObservation.Spec> specs = new HashMap<>();
      private final Map<TextNumberObservation.Key, TextNumberObservation> observations = new HashMap<>();
      private final int initialSeconds;
      private final long animationMs;
      private String current = "node-1";
      private Menu menu = Menu.NONE;
      private long nowMs;
      private long rewardAtMs;
      private long selectionReadyAtMs;
      private int nodeTicks;
      private int keys;
      private int exchanges;
      private int claims;
      private boolean stopped;
      private boolean boardAvailable = true;
      private boolean frozenBoard;

      VaultSimulation(MacroModel model, int initialSeconds, int keys, long animationMs) {
         this.model = model;
         this.initialSeconds = initialSeconds;
         this.keys = keys;
         this.animationMs = animationMs;
         for (MacroModel.Node node : model.nodes()) {
            if (MacroModel.SCOREBOARD_NUMBER.equals(node.type) || MacroModel.CHAT_NUMBER.equals(node.type)) {
               TextNumberObservation.Spec spec = TextNumberObservation.Spec.parse(MacroModel.CHAT_NUMBER.equals(node.type), node.value, node.value2);
               this.specs.put(node.id, spec);
               this.observations.computeIfAbsent(spec.key(), key -> new TextNumberObservation(spec));
            }
         }
      }

      void recordMessage(String message) {
         this.observations.forEach((key, observation) -> {
            if (key.chat()) {
               observation.recordMessage(message, this.nowMs);
            }
         });
      }

      void runUntil(long endMs) {
         while (!this.stopped && this.nowMs < endMs) {
            int remaining = this.frozenBoard ? this.initialSeconds : this.nowMs < this.initialSeconds * 1000L
               ? this.initialSeconds - (int)(this.nowMs / 1000L)
               : 600 - (int)((this.nowMs - this.initialSeconds * 1000L) / 1000L % 600);
            this.observations.forEach((key, observation) -> {
               if (!key.chat()) {
                  observation.observeCurrent(this.boardAvailable ? serverSidebarLines(remaining) : List.of(), this.nowMs);
               }
            });
            if (this.menu == Menu.ROLLING && this.nowMs >= this.rewardAtMs) {
               this.menu = Menu.REWARD;
            }
            this.tick();
            this.nowMs += 50;
         }
      }

      private boolean conditions(String ids) {
         boolean matched = true;
         for (String id : ids.split("\\s+")) {
            TextNumberObservation.Spec spec = this.specs.get(id);
            matched &= spec.matches(this.observations.get(spec.key()).snapshot(this.nowMs));
         }
         return matched;
      }

      private void tick() {
         MacroModel.Node node = this.model.node(this.current);
         this.nodeTicks++;
         switch (node.type) {
            case "official:start" -> this.advance(node, "started");
            case "official:player.isAlive" -> this.advance(node, "true");
            case "official:inventory.hasOpenContainer" -> this.advance(node, this.menu == Menu.NONE ? "false" : "true");
            case "official:inventory.hasItem" -> this.advance(node, this.keys > 0 ? "true" : "false");
            case "builder:inventory.itemOrSlotHasTag" -> {
               boolean matches = this.menu == Menu.REWARD && node.value2.contains("点击领取全部奖励")
                  || this.menu == Menu.SELECTION && (node.value2.equals("普通宝库") || node.value2.equals("不祥宝库"))
                  || this.menu == Menu.MAIN && node.value2.equals("云宝库");
               this.advance(node, matches ? "true" : "false");
            }
            case "official:player.setCrouch", "builder:localMessage" -> this.advance(node, "completed");
            case "builder:player.pressKey" -> {
               this.menu = Menu.MAIN;
               this.advance(node, "completed");
            }
            case "official:misc.wait" -> {
               if (this.nodeTicks * 50 >= Integer.parseInt(node.value)) {
                  this.advance(node, "completed");
               }
            }
            case MacroModel.SCOREBOARD_NUMBER, MacroModel.CHAT_NUMBER -> this.advance(node, this.conditions(node.id) ? "true" : "false");
            case MacroModel.WAIT_FOR_CONDITION -> {
               if ((this.nodeTicks - 1) % ((Integer.parseInt(node.value2) + 49) / 50) == 0 && this.conditions(node.value)) {
                  this.advance(node, "completed");
               }
            }
            case MacroModel.CLEAR_NUMBER_OBSERVATION -> {
               this.observations.get(this.specs.get(node.value).key()).clear();
               this.advance(node, "completed");
            }
            case "official:inventory.clickOpenContainerSlot" -> {
               this.menu = Menu.SELECTION;
               this.advance(node, "completed");
            }
            case MacroModel.CLICK_WITH_CONDITION -> this.guardedClick(node);
            case "builder:event.ifChatSame" -> this.advance(node, "false");
            case "builder:stop" -> this.stopped = true;
            default -> throw new AssertionError("Unexpected vault component: " + node.type);
         }
      }

      private void guardedClick(MacroModel.Node node) {
         if (!this.conditions(node.value2)) {
            this.advance(node, "blocked");
         } else if (node.id.equals("node-19") && this.menu == Menu.SELECTION && this.nowMs >= this.selectionReadyAtMs) {
            this.exchanges++;
            this.keys--;
            this.menu = Menu.ROLLING;
            this.rewardAtMs = this.nowMs + this.animationMs;
            this.advance(node, "completed");
         } else if (!node.id.equals("node-19") && this.menu == Menu.REWARD) {
            this.claims++;
            this.menu = Menu.NONE;
            this.advance(node, "completed");
         } else if (this.nodeTicks > 100) {
            this.advance(node, "failed");
         }
      }

      private void advance(MacroModel.Node node, String output) {
         String target = node.next(output);
         if (target == null) {
            throw new AssertionError("Missing simulation connection: " + node.id + "/" + output);
         }
         this.current = target;
         this.nodeTicks = 0;
      }
   }
}
