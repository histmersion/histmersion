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
    type LabelResult = LabelResult.T
    object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def label(f: Field): com.histmersion.api.library.Facet.Field.LabelResult = f match
      case Tags       => "tags";
      case Authors    => "authors";
      case Places     => "places"
      case Topics     => "topics";
      case Status     => "status";
      case Provenance => "provenance"

    def parse(s: com.histmersion.api.library.Facet.Field.ParseS): Option[Field] =
      values.find(f => label(f) == s.trim.toLowerCase || f.toString.equalsIgnoreCase(s.trim))

    val all: List[com.histmersion.api.library.Facet.Field.All] = values.toList.map(label)
