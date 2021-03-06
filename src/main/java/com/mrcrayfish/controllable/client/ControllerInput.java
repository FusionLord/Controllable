package com.mrcrayfish.controllable.client;

import com.mrcrayfish.controllable.Controllable;
import com.mrcrayfish.controllable.Reference;
import com.mrcrayfish.controllable.client.gui.GuiControllerLayout;
import com.mrcrayfish.controllable.event.ControllerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.libsdl.SDL.SDL_CONTROLLER_BUTTON_DPAD_DOWN;
import static org.libsdl.SDL.SDL_CONTROLLER_BUTTON_DPAD_UP;

/**
 * Author: MrCrayfish
 */
@SideOnly(Side.CLIENT)
public class ControllerInput
{
    private static final ResourceLocation CURSOR_TEXTURE = new ResourceLocation(Reference.MOD_ID, "textures/gui/cursor.png");

    private int lastUse = 0;
    private boolean keyboardSneaking = false;
    private boolean sneaking = false;
    private boolean isFlying = false;
    private boolean nearSlot = false;
    private int virtualMouseX;
    private int virtualMouseY;
    private float prevXAxis;
    private float prevYAxis;
    private int prevTargetMouseX;
    private int prevTargetMouseY;
    private int targetMouseX;
    private int targetMouseY;
    private float mouseSpeedX;
    private float mouseSpeedY;

    private int dropCounter = -1;

    public int getVirtualMouseX()
    {
        return virtualMouseX;
    }

    public int getVirtualMouseY()
    {
        return virtualMouseY;
    }

