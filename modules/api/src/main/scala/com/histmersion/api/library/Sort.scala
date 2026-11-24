package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

enum Sort:
  case Relevance, Cited, PeriodStart, Year, Oldest, Title

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Sort:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def label(s: Sort): Wire = s match
    case Relevance   => Wire("relevance")
    case Cited       => Wire("cited")
    case PeriodStart => Wire("period")
    case Year        => Wire("year")
    case Oldest      => Wire("oldest")
    case Title       => Wire("title")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Sort] = (s).value.trim.toLowerCase.replace("_", "") match
    case "relevance" | "rank"              => Some(Relevance)
    case "cited" | "citations"             => Some(Cited)
    case "period" | "periodstart" | "time" => Some(PeriodStart)
    case "year" | "newest"                 => Some(Year)
    case "oldest"                          => Some(Oldest)
    case "title"                           => Some(Title)
    case _                                 => None

  val all: List[Wire] = values.toList.map(label)

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  given JsonValueCodec[Sort] = JsonCodec.text[Sort](
    s => label(s).value,
    s => parse(Wire(s)).toRight(s"unknown sort '$s' (${all.mkString("|")})")
  )
  given Schema[Sort] = Schema.string[Sort]
