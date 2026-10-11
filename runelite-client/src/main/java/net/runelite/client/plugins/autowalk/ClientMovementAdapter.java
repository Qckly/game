package net.runelite.client.plugins.autowalk;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.shortestpath.transport.Transport;
import net.runelite.client.plugins.shortestpath.transport.TransportType;

/** All methods are called on the client thread using the current world view. */
public class ClientMovementAdapter
{
	private final Client client;
	private WorldView view;
	private boolean[][] reachable;

	@Inject
	ClientMovementAdapter(Client client)
	{
		this.client = client;
	}

	public boolean refresh()
	{
		view = client.getTopLevelWorldView();
		reachable = null;
		if (view == null || view.isInstance() || client.getLocalPlayer() == null)
		{
			return false;
		}
		CollisionData[] maps = view.getCollisionMaps();
		int plane = view.getPlane();
		if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
		{
			return false;
		}
		WorldPoint p = client.getLocalPlayer().getWorldLocation();
		reachable = LocalReachability.find(maps[plane].getFlags(), p.getX() - view.getBaseX(), p.getY() - view.getBaseY(), 18);
		return true;
	}

	public boolean canReach(WalkSession.Tile p)
	{
		if (reachable == null || p.plane != view.getPlane())
		{
			return false;
		}
		int x = p.x - view.getBaseX();
		int y = p.y - view.getBaseY();
		return x >= 0 && y >= 0 && x < reachable.length && y < reachable[x].length && reachable[x][y];
	}

	public void walk(WalkSession.Tile p)
	{
		if (!canReach(p))
		{
			throw new IllegalStateException("Waypoint is no longer reachable");
		}
		client.menuAction(p.x - view.getBaseX(), p.y - view.getBaseY(), MenuAction.WALK, 0, -1, "Walk here", "");
	}

	public static boolean supports(Transport transport)
	{
		ObjectAction action = ObjectAction.parse(transport.getObjectInfo());
		if (transport.getType() != TransportType.TRANSPORT || transport.getOrigin() == Transport.UNDEFINED_ORIGIN || action == null)
		{
			return false;
		}
		if (action.option.equals("Open"))
		{
			WorldPoint origin = WorldPointUtil.unpackWorldPoint(transport.getOrigin());
			WorldPoint destination = WorldPointUtil.unpackWorldPoint(transport.getDestination());
			return origin.getPlane() == destination.getPlane() && origin.distanceTo(destination) <= 2;
		}
		return true;
	}

	/** Doors are first opened, then walked through; stairs acknowledge by destination change. */
	public boolean execute(Transport transport)
	{
		if (!supports(transport))
		{
			return false;
		}
		ObjectAction requested = ObjectAction.parse(transport.getObjectInfo());
		WorldPoint destination = WorldPointUtil.unpackWorldPoint(transport.getDestination());
		WalkSession.Tile dest = new WalkSession.Tile(destination.getX(), destination.getY(), destination.getPlane());
		if (requested.option.equals("Open") && canReach(dest))
		{
			walk(dest);
			return true;
		}
		WorldPoint origin = WorldPointUtil.unpackWorldPoint(transport.getOrigin());
		Tile[][][] tiles = view.getScene().getTiles();
		for (int x = Math.max(0, origin.getX() - view.getBaseX() - 2); x <= Math.min(view.getSizeX() - 1, origin.getX() - view.getBaseX() + 2); x++)
		{
			for (int y = Math.max(0, origin.getY() - view.getBaseY() - 2); y <= Math.min(view.getSizeY() - 1, origin.getY() - view.getBaseY() + 2); y++)
			{
				Tile tile = tiles[view.getPlane()][x][y];
				if (tile == null)
				{
					continue;
				}
				List<TileObject> objects = new ArrayList<>();
				objects.add(tile.getWallObject());
				objects.add(tile.getGroundObject());
				objects.add(tile.getDecorativeObject());
				for (GameObject object : tile.getGameObjects())
				{
					objects.add(object);
				}
				for (TileObject object : objects)
				{
					if (object == null || object.getId() != requested.objectId)
					{
						continue;
					}
					ObjectComposition composition = client.getObjectDefinition(object.getId());
					if (composition.getImpostorIds() != null)
					{
						composition = composition.getImpostor();
					}
					if (composition == null || !requested.name.equalsIgnoreCase(composition.getName()))
					{
						continue;
					}
					String[] actions = composition.getActions();
					for (int index = 0; actions != null && index < Math.min(5, actions.length); index++)
					{
						if (requested.option.equalsIgnoreCase(actions[index]))
						{
							// Object opcodes are not contiguous after the fourth option.
							MenuAction[] options = {MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION,
								MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION};
							client.menuAction(x, y, options[index], object.getId(), -1, requested.option, composition.getName());
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}
