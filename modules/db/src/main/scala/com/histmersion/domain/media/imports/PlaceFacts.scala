package com.histmersion
package domain.media.imports

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime

/** A place as a collection match sees it: every name key it goes by (label and alternative names,
  * `histmersion_place_key`), its period — its own when bounded at both ends, else the span of the findings it holds —
  * and its Wikidata item and point (to tell equivalents apart).
  */
final case class PlaceFacts(
    id: PlaceFacts.Id,
    label: PlaceFacts.Label,
    keys: Set[PlaceFacts.Keys],
    period: Option[(DeepTime.SignedYear, DeepTime.SignedYear)],
    qid: Option[PlaceFacts.Qid],
    lat: Option[Latitude],
    lon: Option[Longitude]
)

object PlaceFacts:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Keys = Keys.T
  object Keys extends Newtype[String, Pure]
  export Target.Qid
