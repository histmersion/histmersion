package com.histmersion.domain.library.sources

import com.histmersion.domain.media.MediaSources

/** The sources as media reads them for their figures and covers: a citekey followed through its old ones, whether a
  * source is stored under a citekey, its converted text.
  */
final class SourceMedia(sources: SourceService) extends MediaSources:
  def current(citekey: String): Option[String] = sources.current(citekey)
  def exists(citekey: String): Boolean = sources.load(citekey).isDefined
  def markdown(citekey: String): String = sources.markdown(citekey)
