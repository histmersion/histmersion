package com.histmersion.domain.media.imports

import com.histmersion.api.media.MediaProvider
import com.histmersion.config.MediaConfig

/** Histmersion's own media collections, by name only — the plugins of `plugins/media` implement them; the host keeps
  * what it says about one whose plugin is switched off, the names it records and stores their pictures under, the
  * allowlist each is judged by, the names `media import` also takes, and the names no other plugin may take.
  */
object BuiltinMedia:

  /** One first-party collection: its plugin, the name `media import` is asked for, what a refusal calls it, the name
    * its checks and reports are recorded under, the provider its pictures are stored under, and the allowlist it is
    * judged by (its own `media.<provider>.licences`).
    */
  final case class Entry(
      plugin: Entry.Plugin,
      name: Entry.Name,
      title: Entry.Title,
      check: Entry.Check,
      provider: Entry.Provider,
      licences: MediaConfig => String
  )

  object Entry:
    type Plugin = Plugin.T
    object Plugin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Check = Check.T
    object Check extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provider = Provider.T
    object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** In the order `media import`'s list names them. */
  val entries: List[Entry] = List(
    Entry(
      "media-wikidata",
      "wikidata",
      "Wikidata pictures",
      MediaProvider.Commons,
      MediaProvider.Commons,
      _.commons.licences
    ),
    Entry(
      "media-depicts",
      "depicts",
      "Commons depictions",
      MediaProvider.CommonsDepicts,
      MediaProvider.Commons,
      _.depicts.licences
    ),
    Entry("media-met", "met", "Met objects", MediaProvider.Met, MediaProvider.Met, _.met.licences),
    Entry(
      "media-cleveland",
      "cleveland",
      "Cleveland Museum of Art objects",
      MediaProvider.Cleveland,
      MediaProvider.Cleveland,
      _.cleveland.licences
    ),
    Entry(
      "media-aic",
      "aic",
      "Art Institute of Chicago objects",
      MediaProvider.ArtInstitute,
      MediaProvider.ArtInstitute,
      _.aic.licences
    ),
    Entry(
      "media-loc",
      "loc",
      "Library of Congress maps",
      MediaProvider.LibraryOfCongress,
      MediaProvider.LibraryOfCongress,
      _.loc.licences
    ),
    Entry(
      "media-pleiades",
      "pleiades",
      "Pleiades-linked pictures",
      MediaProvider.Pleiades,
      MediaProvider.Commons,
      _.pleiades.licences
    ),
    Entry(
      "media-opencontext",
      "opencontext",
      "Open Context media",
      MediaProvider.OpenContext,
      MediaProvider.OpenContext,
      _.openContext.licences
    ),
    Entry(
      "media-smithsonian",
      "smithsonian",
      "Smithsonian Open Access objects",
      MediaProvider.Smithsonian,
      MediaProvider.Smithsonian,
      _.smithsonian.licences
    ),
    Entry(
      "media-europeana",
      "europeana",
      "Europeana images",
      MediaProvider.Europeana,
      MediaProvider.Europeana,
      _.europeana.licences
    )
  )

  /** The collections that need a key of the user's own, OFF by default: what each needs. */
  val keyed: Map[String, String] =
    Map("smithsonian" -> "an api.data.gov key", "europeana" -> "a Europeana personal API key")

  /** The plugin ids of the first-party collections. */
  val pluginIds: Set[String] = entries.map(_.plugin).toSet

  private val byName: Map[String, Entry] = entries.map(e => e.name -> e).toMap

  /** The first-party collection `name` names, if it is one. */
  def entry(name: String): Option[Entry] = byName.get(name)

  /** The other names `media import` takes for a built-in collection. */
  val aliases: Map[String, String] = Map(
    "si" -> "smithsonian",
    "smithsonian-open-access" -> "smithsonian",
    "commons" -> "wikidata",
    MediaProvider.Commons -> "wikidata",
    "depictions" -> "depicts",
    MediaProvider.CommonsDepicts -> "depicts",
    MediaProvider.Met -> "met",
    "cma" -> "cleveland",
    MediaProvider.Cleveland -> "cleveland",
    "artic" -> "aic",
    MediaProvider.ArtInstitute -> "aic",
    MediaProvider.LibraryOfCongress -> "loc",
    "open-context" -> "opencontext",
    "oc" -> "opencontext"
  )

  /** Names a plugin's collection may not take: the built-in imports, their aliases, and every provider the host stores
    * pictures under (a plugin's pictures would be taken for theirs).
    */
  val reserved: Set[String] = entries.map(_.name).toSet ++ aliases.keySet ++ MediaProvider.all ++
    Set(MediaProvider.CommonsDepicts, MediaProvider.Pleiades)

  /** Pure: the name an import request means — an alias resolved, blank = `wikidata`, else as given (lower case): the
    * registry decides whether it exists.
    */
  def canonical(asked: String): String =
    val s = asked.trim.toLowerCase(java.util.Locale.ROOT)
    if s.isEmpty then "wikidata" else aliases.getOrElse(s, s)

  /** Pure: the registered names in the order lists show them — the built-ins in their order, then the plugins'
    * collections in registration order.
    */
  def ordered(registered: List[String]): List[String] =
    val own = entries.map(_.name)
    own.filter(registered.contains) ++ registered.filterNot(own.contains)

  /** The name a collection's checks and report are recorded under: a first-party one's own, else its name. */
  def checkOf(name: String): String = entry(name).fold(name)(_.check)

  /** The provider a collection's pictures are stored under: a first-party one's own, else its name. */
  def providerOf(name: String): String = entry(name).fold(name)(_.provider)

  /** The allowlist a collection is judged by: a first-party one's own `media.<provider>.licences`, a plugin's
    * `media.collections` (its `per-collection` entry, else the default).
    */
  def licencesFor(name: String, cfg: MediaConfig): String =
    entry(name).fold(cfg.collections.licencesFor(name))(_.licences(cfg))
