# RSPS Auto-walk

This integrates the full Shortest Path plugin into the client and adds a separate
RSPS Auto-walk plugin. It follows routes across ordinary map-region boundaries;
it is not limited to the destination being in the currently loaded scene.

## Build

Use the fork's normal JDK 11 + Gradle wrapper setup. The first resource build
downloads the pinned upstream collision-map.zip and verifies its Git object ID.

```powershell
.\gradlew.bat :client:compileJava :client:test --tests "net.runelite.client.plugins.autowalk.*"
.\gradlew.bat buildAll
```

Run the client through your existing RSPS launch configuration. The plugin does
not alter server endpoints or replace the injected game client. The repository's
checked-in properties currently still use the default OSRS jav_config address;
RSPS redirection supplied through launch arguments or another gamepack must be
preserved when applying this change.

## Use

1. Enable **Shortest Path** and **RSPS Auto-walk** in the plugin list.
2. Right-click a world-map point and choose **Auto-walk here**. On scene tiles,
   use Shift + right-click to get this option.
3. Close the world map to allow movement. Keep it open to pause execution.
4. Watch the state overlay and normal Shortest Path map/minimap path.
5. Press **Esc** or choose **Stop auto-walk** to stop sending further actions.
   A movement already accepted by the server can finish. Manual Walk here also
   cancels the journey. Logout, death and plugin disable cancel it permanently.

Automation is disabled by default. Enabling the plugin does not start movement;
each journey needs an explicit menu command. Recovery timeout and retry count
are configurable. Changing settings applies to the next journey.

**Dry run** plans an executable route and shows/logs the next proposed action,
then stops without sending any game actions. It does not simulate a complete
journey or fake a server acknowledgement. **Diagnostic logging** records run ID,
route generation, tick, observed position, state, action, waypoint and reason.
Server-side instrumentation is a separate integration requiring server code.

## Supported routes and transitions

- Ordinary walking across multiple loaded regions with live local collision
  checks, bounded waypoint distances, and limited stuck recovery.
- Adjacent same-plane doors/gates with an explicit `Open` object action.
- Ordinary transport records with an explicit `Climb-up` or `Climb-down` object
  action, provided the named object/action exists and leads directly to the
  recorded destination. Runtime success is confirmed from actual position.
- Manual Shortest Path retains all upstream transports and rendering options.

Auto-walk currently excludes dialog transports, fairy rings, teleports, bank
pickup routes, boats/sailing, agility shortcuts and ambiguous `Climb` actions.
Instanced areas are not supported. It plans a walking alternative when one
exists; destinations requiring excluded transitions are reported unreachable.
A missing supported object/action stops the journey with a reason.

## RSPS acceptance checks

Run with a test account on the configured RSPS:

- Choose a far destination connected by roads; cross at least three regions.
- Walk through an ordinary closed door and use explicit up/down stairs.
- Block a planned path; confirm recovery is bounded and a reason is shown.
- Stop during searching and moving; no subsequent automated actions should run.
- Logout/reconnect and confirm the old journey does not resume.
- Choose an island requiring a boat; expect an unsupported-route result rather
  than an apparent success or endless interaction loop.

Java behavior checks have a dependency-free main entry point in
WalkSessionChecks; JUnit runs those same checks along with adapter tests.
Actual RSPS packet handling, cache compatibility and in-game movement need
the acceptance run above; a compile or unit test cannot establish those.
