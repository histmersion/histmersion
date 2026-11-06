package com.histmersion.domain.research
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** An article's slug: a URL segment and a file name, lower-case, 1 to 121 characters (`sea_peoples`, `uluburun.ship`).
  */
type Slug = Slug.T

object Slug extends RefinedType[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The slug a person or a model wrote, trimmed and lower-cased; why it is none, when it is none. */

  def parse(raw: String): Either[com.histmersion.domain.research.Slug.Problem, Slug] =
    either((raw).value.trim.toLowerCase).left.map(_ =>
      _root_.com.histmersion.domain.research.Slug.Problem(s"invalid slug '$raw' — use lower-case letters, digits, '_', '-' or '.' (e.g. sea_peoples)")
    )
