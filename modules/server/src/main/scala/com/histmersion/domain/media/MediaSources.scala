package com.histmersion
package domain.media
import com.histmersion.util.problem.Problem

import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** What media needs of the sources its figures and covers belong to (the library's, which implements it): the citekey a
  * source has now — an old one followed —, whether a source is stored under a citekey, and its converted text, where
  * its figures are linked from.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
trait MediaSources:
  def current(citekey: Citekey): Option[MediaSources.Current]
  def lookup(citekey: Citekey): Either[MediaSources.Error, MediaSources.Key]
  def markdown(citekey: Citekey): MediaSources.Markdown

object MediaSources:
  type Current = Current.T
  object Current extends Newtype[String, Pure]
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  type Key = Key.T
  object Key extends Newtype[String, io.github.iltotore.iron.Pure]

  enum Error:
    case Invalid(message: Problem)
    case NotFound
