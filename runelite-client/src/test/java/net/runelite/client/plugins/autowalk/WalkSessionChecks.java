package net.runelite.client.plugins.autowalk;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free behavioral checks, also called by the JUnit suite. */
public final class WalkSessionChecks
{
	private static int checks;

	public static void main(String[] args)
	{
		runAll();
		System.out.println("Auto-walk behavior checks passed: " + checks);
	}

	public static void runAll()
	{
		checks = 0;
		longWalk();
		staleRoute();
		loadingAndPlane();
		boundedRecovery();
		transportBoundary();
		unreachableRoute();
		collisionChecks();
		objectActionChecks();
		journeyPolicyChecks();
		positionJitterIsNotProgress();
	}

	private static WalkSession.Tile tile(int x)
	{
		return new WalkSession.Tile(x, 10, 0);
	}

	private static List<WalkSession.Step> road(int end)
	{
		List<WalkSession.Step> steps = new ArrayList<>();
		for (int x = 0; x <= end; x++)
		{
			steps.add(new WalkSession.Step(tile(x), null));
		}
		return steps;
	}

	private static void check(boolean ok, String reason)
	{
		checks++;
		if (!ok)
		{
			throw new AssertionError(reason);
		}
	}

	private static void longWalk()
	{
		WalkSession s = new WalkSession(5, 3);
		long generation = s.start(tile(150), 0);
		check(s.acceptRoute(generation, road(150), 0), "accept complete long route");
		WalkSession.Decision d = s.tick(tile(0), false, false, 0, p -> true);
		check(d.kind == WalkSession.Kind.WALK && d.step.tile.x == 12, "click bounded waypoint");
		check(s.tick(tile(1), false, true, 1, p -> true).kind == WalkSession.Kind.NONE, "no repeated click while moving");
		check(s.tick(tile(12), false, false, 2, p -> true).step.tile.x == 24, "continue after first waypoint");
		check(s.tick(tile(150), false, false, 3, p -> true).kind == WalkSession.Kind.NONE, "arrival sends no action");
		check(s.getState() == WalkSession.State.ARRIVED, "exact destination arrival");
	}

	private static void staleRoute()
	{
		WalkSession s = new WalkSession(5, 3);
		long old = s.start(tile(10), 0);
		s.stop("manual");
		check(!s.acceptRoute(old, road(10), 1), "stop invalidates pending route");
		check(s.tick(tile(0), false, false, 1, p -> true).kind == WalkSession.Kind.NONE, "stop prevents movement");
		long fresh = s.start(tile(20), 2);
		check(!s.acceptRoute(old, road(10), 2), "new destination rejects old route");
		check(s.acceptRoute(fresh, road(20), 2), "new route accepted");
	}

	private static void loadingAndPlane()
	{
		WalkSession s = new WalkSession(5, 3);
		long id = s.start(tile(30), 0);
		s.acceptRoute(id, road(30), 0);
		check(s.tick(tile(0), true, false, 20, p -> true).kind == WalkSession.Kind.NONE, "loading waits without action");
		check(s.tick(tile(0), false, false, 21, p -> true).kind == WalkSession.Kind.WALK, "resume with new scene after loading");
		check(s.tick(new WalkSession.Tile(30, 10, 1), false, false, 22, p -> true).kind == WalkSession.Kind.REPLAN, "wrong plane is not arrival");
	}

	private static void boundedRecovery()
	{
		WalkSession s = new WalkSession(2, 2);
		long id = s.start(tile(30), 0);
		s.acceptRoute(id, road(30), 0);
		s.tick(tile(0), false, false, 0, p -> true);
		check(s.tick(tile(0), false, false, 2, p -> true).kind == WalkSession.Kind.REPLAN, "stuck requests replan");
		check(!s.acceptRoute(id, road(30), 2), "replanning invalidates old result");
		s.acceptRoute(s.getGeneration(), road(30), 2);
		s.tick(tile(0), false, false, 2, p -> true);
		check(s.tick(tile(0), false, false, 4, p -> true).kind == WalkSession.Kind.REPLAN, "second retry allowed");
		s.acceptRoute(s.getGeneration(), road(30), 4);
		s.tick(tile(0), false, false, 4, p -> true);
		check(s.tick(tile(0), false, false, 6, p -> true).kind == WalkSession.Kind.NONE, "retry budget stops further actions");
		check(s.getState() == WalkSession.State.FAILED, "retry limit reported");
		WalkSession pending = new WalkSession(2, 2);
		pending.start(tile(30), 0);
		check(pending.tick(tile(0), false, false, 40, p -> true).kind == WalkSession.Kind.NONE && pending.getState() == WalkSession.State.FAILED, "search timeout cannot hang forever");
	}

