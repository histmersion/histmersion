package com.histmersion.domain.library.sources

import com.histmersion.domain.media.MediaSources

/** The sources as media reads them for their figures and covers: a citekey followed through its old ones, whether a
  * source is stored under a citekey, its converted text.
  */
final class SourceMedia(sources: SourceService) extends MediaSources:
  def current(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceMedia.CurrentResult] = sources.current(citekey)
  def lookup(citekey: com.histmersion.domain.library.Citekey): Either[MediaSources.Error, MediaSources.Key] =
    com.histmersion.domain.library.Citekey.parse(citekey).left.map(MediaSources.Error.Invalid(_)).flatMap { _ =>
      if sources.load(citekey).isDefined then Right(MediaSources.Key(citekey))
      else Left(MediaSources.Error.NotFound)
    }
  def markdown(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceMedia.MarkdownResult = sources.markdown(citekey)

object SourceMedia:
  type CurrentResult = CurrentResult.T
  object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
