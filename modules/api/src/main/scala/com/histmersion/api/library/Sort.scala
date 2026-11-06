package com.histmersion.api.library
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.given

enum Sort:
  case Relevance, Cited, PeriodStart, Year, Oldest, Title

object Sort:
  type LabelResult = LabelResult.T
  object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def label(s: Sort): com.histmersion.api.library.Sort.LabelResult = s match
    case Relevance   => _root_.com.histmersion.api.library.Sort.LabelResult("relevance")
    case Cited       => _root_.com.histmersion.api.library.Sort.LabelResult("cited")
    case PeriodStart => _root_.com.histmersion.api.library.Sort.LabelResult("period")
    case Year        => _root_.com.histmersion.api.library.Sort.LabelResult("year")
    case Oldest      => _root_.com.histmersion.api.library.Sort.LabelResult("oldest")
    case Title       => _root_.com.histmersion.api.library.Sort.LabelResult("title")

  def parse(s: com.histmersion.api.library.Sort.ParseS): Option[Sort] = s.trim.toLowerCase.replace("_", "") match
    case "relevance" | "rank"              => Some(Relevance)
    case "cited" | "citations"             => Some(Cited)
    case "period" | "periodstart" | "time" => Some(PeriodStart)
    case "year" | "newest"                 => Some(Year)
    case "oldest"                          => Some(Oldest)
    case "title"                           => Some(Title)
    case _                                 => None

  val all: List[com.histmersion.api.library.Sort.All] = values.toList.map(label)

  given CirceCodec[Sort] = CirceCodec.from(
    Decoder.decodeString.emap(s => parse(s).toRight(s"unknown sort '$s' (${all.mkString("|")})")),
    Encoder.encodeString.contramap(label)
  )
  given Schema[Sort] = Schema.string[Sort]
