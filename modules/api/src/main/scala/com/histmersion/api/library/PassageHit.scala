package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.MatchSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class PassageHit(
    offset: PassageHit.Offset,
    heading: Option[PassageHit.Heading],
    text: PassageHit.Text,
    matches: List[MatchSpan]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object PassageHit:
  type Offset = Offset.T
  object Offset extends Newtype[Int, Pure]
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]
  export Passage.Excerpt as Text
