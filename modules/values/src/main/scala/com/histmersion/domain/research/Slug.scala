package com.histmersion.domain.research
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** An article's slug: a URL segment and a file name, lower-case, 1 to 121 characters (`sea_peoples`, `uluburun.ship`).
  */
type Slug = Slug.T

object Slug extends RefinedType[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]:

  /** The slug a person or a model wrote, trimmed and lower-cased; why it is none, when it is none. */
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(raw: com.histmersion.domain.research.Slug.ParseRaw): Either[com.histmersion.domain.research.Slug.ParseResultPart1, Slug] =
    either(raw.trim.toLowerCase).left.map(_ =>
      _root_.com.histmersion.domain.research.Slug.ParseResultPart1(s"invalid slug '$raw' — use lower-case letters, digits, '_', '-' or '.' (e.g. sea_peoples)")
    )
