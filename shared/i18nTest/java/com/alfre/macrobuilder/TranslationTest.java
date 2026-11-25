package com.alfre.macrobuilder;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.StringSplitter;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

public final class TranslationTest {
   private static int checks;

   public static void main(String[] args) throws Exception {
      Language original = Language.getInstance();
      Map<String, String> english = translations("en_us");
      Map<String, String> chinese = new HashMap<>(english);
      chinese.putAll(translations("zh_cn"));
      try {
         Language.inject(language(english, original));
         Component title = Component.translatable("macro_builder.builder.save");
         check(title.getString().equals("Save"), "English title");
         for (MacroModel.Descriptor descriptor : MacroModel.paletteDescriptors()) {
            check(descriptor.label().equals(descriptor.legacyLabel()), "English name: " + descriptor.type());
            check(!descriptor.description().startsWith("macro_builder."), "description coverage");
         }

         MacroModel model = MacroModel.createDefault();
         model.setName("我的宏 / My macro");
         MacroModel.Node chat = model.addNode(MacroModel.CHAT, 400, 120);
         chat.value = "/say 中文 / English";
         chat.displayName = "自定义名称 / Custom name";
         model.startNode().addTarget("started", chat.id);
         String before = model.toJson();
         String canonicalEnglish = MacroModel.fromJson(before).toJson();
         Language.inject(language(chinese, original));
         check(title.getString().equals("保存"), "existing component follows language change");
         check(MacroText.tr("macro_builder.builder.saved").equals("已保存"), "String rendering follows language");
         check(chat.displayLabel().equals(chat.displayName), "custom component names remain literal");
         check(model.toJson().equals(before), "switching language does not alter saved macro JSON");
         check(MacroModel.fromJson(before).toJson().equals(canonicalEnglish), "saved macro normalization is language independent");
         check(MacroText.output("started").equals("启动"), "output display is localized");
         check(MacroText.output("future_output").equals("future_output"), "unknown output remains usable");
         check(MacroText.tr("macro_builder.runner.stopped_mine_area_found_protected_block_at", "diamond", "10 64 10")
            .contains("10 64 10 发现受保护方块 diamond"), "numbered arguments can change order");
         for (MacroModel.Descriptor descriptor : MacroModel.paletteDescriptors()) {
            check(!descriptor.label().equals(descriptor.legacyLabel()), "Chinese name: " + descriptor.type());
            check(descriptor.description().codePoints().anyMatch(point -> point >= 0x4E00 && point <= 0x9FFF), "Chinese help");
         }
         check(MacroModel.descriptor(MacroModel.ITEM_IN_INVENTORY).primaryItemField(), "item picker in Chinese");
         check(MacroModel.descriptor(MacroModel.CHEST_DEPOSIT_ITEMS).secondaryItemField(), "deposit item picker in Chinese");
         check(!MacroModel.descriptor(MacroModel.WAIT).primaryItemField(), "duration is not an item field");

         Path scenarios = Path.of(args[0]);
         for (String scenario : List.of("advanced-relative-quarry-deposit-loop.json", "runtime-data-target-block-message.json")) {
            MacroModel saved = MacroModel.load(scenarios.resolve(scenario));
            String chineseJson = saved.toJson();
            Language.inject(language(english, original));
            check(saved.toJson().equals(chineseJson), "existing scenario keeps IDs and values: " + scenario);
            Language.inject(language(chinese, original));
         }
         Language.inject(language(english, original));
         check(title.getString().equals("Save"), "language can switch back");
         check(MacroText.tr("macro_builder.builder.save").equals("Save"), "English fallback");
         Map<String, String> resourcePack = new HashMap<>(english);
         resourcePack.put("macro_builder.builder.save", "Save macro");
         Language.inject(language(resourcePack, original));
         check(title.getString().equals("Save macro"), "resource pack override updates existing components");

         StringSplitter splitter = new StringSplitter((point, style) -> point >= 0x4E00 ? 10.0F : 6.0F);
         for (String text : List.of("中文说明没有空格也应自动换行", "English text and 中文混排", "中文𠀀字符边界")) {
            List<String> lines = MacroText.wrap(text, splitter, 60);
            check(lines.size() > 1, "long text wraps: " + text);
            check(lines.stream().allMatch(line -> splitter.stringWidth(line) <= 60), "wrapped lines fit");
            check(String.join("", lines).replace(" ", "").equals(text.replace(" ", "")), "wrapping preserves text");
         }
      } finally {
         Language.inject(original);
      }
      System.out.println("Translation checks passed: " + checks);
   }

   private static Map<String, String> translations(String code) throws Exception {
      String resource = "/assets/macro_builder/lang/" + code + ".json";
      try (InputStream input = TranslationTest.class.getResourceAsStream(resource)) {
         if (input == null) throw new IllegalStateException("Missing packaged resource: " + resource);
         Map<String, String> values = new HashMap<>();
         Language.loadFromJson(input, values::put);
         return values;
      }
   }

   private static Language language(Map<String, String> values, Language original) {
      return new Language() {
         public String getOrDefault(String key, String fallback) {
            return values.getOrDefault(key, original.getOrDefault(key, fallback));
         }

         public boolean has(String key) {
            return values.containsKey(key) || original.has(key);
         }

         public boolean isDefaultRightToLeft() {
            return false;
         }

         public FormattedCharSequence getVisualOrder(FormattedText text) {
            return original.getVisualOrder(text);
         }
      };
   }

   private static void check(boolean condition, String message) {
      checks++;
      if (!condition) throw new AssertionError(message);
   }
}
