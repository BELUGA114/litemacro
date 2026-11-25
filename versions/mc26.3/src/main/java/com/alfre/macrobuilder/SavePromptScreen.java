package com.alfre.macrobuilder;

import static com.alfre.macrobuilder.MacroText.tr;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

final class SavePromptScreen extends Screen {
   private final MacroBuilderScreen builder;

   SavePromptScreen(MacroBuilderScreen builder) {
      super(Component.translatable("macro_builder.save.unsaved_changes"));
      this.builder = builder;
   }

   protected void init() {
      int panelWidth = 280;
      int buttonWidth = 82;
      int left = (this.width - panelWidth) / 2;
      int top = Math.max(50, (this.height - 116) / 2);
      int buttonY = top + 76;
      this.addRenderableWidget(
         Button.builder(Component.translatable("macro_builder.save.save"), button -> this.builder.saveAndLeaveToMacroList()).bounds(left + 12, buttonY, buttonWidth, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("macro_builder.save.discard"), button -> this.builder.discardAndLeaveToMacroList()).bounds(left + 99, buttonY, buttonWidth, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("macro_builder.save.cancel"), button -> this.builder.cancelSavePrompt()).bounds(left + 186, buttonY, buttonWidth, 20).build()
      );
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      this.extractBackground(context, mouseX, mouseY, delta);
      int panelWidth = 280;
      int left = (this.width - panelWidth) / 2;
      int top = Math.max(50, (this.height - 116) / 2);
      context.fill(left, top, left + panelWidth, top + 108, -14867926);
      context.centeredText(this.font, this.title, this.width / 2, top + 14, -1);
      context.centeredText(this.font, Component.translatable("macro_builder.save.save_changes_before_going_back"), this.width / 2, top + 36, -2565928);
      context.centeredText(this.font, Component.translatable("macro_builder.save.discard_will_keep_the_last_saved_version"), this.width / 2, top + 50, -5327166);
      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, -804253680);
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() == 256) {
         this.builder.cancelSavePrompt();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
