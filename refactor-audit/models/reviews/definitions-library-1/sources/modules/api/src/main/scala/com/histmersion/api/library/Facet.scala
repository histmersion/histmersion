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
    contested: Facet.Contested = false
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object Facet:


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
    def label(f: Field): String = f match
      case Tags       => "tags";
      case Authors    => "authors";
      case Places     => "places"
      case Topics     => "topics";
      case Status     => "status";
      case Provenance => "provenance"

    def parse(s: String): Option[Field] =
      values.find(f => label(f) == s.trim.toLowerCase || f.toString.equalsIgnoreCase(s.trim))

    val all: List[String] = values.toList.map(label)
