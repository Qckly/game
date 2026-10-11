package net.runelite.client.plugins.autowalk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Tick-driven execution; never calls the client or runs a search itself. */
public final class WalkSession
{
	public enum State
	{
		IDLE, PLANNING, WALKING, WAITING_FOR_REGION, EXECUTING_TRANSPORT, ARRIVED, STOPPED, FAILED
	}
	public enum Kind
	{
		NONE, WALK, TRANSPORT, REPLAN
	}

	public static final class Tile
	{
		public final int x;
		public final int y;
		public final int plane;

		public Tile(int x, int y, int plane)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
		}

		public int distance(Tile other)
		{
			return plane == other.plane ? Math.max(Math.abs(x - other.x), Math.abs(y - other.y)) : Integer.MAX_VALUE;
		}

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof Tile))
			{
				return false;
			}
			Tile p = (Tile) other;
			return x == p.x && y == p.y && plane == p.plane;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(x, y, plane);
		}

		@Override
		public String toString()
		{
			return x + "," + y + "," + plane;
		}
	}

	public static final class Step
	{
		public final Tile tile;
		public final Object transport;

		public Step(Tile tile, Object transport)
		{
			this.tile = Objects.requireNonNull(tile);
			this.transport = transport;
		}
	}

	public static final class Decision
	{
		public final Kind kind;
		public final Step step;

		private Decision(Kind kind, Step step)
		{
			this.kind = kind;
			this.step = step;
		}
	}

	private static final Decision NONE = new Decision(Kind.NONE, null);
	private final int stuckTicks;
	private final int maxReplans;
	private State state = State.IDLE;
	private List<Step> route = Collections.emptyList();
	private Tile target;
	private Tile lastPosition;
	private Tile waypoint;
	private long generation;
	private int cursor;
	private int progressTick;
	private int planTick;
	private int actionTick = -100;
	private int replans;
	private int bestWaypointDistance = Integer.MAX_VALUE;
	private String reason = "";

	public WalkSession(int stuckTicks, int maxReplans)
	{
		this.stuckTicks = Math.max(2, stuckTicks);
		this.maxReplans = Math.max(0, maxReplans);
	}

	public long start(Tile destination, int tick)
	{
		target = Objects.requireNonNull(destination);
		route = Collections.emptyList();
		cursor = 0;
		replans = 0;
		lastPosition = null;
		waypoint = null;
		progressTick = planTick = tick;
		actionTick = -100;
		reason = "Planning route";
		state = State.PLANNING;
		return ++generation;
	}

	public boolean acceptRoute(long routeGeneration, List<Step> steps, int tick)
	{
		if (routeGeneration != generation || state != State.PLANNING)
		{
			return false;
		}
		if (steps == null || steps.isEmpty() || !steps.get(steps.size() - 1).tile.equals(target))
		{
			fail("Destination is unreachable");
			return false;
		}
		route = Collections.unmodifiableList(new ArrayList<>(steps));
		cursor = 0;
		waypoint = null;
		lastPosition = null;
		progressTick = tick;
		actionTick = -100;
		state = State.WALKING;
		reason = "Following route";
		return true;
	}

	public void stop(String why)
	{
		generation++;
		state = State.STOPPED;
		reason = why;
		route = Collections.emptyList();
		waypoint = null;
	}

	public void fail(String why)
	{
		generation++;
		state = State.FAILED;
		reason = why;
		route = Collections.emptyList();
		waypoint = null;
	}

	public boolean isActive()
	{
		return state == State.PLANNING || state == State.WALKING || state == State.WAITING_FOR_REGION || state == State.EXECUTING_TRANSPORT;
	}

	public Decision tick(Tile position, boolean loading, boolean moving, int tick, Predicate<Tile> reachable)
	{
		if (!isActive())
		{
			return NONE;
		}
		if (loading)
		{
			progressTick = tick;
			planTick = tick;
			if (state != State.PLANNING)
			{
				state = State.WAITING_FOR_REGION;
			}
			return NONE;
		}
		if (state == State.PLANNING)
		{
			if (tick - planTick >= 30)
			{
				fail("Route search timed out");
			}
			return NONE;
		}
		if (position == null)
		{
			return NONE;
		}
		if (position.equals(target))
		{
			state = State.ARRIVED;
			reason = "Destination reached";
			return NONE;
		}
		if (lastPosition == null || (waypoint != null && position.distance(waypoint) < bestWaypointDistance))
		{
			progressTick = tick;
			bestWaypointDistance = waypoint == null ? Integer.MAX_VALUE : position.distance(waypoint);
		}
		lastPosition = position;
		if (state == State.WAITING_FOR_REGION)
		{
			state = State.WALKING;
			waypoint = null;
			progressTick = tick;
		}
		// Match only before the next unacknowledged transport. Its destination acknowledges it.
		int boundary = route.size() - 1;
		for (int i = cursor + 1; i < route.size(); i++)
		{
			if (route.get(i).transport != null)
			{
				boundary = i;
				break;
			}
		}
		boolean onRoute = false;
		int previousCursor = cursor;
		for (int i = cursor; i <= boundary; i++)
		{
			if (route.get(i).tile.equals(position))
			{
				cursor = i;
				onRoute = true;
			}
		}
		if (cursor > previousCursor)
		{
			progressTick = tick;
		}
		if (tick - progressTick >= stuckTicks)
		{
			return replan(tick, "No movement progress");
		}
		if (!onRoute && (cursor >= route.size() || position.distance(route.get(cursor).tile) > 12))
		{
			return replan(tick, "Left the route");
		}
		if (cursor + 1 < route.size() && route.get(cursor + 1).transport != null && position.equals(route.get(cursor).tile))
		{
			boolean waiting = state == State.EXECUTING_TRANSPORT;
			state = State.EXECUTING_TRANSPORT;
			if (waiting && tick - actionTick < 3)
			{
				return NONE;
			}
			actionTick = tick;
			waypoint = null;
			return new Decision(Kind.TRANSPORT, route.get(cursor + 1));
		}
		state = State.WALKING;
		if (waypoint != null && !position.equals(waypoint) && moving)
		{
			return NONE;
		}
		if (tick - actionTick < 2)
		{
			return NONE;
		}
		Step next = null;
		for (int i = cursor + 1; i < route.size(); i++)
		{
			Step step = route.get(i);
			if (step.transport != null || position.distance(step.tile) > 12)
			{
				break;
			}
			if (reachable.test(step.tile))
			{
				next = step;
			}
			else
			{
				break;
			}
		}
		if (next == null)
		{
			return replan(tick, "No reachable local waypoint");
		}
		waypoint = next.tile;
		bestWaypointDistance = position.distance(waypoint);
		actionTick = tick;
		return new Decision(Kind.WALK, next);
	}

	private Decision replan(int tick, String why)
	{
		if (replans++ >= maxReplans)
		{
			fail(why + "; retry limit reached");
			return NONE;
		}
		generation++;
		state = State.PLANNING;
		reason = why;
		route = Collections.emptyList();
		waypoint = null;
		planTick = progressTick = tick;
		return new Decision(Kind.REPLAN, null);
	}

	public State getState()
	{
		return state;
	}
	public String getReason()
	{
		return reason;
	}
	public long getGeneration()
	{
		return generation;
	}
	public Tile getTarget()
	{
		return target;
	}
}
