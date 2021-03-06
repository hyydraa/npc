/*
 * Copyright (c) 2017, Kronos <https://github.com/KronosDesign>
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.devtools;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemLayer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Node;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Region;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class DevToolsOverlay extends Overlay
{
	public static final int ITEM_EMPTY = 6512;
	public static final int ITEM_FILLED = 20594;

	private static final Color RED = new Color(221, 44, 0);
	private static final Color GREEN = new Color(0, 200, 83);
	private static final Color ORANGE = new Color(255, 109, 0);
	private static final Color YELLOW = new Color(255, 214, 0);
	private static final Color CYAN = new Color(0, 184, 212);
	private static final Color BLUE = new Color(41, 98, 255);
	private static final Color DEEP_PURPLE = new Color(98, 0, 234);
	private static final Color PURPLE = new Color(170, 0, 255);
	private static final Color GRAY = new Color(158, 158, 158);

	private static final int REGION_SIZE = 104;
	private static final int MAX_DISTANCE = 2400;

	private final Client client;
	private final DevToolsPlugin plugin;

	@Inject
	public DevToolsOverlay(Client client, DevToolsPlugin plugin)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		this.client = client;
		this.plugin = plugin;
	}

	@Override
	public Dimension render(Graphics2D graphics, Point parent)
	{
		Font font = plugin.getFont();
		if (font != null)
		{
			graphics.setFont(font);
		}

		if (plugin.isTogglePlayers())
		{
			renderPlayers(graphics);
		}

		if (plugin.isToggleNpcs())
		{
			renderNpcs(graphics);
		}

		if (plugin.isToggleGroundItems() || plugin.isToggleGroundObjects() || plugin.isToggleGameObjects() || plugin.isToggleWalls() || plugin.isToggleDecor())
		{
			renderTileObjects(graphics);
		}

		if (plugin.isToggleInventory())
		{
			renderInventory(graphics);
		}

		if (plugin.isToggleProjectiles())
		{
			renderProjectiles(graphics);
		}

		renderWidgets(graphics);

		return null;
	}

	private void renderPlayers(Graphics2D graphics)
	{
		List<Player> players = client.getPlayers();
		Player local = client.getLocalPlayer();

		for (Player p : players)
		{
			if (p != local)
			{
				String text = p.getName() + " (A: " + p.getAnimation() + ") (G: " + p.getGraphic() + ")";
				OverlayUtil.renderActorOverlay(graphics, p, text, BLUE);
			}
		}

		String text = local.getName() + " (A: " + local.getAnimation() + ") (G: " + local.getGraphic() + ")";
		OverlayUtil.renderActorOverlay(graphics, local, text, CYAN);
		renderPlayerWireframe(graphics, local, CYAN);
	}

	private void renderNpcs(Graphics2D graphics)
	{
		List<NPC> npcs = client.getNpcs();
		for (NPC npc : npcs)
		{
			NPCComposition composition = npc.getComposition();
			if (composition.getConfigs() != null && composition.transform() != null)
			{
				composition = composition.transform();
			}

			String text = composition.getName() + " (ID: " + composition.getId() + ") (A: " + npc.getAnimation()
					+ ") (G: " + npc.getGraphic() + ")";
			if (npc.getCombatLevel() > 1)
			{
				OverlayUtil.renderActorOverlay(graphics, npc, text, YELLOW);
			}
			else
			{
				OverlayUtil.renderActorOverlay(graphics, npc, text, ORANGE);
			}
		}
	}

	private void renderTileObjects(Graphics2D graphics)
	{
		Region region = client.getRegion();
		Tile[][][] tiles = region.getTiles();

		int z = client.getPlane();

		for (int x = 0; x < REGION_SIZE; ++x)
		{
			for (int y = 0; y < REGION_SIZE; ++y)
			{
				Tile tile = tiles[z][x][y];

				if (tile == null)
				{
					continue;
				}

				Player player = client.getLocalPlayer();
				if (player == null)
				{
					continue;
				}

				if (plugin.isToggleGroundItems())
				{
					renderGroundItems(graphics, tile, player);
				}

				if (plugin.isToggleGroundObjects())
				{
					renderGroundObject(graphics, tile, player);
				}

				if (plugin.isToggleGameObjects())
				{
					renderGameObjects(graphics, tile, player);
				}

				if (plugin.isToggleWalls())
				{
					renderWallObject(graphics, tile, player);
				}

				if (plugin.isToggleDecor())
				{
					renderDecorObject(graphics, tile, player);
				}
			}
		}
	}

	private void renderGroundItems(Graphics2D graphics, Tile tile, Player player)
	{
		ItemLayer itemLayer = tile.getItemLayer();
		if (itemLayer != null)
		{
			if (player.getLocalLocation().distanceTo(itemLayer.getLocalLocation()) <= MAX_DISTANCE)
			{
				Node current = itemLayer.getBottom();
				while (current instanceof Item)
				{
					Item item = (Item) current;
					OverlayUtil.renderTileOverlay(graphics, itemLayer, "ID: " + item.getId() + " Qty:" + item.getQuantity(), RED);
					current = current.getNext();
				}
			}
		}
	}

	private void renderGameObjects(Graphics2D graphics, Tile tile, Player player)
	{
		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects != null)
		{
			for (GameObject gameObject : gameObjects)
			{
				if (gameObject != null)
				{
					if (player.getLocalLocation().distanceTo(gameObject.getLocalLocation()) <= MAX_DISTANCE)
					{
						OverlayUtil.renderTileOverlay(graphics, gameObject, "ID: " + gameObject.getId(), GREEN);
					}

					// Draw a polygon around the convex hull
					// of the model vertices
					Polygon p = gameObject.getConvexHull();
					if (p != null)
					{
						graphics.drawPolygon(p);
					}
				}
			}
		}
	}

	private void renderGroundObject(Graphics2D graphics, Tile tile, Player player)
	{
		GroundObject groundObject = tile.getGroundObject();
		if (groundObject != null)
		{
			if (player.getLocalLocation().distanceTo(groundObject.getLocalLocation()) <= MAX_DISTANCE)
			{
				OverlayUtil.renderTileOverlay(graphics, groundObject, "ID: " + groundObject.getId(), PURPLE);
			}
		}
	}

	private void renderWallObject(Graphics2D graphics, Tile tile, Player player)
	{
		WallObject wallObject = tile.getWallObject();
		if (wallObject != null)
		{
			if (player.getLocalLocation().distanceTo(wallObject.getLocalLocation()) <= MAX_DISTANCE)
			{
				OverlayUtil.renderTileOverlay(graphics, wallObject, "ID: " + wallObject.getId(), GRAY);
			}
		}
	}

	private void renderDecorObject(Graphics2D graphics, Tile tile, Player player)
	{
		DecorativeObject decorObject = tile.getDecorativeObject();
		if (decorObject != null)
		{
			if (player.getLocalLocation().distanceTo(decorObject.getLocalLocation()) <= MAX_DISTANCE)
			{
				OverlayUtil.renderTileOverlay(graphics, decorObject, "ID: " + decorObject.getId(), DEEP_PURPLE);
			}

			Polygon p = decorObject.getConvexHull();
			if (p != null)
			{
				graphics.drawPolygon(p);
			}
		}
	}

	private void renderInventory(Graphics2D graphics)
	{
		Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
		if (inventoryWidget == null || inventoryWidget.isHidden())
		{
			return;
		}

		for (WidgetItem item : inventoryWidget.getWidgetItems())
		{
			Rectangle slotBounds = item.getCanvasBounds();

			String idText = "" + item.getId();
			FontMetrics fm = graphics.getFontMetrics();
			Rectangle2D textBounds = fm.getStringBounds(idText, graphics);

			int textX = (int) (slotBounds.getX() + (slotBounds.getWidth() / 2) - (textBounds.getWidth() / 2));
			int textY = (int) (slotBounds.getY() + (slotBounds.getHeight() / 2) + (textBounds.getHeight() / 2));

			graphics.setColor(new Color(255, 255, 255, 65));
			graphics.fill(slotBounds);

			graphics.setColor(Color.BLACK);
			graphics.drawString(idText, textX + 1, textY + 1);
			graphics.setColor(YELLOW);
			graphics.drawString(idText, textX, textY);
		}
	}

	private void renderProjectiles(Graphics2D graphics)
	{
		List<Projectile> projectiles = client.getProjectiles();

		for (Projectile projectile : projectiles)
		{
			int originX = projectile.getX1();
			int originY = projectile.getY1();

			LocalPoint tilePoint = new LocalPoint(originX, originY);
			Polygon poly = Perspective.getCanvasTilePoly(client, tilePoint);

			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, Color.RED);
			}

			int projectileId = projectile.getId();
			Actor projectileInteracting = projectile.getInteracting();

			String infoString = "";

			if (projectileInteracting == null)
			{
				infoString += "AoE";
			}
			else
			{
				infoString += "Targeted (T: " + projectileInteracting.getName() + ")";
			}

			infoString += " (ID: " + projectileId + ")";

			if (projectileInteracting != null)
			{
				OverlayUtil.renderActorOverlay(graphics, projectile.getInteracting(), infoString, Color.RED);
			}
		}
	}

	public void renderWidgets(Graphics2D graphics)
	{
		Widget widget = plugin.currentWidget;
		int itemIndex = plugin.itemIndex;

		if (widget == null || widget.isHidden())
		{
			return;
		}

		Rectangle childBounds = widget.getBounds();
		graphics.setColor(CYAN);
		graphics.draw(childBounds);

		if (itemIndex == -1)
		{
			return;
		}

		if (widget.getItemId() != ITEM_EMPTY
			&& widget.getItemId() != ITEM_FILLED)
		{
			Rectangle componentBounds = widget.getBounds();

			graphics.setColor(ORANGE);
			graphics.draw(componentBounds);

			renderWidgetText(graphics, componentBounds, widget.getItemId(), YELLOW);
		}

		WidgetItem widgetItem = widget.getWidgetItem(itemIndex);
		if (widgetItem == null
			|| widgetItem.getId() == ITEM_EMPTY
			|| widgetItem.getId() == ITEM_FILLED)
		{
			return;
		}

		Rectangle itemBounds = widgetItem.getCanvasBounds();

		graphics.setColor(ORANGE);
		graphics.draw(itemBounds);

		renderWidgetText(graphics, itemBounds, widgetItem.getId(), YELLOW);
	}

	private void renderWidgetText(Graphics2D graphics, Rectangle bounds, int itemId, Color color)
	{
		if (itemId == -1)
		{
			return;
		}

		String text = itemId + "";
		FontMetrics fm = graphics.getFontMetrics();
		Rectangle2D textBounds = fm.getStringBounds(text, graphics);

		int textX = (int) (bounds.getX() + (bounds.getWidth() / 2) - (textBounds.getWidth() / 2));
		int textY = (int) (bounds.getY() + (bounds.getHeight() / 2) + (textBounds.getHeight() / 2));

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, textX + 1, textY + 1);
		graphics.setColor(color);
		graphics.drawString(text, textX, textY);
	}

	private void renderPlayerWireframe(Graphics2D graphics, Player player, Color color)
	{
		Polygon[] polys = player.getPolygons();

		if (polys == null)
		{
			return;
		}

		graphics.setColor(color);

		for (Polygon p : polys)
		{
			graphics.drawPolygon(p);
		}
	}

}