	private static void transportBoundary()
	{
		WalkSession s = new WalkSession(8, 3);
		List<WalkSession.Step> steps = road(3);
		steps.add(new WalkSession.Step(tile(4), "door"));
		steps.add(new WalkSession.Step(tile(5), null));
		s.acceptRoute(s.start(tile(5), 0), steps, 0);
		check(s.tick(tile(0), false, false, 0, p -> true).step.tile.x == 3, "never skip transport boundary");
		WalkSession.Decision d = s.tick(tile(3), false, false, 1, p -> true);
		check(d.kind == WalkSession.Kind.TRANSPORT && "door".equals(d.step.transport), "execute exact selected transport");
		check(s.tick(tile(3), false, false, 2, p -> true).kind == WalkSession.Kind.NONE, "wait for transport acknowledgement");
		check(s.tick(tile(4), false, false, 3, p -> true).kind == WalkSession.Kind.WALK, "continue after observed transport destination");
	}

	private static void unreachableRoute()
	{
		WalkSession s = new WalkSession(5, 3);
		s.acceptRoute(s.start(tile(30), 0), road(30), 0);
		check(s.tick(tile(0), false, false, 0, p -> false).kind == WalkSession.Kind.REPLAN, "no reachable waypoint causes replan");
		WalkSession partial = new WalkSession(5, 3);
		check(!partial.acceptRoute(partial.start(tile(30), 0), road(10), 0), "partial route cannot be treated as complete");
		check(partial.getState() == WalkSession.State.FAILED, "unreachable destination reported");
	}

	private static void collisionChecks()
	{
		int[][] grid = new int[5][5];
		for (int y = 0; y < 5; y++)
		{
			grid[2][y] = 0x240100;
		}
		boolean[][] reachable = LocalReachability.find(grid, 0, 2, 12);
		check(reachable[1][2] && !reachable[3][2], "wall blocks disconnected waypoint");
		grid[2][2] = 0;
		check(LocalReachability.find(grid, 0, 2, 12)[3][2], "opening door allows route");
		int[][] corner = new int[2][2];
		corner[1][0] = corner[0][1] = 0x240100;
		check(!LocalReachability.find(corner, 0, 0, 12)[1][1], "diagonal cannot cut blocked corner");
		int[][] edge = new int[2][1];
		edge[0][0] = 0x8;
		check(!LocalReachability.find(edge, 0, 0, 12)[1][0], "directional edge blocks movement");
	}

	private static void objectActionChecks()
	{
		ObjectAction door = ObjectAction.parse("Open Large door 1234");
		check(door != null && door.objectId == 1234 && door.option.equals("Open") && door.name.equals("Large door"), "parse exact object action");
		check(ObjectAction.parse("Climb-up Staircase 16671") != null, "support explicit climb up");
		check(ObjectAction.parse("Talk-to Sailor 123") == null, "dialog transports excluded");
		check(ObjectAction.parse("Climb Staircase 123") == null, "ambiguous climb excluded");
		check(ObjectAction.parse("Open Door nonsense") == null, "malformed transport excluded");
	}

	private static void journeyPolicyChecks()
	{
		check(!JourneyPolicy.shouldClearPath(true, 4, 5), "automation retains path near destination");
		check(!JourneyPolicy.shouldClearPath(true, 0, 5), "session owns exact arrival");
		check(JourneyPolicy.shouldClearPath(false, 4, 5), "manual plugin keeps existing finish distance");
		check(JourneyPolicy.isWorldInteraction(23), "walk opcode cancels independent of option label");
		check(JourneyPolicy.isWorldInteraction(3), "manual object action cancels movement");
		check(JourneyPolicy.isWorldInteraction(9), "manual NPC interaction cancels movement");
		check(!JourneyPolicy.isWorldInteraction(57), "closing world map does not cancel journey");
		check(JourneyPolicy.isMinimapPress(1, true) && !JourneyPolicy.isMinimapPress(3, true), "only left minimap click cancels");
	}

	private static void positionJitterIsNotProgress()
	{
		WalkSession s = new WalkSession(3, 1);
		s.acceptRoute(s.start(tile(30), 0), road(30), 0);
		s.tick(tile(0), false, false, 0, p -> true);
		s.tick(new WalkSession.Tile(0, 11, 0), false, true, 1, p -> true);
		s.tick(tile(0), false, true, 2, p -> true);
		check(s.tick(new WalkSession.Tile(0, 11, 0), false, true, 3, p -> true).kind == WalkSession.Kind.REPLAN,
			"position oscillation without route progress triggers recovery");
	}
}
