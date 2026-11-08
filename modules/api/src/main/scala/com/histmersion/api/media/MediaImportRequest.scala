package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.RepairAcquire
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Event
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaImportRequest(
    provider: MediaItem.Provider = MediaItem.Provider("wikidata"),
    dryRun: MediaImportRequest.DryRun = RepairAcquire.RepairRequest.DryRun(true),
    limit: Option[MediaImportRequest.Limit] = None,
    place: Option[Place.Id] = None,
    event: Option[Event.Id] = None,
    refresh: MediaImportRequest.Refresh = MediaImportRequest.Refresh(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaImportRequest:
  export RepairAcquire.RepairRequest.DryRun
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Refresh = Refresh.T
  object Refresh extends Newtype[Boolean, Pure]
