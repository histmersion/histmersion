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

  /** Pure: the registered collection `asked` names (an alias resolved), or why none: unknown, or its plugin switched
    * off.
    */
  type LookupResultPart1 = LookupResultPart1.T
  object LookupResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LookupResultPart2Part1 = LookupResultPart2Part1.T
  object LookupResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LookupAsked = LookupAsked.T
  object LookupAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LookupCollectionsPart1 = LookupCollectionsPart1.T
  object LookupCollectionsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveAsked = _root_.com.histmersion.domain.media.imports.MediaImporters.LookupAsked
  val ResolveAsked: _root_.com.histmersion.domain.media.imports.MediaImporters.LookupAsked.type = _root_.com.histmersion.domain.media.imports.MediaImporters.LookupAsked
  type ResolveCollectionsPart1 = _root_.com.histmersion.domain.media.imports.MediaImporters.LookupCollectionsPart1
  val ResolveCollectionsPart1: _root_.com.histmersion.domain.media.imports.MediaImporters.LookupCollectionsPart1.type = _root_.com.histmersion.domain.media.imports.MediaImporters.LookupCollectionsPart1
  type InfosCollectionsPart1 = InfosCollectionsPart1.T
  object InfosCollectionsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfosPluginOfArg1 = InfosPluginOfArg1.T
  object InfosPluginOfArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfosPluginOfResult = InfosPluginOfResult.T
  object InfosPluginOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfosAllowForArg1 = InfosAllowForArg1.T
  object InfosAllowForArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfosAllowForResult = InfosAllowForResult.T
  object InfosAllowForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def lookup(asked: com.histmersion.domain.media.imports.MediaImporters.LookupAsked, collections: ListMap[com.histmersion.domain.media.imports.MediaImporters.LookupCollectionsPart1, MediaCollection]): Either[com.histmersion.domain.media.imports.MediaImporters.LookupResultPart1, (com.histmersion.domain.media.imports.MediaImporters.LookupResultPart2Part1, MediaCollection)] =
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
  ): Either[com.histmersion.domain.media.imports.MediaImporters.ResolveResultPart1, MediaImporter] =
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
      collections: ListMap[com.histmersion.domain.media.imports.MediaImporters.InfosCollectionsPart1, MediaCollection],
      pluginOf: com.histmersion.domain.media.imports.MediaImporters.InfosPluginOfArg1 => com.histmersion.domain.media.imports.MediaImporters.InfosPluginOfResult,
      allowFor: com.histmersion.domain.media.imports.MediaImporters.InfosAllowForArg1 => com.histmersion.domain.media.imports.MediaImporters.InfosAllowForResult
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
