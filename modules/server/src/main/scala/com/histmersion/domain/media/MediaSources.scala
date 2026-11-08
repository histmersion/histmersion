package com.histmersion
package domain.media

import com.histmersion.api.Problem
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

/** What media needs of the sources its figures and covers belong to (the library's, which implements it): the citekey a
  * source has now — an old one followed —, whether a source is stored under a citekey, and its converted text, where
  * its figures are linked from.
  */
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

  enum Error derives CanEqual:
    case Invalid(message: Error.Invalid.Message)
    case NotFound

  object Error:
    object Invalid:
      type Message = Problem
      val Message: Problem.type = Problem
