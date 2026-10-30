package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/events/rederive` (a job): compute the dating of the stored catalogue records again from what each record
  * keeps (`event_catalogue.record`: the catalogue's own record as read) — NO network — and refresh the events those
  * catalogues created, their corrections by hand applied. `provider` narrows it to one catalogue; `dryRun` counts what
  * would change.
  */
final case class EventRederiveRequest(provider: Option[String] = None, dryRun: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventRederiveRequest:
  given Schema[EventRederiveRequest] = summon[KindlingsSchema[EventRederiveRequest]].schema
