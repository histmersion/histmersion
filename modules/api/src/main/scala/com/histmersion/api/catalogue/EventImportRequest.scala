package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EventImportRequest(
    provider: EventImportRequest.Provider,
    dryRun: EventImportRequest.DryRun = EventImportRequest.DryRun(true),
    limit: Option[EventImportRequest.Limit] = None,
    bbox: Option[EventImportRequest.Bbox] = None,
    from: Option[EventImportRequest.From] = None,
    to: Option[EventImportRequest.To] = None,
    minVei: Option[EventImportRequest.MinVei] = None,
    minMagnitude: Option[EventImportRequest.MinMagnitude] = None,
    refresh: EventImportRequest.Refresh = EventImportRequest.Refresh(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object EventImportRequest:
  export EventImportReport.Provider
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Bbox = Bbox.T
  object Bbox extends Newtype[String, Pure]
  type From = From.T
  object From extends Newtype[Long, Pure]
  type To = To.T
  object To extends Newtype[Long, Pure]
  type MinVei = MinVei.T
  object MinVei extends Newtype[Int, Pure]
  type MinMagnitude = MinMagnitude.T
  object MinMagnitude extends Newtype[Double, Pure]
  type Refresh = Refresh.T
  object Refresh extends Newtype[Boolean, Pure]
