package com.histmersion.util.data.mirror

import com.histmersion.api.ApiError
import com.histmersion.util.data.DataOwner

/** The Markdown mirror's notes of one kind of record: whose they are, where they go in the mirror's layout, and the
  * note of one record by its identifier (an old identifier answers with the record's current note).
  */
trait MirrorExport:
  def owner: DataOwner
  def records: MirrorRecords
  def placement: MirrorPlacement
  def note(id: String)(using TransitionNotes): Either[ApiError, String]
