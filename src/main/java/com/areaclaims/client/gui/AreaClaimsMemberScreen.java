package com.areaclaims.client.gui;

import com.areaclaims.claim.ClaimRole;
import com.areaclaims.client.data.ClientClaimCache;
import com.areaclaims.client.data.ClientNetworkUtil;
import com.areaclaims.network.ClaimEditorSnapshot;
import com.areaclaims.network.SetRolePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Eigenständiger Mitglieder-Verwaltungs-Screen (ROADMAP.md Phase 7-Nachtrag 2, Punkt 3) - ersetzt
 * die frühere Inline-Bearbeitung im {@link AreaClaimsEditorScreen}-Detail-Panel (das zeigt jetzt
 * nur noch eine schreibgeschützte Liste + einen Stift-Button, der HIERHER navigiert). Reihenfolge
 * je Zeile laut Nutzer-Vorgabe: Rollen-Button ZUERST, dann Name, dann der rote CobbleCompanion-
 * Stil-"✗" (siehe {@link GlyphButton}), der sich je nach Namenslänge verschiebt (kein fester
 * Spalten-X, sondern {@code nameX + Textbreite(name) + Abstand}).
 */
public class AreaClaimsMemberScreen extends FixedScaleScreen {

    private static final int TITLE_Y = 8;
    private static final int MARGIN = 10;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xA0A0A0;

    private static final int ROW_Y_START = 30;
    private static final int ROW_HEIGHT = 20;
    private static final int ROLE_BUTTON_X = MARGIN;
    private static final int NAME_X_GAP = 6;
    private static final int REMOVE_GAP = 6;
    private static final int REMOVE_SIZE = 12;
    private static final int BUTTON_TEXT_PADDING = 12;

    private static final int ADD_FIELD_WIDTH = 150;
    private static final int ADD_BUTTON_GAP = 6;

    private static final int CLOSE_BUTTON_WIDTH = 80;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 10;

    private final String claimId;
    private int lastSeenCacheGeneration = -1;
    private EditBox addMemberField;

    public AreaClaimsMemberScreen(String claimId) {
        super(Component.translatable("areaclaims.members.title"));
        this.claimId = claimId;
    }

    @Override
    protected void initScaled() {
        lastSeenCacheGeneration = ClientClaimCache.generation();
        buildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (ClientClaimCache.generation() != lastSeenCacheGeneration) {
            lastSeenCacheGeneration = ClientClaimCache.generation();
            clearWidgets();
            buildWidgets();
        }
    }

    private ClaimEditorSnapshot.ClaimEntry claim() {
        return ClientClaimCache.get().claims.stream().filter(c -> c.id.equals(claimId)).findFirst().orElse(null);
    }

    private int roleButtonWidth() {
        int max = 0;
        for (ClaimRole role : ClaimRole.values()) {
            max = Math.max(max, this.font.width(role.translatable()));
        }
        return max + BUTTON_TEXT_PADDING;
    }

