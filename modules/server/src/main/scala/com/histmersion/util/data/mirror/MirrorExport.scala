package com.histmersion.util.data.mirror

import com.histmersion.util.data.DataOwner

/** The Markdown mirror's notes of one kind of record. */
trait MirrorExport:
  def owner: DataOwner
