package com.histmersion.util.data.mirror

import com.histmersion.api.ApiError

/** The Markdown mirror over the registered notes: the note of one record of a registered kind. */
final class Mirror(exports: List[MirrorExport]):
  def kinds: List[MirrorRecords] = exports.map(_.records)

  def note(records: MirrorRecords, id: String)(using TransitionNotes): Either[ApiError, String] =
    exports
      .find(_.records == records)
      .toRight(ApiError(s"no mirror notes of ${records.value} are registered"))
      .flatMap(_.note(id))
