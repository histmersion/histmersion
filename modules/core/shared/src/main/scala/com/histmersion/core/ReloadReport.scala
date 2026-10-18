package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What `histmersion config reload` (`POST /api/config/reload`, the MCP `config` tool, the file watcher)
  * did (docs/research-plugins.md §3.5, §3.6): the running server read its config file again and
  * switched to it in place.
  *
  * @param published   a new generation is live (false: nothing changed, or the reload failed)
  * @param generation  the generation now live
  * @param applied     the settings that changed and are in effect now
  * @param restartOnly the settings that changed but take effect only after a restart (the running
  *                    value is kept, so what the server reports is what it does)
  * @param rebuilt     the components built again for this config, in build order
  * @param plugins     the plugins registered again (an unchanged plugin keeps its registration)
  * @param draining    the components of an earlier generation still in use when the reload stopped
  *                    waiting for them: they close as soon as their last user lets go
  * @param errors      why nothing was published (the running config stays)
  * @param warnings    the config file's notes (an unknown key)
  * @param file        the file read */
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
) derives KindlingsCodecAsObject:
  def ok: Boolean = errors.isEmpty

object ReloadReport:
  given Schema[ReloadReport] = KindlingsSchema.derived[ReloadReport].schema

/** The reload report as Markdown, every surface's. Pure. */
object ReloadMarkdown:
  def report(r: ReloadReport): String =
    def list(title: String, xs: List[String]): List[String] =
      if xs.isEmpty then Nil else s"**$title:**" :: xs.map(x => s"- $x")
    val head =
      if r.errors.nonEmpty then s"# Config reload failed — generation ${r.generation} stays"
      else if r.published then s"# Config reloaded — generation ${r.generation}"
      else s"# Config unchanged — generation ${r.generation}"
    val file = r.file.map(f => s"File: `$f`").toList
    val none =
      if r.published || r.errors.nonEmpty || r.restartOnly.nonEmpty then Nil
      else List("Nothing in the config differs from what is running.")
    (List(head, "") ++ file ++ none ++
      list("Errors (nothing was changed)", r.errors) ++
      list("Applied", r.applied) ++
      list("Changed, takes effect after a restart (not applied)", r.restartOnly) ++
      list("Rebuilt", r.rebuilt) ++
      list("Plugins registered again", r.plugins) ++
      list("Still in use from the previous generation (closes when released)", r.draining) ++
      list("Warnings", r.warnings)).mkString("\n")
