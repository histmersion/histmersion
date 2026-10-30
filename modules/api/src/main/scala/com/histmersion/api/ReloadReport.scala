package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What `histmersion config reload` (`POST /api/config/reload`, the MCP `config` tool, the file watcher) did
  * (docs/research-plugins.md §3.5, §3.6): the running server read its config file again and switched to it in place.
  *
  * @param published
  *   a new generation is live (false: nothing changed, or the reload failed)
  * @param generation
  *   the generation now live
  * @param applied
  *   the settings that changed and are in effect now
  * @param restartOnly
  *   the settings that changed but take effect only after a restart (the running value is kept, so what the server
  *   reports is what it does)
  * @param rebuilt
  *   the components built again for this config, in build order
  * @param plugins
  *   the plugins registered again (an unchanged plugin keeps its registration)
  * @param draining
  *   the components of an earlier generation still in use when the reload stopped waiting for them: they close as soon
  *   as their last user lets go
  * @param errors
  *   why nothing was published (the running config stays)
  * @param warnings
  *   the config file's notes (an unknown key)
  * @param file
  *   the file read
  */
final case class ReloadReport(
    published: Boolean,
    generation: Long,
    applied: List[String] = Nil,
    restartOnly: List[String] = Nil,
    rebuilt: List[String] = Nil,
    plugins: List[String] = Nil,
    draining: List[String] = Nil,
    errors: List[String] = Nil,
    warnings: List[String] = Nil,
    file: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def ok: Boolean = errors.isEmpty

object ReloadReport:
  given Schema[ReloadReport] = summon[KindlingsSchema[ReloadReport]].schema
