package com.alfre.macrobuilder;

import static com.alfre.macrobuilder.MacroText.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class MacroAutoSettingsScreen extends Screen {
   private final MacroBuilderScreen parent;
   private final MacroModel model;
   private boolean alwaysOn;
   private String autoStartMode;
   private EditBox filterField;
   private Button alwaysButton;
   private Button modeButton;

   MacroAutoSettingsScreen(MacroBuilderScreen parent, MacroModel model) {
      super(Component.translatable("macro_builder.auto.auto_start"));
      this.parent = parent;
      this.model = model;
      this.alwaysOn = model.alwaysOn();
      this.autoStartMode = model.autoStartMode();
   }

   protected void init() {
      int panelWidth = 336;
      int panelHeight = 206;
      int left = this.width / 2 - panelWidth / 2;
      int top = this.height / 2 - panelHeight / 2;
      this.alwaysButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal(this.alwaysText()), button -> {
            this.alwaysOn = !this.alwaysOn;
            this.alwaysButton.setMessage(Component.literal(this.alwaysText()));
         }).bounds(left + 16, top + 44, 304, 20).build()
      );
      this.modeButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal(this.modeText()), button -> {
            this.autoStartMode = this.nextMode(this.autoStartMode);
            this.modeButton.setMessage(Component.literal(this.modeText()));
         }).bounds(left + 16, top + 76, 304, 20).build()
      );
      this.filterField = new EditBox(this.font, left + 16, top + 124, 304, 20, Component.empty());
      this.filterField.setMaxLength(256);
      this.filterField.setValue(this.model.autoStartFilter());
      this.filterField.setSuggestion(tr("macro_builder.auto.optional_chat_text_player_or_kick_reason"));
      this.addRenderableWidget(this.filterField);
      this.addRenderableWidget(Button.builder(Component.translatable("macro_builder.auto.apply"), button -> this.applyAndClose()).bounds(left + 128, top + 170, 92, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("macro_builder.auto.cancel"), button -> this.onClose()).bounds(left + 228, top + 170, 92, 20).build());
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context, mouseX, mouseY, delta);
      int panelWidth = 336;
      int panelHeight = 206;
      int left = this.width / 2 - panelWidth / 2;
      int top = this.height / 2 - panelHeight / 2;
      context.fill(left, top, left + panelWidth, top + panelHeight, -15724528);
      context.fill(left, top, left + panelWidth, top + 1, -9205921);
      context.fill(left, top + panelHeight - 1, left + panelWidth, top + panelHeight, -9205921);
      context.fill(left, top, left + 1, top + panelHeight, -9205921);
      context.fill(left + panelWidth - 1, top, left + panelWidth, top + panelHeight, -9205921);
      context.drawCenteredString(this.font, this.title, this.width / 2, top + 14, -1);
      context.drawString(this.font, tr("macro_builder.auto.filter"), left + 16, top + 112, -2565928);
      context.drawString(this.font, tr("macro_builder.auto.chat_player_kicked_can_use_this_filter"), left + 16, top + 150, -7366491);
      super.render(context, mouseX, mouseY, delta);
   }

   public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, -804253680);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   private void applyAndClose() {
      this.model.setAlwaysOn(this.alwaysOn);
      this.model.setAutoStartMode(this.autoStartMode);
      this.model.setAutoStartFilter(this.filterField.getValue());
      this.parent.refreshAfterAutoSettings();
      this.minecraft.setScreen(this.parent);
   }

   private String alwaysText() {
      return this.alwaysOn ? tr("macro_builder.auto.always_on_on") : tr("macro_builder.auto.always_on_off");
   }

   private String modeText() {
      return tr("macro_builder.auto.enable_when", this.modeLabel(this.autoStartMode));
   }

   private String nextMode(String mode) {
      return switch (mode == null ? "" : mode) {
         case "off", "" -> "chat";
         case "chat" -> "player";
         case "player" -> "inventory_full";
         case "inventory_full" -> "kicked";
         case "kicked" -> "off";
         default -> "off";
      };
   }

   private String modeLabel(String mode) {
      return switch (mode == null ? "" : mode) {
         case "chat" -> tr("macro_builder.auto.chat_message");
         case "player" -> tr("macro_builder.auto.player_nearby");
         case "inventory_full" -> tr("macro_builder.auto.inventory_full");
         case "kicked" -> tr("macro_builder.auto.kicked_disconnected");
         default -> tr("macro_builder.auto.off");
      };
   }
}
