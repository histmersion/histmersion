# 0025. The map's point markers are drawn on a canvas; their elements stay as the accessible proxies

- Status: accepted (owner, 2026-11-22: "canvas 2D as planned", queue row 31)
- Date: 2026-11-22

## Context

Every point on the map (a finding, a site, an event, a picture, a bubble of several) was a DOM element in a marker
pane over the map, moved by writing its `--tx`/`--ty` every frame the map moves. A zoom over a full view moves and
regroups a few hundred of them: 35–42 ms of style recalculation plus ~30 ms of script per frame at 3× CPU, the
longest zoom task 79–95 ms at 3× and ~120 ms on the hosted macOS runner, against a 50 ms budget. Registering the
place properties (`@property`, not inherited) already confined each restyle to its marker; the cost is the number of
elements restyled and laid out per frame, not any one of them.

A WebGL symbol layer of MapLibre would need every look as a sprite image and gives up the slide animations, the
reduced-motion rule and the per-marker hit testing the app already has. Canvas 2D draws a few hundred shapes and
glyph bitmaps in 1–3 ms and keeps all of it in the app's hands.

## Decision

- A marker keeps its `DomMarker` (its point, its handlers, its stacking, its element); the cluster layer, the point
  layers, the slide animations and the hover cards above it do not change.
- The pane no longer paints the element. It draws every marker of the point panes on one canvas over the map's own,
  in the panes' stacking order, from the marker's look: its shape (circle, square, turned square, print, bubble),
  fill (a colour or the coincident findings' gradient), edge (width, style, colour), rings, opacity, count, and
  glyph. The canvas is redrawn on the map's move, resize and render, and on every frame of a running slide or fade.
- The stylesheet stays the one description of a look: the look is read from the computed style of the marker's
  element (boxes with their fill, gradient, edge, corners, rings and shadows; a turned `::before`; the inline SVG of a
  glyph or a bubble; a count's text), so the themes and any later change of the stylesheet reach the canvas without
  a second copy. Animations and transitions are off for the elements the canvas reads; the canvas animates itself.
  A look is drawn once into a bitmap (a sprite) and the canvas draws the sprites; looks are shared by markers that
  look the same, and read again only when a marker's classes or inline style change.
- The pane finds the marker under the pointer itself (topmost first, its drawn box, a turned square's diamond) and
  calls the same `MarkerHandlers` the delegated listeners called: over, out, move, click; the map's own pointer
  handling still learns that the pointer is over a marker (`MapEvent.overMarker`).
- The element becomes the marker's accessible proxy: it stays in the document in an off-screen list, focusable,
  with its name and keyboard handlers, and is not moved per frame. The focused proxy alone is placed over its marker,
  so the focus ring and a card opened from the keyboard stand where the marker is drawn.
- The proxies keep the DOM mode's reading order: one group per point pane, in the panes' order, each holding its
  pane's markers in the order they were added. A proxy of a marker far outside the view is `inert`, as the painted
  element of such a marker is hidden, so Tab and a screen reader meet the same markers in both modes.
- An unfocused proxy is clipped away (`clip-path`), and its labelled element (the element carrying `aria-label`)
  is `content-visibility: hidden`: the element stays in the accessibility tree with its name and role, and nothing
  inside it is styled or laid out, so a zoom that adds thousands of markers restyles about one element per marker.
  The whole list is not `content-visibility: auto`, which was faster (3× CPU, the longest zoom task: 0–65 ms
  against 58–63 ms) but took every proxy out of Chromium's accessibility tree. When focused, a proxy shows its
  content and ring. The test handle measures a drawn marker's face with its content shown for that read
  (`data-measure`).
- The tests read markers through `window.histmersionMap.markers` (row 31 step 1), which the canvas answers from the
  same looks it draws.
- A zoom that regroups the cluster layer spreads its work over frames (2026-11-23, row 31's last step). The regroup,
  the markers wanted and the moves' start and end points are decided in the zoom's frame, so the counts, the handle
  and the animation are those of one regroup; the markers are then made a slice at a time (6 ms each), markers on
  screen first. The first slice has only what the layout left of its 6 ms (it always makes at least one marker). The
  moves start in a slice of their own, once the markers on screen are made, and a layout queued meanwhile (the zoom's
  points reloaded) waits a frame after the moves started: each had been ~10–25 ms at 3× CPU, and together with the
  slices and the tiles' read they made the 50–60 ms frames. A layout that must see its marker at once (zooming to
  show a point) keeps a whole slice. Until its moves start a made marker is pending: not drawn (the canvas skips it,
  the stylesheet hides it) and not listed by the handle's `markers`/`spots`, which had read the points of a split at
  their ends for the frame before their slide.
- A marker whose own `class` or `style` changes keeps its look when the change is only its motion or placement (the
  transient classes; `--move-x`/`--move-y`, `--z`, `--sx`/`--sy`, `--ax`/`--ay`, `--tx`/`--ty`): only its state is
  read again. A regroup's moves restyle every moving marker, and reading each one's look again (an event's look is
  read per marker, its fill being a `color-mix` of its colour) made the frame after the moves 15–31 ms of painting.
  A look's place in the atlas is kept with the marker while the atlas holds it, not looked up by a string key per
  marker per frame.
- The two sprites that check a look's colours are canvases the CPU draws (`willReadFrequently`): reading them back
  from the GPU took 3–8 ms each, ~20 a full-window zoom sequence, and up to 500 ms when the GPU was busy.

## Consequences

A zoom's frames restyle no marker elements. The canvas is one more layer to keep in step with the map: it must be
redrawn in the same frame as the map, or markers lag the tiles. Hover effects the stylesheet gave for free (the
point's 12 % growth, the cursor) are drawn and set by the pane. The playback event markers (a few, with text labels)
stay DOM elements. A look may only use what the painter reads (the subset above); a property outside it is not
drawn, and the browser test that compares the canvas with the elements pixel by pixel catches it. A browser test
holds the keyboard and the screen reader to the DOM mode: the same stops pane by pane, the same names and roles per
marker kind, every marker button in Chromium's accessibility tree (`Accessibility.getFullAXTree`), the focus ring
painted at the drawn marker, a finding's popup opened with Enter where it opened before, with and without reduced
motion.

## Code

`web/map/MarkerPane.scala`, `web/map/DomMarker.scala`, `web/map/MarkerCanvas.scala` (the canvas, the looks held, the
atlas, the paint, the pointer), `web/map/MarkerLook.scala` (a look read from computed style),
`web/map/MarkerPaint.scala` (what a look draws, as data: no closure keeps the elements it was read from alive),
`web/map/MarkerMotion.scala` (the motion), `web/map/TheMap.scala` (the canvas by default; `localStorage`
`histmersion.ui.markerCanvas` = `off` paints the elements, the pixel test's reference), `web/MapHandle.scala`.
