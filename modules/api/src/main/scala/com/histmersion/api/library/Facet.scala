package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import io.github.iltotore.iron.constraint.any.Pure

final case class Facet(
    value: Facet.Value,
    count: Facet.Count,
    id: Option[Facet.Id] = None,
    contested: Facet.Contested = Facet.Contested(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema
object Facet:
  type FieldText = FieldText.T
  object FieldText extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  type Value = Value.T
  object Value extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Contested = Contested.T
  object Contested extends Newtype[Boolean, Pure]

  enum Field derives CanEqual:
    case Tags, Authors, Places, Topics, Status, Provenance

  object Field:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def label(f: Field): Wire = f match
      case Tags       => Wire("tags");
      case Authors    => Wire("authors");
      case Places     => Wire("places")
      case Topics     => Wire("topics");
      case Status     => Wire("status");
      case Provenance => Wire("provenance")

    def parse(s: Wire): Option[Field] =
      values.find(f => label(f) == Wire((s).value.trim.toLowerCase) || f.toString.equalsIgnoreCase((s).value.trim))

    val all: List[Wire] = values.toList.map(label)
