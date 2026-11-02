package com.histmersion.domain.library.acquisition

import com.histmersion.api
import com.histmersion.upstreams
import io.scalaland.chimney.dsl.*

extension (w: com.histmersion.domain.library.acquisition.WorkRef)
  /** The work as the wire shows it, keyed by the citekey the corpus gives a work (not in the corpus yet). */
  def wire: api.library.WorkRef =
    w.into[api.library.WorkRef]
      .withFieldComputed(_.citekey, w => Some(api.library.Acquire.citekey(w.authors, w.year, w.title)))
      .withFieldConst(_.inDb, false)
      .transform

extension (w: api.library.WorkRef)
  /** The work as the upstreams' rules read it. */
  def upstream: com.histmersion.domain.library.acquisition.WorkRef =
    w.transformInto[com.histmersion.domain.library.acquisition.WorkRef]

extension (r: com.histmersion.domain.library.acquisition.FollowupRow)
  def wire: api.library.FollowupRow = r.into[api.library.FollowupRow].enableDefaultValues.transform

extension (r: com.histmersion.domain.library.acquisition.RefRow)
  def wire: api.library.RefRow = r.into[api.library.RefRow].enableDefaultValues.transform

extension (i: upstreams.archiveorg.ArchiveItem)
  def wire: api.library.ArchiveItem = i.transformInto[api.library.ArchiveItem]

extension (f: upstreams.archiveorg.ArchiveFile)
  def wire: api.library.ArchiveFile = f.transformInto[api.library.ArchiveFile]

extension (f: api.library.ArchiveFile)
  def upstream: upstreams.archiveorg.ArchiveFile = f.transformInto[upstreams.archiveorg.ArchiveFile]
