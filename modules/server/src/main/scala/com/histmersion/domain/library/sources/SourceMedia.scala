package com.histmersion.domain.library.sources

import com.histmersion.domain.media.MediaSources

/** The sources as media reads them for their figures and covers: a citekey followed through its old ones, whether a
  * source is stored under a citekey, its converted text.
  */
final class SourceMedia(sources: SourceService) extends MediaSources:

  def current(citekey: com.histmersion.domain.library.Citekey): Option[MediaSources.Current] =
    sources.current(citekey).map(c => MediaSources.Current(c.value))

  def lookup(citekey: com.histmersion.domain.library.Citekey): Either[MediaSources.Error, MediaSources.Key] =
    com.histmersion.domain.library.Citekey
      .parse(citekey.value)
      .left
      .map(p => MediaSources.Error.Invalid(MediaSources.Problem(p.value)))
      .flatMap { _ =>
        if sources.load(citekey).isDefined then Right(MediaSources.Key(citekey.value))
        else Left(MediaSources.Error.NotFound)
      }

  def markdown(citekey: com.histmersion.domain.library.Citekey): MediaSources.Markdown =
    MediaSources.Markdown(sources.markdown(citekey).value)
