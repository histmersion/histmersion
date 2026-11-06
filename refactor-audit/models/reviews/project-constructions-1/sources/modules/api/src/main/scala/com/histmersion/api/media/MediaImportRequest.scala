package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaImportRequest(
    provider: com.histmersion.api.media.MediaItem.Provider = _root_.com.histmersion.api.media.MediaItem.Provider("wikidata"),
    dryRun: MediaImportRequest.DryRun = _root_.com.histmersion.api.media.MediaImportRequest.DryRun(true),
    limit: Option[MediaImportRequest.Limit] = None,
    place: Option[com.histmersion.api.catalogue.Place.Id] = None,
    event: Option[com.histmersion.api.catalogue.Event.Id] = None,
    refresh: MediaImportRequest.Refresh = _root_.com.histmersion.api.media.MediaImportRequest.Refresh(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaImportRequest:
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refresh = Refresh.T
  object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
