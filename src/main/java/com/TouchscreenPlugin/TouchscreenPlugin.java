package com.TouchscreenPlugin;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

@Slf4j
@PluginDescriptor(
	name = "Touchscreen"
)
public class TouchscreenPlugin extends Plugin implements MouseListener, MouseWheelListener
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private DrawManager       drawManager;
	@Inject private TouchscreenConfig config;
	@Inject private MouseManager      mouseManager;

	private OverlayManager overlayManager;

	private boolean isTouchPressed = false;
	private boolean isTouchingGui   = false;
	private boolean forceLeftClick = false;

	private boolean readyToRotate = false;
	private boolean isRotating    = false;

	private boolean readyToScroll = false;
	private boolean isScrolling   = false;
	private boolean isScrollingOnMinimap = false;
	private boolean isScrolling3dZoom = false;

	private Point touchStartPoint = null;
	private Point previousTouchPointForScrolling = null;
	private Point scrollingHoldPoint = null;

	private boolean    doEmulatedClickNextFrame    = false;
	private boolean    waitedOneFrame              = false;
	private MouseEvent emulatedClickEventPrototype = null;

	// Widgets that are time sensitive and skip the emulated mouse click
	private final WidgetInfo[] QUICK_WIDGETS = new WidgetInfo[] {
			WidgetInfo.MINIMAP_QUICK_PRAYER_ORB,
			WidgetInfo.MINIMAP_TOGGLE_RUN_ORB,
			WidgetInfo.MINIMAP_SPEC_ORB,
			WidgetInfo.MINIMAP_HEALTH_ORB,
	};

	// Widgets that perform scrolling when dragged
	private final WidgetInfo[] SCROLLABLE_WIDGETS = new WidgetInfo[] {
			WidgetInfo.CHATBOX,
			WidgetInfo.BANK_ITEM_CONTAINER,
			WidgetInfo.QUESTLIST_CONTAINER,
			WidgetInfo.ACHIEVEMENT_DIARY_CONTAINER,
			WidgetInfo.WORLD_SWITCHER_LIST,
	};

	// Widgets that suppress camera rotation
	private final WidgetInfo[] BLOCKING_WIDGETS = new WidgetInfo[] {
			WidgetInfo.BANK_CONTAINER,
			WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER,
			WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_CONTAINER,
			WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_CONTAINER,
			WidgetInfo.WORLD_MAP_VIEW,
			WidgetInfo.FIXED_VIEWPORT_INVENTORY_CONTAINER,
			WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_CONTAINER,
			WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_CONTAINER,
			WidgetInfo.DEPOSIT_BOX_INVENTORY_ITEMS_CONTAINER,
			WidgetInfo.GENERIC_SCROLL_TEXT,
			WidgetInfo.WORLD_SWITCHER_LIST,
			WidgetInfo.WORLD_SWITCHER_BUTTON,
			WidgetInfo.BANK_SCROLLBAR,
	};

	private final WidgetInfo[] MINIMAP_WIDGETS = new WidgetInfo[] {
			WidgetInfo.FIXED_VIEWPORT_MINIMAP_DRAW_AREA,
			WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA,
			WidgetInfo.RESIZABLE_MINIMAP_STONES_DRAW_AREA
	};

	private final Runnable frameListener = () -> {
		if (!doEmulatedClickNextFrame || emulatedClickEventPrototype == null) {
			return;
		}

		// Order of events seems to necessitate this
		if (!waitedOneFrame) {
			waitedOneFrame = true;
		}

		forceLeftClick = true;
		emulatedClickEventPrototype.getComponent().dispatchEvent(
				rebuildMouseEvent(emulatedClickEventPrototype, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, true)
		);

		isTouchingGui = true; // So we return early and don't loop
		emulatedClickEventPrototype.getComponent().dispatchEvent(
				rebuildMouseEvent(emulatedClickEventPrototype, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, true)
		);

		doEmulatedClickNextFrame = false;
	};

	@Provides
	TouchscreenConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TouchscreenConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		resetState();
		mouseManager.registerMouseListener(this);
		mouseManager.registerMouseWheelListener(this);
		drawManager.registerEveryFrameListener(frameListener);
	}

	@Override
	protected void shutDown() throws Exception
	{
		mouseManager.unregisterMouseListener(this);
		mouseManager.unregisterMouseWheelListener(this);
		drawManager.unregisterEveryFrameListener(frameListener);
	}

	private void resetState() {
		isTouchPressed = false;
		isTouchingGui = false;
		forceLeftClick = false;

		readyToRotate = false;
		isRotating = false;

		readyToScroll = false;
		isScrolling = false;
		isScrollingOnMinimap = false;
		isScrolling3dZoom = false;

		touchStartPoint = null;
		previousTouchPointForScrolling = null;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent mouseEvent) {
		if (shouldSkipProcessing(mouseEvent)) {
			return mouseEvent;
		}

		if (forceLeftClick) {
			forceLeftClick = false;
			return mouseEvent;
		}

		// Avoid adding lag to prayer flicking, run toggling, etc.
		if (isMouseOverGui(mouseEvent.getPoint(), QUICK_WIDGETS, false)) {
			return mouseEvent;
		}

		clientThread.invokeLater(() -> {
			if (!isTouchPressed) {
				return;
			}

			boolean isItemUnderMouse = -1 != findItemIdUnderMouse();
			isScrollingOnMinimap = isMouseOverMinimap(mouseEvent.getPoint());

			if (!isItemUnderMouse && (isScrollingOnMinimap || isMouseOverScrollableGui(mouseEvent.getPoint()))) {
				readyToRotate = false;
				readyToScroll = true;
				return;
			}

			isScrollingOnMinimap = false;
			if (isItemUnderMouse || isMouseOverBlockingGui(mouseEvent.getPoint())) {
				forceLeftClick = true;
				isTouchingGui = true;
				readyToRotate = false;
				mouseEvent.getComponent().dispatchEvent(
					rebuildMouseEvent(mouseEvent, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, true)
				);
			}
		});

		resetState();
		isTouchPressed = true;
		readyToRotate = true;
		touchStartPoint = mouseEvent.getPoint();
		scrollingHoldPoint = touchStartPoint;
		previousTouchPointForScrolling = touchStartPoint;

		mouseEvent.consume();
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent mouseEvent) {
		if (!isTouchPressed || shouldSkipProcessing(mouseEvent)) {
			return mouseEvent;
		}

		if (readyToScroll) {
			return emulateScrolling(mouseEvent);
		}

		if (readyToRotate) {
			return emulateCameraRotate(mouseEvent);
		}

		return mouseEvent;
	}

	private MouseEvent emulateCameraRotate(MouseEvent mouseEvent) {
		boolean isUnderThreshold = config.touchDragThreshold() > getDistance(touchStartPoint, mouseEvent.getPoint());
		if (!isRotating && isUnderThreshold) {
			return mouseEvent;
		}

		if (!isRotating) {
			// Fire middle mouse click event to start camera rotation
			Component component = mouseEvent.getComponent();
			component.dispatchEvent(
					rebuildMouseEvent(mouseEvent, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON2, true)
			);
		}
		isRotating = true;

		// Replace the left mouse drag with a middle mouse drag
		return rebuildMouseEvent(mouseEvent, mouseEvent.getID(), MouseEvent.BUTTON2, false);
	}

	private MouseEvent emulateScrolling(MouseEvent mouseEvent) {
		if (isScrollingOnMinimap) {
			handleMinimapScrolling(mouseEvent);
		} else {
			int threshold = config.touchScrollThreshold();
			int scrollDelta = readScrollDeltaY(mouseEvent, threshold);
			if (scrollDelta != 0) {
				scrollDelta = config.invertScrolling() ? -scrollDelta : scrollDelta;

				dispatchMouseWheelEventAt(mouseEvent, scrollingHoldPoint, scrollDelta);
				previousTouchPointForScrolling = mouseEvent.getPoint();
				isScrolling = true;
			}
		}

		// Prevent the mouse from moving from the client's perspective
		return new MouseEvent(
				mouseEvent.getComponent(),
				mouseEvent.getID(),
				mouseEvent.getWhen(),
				mouseEvent.getModifiersEx(),
				scrollingHoldPoint.x,
				scrollingHoldPoint.y,
				mouseEvent.getClickCount(),
				false,
				mouseEvent.getButton()
		);
	}

	private void handleMinimapScrolling(MouseEvent mouseEvent) {
		if (!isScrolling) {
			// Double the threshold for the minimap, since we process scrolls in both directions
			int startThreshold = config.touchScrollThreshold() * 2;

			int distance = getDistance(touchStartPoint, mouseEvent.getPoint());
			if (distance < startThreshold) {
				return;
			}

			int xDelta = mouseEvent.getX() - touchStartPoint.x;
			int yDelta = mouseEvent.getY() - touchStartPoint.y;
			isScrolling3dZoom = Math.abs(xDelta) <= Math.abs(yDelta);

			// 12,12 to avoid fixed mode's bezel
			scrollingHoldPoint = isScrolling3dZoom ? new Point(12,12) : touchStartPoint;

			previousTouchPointForScrolling = mouseEvent.getPoint();
			isScrolling = true;
			return;
		}

		int threshold = config.touchMinimapScrollThreshold();
		int scrollDelta = isScrolling3dZoom ? readScrollDeltaY(mouseEvent, threshold) : readScrollDeltaX(mouseEvent, threshold);
		if (scrollDelta != 0) {
			if (isScrolling3dZoom) {
				scrollDelta = config.invertZoom() ? -scrollDelta : scrollDelta;
			} else {
				scrollDelta = config.invertMinimapZoom() ? -scrollDelta : scrollDelta;
			}

			dispatchMouseWheelEventAt(mouseEvent, scrollingHoldPoint, scrollDelta);
			previousTouchPointForScrolling = mouseEvent.getPoint();
		}
	}

	private int readScrollDeltaX(MouseEvent mouseEvent, int threshold) {
		int deltaX = mouseEvent.getX() - previousTouchPointForScrolling.x;
		return deltaX / threshold;
	}

	private int readScrollDeltaY(MouseEvent mouseEvent, int threshold) {
		int deltaY = mouseEvent.getY() - previousTouchPointForScrolling.y;
		return deltaY / -threshold;
	}

	private void dispatchMouseWheelEventAt(MouseEvent mouseEvent, Point point, int scrollAmount) {
		// Do I need to correct for stretched mode here?
		mouseEvent.getComponent().dispatchEvent(new MouseWheelEvent(
				mouseEvent.getComponent(),
				MouseWheelEvent.MOUSE_WHEEL,
				mouseEvent.getWhen(),
				mouseEvent.getModifiersEx(),
				point.x,
				point.y,
				mouseEvent.getClickCount(),
				false,
				MouseWheelEvent.WHEEL_UNIT_SCROLL,
				1,
				scrollAmount
		));
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent mouseEvent) {
		if (isScrolling) {
			// Prevent the mouse from moving from the client's perspective
			mouseEvent = new MouseEvent(
					mouseEvent.getComponent(),
					mouseEvent.getID(),
					mouseEvent.getWhen(),
					mouseEvent.getModifiersEx(),
					scrollingHoldPoint.x,
					scrollingHoldPoint.y,
					mouseEvent.getClickCount(),
					false,
					mouseEvent.getButton()
			);
		}

		return mouseEvent;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event) {
		if (isScrolling) {
			System.out.println("Mouse wheel at: " + event.getPoint());

			// Prevent the mouse from moving from the client's perspective
			event = new MouseWheelEvent(
					event.getComponent(),
					event.getID(),
					event.getWhen(),
					event.getModifiersEx(),
					scrollingHoldPoint.x,
					scrollingHoldPoint.y,
					event.getClickCount(),
					event.isPopupTrigger(),
					event.getScrollType(),
					event.getScrollAmount(),
					event.getWheelRotation()
			);
		}
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent mouseEvent) {
		if (shouldSkipProcessing(mouseEvent) || !isTouchPressed) {
			return mouseEvent;
		}

		isTouchPressed = false;

		if (isTouchingGui) {
			return mouseEvent;
		}

		if (isRotating) {
			isRotating = false;
			mouseEvent.getComponent().dispatchEvent(
				rebuildMouseEvent(mouseEvent, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON2, true)
			);
			return mouseEvent;
		}

		if (isScrolling) {
			readyToScroll = false;
			isScrolling = false;
			mouseEvent.consume();
			return mouseEvent;
		}

		queueEmulatedLeftClick(mouseEvent);
		mouseEvent.consume();
		return mouseEvent;
	}

	// Waits for the next frame before emulating a left click.
	// This delay fixes the issue where touch input would be registered at incorrect coordinates.
	private void queueEmulatedLeftClick(MouseEvent mouseEvent) {
		mouseEvent.getComponent().dispatchEvent(
				rebuildMouseEvent(mouseEvent, MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON, true)
		);
		emulatedClickEventPrototype = mouseEvent;
		waitedOneFrame = false;
		doEmulatedClickNextFrame = true;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent mouseEvent) { return mouseEvent; }
	@Override
	public MouseEvent mouseExited(MouseEvent mouseEvent) { return mouseEvent; }

	@Override
	public MouseEvent mouseClicked(MouseEvent mouseEvent) { return mouseEvent; }

	private boolean shouldSkipProcessing(MouseEvent mouseEvent)
	{
		return !SwingUtilities.isLeftMouseButton(mouseEvent) || mouseEvent.isConsumed() || client.getGameState() != GameState.LOGGED_IN;
	}

	private boolean isMouseOverScrollableGui(Point point) {
		return isMouseOverGui(point, SCROLLABLE_WIDGETS, true);
	}

	private boolean isMouseOverBlockingGui(Point point) {
		return isMouseOverGui(point, BLOCKING_WIDGETS, true);
	}

	private boolean isMouseOverMinimap(Point point) {
		return isMouseOverGui(point, MINIMAP_WIDGETS, false);
	}

	private boolean isMouseOverGui(Point point, WidgetInfo[] widgets, boolean onThread)
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

	private boolean testWidget(Widget widget, net.runelite.api.Point point, boolean onThread) {
		if (widget == null) {
			return false;
		}

		if (onThread) {
			return widget.contains(point) && !widget.isHidden();
		} else {
			return !widget.isSelfHidden() && widget.contains(point);
		}
	}

	private int getDistance(Point a, Point b)
	{
		return (int) Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2));
	}

	private MouseEvent rebuildMouseEvent(MouseEvent mouseEvent, Integer type, Integer button, boolean forDispatch)
	{
		Point point = forDispatch ? client.getCanvas().getMousePosition() : mouseEvent.getPoint();
		return rebuildMouseEvent(mouseEvent, type, button, point);
	}

	private MouseEvent rebuildMouseEvent(MouseEvent mouseEvent, Integer type, Integer button, Point point)
	{
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

	private int findItemIdUnderMouse() {
		Widget widget = findWidgetUnderMouse();
		return widget == null ? -1 : widget.getItemId();
	}

	private Widget findWidgetUnderMouse() {
		MenuEntry[] rightClickMenu = client.getMenuEntries();
		if (rightClickMenu.length == 0) {
			return null;
		}

		MenuEntry menuEntry = rightClickMenu[rightClickMenu.length - 1];
		return menuEntry.getWidget();
	}
}
