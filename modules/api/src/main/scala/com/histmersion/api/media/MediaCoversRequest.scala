package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.RepairAcquire
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaCoversRequest(
    dryRun: RepairAcquire.RepairRequest.DryRun = RepairAcquire.RepairRequest.DryRun(true),
    limit: Option[MediaCoversRequest.Limit] = None,
    citekey: Option[Citekey] = None,
    refresh: MediaImportRequest.Refresh = MediaImportRequest.Refresh(false),
    covers: MediaCoversRequest.Covers = MediaCoversRequest.Covers(true),
    illustrations: MediaCoversRequest.Illustrations = MediaCoversRequest.Illustrations(true),
    isbn: Option[MediaCoversRequest.Isbn] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaCoversRequest:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Covers = Covers.T
  object Covers extends Newtype[Boolean, Pure]
  type Illustrations = Illustrations.T
  object Illustrations extends Newtype[Boolean, Pure]
  type Isbn = Isbn.T
  object Isbn extends Newtype[String, Pure]
