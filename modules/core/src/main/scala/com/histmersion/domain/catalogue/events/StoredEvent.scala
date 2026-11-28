package com.histmersion
package domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.CatalogRecord
import com.histmersion.api.catalogue.EventCatalogueRef
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

// ── merging: one event per thing, whichever catalogues know it ──────────────────────────────────

/** An event already stored, as the merge sees it: its kind, span, point, Wikidata item, the catalogue keys recorded for
  * it, and whether an import made it (`catalogue`) — a compiled event is matched, never overwritten.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
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
  object Kind extends Newtype[String, Pure]
  enum Catalogue derives CanEqual:
    case Catalogue, Corpus
  object Catalogue extends TwoCases[Catalogue]:
    extension (catalogue: Catalogue) def catalogue: Boolean = catalogue == Catalogue
  type Label = Label.T
  object Label extends Newtype[String, Pure]
