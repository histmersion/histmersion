package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.MatchSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class PassageHit(offset: PassageHit.Offset, heading: Option[PassageHit.Heading], text: PassageHit.Text, matches: List[MatchSpan])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object PassageHit:
  type Offset = Offset.T
  object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Heading = Heading.T
  object Heading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  export com.histmersion.api.library.Passage.{Excerpt as Text}
