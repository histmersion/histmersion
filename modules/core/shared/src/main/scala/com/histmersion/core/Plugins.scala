package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What became of the plugins the server found (docs/research-plugins.md §2, §3): `/api/about`,
  * `/api/config`, `histmersion about` and the server's `--self-check` report. Secrets are never in it:
  * a setting that holds a secret shows its REFERENCE (`env:PORTAL_TOKEN`, `keepassxc:Portal#password`),
  * and a literal `value:` reference is masked. */

/** One effective setting of a plugin's block (the user's keys over the plugin's defaults). */
final case class PluginSetting(key: String, value: String) derives KindlingsCodecAsObject
object PluginSetting:
  given Schema[PluginSetting] = KindlingsSchema.derived[PluginSetting].schema

/** How a plugin ended up. */
enum PluginState:
  /** Enabled, decoded, its secrets resolved, `register` succeeded: its contributions are live. */
  case Registered
  /** Not enabled (`histmersion.plugins.<id>.enabled = false`, the default for first-party plugins). */
  case Disabled
  /** Enabled, but its block, its secrets or its `register` failed: it contributes nothing. */
  case Failed
  /** Not loaded at all: a foreign SPI version, an id used twice, an id that is no config key. */
  case Refused

  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object PluginState:
  def parse(s: String): Option[PluginState] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

/** @param state     a [[PluginState]]'s wire name
  * @param provides  what it registered, `<registry>:<id>` (`secret-vault:keepassxc`)
  * @param settings  its effective block, secrets as references (empty when it was refused)
  * @param warnings  what works but should be changed (a secret written as `value:`)
  * @param error     why it failed or was refused */
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
) derives KindlingsCodecAsObject
object PluginStatus:
  given Schema[PluginStatus] = KindlingsSchema.derived[PluginStatus].schema

/** Every plugin in this binary, in registration order, and what is wrong with the config around them
  * (a block no plugin claims, an `order` entry naming no plugin). `spiVersion` is the SPI this server
  * speaks. `set` is the user-added plugin set the binary was linked with (§7.3; `None`: first-party
  * plugins only, as a stock image). */
final case class PluginsReport(spiVersion: Int = 0, plugins: List[PluginStatus] = Nil, warnings: List[String] = Nil,
    set: Option[PluginSet] = None)
    derives KindlingsCodecAsObject:
  /** Every plugin that was enabled is registered, and none was refused. */
  def ok: Boolean = plugins.forall(p => p.state != PluginState.Failed.wire && p.state != PluginState.Refused.wire)
object PluginsReport:
  given Schema[PluginsReport] = KindlingsSchema.derived[PluginsReport].schema

/** The plugins as Markdown, like every other surface. Pure. */
object PluginsMarkdown:

  /** One line per plugin: `keepassxc 0.1.0 — registered (secret-vault:keepassxc)`. */
  def line(p: PluginStatus): String =
    val what = PluginState.parse(p.state) match
      case Some(PluginState.Registered) =>
        "registered" + (if p.provides.isEmpty then "" else p.provides.mkString(" (", ", ", ")"))
      case Some(PluginState.Failed)  => s"FAILED: ${p.error.getOrElse("no reason given")}"
      case Some(PluginState.Refused) => s"REFUSED: ${p.error.getOrElse("no reason given")}"
      case _                         => p.state
    s"${p.id} ${p.version} — $what"

  /** The `about` part: a summary line, then a line per plugin and per warning. */
  def summary(r: PluginsReport): List[String] =
    val enabled = r.plugins.count(_.enabled)
    val head = s"- plugins: ${Plural.count(r.plugins.size, "plugin")} in this binary · $enabled enabled" +
      (if r.ok then "" else " · PROBLEMS")
    head :: s"  - plugin set: ${PluginSet.describe(r.set)}" :: (r.plugins.map(p => s"  - ${line(p)}") ++
      r.plugins.flatMap(p => p.warnings.map(w => s"  - warning: $w")) ++
      r.warnings.map(w => s"  - warning: $w"))

  /** The server's `--self-check` report: the config file, every plugin with its effective settings
    * (secrets as references), and the verdict on the last line. */
  def selfCheck(file: ConfigFileInfo, r: PluginsReport): String =
    val plugins = r.plugins.flatMap { p =>
      s"- ${line(p)}" :: (p.settings.map(s => s"    ${s.key} = ${s.value}") ++ p.warnings.map(w => s"    warning: $w"))
    }
    val verdict = if r.ok then "self-check passed" else "self-check FAILED"
    (List(
      "# Histmersion self-check",
      "",
      s"- config file: ${ConfigFileInfo.describe(file)}",
      s"- plugin SPI: ${r.spiVersion}",
      s"- plugins: ${Plural.count(r.plugins.size, "plugin")} · ${r.plugins.count(_.enabled)} enabled",
      s"- plugin set: ${PluginSet.describe(r.set)}",
      s"- plugin-set digest: ${r.set.fold(PluginSet.EmptyDigest)(_.digest)}",
      ""
    ) ++ plugins ++ r.warnings.map(w => s"- warning: $w") ++ List("", verdict)).mkString("\n")
