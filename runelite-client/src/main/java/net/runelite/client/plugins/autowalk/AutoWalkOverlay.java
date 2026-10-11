package net.runelite.client.plugins.autowalk;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class AutoWalkOverlay extends OverlayPanel
{
	private final AutoWalkPlugin plugin;

	@Inject
	AutoWalkOverlay(AutoWalkPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().add(LineComponent.builder().left("Auto-walk").right("Esc to stop").rightColor(Color.YELLOW).build());
		panelComponent.getChildren().add(LineComponent.builder().left(plugin.status()).build());
		return super.render(graphics);
	}
}
