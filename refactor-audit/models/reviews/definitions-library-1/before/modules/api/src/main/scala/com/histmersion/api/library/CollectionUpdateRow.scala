package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One item of an update of every linked item: its id, the sources linked to it, its state (`would-change`, `updated`,
  * `failed`, `up-to-date`, `shared`, `missing`, `refused`), the changes and why it failed or was refused.
  */
final case class CollectionUpdateRow(
    id: String,
    citekeys: List[String],
    state: String,
    changes: List[CollectionFieldChange],
    reason: Option[String]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
