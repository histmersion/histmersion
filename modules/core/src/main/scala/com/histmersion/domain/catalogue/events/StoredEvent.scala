package com.histmersion
package domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.CatalogRecord
import com.histmersion.api.catalogue.EventCatalogueRef
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

// ── merging: one event per thing, whichever catalogues know it ──────────────────────────────────

/** An event already stored, as the merge sees it: its kind, span, point, Wikidata item, the catalogue keys recorded for
  * it, and whether an import made it (`catalogue`) — a compiled event is matched, never overwritten.
  */
final case class StoredEvent(
    id: Event.Id,
    kind: StoredEvent.Kind,
    timeSpan: TimeSpan,
    lat: Option[Latitude],
    lon: Option[Longitude],
    wikidata: Option[CatalogRecord.Wikidata],
    keys: Set[(CatalogRecord.Provider, EventCatalogueRef.ProviderId)],
    catalogue: StoredEvent.Catalogue,
    label: StoredEvent.Label = StoredEvent.Label("")
)

object StoredEvent:
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Catalogue = Catalogue.T
  object Catalogue extends RefinedType[Boolean, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
