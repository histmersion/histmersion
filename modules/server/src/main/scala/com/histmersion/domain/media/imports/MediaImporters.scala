package com.histmersion.domain.media.imports

import com.histmersion.pluginhost.{HttpTransport, PluginMedia}

import com.histmersion.config.MediaConfig
import com.histmersion.plugin.*
import com.histmersion.util.ocr.ConverterClient

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal
import com.histmersion.api.media.MediaProvider

/** The collections `media import` can run, from the plugins' registry of the calling thread's generation: the
  * first-party ones and a third party's alike.
  */
object MediaImporters:
  type AllowForText = AllowForText.T
  object AllowForText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionKey = CollectionKey.T
  object CollectionKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImporterKey = ImporterKey.T
  object ImporterKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: the registered collection `asked` names (an alias resolved), or why none: unknown, or its plugin switched
    * off.
    */
  type ResolveAsked = _root_.com.histmersion.domain.media.imports.MediaImporters.ImporterKey
  val ResolveAsked: _root_.com.histmersion.domain.media.imports.MediaImporters.ImporterKey.type = _root_.com.histmersion.domain.media.imports.MediaImporters.ImporterKey
  type ResolveCollectionsPart1 = _root_.com.histmersion.domain.media.imports.MediaImporters.CollectionKey
  val ResolveCollectionsPart1: _root_.com.histmersion.domain.media.imports.MediaImporters.CollectionKey.type = _root_.com.histmersion.domain.media.imports.MediaImporters.CollectionKey

  def lookup(asked: com.histmersion.domain.media.imports.MediaImporters.ImporterKey, collections: ListMap[com.histmersion.domain.media.imports.MediaImporters.CollectionKey, MediaCollection]): Either[com.histmersion.domain.media.imports.MediaImporters.ImporterKey, (com.histmersion.domain.media.imports.MediaImporters.Provider, MediaCollection)] =
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
      asked: com.histmersion.domain.media.imports.MediaImporters.ResolveAsked,
      collections: ListMap[com.histmersion.domain.media.imports.MediaImporters.ResolveCollectionsPart1, MediaCollection],
      imports: ImportService,
      store: MediaStore,
      cfg: () => MediaConfig,
      http: HttpTransport,
      converter: ConverterClient
  ): Either[com.histmersion.domain.media.imports.MediaImporters.ImporterKey, MediaImporter] =
    lookup(asked, collections).flatMap { (name, c) =>
      PluginMedia
        .guard(c.check)
        .map(_ =>
          MediaImporter.of(
            name,
            c,
            PluginMedia.guard(Right(c.title)).getOrElse(name),
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
      collections: ListMap[com.histmersion.domain.media.imports.MediaImporters.CollectionKey, MediaCollection],
      pluginOf: com.histmersion.domain.media.imports.MediaImporters.ImporterKey => com.histmersion.util.plugins.Plugin.Id,
      allowFor: com.histmersion.domain.media.imports.MediaImporters.ImporterKey => com.histmersion.domain.media.imports.MediaImporters.AllowForText
  ): List[MediaProvider.Info] =
    BuiltinMedia.ordered(collections.keys.toList).flatMap(n => collections.get(n).map(n -> _)).map { (n, c) =>
      // a reserved name is registered by its first-party plugin alone
      val own = BuiltinMedia.entry(n)
      val firstParty = own.isDefined
      MediaProvider.Info(
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
