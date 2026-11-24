package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation

final case class ArchiveImportRequest(
    id: ArchiveImportRequest.Id,
    citekey: Option[Citekey] = None,
    format: Option[ArchiveImportRequest.Format] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object ArchiveImportRequest:
  export com.histmersion.api.library.ArchiveId as Id
  type Format = Format.T
  object Format extends Newtype[String, Pure]