    private void buildWidgets() {
        ClaimEditorSnapshot.ClaimEntry claim = claim();
        int roleWidth = roleButtonWidth();
        int nameX = ROLE_BUTTON_X + roleWidth + NAME_X_GAP;
        int y = ROW_Y_START;

        if (claim != null) {
            for (ClaimEditorSnapshot.MemberEntry member : claim.members) {
                int rowY = y;
                ClaimRole current = parseRoleOrNone(member.role);
                addRenderableWidget(Button.builder(current.translatable(), b -> {
                        ClaimRole next = cycleRole(current);
                        sendSetRole(member.name, next);
                    })
                    .bounds(ROLE_BUTTON_X, rowY, roleWidth, ROW_HEIGHT - 2)
                    .build());

                // Roter "✗"-Glyph verschiebt sich mit der tatsächlichen Namensbreite dieser Zeile
                // (KEIN fester Spalten-X) - siehe Klassenkommentar/GlyphButton-Referenz.
                int nameWidth = this.font.width(member.name);
                int removeX = nameX + nameWidth + REMOVE_GAP;
                addRenderableWidget(new GlyphButton(removeX, rowY + (ROW_HEIGHT - 2 - REMOVE_SIZE) / 2, REMOVE_SIZE, REMOVE_SIZE,
                    Component.literal("✗"), 0xFFFF5555, () -> sendSetRole(member.name, ClaimRole.NONE)));

                y += ROW_HEIGHT;
            }
        }

        int addFieldY = y + 6;
        addMemberField = new EditBox(this.font, ROLE_BUTTON_X, addFieldY, ADD_FIELD_WIDTH, ROW_HEIGHT - 2,
            Component.translatable("areaclaims.editor.add_member_hint"));
        addMemberField.setHint(Component.translatable("areaclaims.editor.add_member_hint"));
        addRenderableWidget(addMemberField);
        // Beschriftung von "Als Mitglied hinzufügen" -> "Mitglied hinzufügen" (Nutzer-Vorgabe).
        addRenderableWidget(Button.builder(Component.translatable("areaclaims.members.add_button"), b -> {
                String name = addMemberField.getValue().trim();
                if (!name.isEmpty()) sendSetRole(name, ClaimRole.MEMBER);
            })
            .bounds(ROLE_BUTTON_X + ADD_FIELD_WIDTH + ADD_BUTTON_GAP, addFieldY,
                this.font.width(Component.translatable("areaclaims.members.add_button")) + BUTTON_TEXT_PADDING, ROW_HEIGHT - 2)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("areaclaims.editor.close"), b -> returnToEditor())
            .bounds(this.width - CLOSE_BUTTON_WIDTH - MARGIN, this.height - CLOSE_BUTTON_HEIGHT - BOTTOM_MARGIN,
                CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .build());
    }

    private ClaimRole cycleRole(ClaimRole current) {
        return switch (current) {
            case NONE -> ClaimRole.MEMBER;
            case MEMBER -> ClaimRole.STAFF;
            case STAFF -> ClaimRole.COOWNER;
            case COOWNER -> ClaimRole.MEMBER;
        };
    }

    private ClaimRole parseRoleOrNone(String name) {
        try {
            return ClaimRole.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ClaimRole.NONE;
        }
    }

    private void sendSetRole(String playerName, ClaimRole role) {
        SetRolePacket packet = new SetRolePacket(claimId, playerName, role.name());
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }

    /**
     * Punkt 5 (Nachtrag 3): gibt die zuletzt bearbeitete Auswahl an den Editor zurück, statt ihn
     * mit dem parameterlosen Konstruktor (der wieder beim ersten Hauptbereich landet) neu
     * aufzubauen - siehe ausführlichen Kommentar bei {@link AreaClaimsEditorScreen#AreaClaimsEditorScreen(String, String)}.
     */
    private void returnToEditor() {
        ClaimEditorSnapshot.ClaimEntry claim = claim();
        String mainId = claim == null ? null : (claim.main ? claim.id : claim.parentId);
        String subId = claim != null && !claim.main ? claim.id : null;
        Minecraft.getInstance().setScreen(new AreaClaimsEditorScreen(mainId, subId));
    }

    @Override
    public void onClose() {
        returnToEditor();
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderScaled(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, TEXT_COLOR);

        ClaimEditorSnapshot.ClaimEntry claim = claim();
        if (claim == null) return;
        int roleWidth = roleButtonWidth();
        int nameX = ROLE_BUTTON_X + roleWidth + NAME_X_GAP;
        int y = ROW_Y_START;
        if (claim.members.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("areaclaims.editor.no_members"), MARGIN, y, MUTED_TEXT_COLOR, false);
            return;
        }
        List<ClaimEditorSnapshot.MemberEntry> members = new ArrayList<>(claim.members);
        for (ClaimEditorSnapshot.MemberEntry member : members) {
            graphics.drawString(this.font, member.name, nameX, y + 6, TEXT_COLOR, false);
            y += ROW_HEIGHT;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
