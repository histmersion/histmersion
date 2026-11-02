package com.histmersion.util.data.mirror

import com.histmersion.api.ApiError

/** How the owner of one kind of record renders a record's note, over its own services: the composition hands the mirror
  * one per registered kind (an old identifier answers with the record's current note).
  */
trait MirrorNote:
  def records: MirrorRecords
  def note(id: String): Either[ApiError, String]
