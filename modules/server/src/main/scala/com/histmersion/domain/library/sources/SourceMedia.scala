package com.histmersion
package domain.library.sources

import com.histmersion.domain.media.MediaSources
import com.histmersion.domain.library.Citekey

/** The sources as media reads them for their figures and covers: a citekey followed through its old ones, whether a
  * source is stored under a citekey, its converted text.
  */
final class SourceMedia(sources: SourceService) extends MediaSources:

  def current(citekey: Citekey): Option[MediaSources.Current] =
    sources.current(citekey).map(c => MediaSources.Current(c.value))

  def lookup(citekey: Citekey): Either[MediaSources.Error, MediaSources.Key] =
    Citekey
      .parse(citekey.value)
      .left
      .map(p => MediaSources.Error.Invalid(p))
      .flatMap { _ =>
        if sources.load(citekey).isDefined then Right(MediaSources.Key(citekey.value))
        else Left(MediaSources.Error.NotFound)
      }

  def markdown(citekey: Citekey): MediaSources.Markdown =
    MediaSources.Markdown(sources.markdown(citekey).value)
