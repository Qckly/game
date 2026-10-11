package net.runelite.client.plugins.autowalk;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.shortestpath.pathfinder.ActiveSearch;
import net.runelite.client.plugins.shortestpath.pathfinder.PathStep;
import net.runelite.client.plugins.shortestpath.pathfinder.PathfinderResult;
import net.runelite.client.plugins.shortestpath.transport.Transport;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDependency(ShortestPathPlugin.class)
@PluginDescriptor(name = "RSPS Auto-walk", description = "Follow Shortest Path routes on your RSPS", tags = {"walking", "pathfinder", "rsps"}, enabledByDefault = false)
public class AutoWalkPlugin extends Plugin implements KeyListener
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ShortestPathPlugin shortestPath;
	@Inject private ClientMovementAdapter movement;
	@Inject private AutoWalkConfig config;
	@Inject private KeyManager keyManager;
	@Inject private MouseManager mouseManager;
	@Inject private OverlayManager overlayManager;
	@Inject private AutoWalkOverlay overlay;
	private WalkSession session;
	private ActiveSearch previousSearch;
	private String runId = "";
	private boolean issuingAction;
	private boolean dryRun;
	private WalkSession.State reportedState;
	private final MouseAdapter minimapListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			int x = event.getX();
			int y = event.getY();
			int button = event.getButton();
			clientThread.invokeLater(() ->
			{
				if (session != null && session.isActive())
				{
					Shape minimap = shortestPath.getMinimapClipArea();
					if (JourneyPolicy.isMinimapPress(button, minimap != null && minimap.contains(x, y)))
					{
						stop("Manual minimap movement");
					}
				}
			});
			return event;
		}
	};

	@Provides
	AutoWalkConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(AutoWalkConfig.class);
	}

	@Override
	protected void startUp()
	{
		session = new WalkSession(config.stuckTicks(), config.maxReplans());
		keyManager.registerKeyListener(this);
		mouseManager.registerMouseListener(minimapListener);
		overlayManager.add(overlay);
		clientThread.invokeLater(() -> shortestPath.setAutoWalkAvailable(true));
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(this);
		mouseManager.unregisterMouseListener(minimapListener);
		overlayManager.remove(overlay);
		clientThread.invokeLater(() ->
		{
			stop("Plugin disabled");
			shortestPath.setAutoWalkAvailable(false);
		});
	}

	public String status()
	{
		if (session == null)
		{
			return "Idle";
		}
		return session.getState() + ": " + session.getReason();
	}

	public void stop(String reason)
	{
		if (session != null)
		{
			session.stop(reason);
		}
		shortestPath.endAutoWalk();
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (!"autowalk".equals(event.getNamespace()))
		{
			return;
		}
		if ("stop".equals(event.getName()))
		{
			stop("Stopped by user");
			return;
		}
		if (!"start".equals(event.getName()) || event.getData() == null || !(event.getData().get("target") instanceof Integer))
		{
			return;
		}
		int packed = (Integer) event.getData().get("target");
		if (packed == WorldPointUtil.UNDEFINED || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}
		stop("New destination");
		session = new WalkSession(config.stuckTicks(), config.maxReplans());
		session.start(tile(WorldPointUtil.unpackWorldPoint(packed)), client.getTickCount());
		runId = UUID.randomUUID().toString();
		dryRun = config.dryRun();
		reportedState = null;
		requestRoute();
	}

	private void requestRoute()
	{
		previousSearch = shortestPath.getActiveSearch();
		WalkSession.Tile target = session.getTarget();
		shortestPath.beginAutoWalk(WorldPointUtil.packWorldPoint(target.x, target.y, target.plane), ClientMovementAdapter::supports);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (session == null || !session.isActive())
		{
			return;
		}
		Player player = client.getLocalPlayer();
		int tick = client.getTickCount();
		boolean loading = client.getGameState() == GameState.LOADING;
		if (!loading && (client.getGameState() != GameState.LOGGED_IN || player == null))
		{
			stop("Disconnected");
			return;
		}
		if (client.getTopLevelWorldView() != null && client.getTopLevelWorldView().isInstance())
		{
			stop("Instanced areas are not supported yet");
			return;
		}
		ActiveSearch search = shortestPath.getActiveSearch();
		if (session.getState() == WalkSession.State.PLANNING && search != null && search != previousSearch && search.isDone())
		{
			PathfinderResult result = search.getResult();
			if (result == null || !result.isReached())
			{
				session.fail("No executable route to destination");
			}
			else
			{
				List<WalkSession.Step> steps = new ArrayList<>();
				for (PathStep step : result.getPathSteps())
				{
					steps.add(new WalkSession.Step(tile(WorldPointUtil.unpackWorldPoint(step.getPackedPosition())), step.getTransport()));
				}
				session.acceptRoute(session.getGeneration(), steps, tick);
			}
		}
		boolean mapOpen = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) != null;
		if (session.getState() != WalkSession.State.PLANNING && session.isActive()
			&& (search == null || !search.getTargets().contains(WorldPointUtil.packWorldPoint(session.getTarget().x, session.getTarget().y, session.getTarget().plane))))
		{
			stop("Route cleared or changed");
			return;
		}
		if (!session.isActive())
		{
			shortestPath.endAutoWalk();
			return;
		}
		if (dryRun)
		{
			if (session.getState() == WalkSession.State.WALKING && movement.refresh())
			{
				WalkSession.Decision preview = session.tick(tile(player.getWorldLocation()), false, false, tick, movement::canReach);
				log.info("Auto-walk dry-run run={} tick={} next={} tile={}", runId, tick, preview.kind, preview.step == null ? null : preview.step.tile);
				stop("Dry run finished; no actions sent");
			}
			else
			{
				session.tick(player == null ? null : tile(player.getWorldLocation()), loading, false, tick, p -> false);
				if (!session.isActive())
				{
					shortestPath.endAutoWalk();
				}
			}
			return;
		}
		boolean ready = !loading && !mapOpen && player != null && movement.refresh();
		LocalPoint destination = client.getLocalDestinationLocation();
		boolean moving = player != null && destination != null && !destination.equals(player.getLocalLocation());
		WalkSession.Decision action = session.tick(player == null ? null : tile(player.getWorldLocation()), !ready, moving, tick, movement::canReach);
		if (action.kind == WalkSession.Kind.REPLAN)
		{
			requestRoute();
		}
		else if (action.kind == WalkSession.Kind.WALK || action.kind == WalkSession.Kind.TRANSPORT)
		{
			issuingAction = true;
			try
			{
				if (action.kind == WalkSession.Kind.WALK)
				{
					movement.walk(action.step.tile);
				}
				else if (!(action.step.transport instanceof Transport) || !movement.execute((Transport) action.step.transport))
				{
					session.fail("Transport object or action unavailable");
				}
			}
			catch (RuntimeException error)
			{
				log.warn("Auto-walk action failed", error);
				session.fail("Client action failed");
			}
			finally
			{
				issuingAction = false;
			}
		}
		if (config.diagnostics() && (action.kind != WalkSession.Kind.NONE || session.getState() != reportedState))
		{
			log.info("Auto-walk run={} route={} tick={} position={} state={} action={} waypoint={} reason={}", runId,
				session.getGeneration(), tick, player == null ? null : player.getWorldLocation(), session.getState(), action.kind,
				action.step == null ? null : action.step.tile, session.getReason());
		}
		reportedState = session.getState();
		if (!session.isActive())
		{
			shortestPath.endAutoWalk();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN && event.getGameState() != GameState.LOADING && session != null && session.isActive())
		{
			stop("Disconnected");
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			stop("Player died");
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!issuingAction && session != null && session.isActive() && JourneyPolicy.isWorldInteraction(event.getMenuAction().getId()))
		{
			stop("Manual movement");
		}
	}

	private static WalkSession.Tile tile(WorldPoint p)
	{
		return new WalkSession.Tile(p.getX(), p.getY(), p.getPlane());
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (event.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			clientThread.invokeLater(() -> stop("Esc pressed"));
		}
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
	}
}
