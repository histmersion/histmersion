package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventRederiveRequest(provider: Option[EventRederiveRequest.Provider] = None, dryRun: EventRederiveRequest.DryRun = _root_.com.histmersion.api.catalogue.EventRederiveRequest.DryRun(false))
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventRederiveRequest:
  type Provider = com.histmersion.api.catalogue.EventImportReport.Provider
  val Provider: _root_.com.histmersion.api.catalogue.EventImportReport.Provider.type = _root_.com.histmersion.api.catalogue.EventImportReport.Provider
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
