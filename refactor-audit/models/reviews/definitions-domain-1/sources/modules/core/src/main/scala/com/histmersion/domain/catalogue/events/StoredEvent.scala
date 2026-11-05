package com.histmersion.domain.catalogue.events

import com.histmersion.api.TimeSpan

// ── merging: one event per thing, whichever catalogues know it ──────────────────────────────────

/** An event already stored, as the merge sees it: its kind, span, point, Wikidata item, the catalogue keys recorded for
  * it, and whether an import made it (`catalogue`) — a compiled event is matched, never overwritten.
  */
final case class StoredEvent(
    id: StoredEvent.Id,
    kind: StoredEvent.Kind,
    timeSpan: TimeSpan,
    lat: Option[com.histmersion.Latitude],
    lon: Option[com.histmersion.Longitude],
    wikidata: Option[StoredEvent.Wikidata],
    keys: Set[(StoredEvent.KeysPart1, StoredEvent.KeysPart2)],
    catalogue: StoredEvent.Catalogue,
    label: StoredEvent.Label = ""
)

object StoredEvent:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeysPart1 = KeysPart1.T
  object KeysPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeysPart2 = KeysPart2.T
  object KeysPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Catalogue = Catalogue.T
  object Catalogue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
