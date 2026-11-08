package com.histmersion
package domain.media.imports

import com.histmersion.pluginhost.{HttpTransport, PluginMedia, Registries}

import com.histmersion.config.MediaConfig
import com.histmersion.plugin.*
import com.histmersion.util.ocr.ConverterClient

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal
import com.histmersion.api.media.MediaProvider
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Title
import com.histmersion.config.MediaPluginCollectionsConfig

/** The collections `media import` can run, from the plugins' registry of the calling thread's generation: the
  * first-party ones and a third party's alike.
  */
object MediaImporters:
  type ImporterKey = ImporterKey.T
  object ImporterKey extends Newtype[String, Pure]
  type Refusal = Refusal.T
  object Refusal extends Newtype[String, Pure]

  /** Pure: the registered collection `asked` names (an alias resolved), or why none: unknown, or its plugin switched
    * off.
    */
  def lookup(asked: ImporterKey, collections: ListMap[Registries.MediaCollectionsKey, MediaCollection]): Either[Refusal, (MediaImporter.ImporterName, MediaCollection)] =
    val name = BuiltinMedia.canonical(BuiltinMedia.CanonicalAsked(asked.value)).value
    collections
      .get(Registries.MediaCollectionsKey(name))
      .map(MediaImporter.ImporterName(name) -> _)
      .toRight(Refusal(BuiltinMedia.entry(BuiltinMedia.EntryName(name)) match
        case Some(e) if BuiltinMedia.keyed.contains(BuiltinMedia.EntryKey(name)) =>
          s"${e.title} ($name) is switched off — it is off by default and needs ${BuiltinMedia.keyed(BuiltinMedia.EntryKey(name))}: set " +
            s"histmersion.plugins.${e.plugin}.enabled = true and its api-key (a secret reference)"
        case Some(e) => s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)"
        case None    =>
          s"unknown media provider '${asked.value.trim}' (one of: " +
            s"${ordered(collections.keys.toList).map(_.value).mkString(", ")}; `histmersion media providers` lists them)"))

  /** The import `asked` names, or why there is none: unknown, its plugin switched off, or the collection's own check
    * says no (its `media.<provider>.enabled` off, no key set). Every collection is run by the host's own import under
    * the allowlist the host keeps for it ([[BuiltinMedia.licencesFor]]).
    */
  def resolve(
      asked: ImporterKey,
      collections: ListMap[Registries.MediaCollectionsKey, MediaCollection],
      imports: ImportService,
      store: MediaStore,
      cfg: () => MediaConfig,
      http: HttpTransport,
      converter: ConverterClient
  ): Either[Refusal, MediaImporter] =
    lookup(asked, collections).flatMap { (name, c) =>
      PluginMedia
        .guard(c.check)
        .left
        .map(why => Refusal(why.value))
        .map(_ =>
          MediaImporter.of(
            name,
            c,
            PluginMedia.guard(Right(c.title)).fold(_ => Title(name.value), t => Title(t.value)),
            imports,
            store,
            cfg,
            http,
            converter
          )
        )
    }

  /** Every registered collection, as `histmersion media providers` lists it. `pluginOf` names the plugin that
    * registered a collection; `allowFor` the allowlist a third party's is judged by.
    */
  def infos(
      collections: ListMap[Registries.MediaCollectionsKey, MediaCollection],
      pluginOf: Registries.MediaCollectionsKey => Plugin.Info.Id,
      allowFor: Registries.MediaCollectionsKey => MediaPluginCollectionsConfig.Licences
  ): List[MediaProvider.Info] =
    ordered(collections.keys.toList).flatMap(n => collections.get(n).map(n -> _)).map { (n, c) =>
      // a reserved name is registered by its first-party plugin alone
      val own = BuiltinMedia.entry(BuiltinMedia.EntryName(n.value))
      val firstParty = own.isDefined
      MediaProvider.Info(
        com.histmersion.api.media.MediaItem.Provider(n.value),
        MediaProvider.Info.Title(safe(c.title).map(_.value).getOrElse(n.value)),
        MediaProvider.Info.Over(safe(MediaImporter.over(c.asks)).map(_.value).getOrElse("places")),
        MediaProvider.Info.Description(safe(c.description).map(_.value).getOrElse("")),
        MediaProvider.Info.Plugin(own.fold(pluginOf(n).value)(_.plugin.value)),
        builtin = MediaProvider.Info.Builtin(firstParty),
        MediaProvider.Info.Licences(if firstParty then "" else allowFor(n).value)
      )
    }

  private def ordered(registered: List[Registries.MediaCollectionsKey]): List[Registries.MediaCollectionsKey] =
    BuiltinMedia.ordered(registered)

  private def safe[A](a: => A): Option[A] =
    try Option(a)
    catch case NonFatal(_) => None
