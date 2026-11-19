package com.histmersion
package api.library

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import Hit.Rationale

final case class Hit(source: SearchHit, matched: List[Finding.Match], why: List[Rationale])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Hit:
  /** Why a source or one of its findings is in a search's result (`title matches 'collapse'`, `tag: sea-peoples`,
    * `period`): the account of the match, not a failure.
    */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
