package com.histmersion.domain.library.acquisition

import com.histmersion.api
import com.histmersion.upstreams
import io.scalaland.chimney.dsl.*

extension (w: upstreams.WorkRef)
  /** The work as the wire shows it, keyed by the citekey the corpus gives a work (not in the corpus yet). */
  def wire: api.WorkRef =
    w.into[api.WorkRef]
      .withFieldComputed(_.citekey, w => Some(api.Acquire.citekey(w.authors, w.year, w.title)))
      .withFieldConst(_.inDb, false)
      .transform

extension (w: api.WorkRef)
  /** The work as the upstreams' rules read it. */
  def upstream: upstreams.WorkRef = w.transformInto[upstreams.WorkRef]

extension (r: upstreams.FollowupRow) def wire: api.FollowupRow = r.into[api.FollowupRow].enableDefaultValues.transform

extension (r: upstreams.RefRow) def wire: api.RefRow = r.into[api.RefRow].enableDefaultValues.transform

extension (i: upstreams.ArchiveItem) def wire: api.ArchiveItem = i.transformInto[api.ArchiveItem]

extension (f: upstreams.ArchiveFile) def wire: api.ArchiveFile = f.transformInto[api.ArchiveFile]

extension (f: api.ArchiveFile) def upstream: upstreams.ArchiveFile = f.transformInto[upstreams.ArchiveFile]
