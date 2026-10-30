package com.histmersion.server.media

import com.histmersion.pluginhost.{HttpTransport, PluginMedia}

import com.histmersion.domain.{MediaLicence, MediaMatch, MediaRecord, MediaRights as CoreRights}
import com.histmersion.api.MediaProviderInfo
import com.histmersion.plugin.*
import com.histmersion.server.{ConverterClient, Deps}

import com.histmersion.plugin.PluginConfig.given

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal

/** The providers `media import` can run, from the plugins' registry of the calling thread's generation. */
object MediaImporters:

  /** Pure: the registered collection `asked` names (an alias resolved), or why none: unknown, or its plugin switched
    * off.
    */
  def lookup(asked: String, collections: ListMap[String, MediaCollection]): Either[String, (String, MediaCollection)] =
    val name = BuiltinMedia.canonical(asked)
    collections
      .get(name)
      .map(name -> _)
      .toRight(BuiltinMedia.entries.find(_.name == name) match
        case Some(e) => s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)"
        case None if BuiltinMedia.keyed.exists(_.name == name) =>
          val m = BuiltinMedia.keyed.find(_.name == name).get
          s"${m.title} ($name) is switched off — it is off by default and needs ${m.needs}: set " +
            s"histmersion.plugins.${m.plugin}.enabled = true and its api-key (a secret reference)"
        case None =>
          s"unknown media provider '${asked.trim}' (one of: " +
            s"${BuiltinMedia.ordered(collections.keys.toList).mkString(", ")}; `histmersion media providers` lists them)")

  /** The import `asked` names, or why there is none: unknown, its plugin switched off, or (a built-in) its own
    * `media.<provider>.enabled` off. A plugin's collection is run by [[PluginCollection]] under the host's allowlist
    * for it (`media.collections`).
    */
  def resolve(
      asked: String,
      collections: ListMap[String, MediaCollection],
      deps: Deps,
      http: HttpTransport,
      converter: ConverterClient
  ): Either[String, MediaImporter] =
    lookup(asked, collections).flatMap {
      case (_, h: HostMediaCollection) => h.importer(deps, http, converter)
      case (name, c)                   =>
        val cfg = deps.cfg.media
        PluginMedia
          .guard(Right(PluginCollection(name, c, cfg.collections.licencesFor(name))))
          .flatMap(col =>
            MediaImporter.collection(
              col,
              PluginMedia.guard(Right(c.title)).getOrElse(name),
              s"plugin collection $name",
              deps.ds,
              cfg,
              http,
              converter,
              DbMediaStore(deps.ds)
            )
          )
    }

  /** Every registered provider, as `histmersion media providers` lists it. `pluginOf` names the plugin that registered
    * a collection.
    */
  def infos(
      collections: ListMap[String, MediaCollection],
      pluginOf: String => String,
      allowFor: String => String
  ): List[MediaProviderInfo] =
    BuiltinMedia.ordered(collections.keys.toList).flatMap(n => collections.get(n).map(n -> _)).map {
      case (n, e: BuiltinMedia.Entry) =>
        MediaProviderInfo(n, e.title, e.over, e.description, e.plugin, builtin = true, "")
      case (n, k: KeyedEntry) =>
        MediaProviderInfo(
          n,
          k.title,
          k.over,
          k.description + (if k.key.isEmpty then " — NO KEY SET: not asked" else ""),
          k.meta.plugin,
          builtin = true,
          ""
        )
      case (n, h: HostMediaCollection) =>
        MediaProviderInfo(n, h.title, h.over, h.description, pluginOf(n), builtin = true, "")
      case (n, c) =>
        MediaProviderInfo(
          n,
          safe(c.title).getOrElse(n),
          "places",
          safe(c.description).getOrElse(""),
          pluginOf(n),
          builtin = false,
          allowFor(n)
        )
    }

  private def safe[A](a: => A): Option[A] =
    try Option(a)
    catch case NonFatal(_) => None
