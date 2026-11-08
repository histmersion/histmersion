package com.histmersion
package api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class FindingsPatch(
    add: List[Finding.Input] = Nil,
    remove: List[FindingsPatch.Remove] = Nil,
    replace: Option[List[Finding.Input]] = None
) derives KindlingsSchema,
      com.histmersion.api.json.Schema
object FindingsPatch:

  type Remove = Remove.T
  object Remove extends Newtype[String, Pure]

  given Codec.AsObject[FindingsPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[FindingsPatch])
