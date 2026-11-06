package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventImportRequest(
    provider: EventImportRequest.Provider,
    dryRun: EventImportRequest.DryRun = _root_.com.histmersion.api.catalogue.EventImportRequest.DryRun(true),
    limit: Option[EventImportRequest.Limit] = None,
    bbox: Option[EventImportRequest.Bbox] = None,
    from: Option[EventImportRequest.From] = None,
    to: Option[EventImportRequest.To] = None,
    minVei: Option[EventImportRequest.MinVei] = None,
    minMagnitude: Option[EventImportRequest.MinMagnitude] = None,
    refresh: EventImportRequest.Refresh = _root_.com.histmersion.api.catalogue.EventImportRequest.Refresh(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventImportRequest:
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bbox = Bbox.T
  object Bbox extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type From = From.T
  object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type To = To.T
  object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinVei = MinVei.T
  object MinVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinMagnitude = MinMagnitude.T
  object MinMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refresh = Refresh.T
  object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
