package com.histmersion.domain.media

/** What media needs of the sources its figures and covers belong to (the library's, which implements it): the citekey a
  * source has now — an old one followed —, whether a source is stored under a citekey, and its converted text, where
  * its figures are linked from.
  */
trait MediaSources:
  def current(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.media.MediaSources.Current]
  def lookup(citekey: com.histmersion.domain.library.Citekey): Either[MediaSources.Error, MediaSources.Key]
  def markdown(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.media.MediaSources.Markdown

object MediaSources:
  type Current = Current.T
  object Current extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Key = Key.T
  object Key extends io.github.iltotore.iron.RefinedType[String, io.github.iltotore.iron.Pure]

  enum Error:
    case Invalid(message: Error.Invalid.Message)
    case NotFound

  object Error:
    object Invalid:
      type Message = com.histmersion.domain.media.MediaSources.Problem
      val Message: _root_.com.histmersion.domain.media.MediaSources.Problem.type = _root_.com.histmersion.domain.media.MediaSources.Problem
