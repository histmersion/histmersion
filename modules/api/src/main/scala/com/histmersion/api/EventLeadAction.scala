package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── questions that guide research ───────────────────────────────────────────────────────────────

/** A command a lead can be acted on with: `discover` (search the literature for it), `acquire` (put it on the acquire
  * queue), `goal` (link it to a research goal), `confirm` (confirm a proposed source). `command` is the CLI line;
  * `query` the text the web UI hands the discover endpoint.
  */
final case class EventLeadAction(kind: String, label: String, command: String, query: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventLeadAction:
  given Schema[EventLeadAction] = summon[KindlingsSchema[EventLeadAction]].schema
