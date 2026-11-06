package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class MediaCoversRequest(
    dryRun: com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun = _root_.com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun(true),
    limit: Option[MediaCoversRequest.Limit] = None,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    refresh: com.histmersion.api.media.MediaImportRequest.Refresh = _root_.com.histmersion.api.media.MediaImportRequest.Refresh(false),
    covers: MediaCoversRequest.Covers = _root_.com.histmersion.api.media.MediaCoversRequest.Covers(true),
    illustrations: MediaCoversRequest.Illustrations = _root_.com.histmersion.api.media.MediaCoversRequest.Illustrations(true),
    isbn: Option[MediaCoversRequest.Isbn] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaCoversRequest:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Covers = Covers.T
  object Covers extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Illustrations = Illustrations.T
  object Illustrations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Isbn = Isbn.T
  object Isbn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
