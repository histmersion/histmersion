package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One provider `media import <name>` can run (`GET /api/media/providers`): the registered media collections of the
  * plugins in this server — the built-in ones and any a third-party plugin added.
  *
  * @param name
  *   what `media import` is asked for
  * @param title
  *   what its jobs are called
  * @param over
  *   what it asks about: `places`, `events`, `places and events`
  * @param description
  *   what it brings and on what terms
  * @param plugin
  *   the plugin that registered it (`histmersion.plugins.<plugin>.enabled` switches it)
  * @param builtin
  *   Histmersion's own provider (its settings are `media.<provider>.*`)
  * @param licences
  *   the licence families its pictures may be stored under (a plugin's: the host's `media.collections` allowlist for
  *   it)
  */
final case class MediaProviderInfo(
    name: String,
    title: String,
    over: String,
    description: String,
    plugin: String,
    builtin: Boolean,
    licences: String
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MediaProviderInfo:
  given Schema[MediaProviderInfo] = summon[KindlingsSchema[MediaProviderInfo]].schema
