package com.histmersion.api.library
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.given

enum Sort:
  case Relevance, Cited, PeriodStart, Year, Oldest, Title

object Sort:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def label(s: Sort): com.histmersion.api.library.Sort.Wire = s match
    case Relevance   => _root_.com.histmersion.api.library.Sort.Wire("relevance")
    case Cited       => _root_.com.histmersion.api.library.Sort.Wire("cited")
    case PeriodStart => _root_.com.histmersion.api.library.Sort.Wire("period")
    case Year        => _root_.com.histmersion.api.library.Sort.Wire("year")
    case Oldest      => _root_.com.histmersion.api.library.Sort.Wire("oldest")
    case Title       => _root_.com.histmersion.api.library.Sort.Wire("title")

  def parse(s: com.histmersion.api.library.Sort.Wire): Option[Sort] = (s).value.trim.toLowerCase.replace("_", "") match
    case "relevance" | "rank"              => Some(Relevance)
    case "cited" | "citations"             => Some(Cited)
    case "period" | "periodstart" | "time" => Some(PeriodStart)
    case "year" | "newest"                 => Some(Year)
    case "oldest"                          => Some(Oldest)
    case "title"                           => Some(Title)
    case _                                 => None

  val all: List[com.histmersion.api.library.Sort.Wire] = values.toList.map(_root_.com.histmersion.api.library.Sort.Wire((label).value))

  given CirceCodec[Sort] = CirceCodec.from(
    Decoder.decodeString.emap(s => parse(_root_.com.histmersion.api.library.Sort.Wire(s)).toRight(s"unknown sort '$s' (${all.mkString("|")})")),
    Encoder.encodeString.contramap((label).value)
  )
  given Schema[Sort] = Schema.string[Sort]
