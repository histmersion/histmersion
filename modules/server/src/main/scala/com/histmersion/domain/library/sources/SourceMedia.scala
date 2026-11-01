package com.histmersion.domain.library.sources

import com.histmersion.db.SourceRepo
import com.histmersion.domain.media.MediaSources

import javax.sql.DataSource

/** The sources as media reads them for their figures and covers: a citekey followed through its old ones, whether a
  * source is stored under a citekey, its converted text.
  */
final class SourceMedia(ds: DataSource) extends MediaSources:
  def current(citekey: String): Option[String] = SourceRepo.canonical(ds, citekey)
  def exists(citekey: String): Boolean = SourceRepo.load(ds, citekey).isDefined
  def markdown(citekey: String): String = SourceRepo.markdown(ds, citekey)
