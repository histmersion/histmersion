package com.histmersion.api

import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema

/** Result ordering. Wire form is the lowercase label (`relevance|cited|period|year|oldest|title`): `year` is the newest
  * publication first, `oldest` the oldest first.
  */
enum Sort:
  case Relevance, Cited, PeriodStart, Year, Oldest, Title

object Sort:
  def label(s: Sort): String = s match
    case Relevance   => "relevance"
    case Cited       => "cited"
    case PeriodStart => "period"
    case Year        => "year"
    case Oldest      => "oldest"
    case Title       => "title"

  def parse(s: String): Option[Sort] = s.trim.toLowerCase.replace("_", "") match
    case "relevance" | "rank"              => Some(Relevance)
    case "cited" | "citations"             => Some(Cited)
    case "period" | "periodstart" | "time" => Some(PeriodStart)
    case "year" | "newest"                 => Some(Year)
    case "oldest"                          => Some(Oldest)
    case "title"                           => Some(Title)
    case _                                 => None

  val all: List[String] = values.toList.map(label)

  given CirceCodec[Sort] = CirceCodec.from(
    Decoder.decodeString.emap(s => parse(s).toRight(s"unknown sort '$s' (${all.mkString("|")})")),
    Encoder.encodeString.contramap(label)
  )
  given Schema[Sort] = Schema.string[Sort]
