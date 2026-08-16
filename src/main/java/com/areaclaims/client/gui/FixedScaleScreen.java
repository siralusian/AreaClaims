package com.areaclaims.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Basisklasse für Screens, die IMMER so groß erscheinen sollen wie bei GUI-Größe 2 - unabhängig
 * davon, welche GUI-Größe der Spieler tatsächlich eingestellt hat (Nutzer-Fund: bei GUI-Größe 3/4
 * ragte ein Teil der Editor-Screens über den Bildschirmrand hinaus bzw. wurden Listen/Panels
 * zusammengequetscht - "Option 1": nicht mit der GUI-Größe mitskalieren, portiert aus dem
 * gleichnamigen, in CobbleCompanion entworfenen aber dort noch unbenutzten/ungetesteten Muster).
 *
 * Funktionsweise: this.width/this.height werden VOR initScaled() auf eine "virtuelle" Auflösung
 * überschrieben, die auf Basis der physischen Fenstergröße immer so groß ist wie bei GUI-Größe 2
 * (bleibt bei tatsächlich eingestellter GUI-Größe 2 exakt unverändert zum bisherigen Verhalten).
 * Damit brauchen Subklassen ihre bestehende, auf this.width/this.height basierende Layout-Mathematik
 * NICHT anzupassen. Beim Rendern wird zusätzlich ein Skalierungsfaktor auf den PoseStack gelegt, der
 * diese virtuelle Auflösung wieder auf die tatsächlich verfügbare Bildschirmfläche staucht/streckt;
 * eingehende Maus-Koordinaten werden im Gegenzug auf virtuelle Koordinaten umgerechnet, bevor sie an
 * Widgets/Subklassen weitergereicht werden.
 *
 * Wichtig: GuiGraphics.enableScissor() rechnet NICHT über den PoseStack um (eigener ScissorStack,
 * der direkt mit der tatsächlichen Fenster-GUI-Skalierung multipliziert) - Subklassen, die Scissor
 * benutzen, müssen ihre virtuellen Scissor-Koordinaten über {@link #toRealX(int)}/{@link #toRealY(int)}
 * in echte Bildschirm-Koordinaten umrechnen, bevor sie enableScissor aufrufen.
 *
 * Anders als die CobbleCompanion-Vorlage bietet diese Kopie zusätzlich mouseReleasedScaled/
 * mouseDraggedScaled-Hooks (statt die Methoden final direkt an Vanilla durchzureichen) - mehrere
 * CreativeMenu-Screens brauchen eigenes Drag&Drop-Handling.
 */
public abstract class FixedScaleScreen extends Screen {

    private static final double REFERENCE_GUI_SCALE = 2.0;

    /** virtuell -> real (this.width/height-Raum). < 1, wenn die tatsächliche GUI-Größe > 2 ist. */
    private double renderScale = 1.0;

    protected FixedScaleScreen(Component title) {
        super(title);
    }

    private double toVirtualX(double realX) {
        return renderScale > 0 ? realX / renderScale : realX;
    }

    private double toVirtualY(double realY) {
        return renderScale > 0 ? realY / renderScale : realY;
    }

    /** Rechnet eine virtuelle X-Koordinate (this.width-Raum) in eine echte Bildschirm-X-Koordinate um - für enableScissor(), siehe Klassenkommentar. */
    protected int toRealX(int virtualX) {
        return (int) Math.round(virtualX * renderScale);
    }

    /** Rechnet eine virtuelle Y-Koordinate (this.height-Raum) in eine echte Bildschirm-Y-Koordinate um - für enableScissor(), siehe Klassenkommentar. */
    protected int toRealY(int virtualY) {
        return (int) Math.round(virtualY * renderScale);
    }

    /**
     * Für vanilla-Hilfsmethoden bzw. eingebettete echte Screens, die selbst intern enableScissor()
     * mit den ihnen übergebenen Koordinaten aufrufen - diese müssen mit über toRealX/toRealY
     * umgerechneten (echten) Koordinaten aufgerufen werden, damit ihr eigener Scissor-Aufruf stimmt.
     * Das reicht aber allein nicht: ihre interne Positionierung läuft über den PoseStack, der hier
     * gerade noch auf "virtuell" skaliert ist - ruft man sie mit echten Koordinaten in diesem
     * Kontext auf, würde nochmal skaliert (falsche Position). Diese Methode hebt die ambiente
     * Skalierung für die Dauer von action auf, sodass echte Koordinaten auch tatsächlich 1:1 an der
     * richtigen Stelle landen.
     */
    protected void renderInRealSpace(GuiGraphics graphics, Runnable action) {
        graphics.pose().pushPose();
        float inverse = renderScale > 0 ? (float) (1.0 / renderScale) : 1f;
        graphics.pose().scale(inverse, inverse, 1f);
        action.run();
        graphics.pose().popPose();
    }

    @Override
    protected final void init() {
        Minecraft mc = Minecraft.getInstance();
        double actualScale = mc.getWindow().getGuiScale();
        renderScale = actualScale > 0 ? REFERENCE_GUI_SCALE / actualScale : 1.0;

        int physicalWidth = mc.getWindow().getWidth();
        int physicalHeight = mc.getWindow().getHeight();
        this.width = Math.max(1, (int) Math.floor(physicalWidth / REFERENCE_GUI_SCALE));
        this.height = Math.max(1, (int) Math.floor(physicalHeight / REFERENCE_GUI_SCALE));

        initScaled();
    }

    /** Ersetzt init() - this.width/this.height stehen hier bereits auf der virtuellen (GUI-Größe-2-äquivalenten) Auflösung. */
    protected void initScaled() {
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().scale((float) renderScale, (float) renderScale, 1f);
        int vMouseX = (int) toVirtualX(mouseX);
        int vMouseY = (int) toVirtualY(mouseY);
        renderScaled(graphics, vMouseX, vMouseY, partialTick);
        graphics.pose().popPose();
    }

    /** Ersetzt render() - mouseX/mouseY sind hier bereits virtuelle Koordinaten. */
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    /** Zeichnet nur die vanilla-Widgets (Screen.render), ohne den PoseStack erneut zu skalieren - für Subklassen, die statt super.render() aufzurufen diese Methode nutzen sollen. */
    protected final void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClickedScaled(toVirtualX(mouseX), toVirtualY(mouseY), button);
    }

    /** Ersetzt mouseClicked() - mouseX/mouseY sind hier bereits virtuelle Koordinaten. */
    protected boolean mouseClickedScaled(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return mouseScrolledScaled(toVirtualX(mouseX), toVirtualY(mouseY), scrollX, scrollY);
    }

    /** Ersetzt mouseScrolled() - mouseX/mouseY sind hier bereits virtuelle Koordinaten. */
    protected boolean mouseScrolledScaled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return mouseReleasedScaled(toVirtualX(mouseX), toVirtualY(mouseY), button);
    }

    /** Ersetzt mouseReleased() - mouseX/mouseY sind hier bereits virtuelle Koordinaten. */
    protected boolean mouseReleasedScaled(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return mouseDraggedScaled(toVirtualX(mouseX), toVirtualY(mouseY), button, toVirtualX(dragX), toVirtualY(dragY));
    }

    /** Ersetzt mouseDragged() - alle Koordinaten sind hier bereits virtuell. */
    protected boolean mouseDraggedScaled(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public final void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(toVirtualX(mouseX), toVirtualY(mouseY));
    }
}
