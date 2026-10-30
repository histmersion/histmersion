package com.histmersion.domain

import com.histmersion.api.TimeSpan

// ── merging: one event per thing, whichever catalogues know it ──────────────────────────────────

/** An event already stored, as the merge sees it: its kind, span, point, Wikidata item, the catalogue keys recorded for
  * it, and whether an import made it (`catalogue`) — a compiled event is matched, never overwritten.
  */
final case class StoredEvent(
    id: String,
    kind: String,
    timeSpan: TimeSpan,
    lat: Option[Double],
    lon: Option[Double],
    wikidata: Option[String],
    keys: Set[(String, String)],
    catalogue: Boolean,
    label: String = ""
)
