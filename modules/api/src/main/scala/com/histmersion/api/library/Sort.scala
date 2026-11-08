package com.histmersion
package api.library
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

enum Sort:
  case Relevance, Cited, PeriodStart, Year, Oldest, Title

object Sort:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]

  def label(s: Sort): Wire = s match
    case Relevance   => Wire("relevance")
    case Cited       => Wire("cited")
    case PeriodStart => Wire("period")
    case Year        => Wire("year")
    case Oldest      => Wire("oldest")
    case Title       => Wire("title")

  def parse(s: Wire): Option[Sort] = (s).value.trim.toLowerCase.replace("_", "") match
    case "relevance" | "rank"              => Some(Relevance)
    case "cited" | "citations"             => Some(Cited)
    case "period" | "periodstart" | "time" => Some(PeriodStart)
    case "year" | "newest"                 => Some(Year)
    case "oldest"                          => Some(Oldest)
    case "title"                           => Some(Title)
    case _                                 => None

  val all: List[Wire] = values.toList.map(label)

  given CirceCodec[Sort] = CirceCodec.from(
    Decoder.decodeString.emap(s => parse(Wire(s)).toRight(s"unknown sort '$s' (${all.mkString("|")})")),
    Encoder.encodeString.contramap[Sort](s => label(s).value)
  )
  given Schema[Sort] = Schema.string[Sort]
