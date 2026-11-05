package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import io.circe.{Codec as CirceCodec, Decoder, Encoder}

final case class Facet(
    value: String,
    count: Int,
    id: Option[String] = None,
    contested: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object Facet:


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
