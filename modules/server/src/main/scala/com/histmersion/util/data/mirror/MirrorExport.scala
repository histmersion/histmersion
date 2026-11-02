package com.histmersion.util.data.mirror

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.mirror.MirrorPlacement

/** The Markdown mirror's notes of one kind of record: whose they are and where they go in the mirror's layout. Each
  * note is rendered by the owner's [[MirrorNote]].
  */
trait MirrorExport:
  def owner: DataOwner
  def records: MirrorRecords
  def placement: MirrorPlacement
