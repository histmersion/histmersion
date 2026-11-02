package com.histmersion.util.data.mirror

import com.histmersion.api.ApiError

/** The Markdown mirror over the registered kinds: the note of one record of a registered kind, rendered by its owner.
  */
final class Mirror(exports: List[MirrorExport], notes: List[MirrorNote]):
  def kinds: List[MirrorRecords] = exports.map(_.records)

  def note(records: MirrorRecords, id: String): Either[ApiError, String] =
    exports
      .find(_.records == records)
      .flatMap(_ => notes.find(_.records == records))
      .toRight(ApiError(s"no mirror notes of ${records.value} are registered"))
      .flatMap(_.note(id))
