# Shortest Path provenance

- Repository: https://github.com/Skretzo/shortest-path
- Plugin Hub entry: https://github.com/runelite/plugin-hub/blob/master/plugins/shortest-path
- Vendored revision: `cbfc16acc96ea54360c67ea9f0a1f0b42ff9592b`
- Authors listed by Plugin Hub: Skretzo, FIrgolitsch, wvanderp, mpickering.
- License: BSD-2-Clause; original LICENSE retained verbatim in this directory.

All 100 main Java source files, 79 upstream test source files and the complete
runtime resource set are integrated. Packages moved under
`net.runelite.client.plugins.shortestpath`; resource roots moved to `/shortestpath/`.
Main changes are explicit auto-walk menu events and an opt-in transport filter.
Original routing and rendering remain available when automation is inactive.

The 1,616,661-byte collision-map.zip is retrieved at build time from this fixed
revision and validated against its Git blob SHA-1
`948cf57f873c7f24b4cde167a9def1c293928257`, including the Git blob header.
No unpinned runtime download occurs. Marker, routing cuts, destinations and
transport tables are checked into this repository.

Upstream tests are retained; upstream Checkstyle/PMD differences are excluded
only for the vendored shortestpath package. New autowalk code remains checked.
