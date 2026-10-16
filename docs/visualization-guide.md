# Visualization guide

Rules for the Histmersion web UI (map × timeline at `http://localhost:8000`). These exist so the map
tells the truth about uncertainty and so every dot is an attestation a reader can trace, not a pin
on a background map.

## 1. Every mapped point is a cited attestation

A finding is a claim from a source, not a map decoration. The popup shows: **citekey**, title,
period, the claim text, and confidence (when not `high`). No bare markers — if a dot has no
source behind it, it does not belong on the map. This follows the Linked Places / World Historical
Gazetteer norm: a "place" is a bundle of attestations, each with its own citation and time span.

### 1a. Events: two tiers, never confused (#1449)

An event on the map is either **attested** — a source in the corpus attests it (the event names it as
its `citekey`, or an `attestations` row ties it to a passage) — or **catalogue-only**: known only from
a catalogue (the Smithsonian's Global Volcanism Program, NOAA NCEI's hazard databases, Wikidata).
Catalogue events are there so a reader sees the context and asks the next question; they are research
**leads, not evidence**, and the map must say so before anyone hovers:

- **Attested**: a filled badge in the event's date colour, its kind's icon light on it. Its card lists
  the attesting sources (each a source link with its own card), the articles citing them, the findings
  near it in time and place, and its pictures.
- **Catalogue-only**: an outlined, dashed, lighter badge, the icon in the date colour. Its card names the
  catalogue as provenance — with the catalogue's licence and the citation it asks for — says "known only
  from a catalogue: a research lead, not evidence", and lists its leads (no source discusses it; N
  findings within X km and ±Y years) with the actions that follow from them (search the literature, put
  it on the acquire queue, link it to a goal).
- Sources that **may** discuss an event (findings near it, passages naming it, found without a model)
  are shown as "possibly discussed in", marked **unconfirmed**. Confirming one writes an attestation —
  only from a passage of its text that names the event — and only then does the event become attested.
- A catalogue event is never cited in prose, and an article's coverage lists the catalogue events of its
  period and region that it never names as leads for the writer, never as text.

The kind is the badge's **icon**, the date its **hue** like everything else, the tier its **fill** —
three channels, three attributes. A site (settlement, fortification, sanctuary, burial…) is likewise its
type's icon on a badge in its period colour. The legend's key names every shape and icon (§2a).

### 1b. Event dating: a stated year, its ±, and how precisely it is dated

A catalogue's date is a central estimate with an uncertainty, not a window of equally likely years. The
Smithsonian GVP dates Hekla eruption 12724 to **250 CE ± 2500 years by tephrochronology**: that is what
its card says (the stated year, the ±, the method, from `StartDateYear`, `StartDateYearUncertainty`,
`StartEvidenceMethod`), with the window it implies beside it. The window is held inside what the
catalogue can mean: never after the present year (a ± is not a claim that the eruption may lie in the
future), and for the GVP's list of Holocene eruptions never before −9700 (`events.gvp.floor-year`).

How precisely an event is dated is decided ONCE, in `core.EventPrecision`, and every surface follows it.
The event's uncertainty is its stated ± or, where it is larger, half its widest uncertain window (the
start window `notBefore…beginBy`, the end window `endFrom…notAfter`; with no certain core, the whole
span) — V088's generated `events.uncertainty` computes the same in the database:

| ± (years) | class | on the map at a year | elsewhere |
|---|---|---|---|
| ≤ 50 | **precise** | drawn like any dated thing | — |
| 50 – 500 | **approximate** | drawn **faded**, its edge dotted (marker and playback label) | marked "approximate date" |
| > 500 | **imprecise** | **not drawn**: a ± 2 500-year eruption would stand in every view of five millennia | listed (events list, cards), marked "imprecise date — listed, not drawn on the map at a year" |
| no year | undated | not drawn (a time view shows dated things only) | listed |