    public int getLastUse()
    {
        return lastUse;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        if(event.phase == TickEvent.Phase.START)
        {
            prevTargetMouseX = targetMouseX;
            prevTargetMouseY = targetMouseY;

            if(lastUse > 0)
            {
                lastUse--;
            }

            Controller controller = Controllable.getController();
            if(controller == null) return;

            Minecraft mc = Minecraft.getMinecraft();
            if(mc.inGameHasFocus) return;

            if(mc.currentScreen == null || mc.currentScreen instanceof GuiControllerLayout) return;

            float deadZone = (float) Controllable.getOptions().getDeadZone();

            /* Only need to run code if left thumb stick has input */
            boolean moving = Math.abs(controller.getLThumbStickXValue()) >= deadZone || Math.abs(controller.getLThumbStickYValue()) >= deadZone;
            if(moving)
            {
                /* Updates the target mouse position when the initial thumb stick movement is
                 * detected. This fixes an issue when the user moves the cursor with the mouse then
                 * switching back to controller, the cursor would jump to old target mouse position. */
                if(Math.abs(prevXAxis) < deadZone && Math.abs(prevYAxis) < deadZone)
                {
                    int mouseX = Mouse.getX();
                    int mouseY = Mouse.getY();
                    if(Controllable.getController() != null && Controllable.getOptions().isVirtualMouse())
                    {
                        mouseX = virtualMouseX;
                        mouseY = mc.displayHeight - virtualMouseY;
                    }
                    prevTargetMouseX = targetMouseX = mouseX;
                    prevTargetMouseY = targetMouseY = mouseY;
                }

                float xAxis = (controller.getLThumbStickXValue() > 0.0F ? 1 : -1) * Math.abs(controller.getLThumbStickXValue());
                if(Math.abs(xAxis) > deadZone)
                {
                    mouseSpeedX = xAxis;
                }
                else
                {
                    mouseSpeedX = 0.0F;
                }

                float yAxis = (controller.getLThumbStickYValue() > 0.0F ? 1 : -1) * Math.abs(controller.getLThumbStickYValue());
                if(Math.abs(yAxis) > deadZone)
                {
                    mouseSpeedY = yAxis;
                }
                else
                {
                    mouseSpeedY = 0.0F;
                }
            }

            if(Math.abs(mouseSpeedX) > 0.05F || Math.abs(mouseSpeedY) > 0.05F)
            {
                double mouseSpeed = Controllable.getOptions().getMouseSpeed();
                targetMouseX += mouseSpeed * mouseSpeedX;
                targetMouseY -= mouseSpeed * mouseSpeedY;
                lastUse = 100;
            }

            prevXAxis = controller.getLThumbStickXValue();
            prevYAxis = controller.getLThumbStickYValue();

            this.moveMouseToClosestSlot(moving, mc.currentScreen);

            if(mc.currentScreen instanceof GuiContainerCreative)
            {
                this.handleCreativeScrolling((GuiContainerCreative) mc.currentScreen, controller);
            }

            if(Controllable.getController() != null && Controllable.getOptions().isVirtualMouse())
            {
                GuiScreen gui = mc.currentScreen;
                if(gui != null && (targetMouseX != prevTargetMouseX || targetMouseY != prevTargetMouseY))
                {
                    if(gui.eventButton != -1 && gui.lastMouseEvent > 0L)
                    {
                        try
                        {
                            long deltaTime = Minecraft.getSystemTime() - gui.lastMouseEvent;
                            int mouseX = virtualMouseX * gui.width / mc.displayWidth;
                            int mouseY = virtualMouseY * gui.height / mc.displayHeight;

                            Method mouseClickMove = ReflectionHelper.findMethod(GuiScreen.class, "mouseClickMove", "func_146273_a", int.class, int.class, int.class, long.class);
                            mouseClickMove.setAccessible(true);
                            mouseClickMove.invoke(gui, mouseX, mouseY, gui.eventButton, deltaTime);
                        }
                        catch(IllegalAccessException | InvocationTargetException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onScreenInit(GuiOpenEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if(mc.currentScreen == null)
        {
            targetMouseX = prevTargetMouseX = virtualMouseX = mc.displayWidth / 2;
            targetMouseY = prevTargetMouseY = virtualMouseY = mc.displayHeight / 2;
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onRenderScreen(GuiScreenEvent.DrawScreenEvent.Pre event)
    {
        /* Makes the cursor movement appear smooth between ticks. This will only run if the target
         * mouse position is different to the previous tick's position. This allows for the mouse
         * to still be used as input. */
        Minecraft mc = Minecraft.getMinecraft();
        if(mc.currentScreen != null && (targetMouseX != prevTargetMouseX || targetMouseY != prevTargetMouseY))
        {
            if(!(mc.currentScreen instanceof GuiControllerLayout))
            {
                float partialTicks = mc.getRenderPartialTicks();
                int mouseX = (int) (prevTargetMouseX + (targetMouseX - prevTargetMouseX) * partialTicks + 0.5F);
                int mouseY = (int) (prevTargetMouseY + (targetMouseY - prevTargetMouseY) * partialTicks + 0.5F);
                if(Controllable.getOptions().isVirtualMouse())
                {
                    virtualMouseX = mouseX;
                    virtualMouseY = mc.displayHeight - mouseY;
                }
                else
                {
                    Mouse.setCursorPosition(mouseX, mouseY);
                }
            }
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onRenderScreen(GuiScreenEvent.DrawScreenEvent.Post event)
    {
        if(Controllable.getController() != null && Controllable.getOptions().isVirtualMouse() && lastUse > 0)
        {
            GlStateManager.pushMatrix();
            {
                Minecraft minecraft = event.getGui().mc;
                if(minecraft.player == null || minecraft.player.inventory.getItemStack().isEmpty())
                {
                    ScaledResolution resolution = new ScaledResolution(minecraft);
                    GlStateManager.translate(virtualMouseX / (double) resolution.getScaleFactor(), virtualMouseY / (double) resolution.getScaleFactor(), 300);
                    GlStateManager.color(1.0F, 1.0F, 1.0F);
                    GlStateManager.disableLighting();
                    minecraft.getTextureManager().bindTexture(CURSOR_TEXTURE);
                    GuiScreen.drawScaledCustomSizeModalRect(-8, -8, nearSlot ? 16 : 0, 0, 16, 16, 16, 16, 32, 32);
                }
            }
            GlStateManager.popMatrix();
        }
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event)
    {
        Controller controller = Controllable.getController();
        if(controller == null) return;

        if(event.phase == TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if(player == null) return;

        if(mc.currentScreen == null)
        {
            float deadZone = (float) Controllable.getOptions().getDeadZone();

            /* Handles rotating the yaw of player */
            if(Math.abs(controller.getRThumbStickXValue()) >= deadZone || Math.abs(controller.getRThumbStickYValue()) >= deadZone)
            {
                lastUse = 100;
                float rotationSpeed = (float) Controllable.getOptions().getRotationSpeed();
                ControllerEvent.Turn turnEvent = new ControllerEvent.Turn(controller, rotationSpeed, rotationSpeed * 0.75F);
                if(!MinecraftForge.EVENT_BUS.post(turnEvent))
                {
                    float rotationYaw = turnEvent.getYawSpeed() * controller.getRThumbStickXValue();
                    float rotationPitch = turnEvent.getPitchSpeed() * -controller.getRThumbStickYValue();
                    player.turn(rotationYaw, rotationPitch);
                }
            }
        }

        if(mc.currentScreen == null)
        {
            if(ButtonBindings.DROP_ITEM.isButtonDown())
            {
                lastUse = 100;
                dropCounter++;
            }
        }

        if(dropCounter > 40)
        {
            if(!mc.player.isSpectator())
            {
                mc.player.dropItem(true);
            }
            dropCounter = 0;
        }
        else if(dropCounter > 0 && !ButtonBindings.DROP_ITEM.isButtonDown())
        {
            if(!mc.player.isSpectator())
            {
                mc.player.dropItem(false);
            }
            dropCounter = 0;
        }
    }

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event)
    {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if(player == null) return;

        Controller controller = Controllable.getController();
        if(controller == null) return;

        Minecraft mc = Minecraft.getMinecraft();

        if(keyboardSneaking && !mc.gameSettings.keyBindSneak.isKeyDown())
        {
            sneaking = false;
            keyboardSneaking = false;
        }

        if(mc.gameSettings.keyBindSneak.isKeyDown())
        {
            sneaking = true;
            keyboardSneaking = true;
        }

        if(mc.player.capabilities.isFlying || mc.player.isRiding())
        {
            lastUse = 100;
            sneaking = mc.gameSettings.keyBindSneak.isKeyDown();
            sneaking |= ButtonBindings.SNEAK.isButtonDown();
            isFlying = true;
        }
        else if(isFlying)
        {
            sneaking = false;
            isFlying = false;
        }

        event.getMovementInput().sneak = sneaking;

        if(mc.currentScreen == null)
        {
            if(!MinecraftForge.EVENT_BUS.post(new ControllerEvent.Move(controller)))
            {
                float deadZone = (float) Controllable.getOptions().getDeadZone();
                if(Math.abs(controller.getLThumbStickYValue()) >= deadZone)
                {
                    lastUse = 100;
                    int dir = controller.getLThumbStickYValue() > 0.0F ? -1 : 1;
                    event.getMovementInput().forwardKeyDown = dir > 0;
                    event.getMovementInput().backKeyDown = dir < 0;
                    event.getMovementInput().moveForward = dir * MathHelper.clamp((Math.abs(controller.getLThumbStickYValue()) - deadZone) / (1.0F - deadZone), 0.0F, 1.0F);

                    if(event.getMovementInput().sneak)
                    {
                        event.getMovementInput().moveForward *= 0.3D;
                    }
                }

                if(Math.abs(controller.getLThumbStickXValue()) >= deadZone)
                {
                    lastUse = 100;
                    int dir = controller.getLThumbStickXValue() > 0.0F ? -1 : 1;
                    event.getMovementInput().rightKeyDown = dir < 0;
                    event.getMovementInput().leftKeyDown = dir > 0;
                    event.getMovementInput().moveStrafe = dir * MathHelper.clamp((Math.abs(controller.getLThumbStickXValue()) - deadZone) / (1.0F - deadZone), 0.0F, 1.0F);

                    if(event.getMovementInput().sneak)
                    {
                        event.getMovementInput().moveStrafe *= 0.3D;
                    }
                }
            }

            if(ButtonBindings.JUMP.isButtonDown())
            {
                event.getMovementInput().jump = true;
            }
        }

        if(ButtonBindings.USE_ITEM.isButtonDown() && mc.rightClickDelayTimer == 0 && !mc.player.isHandActive())
        {
            mc.rightClickMouse();
        }
    }

    public void handleButtonInput(Controller controller, int button, boolean state)
    {
        if(Minecraft.getMinecraft().currentScreen instanceof GuiControllerLayout)
        {
            return;
        }

        lastUse = 100;

        ControllerEvent.ButtonInput eventInput = new ControllerEvent.ButtonInput(controller, button, state);
        if(MinecraftForge.EVENT_BUS.post(eventInput)) return;

        button = eventInput.getModifiedButton();
        ButtonBinding.setButtonState(button, state);

        ControllerEvent.Button event = new ControllerEvent.Button(controller);
        if(MinecraftForge.EVENT_BUS.post(event)) return;

        Minecraft mc = Minecraft.getMinecraft();
        if(state)
        {
            if(mc.currentScreen == null)
            {
                if(ButtonBindings.INVENTORY.isButtonPressed())
                {
                    if(mc.playerController.isRidingHorse())
                    {
                        mc.player.sendHorseInventory();
                    }
                    else
                    {
                        mc.getTutorial().openInventory();
                        mc.displayGuiScreen(new GuiInventory(mc.player));
                    }
                    prevTargetMouseX = targetMouseX = Mouse.getX();
                    prevTargetMouseY = targetMouseY = Mouse.getY();
                }
                else if(ButtonBindings.SNEAK.isButtonPressed())
                {
                    if(mc.player != null && !mc.player.capabilities.isFlying && !mc.player.isRiding())
                    {
                        sneaking = !sneaking;
                    }
                }
                else if(ButtonBindings.SCROLL_RIGHT.isButtonPressed())
                {
                    if(mc.player != null)
                    {
                        mc.player.inventory.changeCurrentItem(-1);
                    }
                }
                else if(ButtonBindings.SCROLL_LEFT.isButtonPressed())
                {
                    if(mc.player != null)
                    {
                        mc.player.inventory.changeCurrentItem(1);
                    }
                }
                else if(ButtonBindings.SWAP_HANDS.isButtonPressed())
                {
                    if(mc.player != null && !mc.player.isSpectator() && mc.getConnection() != null)
                    {
                        mc.getConnection().sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.SWAP_HELD_ITEMS, BlockPos.ORIGIN, EnumFacing.DOWN));
                    }
                }
                else if(ButtonBindings.TOGGLE_PERSPECTIVE.isButtonPressed() && mc.inGameHasFocus)
                {
                    cycleThirdPersonView();
                }
                else if(ButtonBindings.PAUSE_GAME.isButtonPressed())
                {
                    if(mc.player != null)
                    {
                        mc.displayInGameMenu();
                    }
                }
                else if(mc.player != null && !mc.player.isHandActive())
                {
                    if(ButtonBindings.ATTACK.isButtonPressed())
                    {
                        mc.clickMouse();
                    }
                    else if(ButtonBindings.USE_ITEM.isButtonPressed())
                    {
                        mc.rightClickMouse();
                    }
                    else if(ButtonBindings.PICK_BLOCK.isButtonPressed())
                    {
                        mc.middleClickMouse();
                    }
                }
            }
            else
            {
                if(ButtonBindings.INVENTORY.isButtonPressed())
                {
                    if(mc.player != null)
                    {
                        mc.player.closeScreen();
                    }
                }
                else if(ButtonBindings.SCROLL_RIGHT.isButtonPressed())
                {
                    if(mc.currentScreen instanceof GuiContainerCreative)
                    {
                        scrollCreativeTabs((GuiContainerCreative) mc.currentScreen, 1);
                    }
                }
                else if(ButtonBindings.SCROLL_LEFT.isButtonPressed())
                {
                    if(mc.currentScreen instanceof GuiContainerCreative)
                    {
                        scrollCreativeTabs((GuiContainerCreative) mc.currentScreen, -1);
                    }
                }
                else if(ButtonBindings.PAUSE_GAME.isButtonPressed())
                {
                    if(mc.currentScreen instanceof GuiIngameMenu)
                    {
                        mc.displayGuiScreen(null);
                    }
                }
                else if(button == Buttons.A)
                {
                    invokeMouseClick(mc.currentScreen, 0);
                }
                else if(button == Buttons.X)
                {
                    invokeMouseClick(mc.currentScreen, 1);
                }
                else if(button == Buttons.B && mc.player != null && mc.player.inventory.getItemStack().isEmpty())
                {
                    invokeMouseClick(mc.currentScreen, 0);
                }
            }
        }
        else
        {
            if(mc.currentScreen == null)
            {

            }
            else
            {
                if(button == Buttons.A)
                {
                    invokeMouseReleased(mc.currentScreen, 0);
                }
                else if(button == Buttons.X)
                {
                    invokeMouseReleased(mc.currentScreen, 1);
                }
            }
        }
    }

    /**
     * Cycles the third person view. Minecraft doesn't have this code in a convenient method.
     */
    private void cycleThirdPersonView()
    {
        Minecraft mc = Minecraft.getMinecraft();

        mc.gameSettings.thirdPersonView++;
        if(mc.gameSettings.thirdPersonView > 2)
        {
            mc.gameSettings.thirdPersonView = 0;
        }

        if(mc.gameSettings.thirdPersonView == 0)
        {
            mc.entityRenderer.loadEntityShader(mc.getRenderViewEntity());
        }
        else if(mc.gameSettings.thirdPersonView == 1)
        {
            mc.entityRenderer.loadEntityShader(null);
        }
    }

    private void scrollCreativeTabs(GuiContainerCreative creative, int dir)
    {
        lastUse = 100;

        try
        {
            Method method = ReflectionHelper.findMethod(GuiContainerCreative.class, "setCurrentCreativeTab", "func_147050_b", CreativeTabs.class);
            method.setAccessible(true);
            if(dir > 0)
            {
                if(creative.getSelectedTabIndex() < CreativeTabs.CREATIVE_TAB_ARRAY.length - 1)
                {
                    method.invoke(creative, CreativeTabs.CREATIVE_TAB_ARRAY[creative.getSelectedTabIndex() + 1]);
                }
            }
            else if(dir < 0)
            {
                if(creative.getSelectedTabIndex() > 0)
                {
                    method.invoke(creative, CreativeTabs.CREATIVE_TAB_ARRAY[creative.getSelectedTabIndex() - 1]);
                }
            }
        }
        catch(IllegalAccessException | InvocationTargetException e)
        {
            e.printStackTrace();
        }
    }

    private void moveMouseToClosestSlot(boolean moving, GuiScreen screen)
    {
        nearSlot = false;

        /* Makes the mouse attracted to slots. This helps with selecting items when using
         * a controller. */
        if(screen instanceof GuiContainer)
        {
            Minecraft mc = Minecraft.getMinecraft();
            GuiContainer guiContainer = (GuiContainer) screen;
            int guiLeft = (guiContainer.width - guiContainer.getXSize()) / 2;
            int guiTop = (guiContainer.height - guiContainer.getYSize()) / 2;
            int mouseX = targetMouseX * guiContainer.width / mc.displayWidth;
            int mouseY = guiContainer.height - targetMouseY * guiContainer.height / mc.displayHeight - 1;

            /* Finds the closest slot in the GUI within 14 pixels (inclusive) */
            Slot closestSlot = null;
            double closestDistance = -1.0;
            for(Slot slot : guiContainer.inventorySlots.inventorySlots)
            {
                int posX = guiLeft + slot.xPos + 8;
                int posY = guiTop + slot.yPos + 8;

                double distance = Math.sqrt(Math.pow(posX - mouseX, 2) + Math.pow(posY - mouseY, 2));
                if((closestDistance == -1.0 || distance < closestDistance) && distance <= 14.0)
                {
                    closestSlot = slot;
                    closestDistance = distance;
                }
            }

            if(closestSlot != null && (closestSlot.getHasStack() || !mc.player.inventory.getItemStack().isEmpty()))
            {
                nearSlot = true;

                int slotCenterX = guiLeft + closestSlot.xPos + 8;
                int slotCenterY = guiTop + closestSlot.yPos + 8;
                int realMouseX = (int) (slotCenterX / ((float) guiContainer.width / (float) mc.displayWidth));
                int realMouseY = (int) (-(slotCenterY + 1 - guiContainer.height) / ((float) guiContainer.width / (float) mc.displayWidth));
                int deltaX = targetMouseX - realMouseX;
                int deltaY = targetMouseY - realMouseY;
                int targetMouseXScaled = targetMouseX * guiContainer.width / mc.displayWidth;
                int targetMouseYScaled = guiContainer.height - targetMouseY * guiContainer.height / mc.displayHeight - 1;

                if(!moving)
                {
                    if(targetMouseXScaled != slotCenterX || targetMouseYScaled != slotCenterY)
                    {
                        targetMouseX -= deltaX * 0.75;
                        targetMouseY -= deltaY * 0.75;
                    }
                    else
                    {
                        mouseSpeedX = 0.0F;
                        mouseSpeedY = 0.0F;
                    }
                }

                mouseSpeedX *= 0.75F;
                mouseSpeedY *= 0.75F;
            }
            else
            {
                mouseSpeedX *= 0.1F;
                mouseSpeedY *= 0.1F;
            }
        }
        else
        {
            mouseSpeedX = 0.0F;
            mouseSpeedY = 0.0F;
        }
    }

    private void handleCreativeScrolling(GuiContainerCreative creative, Controller controller)
    {
        try
        {
            int i = (((GuiContainerCreative.ContainerCreative) creative.inventorySlots).itemList.size() + 9 - 1) / 9 - 5;
            int dir = 0;

            if(controller.getSDL2Controller().getButton(SDL_CONTROLLER_BUTTON_DPAD_UP) || controller.getRThumbStickYValue() <= -0.8F)
            {
                dir = 1;
            }
            else if(controller.getSDL2Controller().getButton(SDL_CONTROLLER_BUTTON_DPAD_DOWN) || controller.getRThumbStickYValue() >= 0.8F)
            {
                dir = -1;
            }

            Field field = ReflectionHelper.findField(GuiContainerCreative.class, "currentScroll", "field_147067_x");
            field.setAccessible(true);

            float currentScroll = field.getFloat(creative);
            currentScroll = (float) ((double) currentScroll - (double) dir / (double) i);
            currentScroll = MathHelper.clamp(currentScroll, 0.0F, 1.0F);
            field.setFloat(creative, currentScroll);
            ((GuiContainerCreative.ContainerCreative) creative.inventorySlots).scrollTo(currentScroll);
        }
        catch(IllegalAccessException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Invokes a mouse click in a GUI. This is modified version that is designed for controllers.
     * Upon clicking, mouse released is called straight away to make sure dragging doesn't happen.
     *
     * @param gui    the gui instance
     * @param button the button to click with
     */
    private void invokeMouseClick(GuiScreen gui, int button)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if(gui != null)
        {
            int mouseX = Mouse.getX();
            int mouseY = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;
            if(Controllable.getController() != null && Controllable.getOptions().isVirtualMouse() && lastUse > 0)
            {
                mouseX = virtualMouseX;
                mouseY = virtualMouseY * gui.height / mc.displayHeight;
            }
            mouseX = mouseX * gui.width / mc.displayWidth;

            gui.eventButton = button;
            gui.lastMouseEvent = Minecraft.getSystemTime();

            try
            {
                Method mouseClicked = ReflectionHelper.findMethod(GuiScreen.class, "mouseClicked", "func_73864_a", int.class, int.class, int.class);
                mouseClicked.setAccessible(true);
                mouseClicked.invoke(gui, mouseX, mouseY, button);
            }
            catch(IllegalAccessException | InvocationTargetException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Invokes a mouse released in a GUI. This is modified version that is designed for controllers.
     * Upon clicking, mouse released is called straight away to make sure dragging doesn't happen.
     *
     * @param gui    the gui instance
     * @param button the button to click with
     */
    private void invokeMouseReleased(GuiScreen gui, int button)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if(gui != null)
        {
            int mouseX = Mouse.getX();
            int mouseY = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;
            if(Controllable.getController() != null && Controllable.getOptions().isVirtualMouse() && lastUse > 0)
            {
                mouseX = virtualMouseX;
                mouseY = virtualMouseY * gui.height / mc.displayHeight;
            }
            mouseX = mouseX * gui.width / mc.displayWidth;

            gui.eventButton = -1;

            try
            {
                //Resets the mouse straight away
                Method mouseReleased = ReflectionHelper.findMethod(GuiScreen.class, "mouseReleased", "func_146286_b", int.class, int.class, int.class);
                mouseReleased.setAccessible(true);
                mouseReleased.invoke(gui, mouseX, mouseY, button);
            }
            catch(IllegalAccessException | InvocationTargetException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Used in order to fix block breaking progress. This method is linked via ASM.
     */
    @SuppressWarnings("unused")
    public static boolean isLeftClicking()
    {
        Minecraft mc = Minecraft.getMinecraft();
        boolean isLeftClicking = mc.gameSettings.keyBindAttack.isKeyDown();
        Controller controller = Controllable.getController();
        if(controller != null)
        {
            if(ButtonBindings.ATTACK.isButtonDown())
            {
                isLeftClicking = true;
            }
        }
        return mc.currentScreen == null && isLeftClicking && mc.inGameHasFocus;
    }

    /**
     * Used in order to fix actions like eating or pulling bow back. This method is linked via ASM.
     */
    @SuppressWarnings("unused")
    public static boolean isRightClicking()
    {
        Minecraft mc = Minecraft.getMinecraft();
        boolean isRightClicking = mc.gameSettings.keyBindUseItem.isKeyDown();
        Controller controller = Controllable.getController();
        if(controller != null)
        {
            if(ButtonBindings.USE_ITEM.isButtonDown())
            {
                isRightClicking = true;
            }
        }
        return isRightClicking;
    }

    /**
     * Used in order to fix the quick move check in inventories. This method is linked via ASM.
     */
    @SuppressWarnings("unused")
    public static boolean canQuickMove()
    {
        boolean isSneaking = (Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54));
        Controller controller = Controllable.getController();
        if(controller != null)
        {
            if(ButtonBindings.QUICK_MOVE.isButtonDown())
            {
                isSneaking = true;
            }
        }
        return isSneaking;
    }

    /**
     * Allows the player list to be shown. This method is linked via ASM.
     */
    @SuppressWarnings("unused")
    public static boolean canShowPlayerList()
    {
        Minecraft mc = Minecraft.getMinecraft();
        boolean canShowPlayerList = mc.gameSettings.keyBindPlayerList.isKeyDown();
        Controller controller = Controllable.getController();
        if(controller != null)
        {
            if(ButtonBindings.PLAYER_LIST.isButtonDown())
            {
                canShowPlayerList = true;
            }
        }
        return canShowPlayerList;
    }
}
