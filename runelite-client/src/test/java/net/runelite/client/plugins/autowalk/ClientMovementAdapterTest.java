package net.runelite.client.plugins.autowalk;

import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClientMovementAdapterTest
{
	@Test
	public void movementUsesCurrentSceneCoordinatesAfterRegionChange()
	{
		Client client = mock(Client.class);
		WorldView view = mock(WorldView.class);
		Player player = mock(Player.class);
		CollisionData data = mock(CollisionData.class);
		when(client.getTopLevelWorldView()).thenReturn(view);
		when(client.getLocalPlayer()).thenReturn(player);
		when(view.getCollisionMaps()).thenReturn(new CollisionData[]{data});
		when(data.getFlags()).thenReturn(new int[104][104]);
		when(view.getBaseX()).thenReturn(3200);
		when(view.getBaseY()).thenReturn(3200);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3210, 3210, 0));
		ClientMovementAdapter adapter = new ClientMovementAdapter(client);
		assertTrue(adapter.refresh());
		adapter.walk(new WalkSession.Tile(3212, 3210, 0));
		verify(client).menuAction(12, 10, MenuAction.WALK, 0, -1, "Walk here", "");
		when(view.getBaseX()).thenReturn(3208);
		assertTrue(adapter.refresh());
		adapter.walk(new WalkSession.Tile(3212, 3210, 0));
		verify(client).menuAction(4, 10, MenuAction.WALK, 0, -1, "Walk here", "");
	}

	@Test
	public void missingCollisionDataDoesNotPermitMovement()
	{
		Client client = mock(Client.class);
		WorldView view = mock(WorldView.class);
		when(client.getTopLevelWorldView()).thenReturn(view);
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		ClientMovementAdapter adapter = new ClientMovementAdapter(client);
		assertFalse(adapter.refresh());
		assertFalse(adapter.canReach(new WalkSession.Tile(3212, 3210, 0)));
	}
}