The map's feed (`/api/events/geo`) leaves the imprecise and the undated out in SQL and counts the
imprecise (`imprecise`), so no view has to filter, and none shows fallback noise. A long event with a
certain core (a war from 431 to 404 BCE) is precisely dated however long it lasted: the rule is about
how uncertain its edges are, not its length.

A person's correction of a catalogue event (its label, kind, span, historicity, description, point —
`patch_event`) is kept over the catalogue's value on every re-import; its card lists it ("corrected by
hand"), and when the catalogue later changes the same field to something else it says so (a
**conflict**, for review). A span set by hand replaces the catalogue's dating, ± included.

## 2. Visual channels

| attribute | channel | rationale |
|---|---|---|
| **location** | position (lat/lon) | mandatory — the map itself |
| **date** | hue (viridis ramp: purple = older, yellow = newer) | ordered; the ramp is shared with the timeline histogram |
| **confidence** | saturation (VSUP desaturation: `high` = full, `medium` = 70%, `low` = 40%, `contested` = 30%) | uncertain findings must not look as crisp as high-confidence ones (Correll, Moritz & Heer, CHI 2018) |
| **what kind of thing** | shape: diamond = a finding (a source's claim), circle = a site, square = an event, landscape photo print (white mat) = a picture (§2a) | categorical, four values; shape is read before colour and leaves hue to the date |
| **its type** | a monochrome glyph (vendored Lucide) on the shape — a finding's source kind, a site's type, an event's kind; `!` when unknown | categorical and many-valued — an icon names it without taking the hue the date already uses |
| **count** | size (badge radius grows with digit count) | a cluster at a single point is visually heavier |
| **evidence type** | (reserved for hue when we add it) | categorical — qualitative palette, not a ramp |
| **event tier** | outline: filled with a solid edge (attested) vs paler with a dashed edge (catalogue only) | evidence must not look like a lead (§1a) |

Bertin's rule: hue is selective but not ordered — never use it for dates. Value and size are
ordered — never use them for unordered categories.

## 2a. The shape grammar of points, and clusters

Every point on the map — findings, sites, events, pictures — is drawn by ONE implementation
(docs/map-layers.md), so these rules hold for all of them by construction:

- **Shape = what kind of thing.** Diamond: a finding. Circle: a site. Square: an event. Landscape photo
  print (4:3): a picture. Regions and polities are shapes (their boundaries), never points. The four differ
  by OUTLINE, not by a corner radius alone: the square's corners are barely rounded (3 px), the circle is
  round, the diamond is a square turned 45°, and the print has a white mat inside a thin dark edge.
- **Glyph = its type.** White strokes over a wider dark halo (drawn twice, no CSS filter — thousands of
  markers), so a white line stays readable on a pale period colour. The glyph is ~62 % of its shape
  (15–16 px) and its strokes are thicker than a toolbar icon's (2.5 of 24 units, the halo 5), so they do
  not blur at that size. A thing whose type is not known gets its kind's shape with an exclamation mark —
  **never a bare dot**: a dot says nothing about what it is.
- **Every glyph tells itself apart from its siblings at marker size** (`EventMarkersSuite` holds each shape's
  glyphs distinct). Lucide icons too detailed for ~15 px were replaced with simpler ones of the same set
  (v0.460.0): epidemic a skull (was the biohazard sign), famine a plain ear of wheat (was the
  struck-through one), treaty a scroll (was the handshake), technological change a light bulb (the cog
  read as the drought's sun), a theatre site a mask (was the curtained stage), a paper's finding the page
  with lines (was the newspaper; a source of unknown kind's card is now the blank page).
- **Fill = its date** (the period ramp; a gradient where one point spans several dates), desaturated by
  confidence for findings. **Outline = its tier**: a thin dark edge stands every fill off the tiles; a
  catalogue-only event is paler with a dashed edge.
- **Equal optical size**: the diamond's box is the largest (34 px; the turned square inside is 24 px) so
  it looks as big as the circle (26 px) and the square (24 px); the print is 32 × 24 px, its mat included.
  Points nearer than the widest bubble (36 px) join one, which is wider than every marker box: two
  markers drawn apart never overlap.
- **Coincident findings** (several claims at one exact point) are one diamond with their count instead of
  a glyph, the native tooltip "N findings here"; a click lists them.
- **Clusters**: points near each other on screen join ONE bubble whatever their kind (never a region).
  A bubble is a round count in a white coin ring — never a glyph, so it cannot be mistaken for a site
  circle. Its disc and its rings (a dark line, the coin, a dark line) are ONE inline SVG, every circle
  about one centre: as a CSS background with box-shadow rings they were rasterised apart (the background
  snapped to the device pixels, the shadows not) and the disc sat up to a pixel off-centre wherever the
  marker fell between pixels. It counts only what is drawn (a layer switched off, a filter, the year window, a playback frame
  take their points out at once), its colour spans every dated point in it, its title and accessible name
  say what it holds ("12 findings · 3 sites · 2 events"), and a click lists them all, each with its icon —
  a finding opens its source, a site or an event pins its card, a picture opens on the map. Zooming in
  splits it.
- **The same icons everywhere**: the toolbar's layer switches are the shapes (outlined), the legend's key
  shows them with their meaning and every glyph with its name, the filter menus show each option's badge
  (key and menus alike: the map's shape at ≥ 22 px, a 14 px glyph, on a neutral grey fill that stands off
  the dark panel),
  and every card leads with its kind's icon (a source's kind, a site's type, a region's map, an event's
  kind, an article's notebook, a picture's frame).

## 3. No invented polygons

Bronze-Age cultures are distributions of finds, not states with surveyed borders. The honest default
is **points** — plot the findings and let density imply extent. When a polity polygon is drawn
(from `db/places/polities/`), it is a modern historian's reading, not a measured boundary:

- **Dashed lines** (`dashArray: '8 6'`) for any feature carrying `contested: true` or `uncertain: true`
- **Reduced fill opacity** (0.03 vs 0.06) for uncertain extents
- Every traced polygon carries a note in its GeoJSON `properties` saying whose reading it is

A crisp "Yamnaya territory" polygon without a dashed line is itself an unattributable claim.

## 4. Color accessibility

- **ColorBrewer** colorblind-safe palettes; ≤8 qualitative classes
- Color is **never the sole channel** — pair hue with the popup text and the confidence desaturation
  (WCAG 2.1 §1.4.1)
- Graphical objects maintain **≥3:1 contrast** against their background (WCAG 2.1 §1.4.11)
- The confidence desaturation scale is shown in the legend alongside the temporal ramp

## 5. Text accompanies every map link

Every web UI deep-link in an article (`http://localhost:8000/#…`) carries a **one-sentence text
statement** of what the view shows. A reader who cannot see the map (screen reader, print, broken
link) still gets the information. This is both a Chartability heuristic ("the chart has a text
summary of its takeaway") and Tufte's principle 4 (integrate words and numbers).

Bad: `[map](http://localhost:8000/#tag=lba_collapse&from=-1300&to=-1100)`

Good: `[The 37 collapse-era findings cluster around the Aegean and the Levant, with a gap in inland
Anatolia](http://localhost:8000/#tag=lba_collapse&from=-1300&to=-1100)`

## 6. Temporal change via the timeline strip

Show change over time through the **histogram strip** (drag to zoom in, +/− and wheel to zoom out),
not through animation. The reader controls the time window; the map re-renders. This is the small-
multiples principle applied interactively: the same spatial layout, different time slices, under the
reader's control.

## 7. Task-first encoding

Before choosing a visual encoding for a new web UI feature, write down the task:

> "Compare where contested vs. current findings sit for the Late Bronze Age collapse, 1300–1100 BCE."

Then pick the channels that serve that task (Munzner's what/why/how discipline). A chart whose
encoding does not serve any task the reader would actually perform is decoration.
