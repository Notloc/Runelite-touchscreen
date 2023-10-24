package com.TouchScreenPlugin;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.*;
import java.awt.event.MouseEvent;

@Slf4j
@PluginDescriptor(
	name = "Touch Screen"
)
public class TouchScreenPlugin extends Plugin implements MouseListener
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TouchScreenConfig config;

	@Inject
	private MouseManager mouseManager;

	private boolean leftMouseButtonDown = false;
	private boolean forceLeftClick = false;
	private boolean isRotating = false;

	private Point dragStartPoint = null;

	private WidgetInfo[] quickWidgets = new WidgetInfo[] {
		WidgetInfo.MINIMAP_QUICK_PRAYER_ORB,
		WidgetInfo.MINIMAP_TOGGLE_RUN_ORB,
		WidgetInfo.MINIMAP_SPEC_ORB,
		WidgetInfo.MINIMAP_HEALTH_ORB
	};

	private WidgetInfo[] dragWidgets = new WidgetInfo[] {
			WidgetInfo.BANK_CONTAINER,
			WidgetInfo.WORLD_MAP_VIEW,
			WidgetInfo.CHATBOX,
			WidgetInfo.FIXED_VIEWPORT_INVENTORY_CONTAINER,
			WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_CONTAINER,
			WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_CONTAINER,
			WidgetInfo.DEPOSIT_BOX_INVENTORY_ITEMS_CONTAINER
	};

	@Override
	protected void startUp() throws Exception
	{
		leftMouseButtonDown = false;
		forceLeftClick = false;
		isRotating = false;
		mouseManager.registerMouseListener(this);
	}

	@Override
	protected void shutDown() throws Exception
	{
		mouseManager.unregisterMouseListener(this);
	}

	@Provides
	TouchScreenConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TouchScreenConfig.class);
	}

	@Override
	public MouseEvent mousePressed(MouseEvent mouseEvent) {
		if (!isLeftMouse(mouseEvent) || client.getGameState() != GameState.LOGGED_IN) {
			return mouseEvent;
		}

		// Avoid adding lag to prayer flicking and such
		if (forceLeftClick || mouseIsOverGui(mouseEvent.getPoint(), quickWidgets, false)) {
			forceLeftClick = false;
			return mouseEvent;
		}

		// We need to be on the client thread to properly check widget visibility
		clientThread.invokeLater(() -> {
			if (mouseIsOverGui(mouseEvent.getPoint(), dragWidgets, true)) {
				forceLeftClick = true;
				mouseEvent.getComponent().dispatchEvent(
					rebuildMouseEvent(mouseEvent, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, true)
				);
				return;
			}

			dragStartPoint = mouseEvent.getPoint();
			leftMouseButtonDown = true;
		});

		mouseEvent.consume();
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent mouseEvent) {
		if (!leftMouseButtonDown || !isLeftMouse(mouseEvent)) {
			return mouseEvent;
		}

		if (isRotating || getDistance(dragStartPoint, mouseEvent.getPoint()) > config.touchDragThreshold()) {
			if (!isRotating) {
				// Fire middle mouse click event to start camera rotation
				Component component = mouseEvent.getComponent();
				component.dispatchEvent(
					rebuildMouseEvent(mouseEvent, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON2, true)
				);
			}
			isRotating = true;

			// Replace the left mouse drag with a middle mouse drag
			mouseEvent = rebuildMouseEvent(mouseEvent, mouseEvent.getID(), MouseEvent.BUTTON2, false);
		}

		return mouseEvent;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent mouseEvent) {
		if (!isLeftMouse(mouseEvent) || !leftMouseButtonDown || mouseEvent.isConsumed()) {
			return mouseEvent;
		}

		leftMouseButtonDown = false;
		Component component = mouseEvent.getComponent();

		if (isRotating) {
			isRotating = false;
			component.dispatchEvent(
				rebuildMouseEvent(mouseEvent, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON2, true)
			);
		} else {
			final long t = System.currentTimeMillis();
			clientThread.invokeLater(() -> {
				// Wait a few ms to fix the dead click bug on touch screens
				// Is RS polling the mouse position somewhere instead of using events?
				long delta = System.currentTimeMillis() - t;
				if (delta < config.touchDelayMs()){
					return false;
				}

				forceLeftClick = true;
				component.dispatchEvent(
					rebuildMouseEvent(mouseEvent, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, true)
				);
				return true;
			});
		}
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent mouseEvent) { return mouseEvent; }
	@Override
	public MouseEvent mouseExited(MouseEvent mouseEvent) { return mouseEvent; }
	@Override
	public MouseEvent mouseMoved(MouseEvent mouseEvent) { return mouseEvent; }
	@Override
	public MouseEvent mouseClicked(MouseEvent mouseEvent) { return mouseEvent; }

	private boolean isLeftMouse(MouseEvent mouseEvent)
	{
		return SwingUtilities.isLeftMouseButton(mouseEvent);
	}

	private boolean mouseIsOverGui(Point point, WidgetInfo[] widgets, boolean onThread)
	{
		net.runelite.api.Point rlPoint = new net.runelite.api.Point(point.x, point.y);
		for (WidgetInfo widgetInfo : widgets) {
			Widget widget = client.getWidget(widgetInfo);
			if (testWidget(widget, rlPoint, onThread)) {
				return true;
			}
		}

		return false;
	}

	private boolean testWidget(Widget widget, net.runelite.api.Point point, boolean onThread)
	{
		if (widget == null) {
			return false;
		}

		if (onThread) {
			if (widget.contains(point) && !widget.isHidden()) {
				return true;
			}
		} else {
			if (!widget.isSelfHidden() && widget.contains(point)) {
				return true;
			}
		}

		// Got some NPEs from the children accessors in some situations, hence the trys
		try {
			Widget[] widgets = widget.getChildren();
			for (Widget child : widgets) {
				if (testWidget(child, point, onThread)) {
					return true;
				}
			}
		} catch (Exception e) {}
		try {
			Widget[] widgets = widget.getStaticChildren();
			for (Widget child : widgets) {
				if (testWidget(child, point, onThread)) {
					return true;
				}
			}
		} catch (Exception e) {}
		try {
			Widget[] widgets = widget.getDynamicChildren();
			for (Widget child : widgets) {
				if (testWidget(child, point, onThread)) {
					return true;
				}
			}
		} catch (Exception e) {}

		return false;
	}

	private int getDistance(Point a, Point b)
	{
		return (int) Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2));
	}

	private MouseEvent rebuildMouseEvent(MouseEvent mouseEvent, Integer type, Integer button, boolean forDispatch)
	{
		Point point = forDispatch ? client.getCanvas().getMousePosition() : mouseEvent.getPoint();
		return new MouseEvent(
			mouseEvent.getComponent(),
			type,
			mouseEvent.getWhen(),
			mouseEvent.getModifiersEx(),
			point.x,
			point.y,
			mouseEvent.getClickCount(),
			mouseEvent.isPopupTrigger(),
			button
		);
	}
}
