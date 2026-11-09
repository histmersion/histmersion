package com.histmersion
package domain.research
import com.histmersion.util.problem.{Legacy, Problem}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import io.github.iltotore.iron.constraint.any.Pure

/** An article's slug: a URL segment and a file name, lower-case, 1 to 121 characters (`sea_peoples`, `uluburun.ship`).
  */
type Slug = Slug.T

object Slug extends Newtype[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]:

  /** The slug a person or a model wrote, trimmed and lower-cased; why it is none, when it is none. */

  def parse(raw: String): Either[Problem, Slug] =
    either((raw).trim.toLowerCase).left.map(_ =>
      Problem.Unclassified(
        Legacy.Text(s"invalid slug '$raw' — use lower-case letters, digits, '_', '-' or '.' (e.g. sea_peoples)")
      )
    )
