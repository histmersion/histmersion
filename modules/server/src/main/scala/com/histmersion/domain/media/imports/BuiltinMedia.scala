package com.histmersion.domain.media.imports

import com.histmersion.api.media.MediaProvider
import com.histmersion.config.MediaConfig

/** Histmersion's own media collections, by name only — the plugins of `plugins/media` implement them; the host keeps
  * what it says about one whose plugin is switched off, the names it records and stores their pictures under, the
  * allowlist each is judged by, the names `media import` also takes, and the names no other plugin may take.
  */
object BuiltinMedia:
  type Alias = Alias.T
  object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckName = CheckName.T
  object CheckName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryKey = EntryKey.T
  object EntryKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Licences = Licences.T
  object Licences extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReservedName = ReservedName.T
  object ReservedName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One first-party collection: its plugin, the name `media import` is asked for, what a refusal calls it, the name
    * its checks and reports are recorded under, the provider its pictures are stored under, and the allowlist it is
    * judged by (its own `media.<provider>.licences`).
    */
  type KeyedPart2 = KeyedPart2.T
  object KeyedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByNamePart1 = ByNamePart1.T
  object ByNamePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryName = EntryName.T
  object EntryName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasesPart2 = AliasesPart2.T
  object AliasesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalResult = CanonicalResult.T
  object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalAsked = CanonicalAsked.T
  object CanonicalAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderedResult = OrderedResult.T
  object OrderedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderedRegistered = OrderedRegistered.T
  object OrderedRegistered extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckOfName = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  val CheckOfName: _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName.type = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  type ProviderOfName = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  val ProviderOfName: _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName.type = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  type LicencesForName = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  val LicencesForName: _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName.type = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName

  final case class Entry(
      plugin: Entry.Plugin,
      name: Entry.Name,
      title: Entry.Title,
      check: Entry.Check,
      provider: Entry.Provider,
      licences: MediaConfig => Entry.LicencesResult
  )

  object Entry:
    type LicencesResult = com.histmersion.domain.media.imports.BuiltinMedia.Licences
    val LicencesResult: _root_.com.histmersion.domain.media.imports.BuiltinMedia.Licences.type = _root_.com.histmersion.domain.media.imports.BuiltinMedia.Licences

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
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-wikidata"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("wikidata"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Wikidata pictures"),
      MediaProvider.Commons,
      MediaProvider.Commons,
      _.commons.licences
    ),
    Entry(
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-depicts"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("depicts"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Commons depictions"),
      MediaProvider.CommonsDepicts,
      MediaProvider.Commons,
      _.depicts.licences
    ),
    Entry(com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-met"), com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("met"), com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Met objects"), MediaProvider.Met, MediaProvider.Met, _.met.licences),
    Entry(
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-cleveland"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("cleveland"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Cleveland Museum of Art objects"),
      MediaProvider.Cleveland,
      MediaProvider.Cleveland,
      _.cleveland.licences
    ),
    Entry(
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-aic"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("aic"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Art Institute of Chicago objects"),
      MediaProvider.ArtInstitute,
      MediaProvider.ArtInstitute,
      _.aic.licences
    ),
    Entry(
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-loc"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("loc"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Library of Congress maps"),
      MediaProvider.LibraryOfCongress,
      MediaProvider.LibraryOfCongress,
      _.loc.licences
    ),
    Entry(
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-pleiades"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("pleiades"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Pleiades-linked pictures"),
      MediaProvider.Pleiades,
      MediaProvider.Commons,
      _.pleiades.licences
    ),
    Entry(
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-opencontext"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("opencontext"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Open Context media"),
      MediaProvider.OpenContext,
      MediaProvider.OpenContext,
      _.openContext.licences
    ),
    Entry(
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-smithsonian"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("smithsonian"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Smithsonian Open Access objects"),
      MediaProvider.Smithsonian,
      MediaProvider.Smithsonian,
      _.smithsonian.licences
    ),
    Entry(
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Plugin("media-europeana"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("europeana"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Europeana images"),
      MediaProvider.Europeana,
      MediaProvider.Europeana,
      _.europeana.licences
    )
  )

  /** The collections that need a key of the user's own, OFF by default: what each needs. */
  val keyed: Map[com.histmersion.domain.media.imports.BuiltinMedia.EntryKey, com.histmersion.domain.media.imports.BuiltinMedia.KeyedPart2] =
    Map("smithsonian" -> "an api.data.gov key", "europeana" -> "a Europeana personal API key")

  /** The plugin ids of the first-party collections. */
  val pluginIds: Set[com.histmersion.util.plugins.Plugin.Id] = entries.map(_.plugin).toSet

  private val byName: Map[com.histmersion.domain.media.imports.BuiltinMedia.ByNamePart1, Entry] = entries.map(e => e.name -> e).toMap

  /** The first-party collection `name` names, if it is one. */
  def entry(name: com.histmersion.domain.media.imports.BuiltinMedia.EntryName): Option[Entry] = byName.get(name)

  /** The other names `media import` takes for a built-in collection. */
  val aliases: Map[com.histmersion.domain.media.imports.BuiltinMedia.Alias, com.histmersion.domain.media.imports.BuiltinMedia.AliasesPart2] = Map(
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
  val reserved: Set[com.histmersion.domain.media.imports.BuiltinMedia.ReservedName] = entries.map(_.name).toSet ++ aliases.keySet ++ MediaProvider.all ++
    Set(MediaProvider.CommonsDepicts, MediaProvider.Pleiades)

  /** Pure: the name an import request means — an alias resolved, blank = `wikidata`, else as given (lower case): the
    * registry decides whether it exists.
    */
  def canonical(asked: com.histmersion.domain.media.imports.BuiltinMedia.CanonicalAsked): com.histmersion.domain.media.imports.BuiltinMedia.CanonicalResult =
    val s = asked.trim.toLowerCase(java.util.Locale.ROOT)
    if s.isEmpty then com.histmersion.domain.media.imports.BuiltinMedia.CanonicalResult("wikidata") else aliases.getOrElse(s, s)

  /** Pure: the registered names in the order lists show them — the built-ins in their order, then the plugins'
    * collections in registration order.
    */
  def ordered(registered: List[com.histmersion.domain.media.imports.BuiltinMedia.OrderedRegistered]): List[com.histmersion.domain.media.imports.BuiltinMedia.OrderedResult] =
    val own = entries.map(_.name)
    own.filter(registered.contains) ++ registered.filterNot(own.contains)

  /** The name a collection's checks and report are recorded under: a first-party one's own, else its name. */
  def checkOf(name: com.histmersion.domain.media.imports.BuiltinMedia.CheckOfName): com.histmersion.domain.media.imports.BuiltinMedia.CheckName = entry(name).fold(name)(_.check)

  /** The provider a collection's pictures are stored under: a first-party one's own, else its name. */
  def providerOf(name: com.histmersion.domain.media.imports.BuiltinMedia.ProviderOfName): com.histmersion.domain.media.imports.BuiltinMedia.Provider = entry(name).fold(name)(_.provider)

  /** The allowlist a collection is judged by: a first-party one's own `media.<provider>.licences`, a plugin's
    * `media.collections` (its `per-collection` entry, else the default).
    */
  def licencesFor(name: com.histmersion.domain.media.imports.BuiltinMedia.LicencesForName, cfg: MediaConfig): com.histmersion.domain.media.imports.BuiltinMedia.Licences =
    entry(name).fold(cfg.collections.licencesFor(name))(_.licences(cfg))
