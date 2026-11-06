package com.histmersion.domain.catalogue.events

import com.histmersion.api.TimeSpan

// ── merging: one event per thing, whichever catalogues know it ──────────────────────────────────

/** An event already stored, as the merge sees it: its kind, span, point, Wikidata item, the catalogue keys recorded for
  * it, and whether an import made it (`catalogue`) — a compiled event is matched, never overwritten.
  */
final case class StoredEvent(
    id: com.histmersion.domain.catalogue.Event.Id,
    kind: StoredEvent.Kind,
    timeSpan: TimeSpan,
    lat: Option[com.histmersion.Latitude],
    lon: Option[com.histmersion.Longitude],
    wikidata: Option[com.histmersion.api.catalogue.CatalogRecord.Wikidata],
    keys: Set[(com.histmersion.api.catalogue.CatalogRecord.Provider, com.histmersion.api.catalogue.EventCatalogueRef.ProviderId)],
    catalogue: StoredEvent.Catalogue,
    label: StoredEvent.Label = com.histmersion.domain.catalogue.events.StoredEvent.Label("")
)

object StoredEvent:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Catalogue = Catalogue.T
  object Catalogue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
