package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import io.circe.{Codec as CirceCodec, Decoder, Encoder}

final case class Facet(
    value: Facet.Value,
    count: Facet.Count,
    id: Option[Facet.Id] = None,
    contested: Facet.Contested = _root_.com.histmersion.api.library.Facet.Contested(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object Facet:
  type FieldText = FieldText.T
  object FieldText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Contested = Contested.T
  object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Field:
    case Tags, Authors, Places, Topics, Status, Provenance

  object Field:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def label(f: Field): com.histmersion.api.library.Facet.Field.Wire = f match
      case Tags       => _root_.com.histmersion.api.library.Facet.Field.Wire("tags");
      case Authors    => _root_.com.histmersion.api.library.Facet.Field.Wire("authors");
      case Places     => _root_.com.histmersion.api.library.Facet.Field.Wire("places")
      case Topics     => _root_.com.histmersion.api.library.Facet.Field.Wire("topics");
      case Status     => _root_.com.histmersion.api.library.Facet.Field.Wire("status");
      case Provenance => _root_.com.histmersion.api.library.Facet.Field.Wire("provenance")

    def parse(s: com.histmersion.api.library.Facet.Field.Wire): Option[Field] =
      values.find(f => label(f) == _root_.com.histmersion.api.library.Facet.Field.Wire((s).value.trim.toLowerCase) || f.toString.equalsIgnoreCase((s).value.trim))

    val all: List[com.histmersion.api.library.Facet.Field.Wire] = values.toList.map(_root_.com.histmersion.api.library.Facet.Field.Wire((label)))
