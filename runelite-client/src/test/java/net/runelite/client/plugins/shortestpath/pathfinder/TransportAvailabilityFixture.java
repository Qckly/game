package net.runelite.client.plugins.shortestpath.pathfinder;

import net.runelite.client.plugins.shortestpath.transport.Transport;

/** Test-only bridge for the package-private availability builder. */
public final class TransportAvailabilityFixture
{
	private TransportAvailabilityFixture()
	{
	}

	public static TransportAvailability of(Transport... transports)
	{
		TransportAvailability.Builder builder = new TransportAvailability.Builder(transports.length);
		for (Transport transport : transports) builder.add(transport);
		return builder.build();
	}
}
