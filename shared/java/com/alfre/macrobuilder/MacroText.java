package com.alfre.macrobuilder;

import java.util.List;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

final class MacroText {
   private MacroText() {
   }

   // 使用 Minecraft 当前语言及资源包；不要缓存已解析的字符串。
   static String tr(String key, Object... arguments) {
      return Component.translatable(key, arguments).getString();
   }

   // 输出 ID 属于宏 JSON 协议，翻译只用于显示。
   static String output(String id) {
      return Component.translatableWithFallback("macro_builder.output." + id, id).getString();
   }

   static List<String> wrap(String text, StringSplitter splitter, int width) {
      if (text == null || text.isBlank()) return List.of();
      return splitter.splitLines(text, Math.max(1, width), Style.EMPTY).stream().map(FormattedText::getString).toList();
   }

   static String fit(String text, Font font, int width) {
      if (text == null || width <= 0) return "";
      if (font == null || font.width(text) <= width) return text;
      String suffix = "…";
      if (font.width(suffix) > width) return "";
      return font.plainSubstrByWidth(text, width - font.width(suffix)) + suffix;
   }
}
