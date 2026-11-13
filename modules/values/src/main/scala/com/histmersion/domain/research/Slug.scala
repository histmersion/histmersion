package com.histmersion
package domain.research
import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** An article's slug: a URL segment and a file name, lower-case, 1 to 121 characters (`sea_peoples`, `uluburun.ship`).
  */
type Slug = Slug.T

object Slug extends Newtype[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]:

  /** The slug a person or a model wrote, trimmed and lower-cased; why it is none, when it is none. */
  def parse(raw: String): Either[Problem, Slug] =
    either((raw).trim.toLowerCase).left.map(_ => Problem.Invalid.malformed(refused(raw)))
