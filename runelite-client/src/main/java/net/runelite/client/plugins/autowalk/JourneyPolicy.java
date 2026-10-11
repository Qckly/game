package net.runelite.client.plugins.autowalk;

/** Input and completion rules shared by the plugin boundary and its checks. */
public final class JourneyPolicy
{
	private JourneyPolicy()
	{
	}

	public static boolean shouldClearPath(boolean automated, int distance, int finishDistance)
	{
		return !automated && distance < finishDistance;
	}

	public static boolean isWorldInteraction(int opcode)
	{
		// OSRS MenuAction ids: object, NPC, player, ground-item interactions and WALK.
		int action = opcode >= 2000 ? opcode - 2000 : opcode;
		return (action >= 1 && action <= 23) || (action >= 44 && action <= 51) || action == 1001;
	}

	public static boolean isMinimapPress(int button, boolean insideMinimap)
	{
		return button == 1 && insideMinimap;
	}
}
