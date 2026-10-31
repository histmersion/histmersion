package com.histmersion.server.media

import com.histmersion.pluginhost.{HttpTransport, PluginMedia}

import com.histmersion.api.MediaProviderInfo
import com.histmersion.plugin.*
import com.histmersion.server.{ConverterClient, Deps}

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal

/** The collections `media import` can run, from the plugins' registry of the calling thread's generation: the
  * first-party ones and a third party's alike.
  */
object MediaImporters:

  /** Pure: the registered collection `asked` names (an alias resolved), or why none: unknown, or its plugin switched
    * off.
    */
  def lookup(asked: String, collections: ListMap[String, MediaCollection]): Either[String, (String, MediaCollection)] =
    val name = BuiltinMedia.canonical(asked)
    collections
      .get(name)
      .map(name -> _)
      .toRight(BuiltinMedia.entry(name) match
        case Some(e) if BuiltinMedia.keyed.contains(name) =>
          s"${e.title} ($name) is switched off — it is off by default and needs ${BuiltinMedia.keyed(name)}: set " +
            s"histmersion.plugins.${e.plugin}.enabled = true and its api-key (a secret reference)"
        case Some(e) => s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)"
        case None    =>
          s"unknown media provider '${asked.trim}' (one of: " +
            s"${BuiltinMedia.ordered(collections.keys.toList).mkString(", ")}; `histmersion media providers` lists them)")

  /** The import `asked` names, or why there is none: unknown, its plugin switched off, or the collection's own check
    * says no (its `media.<provider>.enabled` off, no key set). Every collection is run by the host's own import under
    * the allowlist the host keeps for it ([[BuiltinMedia.licencesFor]]).
    */
  def resolve(
      asked: String,
      collections: ListMap[String, MediaCollection],
      deps: Deps,
      http: HttpTransport,
      converter: ConverterClient
  ): Either[String, MediaImporter] =
    lookup(asked, collections).flatMap { (name, c) =>
      PluginMedia
        .guard(c.check)
        .map(_ => MediaImporter.of(name, c, PluginMedia.guard(Right(c.title)).getOrElse(name), deps, http, converter))
    }

  /** Every registered collection, as `histmersion media providers` lists it. `pluginOf` names the plugin that
    * registered a collection; `allowFor` the allowlist a third party's is judged by.
    */
  def infos(
      collections: ListMap[String, MediaCollection],
      pluginOf: String => String,
      allowFor: String => String
  ): List[MediaProviderInfo] =
    BuiltinMedia.ordered(collections.keys.toList).flatMap(n => collections.get(n).map(n -> _)).map { (n, c) =>
      // a reserved name is registered by its first-party plugin alone
      val own = BuiltinMedia.entry(n)
      val firstParty = own.isDefined
      MediaProviderInfo(
        n,
        safe(c.title).getOrElse(n),
        safe(MediaImporter.over(c.asks)).getOrElse("places"),
        safe(c.description).getOrElse(""),
        own.fold(pluginOf(n))(_.plugin),
        builtin = firstParty,
        if firstParty then "" else allowFor(n)
      )
    }

  private def safe[A](a: => A): Option[A] =
    try Option(a)
    catch case NonFatal(_) => None
