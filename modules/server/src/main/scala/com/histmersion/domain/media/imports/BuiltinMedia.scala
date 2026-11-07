package com.histmersion.domain.media.imports

import com.histmersion.api.media.MediaProvider
import com.histmersion.config.{MediaCollectionConfig, MediaConfig}
import com.histmersion.pluginhost.Registries

/** Histmersion's own media collections, by name only — the plugins of `plugins/media` implement them; the host keeps
  * what it says about one whose plugin is switched off, the names it records and stores their pictures under, the
  * allowlist each is judged by, the names `media import` also takes, and the names no other plugin may take.
  */
object BuiltinMedia:
  type Alias = Alias.T
  object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckName = CheckName.T
  object CheckName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryKey = Entry.Name
  val EntryKey: Entry.Name.type = Entry.Name
  type Licences = MediaCollectionConfig.Licences
  val Licences: MediaCollectionConfig.Licences.type = MediaCollectionConfig.Licences
  type Provider = ImportService.Provider
  val Provider: ImportService.Provider.type = ImportService.Provider
  type ReservedName = Entry.Name
  val ReservedName: Entry.Name.type = Entry.Name

  /** One first-party collection: its plugin, the name `media import` is asked for, what a refusal calls it, the name
    * its checks and reports are recorded under, the provider its pictures are stored under, and the allowlist it is
    * judged by (its own `media.<provider>.licences`).
    */
  type KeyedNeed = KeyedNeed.T
  object KeyedNeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryName = Entry.Name
  val EntryName: Entry.Name.type = Entry.Name
  type CanonicalResult = CanonicalResult.T
  object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalAsked = CanonicalAsked.T
  object CanonicalAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckOfName = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  val CheckOfName: _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName.type = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  type ProviderOfName = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  val ProviderOfName: _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName.type = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  type LicencesForName = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName
  val LicencesForName: _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName.type = _root_.com.histmersion.domain.media.imports.BuiltinMedia.EntryName

  final case class Entry(
      plugin: com.histmersion.util.plugins.Plugin.Id,
      name: Entry.Name,
      title: Entry.Title,
      check: Entry.Check,
      provider: Entry.Provider,
      licences: MediaConfig => Entry.LicencesResult
  )

  object Entry:
    type LicencesResult = com.histmersion.domain.media.imports.BuiltinMedia.Licences
    val LicencesResult: _root_.com.histmersion.domain.media.imports.BuiltinMedia.Licences.type = _root_.com.histmersion.domain.media.imports.BuiltinMedia.Licences

    type Name = Registries.MediaCollectionsKey
    val Name: Registries.MediaCollectionsKey.type = Registries.MediaCollectionsKey
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Check = Check.T
    object Check extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provider = ImportService.Provider
    val Provider: ImportService.Provider.type = ImportService.Provider

  /** In the order `media import`'s list names them. */
  val entries: List[Entry] = List(
    Entry(
      com.histmersion.util.plugins.Plugin.Id("media-wikidata"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("wikidata"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Wikidata pictures"),
      Entry.Check(MediaProvider.Commons),
      Entry.Provider(MediaProvider.Commons),
      c => Licences(c.commons.licences.value)
    ),
    Entry(
      com.histmersion.util.plugins.Plugin.Id("media-depicts"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("depicts"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Commons depictions"),
      Entry.Check(MediaProvider.CommonsDepicts),
      Entry.Provider(MediaProvider.Commons),
      c => Licences(c.depicts.licences.value)
    ),
    Entry(com.histmersion.util.plugins.Plugin.Id("media-met"), com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("met"), com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Met objects"), Entry.Check(MediaProvider.Met), Entry.Provider(MediaProvider.Met), c => Licences(c.met.licences.value)),
    Entry(
      com.histmersion.util.plugins.Plugin.Id("media-cleveland"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("cleveland"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Cleveland Museum of Art objects"),
      Entry.Check(MediaProvider.Cleveland),
      Entry.Provider(MediaProvider.Cleveland),
      c => Licences(c.cleveland.licences.value)
    ),
    Entry(
      com.histmersion.util.plugins.Plugin.Id("media-aic"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("aic"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Art Institute of Chicago objects"),
      Entry.Check(MediaProvider.ArtInstitute),
      Entry.Provider(MediaProvider.ArtInstitute),
      c => Licences(c.aic.licences.value)
    ),
    Entry(
      com.histmersion.util.plugins.Plugin.Id("media-loc"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("loc"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Library of Congress maps"),
      Entry.Check(MediaProvider.LibraryOfCongress),
      Entry.Provider(MediaProvider.LibraryOfCongress),
      c => Licences(c.loc.licences.value)
    ),
    Entry(
      com.histmersion.util.plugins.Plugin.Id("media-pleiades"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("pleiades"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Pleiades-linked pictures"),
      Entry.Check(MediaProvider.Pleiades),
      Entry.Provider(MediaProvider.Commons),
      c => Licences(c.pleiades.licences.value)
    ),
    Entry(
      com.histmersion.util.plugins.Plugin.Id("media-opencontext"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("opencontext"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Open Context media"),
      Entry.Check(MediaProvider.OpenContext),
      Entry.Provider(MediaProvider.OpenContext),
      c => Licences(c.openContext.licences.value)
    ),
    Entry(
      com.histmersion.util.plugins.Plugin.Id("media-smithsonian"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("smithsonian"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Smithsonian Open Access objects"),
      Entry.Check(MediaProvider.Smithsonian),
      Entry.Provider(MediaProvider.Smithsonian),
      c => Licences(c.smithsonian.licences.value)
    ),
    Entry(
      com.histmersion.util.plugins.Plugin.Id("media-europeana"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Name("europeana"),
      com.histmersion.domain.media.imports.BuiltinMedia.Entry.Title("Europeana images"),
      Entry.Check(MediaProvider.Europeana),
      Entry.Provider(MediaProvider.Europeana),
      c => Licences(c.europeana.licences.value)
    )
  )

  /** The collections that need a key of the user's own, OFF by default: what each needs. */
  val keyed: Map[com.histmersion.domain.media.imports.BuiltinMedia.EntryKey, com.histmersion.domain.media.imports.BuiltinMedia.KeyedNeed] =
    Map(EntryKey("smithsonian") -> KeyedNeed("an api.data.gov key"), EntryKey("europeana") -> KeyedNeed("a Europeana personal API key"))

  /** The plugin ids of the first-party collections. */
  val pluginIds: Set[com.histmersion.util.plugins.Plugin.Id] = entries.map(_.plugin).toSet

  private val byName: Map[com.histmersion.domain.media.imports.BuiltinMedia.EntryName, Entry] = entries.map(e => e.name -> e).toMap

  /** The first-party collection `name` names, if it is one. */
  def entry(name: com.histmersion.domain.media.imports.BuiltinMedia.EntryName): Option[Entry] = byName.get(name)

  /** The other names `media import` takes for a built-in collection. */
  val aliases: Map[com.histmersion.domain.media.imports.BuiltinMedia.Alias, com.histmersion.domain.media.imports.BuiltinMedia.CanonicalResult] = Map(
    Alias("si") -> CanonicalResult("smithsonian"),
    Alias("smithsonian-open-access") -> CanonicalResult("smithsonian"),
    Alias("commons") -> CanonicalResult("wikidata"),
    Alias(MediaProvider.Commons) -> CanonicalResult("wikidata"),
    Alias("depictions") -> CanonicalResult("depicts"),
    Alias(MediaProvider.CommonsDepicts) -> CanonicalResult("depicts"),
    Alias(MediaProvider.Met) -> CanonicalResult("met"),
    Alias("cma") -> CanonicalResult("cleveland"),
    Alias(MediaProvider.Cleveland) -> CanonicalResult("cleveland"),
    Alias("artic") -> CanonicalResult("aic"),
    Alias(MediaProvider.ArtInstitute) -> CanonicalResult("aic"),
    Alias(MediaProvider.LibraryOfCongress) -> CanonicalResult("loc"),
    Alias("open-context") -> CanonicalResult("opencontext"),
    Alias("oc") -> CanonicalResult("opencontext")
  )

  /** Names a plugin's collection may not take: the built-in imports, their aliases, and every provider the host stores
    * pictures under (a plugin's pictures would be taken for theirs).
    */
  val reserved: Set[com.histmersion.domain.media.imports.BuiltinMedia.ReservedName] = entries.map(e => ReservedName(e.name.value)).toSet ++ aliases.keySet.map(a => ReservedName(a.value)) ++ MediaProvider.all.map(p => ReservedName(p.value)) ++
    Set(MediaProvider.CommonsDepicts, MediaProvider.Pleiades).map(ReservedName(_))

  /** Pure: the name an import request means — an alias resolved, blank = `wikidata`, else as given (lower case): the
    * registry decides whether it exists.
    */
  def canonical(asked: com.histmersion.domain.media.imports.BuiltinMedia.CanonicalAsked): com.histmersion.domain.media.imports.BuiltinMedia.CanonicalResult =
    val s = asked.value.trim.toLowerCase(java.util.Locale.ROOT)
    if s.isEmpty then com.histmersion.domain.media.imports.BuiltinMedia.CanonicalResult("wikidata") else aliases.getOrElse(Alias(s), CanonicalResult(s))

  /** Pure: the registered names in the order lists show them — the built-ins in their order, then the plugins'
    * collections in registration order.
    */
  def ordered(registered: List[Registries.MediaCollectionsKey]): List[Registries.MediaCollectionsKey] =
    val own = entries.map(_.name)
    own.filter(registered.contains) ++ registered.filterNot(own.contains)

  /** The name a collection's checks and report are recorded under: a first-party one's own, else its name. */
  def checkOf(name: com.histmersion.domain.media.imports.BuiltinMedia.CheckOfName): com.histmersion.domain.media.imports.BuiltinMedia.CheckName = entry(name).fold(CheckName(name.value))(e => CheckName(e.check.value))

  /** The provider a collection's pictures are stored under: a first-party one's own, else its name. */
  def providerOf(name: com.histmersion.domain.media.imports.BuiltinMedia.ProviderOfName): com.histmersion.domain.media.imports.BuiltinMedia.Provider = entry(name).fold(Provider(name.value))(e => Provider(e.provider.value))

  /** The allowlist a collection is judged by: a first-party one's own `media.<provider>.licences`, a plugin's
    * `media.collections` (its `per-collection` entry, else the default).
    */
  def licencesFor(name: com.histmersion.domain.media.imports.BuiltinMedia.LicencesForName, cfg: MediaConfig): com.histmersion.domain.media.imports.BuiltinMedia.Licences =
    entry(name).fold(Licences(cfg.collections.licencesFor(com.histmersion.config.MediaPluginCollectionsConfig.PerCollectionKey(name.value)).value))(_.licences(cfg))
