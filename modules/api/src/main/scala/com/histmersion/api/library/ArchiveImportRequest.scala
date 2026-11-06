package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ArchiveImportRequest(
    id: ArchiveImportRequest.Id,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    format: Option[ArchiveImportRequest.Format] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ArchiveImportRequest:
  type Id = com.histmersion.api.library.ArchiveId
  val Id: _root_.com.histmersion.api.library.ArchiveId.type = _root_.com.histmersion.api.library.ArchiveId
  type Format = Format.T
  object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
