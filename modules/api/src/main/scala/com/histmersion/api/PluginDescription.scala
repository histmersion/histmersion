package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One plugin a jar provides, as `PluginTool describe` found it on the JVM: its info, what a registration with its
  * defaults (enabled, nothing else set) contributes, and the access its source fetchers declare (`open`,
  * `subscription`, `unauthorised`).
  *
  * @param error
  *   why it could not be instantiated or registered with its defaults (the access is then unknown; the plugin may still
  *   work once configured)
  */
final case class PluginDescription(
    id: String,
    version: String,
    spiVersion: Int,
    provides: List[String] = Nil,
    access: List[String] = Nil,
    error: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PluginDescription:
  given Schema[PluginDescription] = summon[KindlingsSchema[PluginDescription]].schema
