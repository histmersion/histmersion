package com.histmersion.server.media

import com.histmersion.pluginhost.HttpTransport

import com.histmersion.domain.{MediaLicence, MediaMatch, MediaRecord, MediaRights as CoreRights}
import com.histmersion.plugin.*
import com.histmersion.server.{ConverterClient, Deps}

import com.histmersion.plugin.PluginConfig.given

/** A key-gated built-in collection as registered: its key, resolved from its secret reference when the plugin was
  * built, or none. Without one, its import is refused with what to set — the API is not asked.
  */
final class KeyedEntry(val meta: KeyedMeta, private[server] val key: Option[String]) extends HostMediaCollection:
  def name: String = meta.name
  val title: String = meta.title
  def over: String = "places"
  val role: String = MediaCollection.Object
  override val description: String = meta.description
  override def toString: String = s"KeyedEntry(${meta.name}, key ${if key.isDefined then "set" else "missing"})"
  def importer(deps: Deps, http: HttpTransport, converter: ConverterClient): Either[String, MediaImporter] =
    val cfg = deps.cfg.media
    KeyedEntry
      .check(meta, key, cfg)
      .flatMap(k =>
        MediaImporter.collection(
          meta.collection(cfg, k),
          meta.title,
          meta.settings,
          deps.ds,
          cfg,
          http,
          converter,
          DbMediaStore(deps.ds)
        )
      )

object KeyedEntry:
  /** Pure: the key an import of `meta` runs with, or why it does not run — no key set, or its own
    * `media.<provider>.enabled` off.
    */
  def check(meta: KeyedMeta, key: Option[String], cfg: com.histmersion.config.MediaConfig): Either[String, String] =
    if !meta.enabled(cfg) then Left(s"${meta.title} is switched off (${meta.settings}.enabled = false)")
    else
      key.toRight(
        s"${meta.title} needs ${meta.needs}: set histmersion.plugins.${meta.plugin}.api-key to a secret " +
          "reference (\"env:<NAME>\" or a vault's, e.g. \"keepassxc:<entry>#password\"); it was not asked"
      )
