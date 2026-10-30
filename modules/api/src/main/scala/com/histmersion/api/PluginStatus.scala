package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** @param state
  *   a [[PluginState]]'s wire name
  * @param provides
  *   what it registered, `<registry>:<id>` (`secret-vault:keepassxc`)
  * @param settings
  *   its effective block, secrets as references (empty when it was refused)
  * @param warnings
  *   what works but should be changed (a secret written as `value:`)
  * @param error
  *   why it failed or was refused
  */
final case class PluginStatus(
    id: String,
    version: String,
    spiVersion: Int,
    enabled: Boolean,
    state: String,
    provides: List[String] = Nil,
    settings: List[PluginSetting] = Nil,
    warnings: List[String] = Nil,
    error: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PluginStatus:
  given Schema[PluginStatus] = summon[KindlingsSchema[PluginStatus]].schema
