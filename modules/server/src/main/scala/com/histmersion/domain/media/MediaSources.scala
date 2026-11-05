package com.histmersion.domain.media

/** What media needs of the sources its figures and covers belong to (the library's, which implements it): the citekey a
  * source has now — an old one followed —, whether a source is stored under a citekey, and its converted text, where
  * its figures are linked from.
  */
trait MediaSources:
  def current(citekey: String): Option[String]
  def lookup(citekey: String): Either[MediaSources.Error, MediaSources.Key]
  def markdown(citekey: String): String

object MediaSources:
  type Key = Key.T
  object Key extends io.github.iltotore.iron.RefinedType[String, io.github.iltotore.iron.Pure]

  enum Error:
    case Invalid(message: String)
    case NotFound
