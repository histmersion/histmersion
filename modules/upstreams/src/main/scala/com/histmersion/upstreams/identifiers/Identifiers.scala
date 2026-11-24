package com.histmersion
package upstreams.identifiers

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.Doi
import com.histmersion.upstreams.ArxivId
import com.histmersion.lint.{LintRule, tolerated}
object Identifiers:
  type ArxivText = ArxivText.T
  object ArxivText extends Newtype[String, Pure]
  type DoiText = DoiText.T
  object DoiText extends Newtype[String, Pure]

  private val DoiCore = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r
  private val ArxivNew = """^(?:arxiv:)?(\d{4}\.\d{4,5})(?:v\d+)?$""".r
  private val ArxivOld = """^(?:arxiv:)?([a-z-]+(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?$""".r

  /** DOIs are case-insensitive, so they are kept lower-case. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def doi(raw: DoiText): Option[Doi] =
    val s = Option(raw)
      .map(_.value)
      .getOrElse("")
      .trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
      .trim
    DoiCore
      .findFirstIn(s)
      .map(d =>
        Doi(
          d.stripSuffix(".")
            .stripSuffix(",")
            .stripSuffix(";")
            .stripSuffix(")")
            .stripSuffix("}")
            .stripSuffix("|")
            .toLowerCase
        )
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def arxiv(raw: ArxivText): Option[ArxivId] =
    Option(raw).map((_).value.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLowerCase match
        case ArxivNew(id) => Some(ArxivId(id))
        case _            =>
          s match
            case ArxivOld(id) => Some(ArxivId(id))
            case _            => None
    }
