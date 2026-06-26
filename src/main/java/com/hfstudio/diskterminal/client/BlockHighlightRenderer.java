package com.hfstudio.diskterminal.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.config.DiskTerminalClientConfig;
import com.hfstudio.diskterminal.util.PosUtil;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Renders pulsing wireframe outlines around highlighted blocks, visible through walls.
 * <p>
 * The 1.12 source also drew directional arrows for distant blocks using the modern
 * {@code BufferBuilder}/{@code DefaultVertexFormats} pipeline; that eye-candy is deferred. The core
 * see-through wireframe is ported to the 1.7.10 immediate-mode {@link Tessellator}.
 */
public class BlockHighlightRenderer {

    private static final Map<BlockPos, Long> highlightedBlocks = new HashMap<>();

    public static void addHighlight(BlockPos pos, long durationMs) {
        highlightedBlocks.put(pos, System.currentTimeMillis() + durationMs);
    }

    public static void removeHighlight(BlockPos pos) {
        highlightedBlocks.remove(pos);
    }

    public static void clearHighlights() {
        highlightedBlocks.clear();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (highlightedBlocks.isEmpty()) return;

        long now = System.currentTimeMillis();
        highlightedBlocks.entrySet()
            .removeIf(blockPosLongEntry -> blockPosLongEntry.getValue() < now);
        if (highlightedBlocks.isEmpty()) return;

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;

        float partialTicks = event.partialTicks;
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        int maxDist = DiskTerminalClientConfig.getInstance()
            .getMaxHighlightDistance();
        double maxDistSq = maxDist > 0 ? (double) maxDist * maxDist : -1;

        GL11.glPushMatrix();
        GL11.glTranslated(-playerX, -playerY, -playerZ);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(3.0F);

        float alpha = 0.7f + 0.3f * (float) Math.sin(now / 500.0);

        for (BlockPos pos : highlightedBlocks.keySet()) {
            if (maxDistSq >= 0 && PosUtil.distSq(pos, blockPosOf(player)) > maxDistSq) continue;
            renderBlockOutline(pos, 0.0f, 1.0f, 0.5f, alpha);
        }

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    private static BlockPos blockPosOf(EntityPlayer player) {
        return new BlockPos(
            (int) Math.floor(player.posX),
            (int) Math.floor(player.posY),
            (int) Math.floor(player.posZ));
    }

    private void renderBlockOutline(BlockPos pos, float red, float green, float blue, float alpha) {
        double minX = pos.getX() - 0.002;
        double minY = pos.getY() - 0.002;
        double minZ = pos.getZ() - 0.002;
        double maxX = pos.getX() + 1.002;
        double maxY = pos.getY() + 1.002;
        double maxZ = pos.getZ() + 1.002;

        Tessellator t = Tessellator.instance;

        t.startDrawing(GL11.GL_LINE_STRIP);
        t.setColorRGBA_F(red, green, blue, alpha);
        t.addVertex(minX, minY, minZ);
        t.addVertex(maxX, minY, minZ);
        t.addVertex(maxX, minY, maxZ);
        t.addVertex(minX, minY, maxZ);
        t.addVertex(minX, minY, minZ);
        t.draw();

        t.startDrawing(GL11.GL_LINE_STRIP);
        t.setColorRGBA_F(red, green, blue, alpha);
        t.addVertex(minX, maxY, minZ);
        t.addVertex(maxX, maxY, minZ);
        t.addVertex(maxX, maxY, maxZ);
        t.addVertex(minX, maxY, maxZ);
        t.addVertex(minX, maxY, minZ);
        t.draw();

        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(red, green, blue, alpha);
        t.addVertex(minX, minY, minZ);
        t.addVertex(minX, maxY, minZ);
        t.addVertex(maxX, minY, minZ);
        t.addVertex(maxX, maxY, minZ);
        t.addVertex(maxX, minY, maxZ);
        t.addVertex(maxX, maxY, maxZ);
        t.addVertex(minX, minY, maxZ);
        t.addVertex(minX, maxY, maxZ);
        t.draw();
    }
}
