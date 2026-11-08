package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EventRederiveRequest(provider: Option[EventRederiveRequest.Provider] = None, dryRun: EventRederiveRequest.DryRun = EventRederiveRequest.DryRun(false))
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventRederiveRequest:
  export EventImportReport.Provider
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
