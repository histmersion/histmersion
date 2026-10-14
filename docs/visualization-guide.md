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

The kind is the badge's **icon** (natural events in a round badge, human ones in a square), the date
its **hue** like everything else, the tier its **fill** — three channels, three attributes. A typed
site (settlement, fortification, sanctuary, burial…) is likewise its type's icon in the colour its dot
would have; a site of no known type keeps the dot. The legend's key names every icon.

## 2. Visual channels

| attribute | channel | rationale |
|---|---|---|
| **location** | position (lat/lon) | mandatory — the map itself |
| **date** | hue (viridis ramp: purple = older, yellow = newer) | ordered; the ramp is shared with the timeline histogram |
| **confidence** | saturation (VSUP desaturation: `high` = full, `medium` = 70%, `low` = 40%, `contested` = 30%) | uncertain findings must not look as crisp as high-confidence ones (Correll, Moritz & Heer, CHI 2018) |
| **count** | size (badge radius grows with digit count) | a cluster at a single point is visually heavier |
| **evidence type** | (reserved for hue when we add it) | categorical — qualitative palette, not a ramp |
| **event kind · site type** | shape: a monochrome icon (vendored Lucide) inside the marker | categorical and many-valued — an icon names it without taking the hue the date already uses |
| **event tier** | fill: filled (attested) vs outlined and dashed (catalogue only) | evidence must not look like a lead (§1a) |

Bertin's rule: hue is selective but not ordered — never use it for dates. Value and size are
ordered — never use them for unordered categories.

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
