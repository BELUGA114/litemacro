package com.alfre.macrobuilder;

import static com.alfre.macrobuilder.MacroText.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class MacroResetConfirmScreen extends Screen {
   private final MacroRunner runner;
   private final int macroNumber;
   private final int returnPage;

   MacroResetConfirmScreen(MacroRunner runner, int macroNumber, int returnPage) {
      super(Component.translatable("macro_builder.reset.reset_macro"));
      this.runner = runner;
      this.macroNumber = macroNumber;
      this.returnPage = returnPage;
   }

   protected void init() {
      int panelWidth = 320;
      int left = (this.width - panelWidth) / 2;
      int top = Math.max(50, (this.height - 130) / 2);
      int buttonY = top + 92;
      this.addRenderableWidget(Button.builder(Component.translatable("macro_builder.reset.reset"), button -> {
         this.runner.resetMacro(this.minecraft, this.macroNumber);
         this.minecraft.setScreen(new MacroListScreen(this.runner, this.returnPage));
      }).bounds(left + 40, buttonY, 104, 20).build());
      this.addRenderableWidget(
         Button.builder(Component.translatable("macro_builder.reset.cancel"), button -> this.minecraft.setScreen(new MacroListScreen(this.runner, this.returnPage)))
            .bounds(left + 176, buttonY, 104, 20)
            .build()
      );
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context, mouseX, mouseY, delta);
      int panelWidth = 320;
      int left = (this.width - panelWidth) / 2;
      int top = Math.max(50, (this.height - 130) / 2);
      context.fill(left, top, left + panelWidth, top + 122, -14867926);
      context.drawCenteredString(this.font, this.title, this.width / 2, top + 14, -1);
      context.drawCenteredString(this.font, Component.translatable("macro_builder.reset.reset_macro_back_to_new", this.macroNumber), this.width / 2, top + 36, -2565928);
      context.drawCenteredString(this.font, Component.translatable("macro_builder.reset.this_clears_components_name_and_keybind"), this.width / 2, top + 52, -11823);
      context.drawCenteredString(this.font, Component.translatable("macro_builder.reset.download_or_copy_first_if_you_need_a_backup"), this.width / 2, top + 68, -5327166);
      super.render(context, mouseX, mouseY, delta);
   }

   public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, -804253680);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) {
         this.minecraft.setScreen(new MacroListScreen(this.runner, this.returnPage));
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
