package net.runelite.client.plugins.autowalk;

import java.util.ArrayDeque;

/** Flood fill using OSRS movement flags, independent of rendering and world coordinates. */
public final class LocalReachability
{
	private static final int FULL = 0x240100;
	private static final int[] DX = { 0, 1, 0, -1 };
	private static final int[] DY = { 1, 0, -1, 0 };
	private static final int[] WALL = { 0x2, 0x8, 0x20, 0x80 };

	private LocalReachability()
	{
	}

	public static boolean[][] find(int[][] flags, int sx, int sy, int radius)
	{
		int width = flags.length;
		int height = width == 0 ? 0 : flags[0].length;
		boolean[][] seen = new boolean[width][height];
		if (sx < 0 || sy < 0 || sx >= width || sy >= height)
		{
			return seen;
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[]{sx, sy});
		seen[sx][sy] = true;
		while (!queue.isEmpty())
		{
			int[] p = queue.removeFirst();
			for (int d = 0; d < 4; d++)
			{
				int x = p[0] + DX[d];
				int y = p[1] + DY[d];
				if (x < 0 || y < 0 || x >= width || y >= height || seen[x][y]
					|| Math.max(Math.abs(x - sx), Math.abs(y - sy)) > radius
					|| (flags[x][y] & (FULL | WALL[(d + 2) % 4])) != 0
					|| (flags[p[0]][p[1]] & WALL[d]) != 0)
				{
					continue;
				}
				seen[x][y] = true;
				queue.add(new int[]{x, y});
			}
		}
		return seen;
	}
}
