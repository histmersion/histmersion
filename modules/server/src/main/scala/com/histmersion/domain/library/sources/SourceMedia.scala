package com.histmersion.domain.library.sources

import com.histmersion.domain.media.MediaSources

/** The sources as media reads them for their figures and covers: a citekey followed through its old ones, whether a
  * source is stored under a citekey, its converted text.
  */
final class SourceMedia(sources: SourceService) extends MediaSources:
  def current(citekey: String): Option[String] = sources.current(citekey)
  def lookup(citekey: String): Either[MediaSources.Error, MediaSources.Key] =
    com.histmersion.domain.library.Citekey.parse(citekey).left.map(MediaSources.Error.Invalid(_)).flatMap { _ =>
      if sources.load(citekey).isDefined then Right(MediaSources.Key(citekey))
      else Left(MediaSources.Error.NotFound)
    }
  def markdown(citekey: String): String = sources.markdown(citekey)
