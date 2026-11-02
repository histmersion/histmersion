package com.histmersion.domain.library.acquisition

import com.histmersion.api
import com.histmersion.upstreams
import io.scalaland.chimney.dsl.*

extension (w: com.histmersion.domain.library.acquisition.WorkRef)
  /** The work as the wire shows it, keyed by the citekey the corpus gives a work (not in the corpus yet). */
  def wire: api.WorkRef =
    w.into[api.WorkRef]
      .withFieldComputed(_.citekey, w => Some(api.Acquire.citekey(w.authors, w.year, w.title)))
      .withFieldConst(_.inDb, false)
      .transform

extension (w: api.WorkRef)
  /** The work as the upstreams' rules read it. */
  def upstream: com.histmersion.domain.library.acquisition.WorkRef =
    w.transformInto[com.histmersion.domain.library.acquisition.WorkRef]

extension (r: com.histmersion.domain.library.acquisition.FollowupRow)
  def wire: api.FollowupRow = r.into[api.FollowupRow].enableDefaultValues.transform

extension (r: com.histmersion.domain.library.acquisition.RefRow)
  def wire: api.RefRow = r.into[api.RefRow].enableDefaultValues.transform

extension (i: upstreams.archiveorg.ArchiveItem) def wire: api.ArchiveItem = i.transformInto[api.ArchiveItem]

extension (f: upstreams.archiveorg.ArchiveFile) def wire: api.ArchiveFile = f.transformInto[api.ArchiveFile]

extension (f: api.ArchiveFile)
  def upstream: upstreams.archiveorg.ArchiveFile = f.transformInto[upstreams.archiveorg.ArchiveFile]
