package com.histmersion.server.media

import com.histmersion.pluginhost.{HostOwned, HttpTransport, Polite, PluginMedia, SpiValues}

import com.histmersion.config.MediaCollectionConfig
import com.histmersion.domain.{MediaLicence, MediaMatch, MediaRecord, MediaRights as CoreRights}
import com.histmersion.api.{MediaProvider, MediaProviderInfo}
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.plugin.*
import com.histmersion.server.{ConverterClient, Deps}
import com.histmersion.server.acquire.fetchers.{Builtin, BuiltinWith}

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.plugin.PluginConfig.given

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal

/** A media collection that is Histmersion's OWN code, registered through the plugin host like any other
  * (docs/research-plugins.md §6, D2) but run by the host's own import: the Wikidata/Commons runs and
  * the museum, map and excavation collections with their `media.<provider>.*` settings, unchanged.
  * The SPI's search is never called for it. */
trait HostMediaCollection extends MediaCollection, HostOwned:
  /** The name `media import` is asked for (`met`). */
  def name: String
  /** What it asks about: `places`, `events`, `places and events`. */
  def over: String
  /** The import, built over this server's config — or why it cannot run (`media.<provider>.enabled`). */
  def importer(deps: Deps, http: HttpTransport, converter: ConverterClient): Either[String, MediaImporter]
  def search(place: MediaPlace, http: PluginHttp): Either[String, List[Either[String, MediaItem]]] =
    Left(s"$name is Histmersion's own import; it is run by the host, not searched through the SPI")

/** Today's media providers as BUILT-IN plugins: in every binary, each enabled by default and switched off
  * by `histmersion.plugins.<plugin>.enabled = false`. Their own settings stay where they were
  * (`media.<provider>.*`, read at each import, so a change applies without rebuilding a plugin); a
  * collection whose `media.<provider>.enabled` is false is still refused at the import, as before. */
object BuiltinMedia:

  /** One built-in provider: its plugin, its name, and what the CLI's list says about it. */
  final case class Entry(plugin: String, name: String, title: String, over: String, role: String,
      override val description: String)
      extends HostMediaCollection:
    def importer(deps: Deps, http: HttpTransport, converter: ConverterClient) = MediaImporter.of(name, deps, http, converter)

  private val museum = " — Open Access objects only (as the record itself says), tied to a place on the map " +
    "when the object's most specific place field names it (and no other) and its dates overlap the place's; " +
    "--dry-run lists the matches with their reasons"

  /** In the order `media import`'s list names them. */
  val entries: List[Entry] = List(
    Entry("media-wikidata", MediaImporter.Wikidata, "Wikidata pictures", "places and events", "image",
      "Pictures of places and events from their Wikidata items (image, locator map, plan, aerial view, route map), " +
        "licensed on Wikimedia Commons; a job asks about every place on the map not asked yet (one per Wikidata " +
        "item, most-cited sites first), batch by batch, and resumes where it stopped"),
    Entry("media-depicts", MediaImporter.Depicts, "Commons depictions", "events", "depiction",
      "Pictures that depict events: Wikimedia Commons files whose structured data says P180 'depicts' the event's " +
        "Wikidata item, and the images of the works its item lists as P1299 'depicted by'; licence read from Commons"),
    Entry("media-met", MediaImporter.Met, "Met objects", "places", MediaCollection.Object,
      "The Metropolitan Museum of Art (CC0, isPublicDomain)" + museum),
    Entry("media-cleveland", MediaImporter.Cleveland, "Cleveland Museum of Art objects", "places", MediaCollection.Object,
      "The Cleveland Museum of Art (CC0, share_license_status)" + museum),
    Entry("media-aic", MediaImporter.Aic, "Art Institute of Chicago objects", "places", MediaCollection.Object,
      "The Art Institute of Chicago (CC0, is_public_domain; IIIF images)" + museum),
    Entry("media-loc", MediaImporter.Loc, "Library of Congress maps", "places", MediaCollection.Map,
      "Historical maps from the Library of Congress, Geography and Map Division: public domain only (rights " +
        "'No known restrictions' and dated by media.loc.max-year), linked as role map to the places their " +
        "locations name"),
    Entry("media-pleiades", MediaImporter.Pleiades, "Pleiades-linked pictures", "places", MediaCollection.Image,
      "Wikimedia Commons files that Pleiades place records link, for the places on the map that carry a Pleiades " +
        "id (matched by that id, no name matching); licence read from Commons; links to Flickr (needs a key), " +
        "Commons categories and other sites are counted, not taken"),
    Entry("media-opencontext", MediaImporter.OpenContext, "Open Context media", "places", MediaCollection.Object,
      "Open Context's excavation media (photographs, drawings), each record under its own Creative Commons licence " +
        "(read from the search record and again from the media item); tied to a place on the map by a shared " +
        "Pleiades/GeoNames/Wikidata id, else by its site's name in its context path, and its dates; records flagged " +
        "as related to human remains are not shown"))

  val SmithsonianPlugin = "media-smithsonian"
  val EuropeanaPlugin   = "media-europeana"

  /** The built-in collections that need a key of the user's own: OFF by default, each with its key as a
    * secret reference in its plugin block (`api-key`). Registered without a key, the collection is
    * listed and its import says what is missing; it is never asked. */
  val keyed: List[KeyedMeta] = List(
    KeyedMeta(SmithsonianPlugin, MediaImporter.Smithsonian, "Smithsonian Open Access objects", "an api.data.gov key",
      "Smithsonian Open Access objects whose image is itself CC0 (usage.access), tied to a place on the map " +
        "when the object's most specific place field names it (and no other) and its dates overlap the place's; " +
        "needs an api.data.gov key (histmersion.plugins.media-smithsonian.api-key, a secret reference)",
      (conf, key) => SmithsonianCollection(conf.smithsonian, key), "media.smithsonian", _.smithsonian.enabled),
    KeyedMeta(EuropeanaPlugin, MediaImporter.Europeana, "Europeana images", "a Europeana personal API key",
      "Europeana's openly licensed images (reusability=open: CC0, PDM, CC BY, CC BY-SA), each record's own " +
        "edm:rights read from the search and again from the Record API; tied to a place on the map by its " +
        "dcterms:spatial name or a gazetteer id, and its dates; needs a personal API key " +
        "(histmersion.plugins.media-europeana.api-key, a secret reference)",
      (conf, key) => EuropeanaCollection(conf.europeana, key), "media.europeana", _.europeana.enabled))

  /** The plugin ids of the built-in collections. */
  val pluginIds: Set[String] = entries.map(_.plugin).toSet ++ keyed.map(_.plugin)

  /** Each a plugin of its own, registering its one collection; a keyed one resolves its key first. */
  def plugins: List[Plugin] = entries.map(e => Builtin(e.plugin, _.mediaCollections.add(e.name, e))) ++
    keyed.map(m => BuiltinWith[KeyedConfig](m.plugin, (c, ctx) =>
      c.apiKey.fold[Either[String, Option[String]]](Right(None))(s => ctx.secret(s).map(KeyedConfig.nonBlank))
        .flatMap(key => ctx.mediaCollections.add(m.name, KeyedEntry(m, key)))))

  /** The other names `media import` takes for a built-in provider. */
  val aliases: Map[String, String] = Map(
    "si" -> MediaImporter.Smithsonian, "smithsonian-open-access" -> MediaImporter.Smithsonian,
    "commons" -> MediaImporter.Wikidata, MediaProvider.Commons -> MediaImporter.Wikidata,
    "depictions" -> MediaImporter.Depicts, MediaProvider.CommonsDepicts -> MediaImporter.Depicts,
    MediaProvider.Met -> MediaImporter.Met,
    "cma" -> MediaImporter.Cleveland, MediaProvider.Cleveland -> MediaImporter.Cleveland,
    "artic" -> MediaImporter.Aic, MediaProvider.ArtInstitute -> MediaImporter.Aic,
    MediaProvider.LibraryOfCongress -> MediaImporter.Loc,
    "open-context" -> MediaImporter.OpenContext, "oc" -> MediaImporter.OpenContext)

  /** Names a plugin's collection may not take: the built-in imports, their aliases, and every provider
    * the host stores pictures under (a plugin's pictures would be taken for theirs). */
  val reserved: Set[String] = MediaImporter.names.toSet ++ keyed.map(_.name) ++ aliases.keySet ++ MediaProvider.all ++
    Set(MediaProvider.CommonsDepicts, MediaProvider.Pleiades)

  /** Pure: the name an import request means — an alias resolved, blank = `wikidata`, else as given
    * (lower case): the registry decides whether it exists. */
  def canonical(asked: String): String =
    val s = asked.trim.toLowerCase(java.util.Locale.ROOT)
    if s.isEmpty then MediaImporter.Wikidata else aliases.getOrElse(s, s)

  /** Pure: the registered names in the order lists show them — the built-ins in their order, then the
    * plugins' collections in registration order. */
  def ordered(registered: List[String]): List[String] =
    val own = entries.map(_.name) ++ keyed.map(_.name)
    own.filter(registered.contains) ++ registered.filterNot(own.contains)

/** The providers `media import` can run, from the plugins' registry of the calling thread's generation. */
object MediaImporters:

  /** Pure: the registered collection `asked` names (an alias resolved), or why none: unknown, or its
    * plugin switched off. */
  def lookup(asked: String, collections: ListMap[String, MediaCollection]): Either[String, (String, MediaCollection)] =
    val name = BuiltinMedia.canonical(asked)
    collections.get(name).map(name -> _).toRight(
      BuiltinMedia.entries.find(_.name == name) match
        case Some(e) => s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)"
        case None if BuiltinMedia.keyed.exists(_.name == name) =>
          val m = BuiltinMedia.keyed.find(_.name == name).get
          s"${m.title} ($name) is switched off — it is off by default and needs ${m.needs}: set " +
            s"histmersion.plugins.${m.plugin}.enabled = true and its api-key (a secret reference)"
        case None => s"unknown media provider '${asked.trim}' (one of: " +
            s"${BuiltinMedia.ordered(collections.keys.toList).mkString(", ")}; `histmersion media providers` lists them)")

  /** The import `asked` names, or why there is none: unknown, its plugin switched off, or (a built-in)
    * its own `media.<provider>.enabled` off. A plugin's collection is run by [[PluginCollection]] under
    * the host's allowlist for it (`media.collections`). */
  def resolve(asked: String, collections: ListMap[String, MediaCollection], deps: Deps, http: HttpTransport,
      converter: ConverterClient): Either[String, MediaImporter] =
    lookup(asked, collections).flatMap {
      case (_, h: HostMediaCollection) => h.importer(deps, http, converter)
      case (name, c) =>
        val cfg = deps.cfg.media
        PluginMedia.guard(Right(PluginCollection(name, c, cfg.collections.licencesFor(name)))).flatMap(col =>
          MediaImporter.collection(col, PluginMedia.guard(Right(c.title)).getOrElse(name), s"plugin collection $name",
            deps.ds, cfg, http, converter, DbMediaStore(deps.ds)))
    }

  /** Every registered provider, as `histmersion media providers` lists it. `pluginOf` names the plugin
    * that registered a collection. */
  def infos(collections: ListMap[String, MediaCollection], pluginOf: String => String,
      allowFor: String => String): List[MediaProviderInfo] =
    BuiltinMedia.ordered(collections.keys.toList).flatMap(n => collections.get(n).map(n -> _)).map {
      case (n, e: BuiltinMedia.Entry) =>
        MediaProviderInfo(n, e.title, e.over, e.description, e.plugin, builtin = true, "")
      case (n, k: KeyedEntry) =>
        MediaProviderInfo(n, k.title, k.over, k.description + (if k.key.isEmpty then " — NO KEY SET: not asked" else ""),
          k.meta.plugin, builtin = true, "")
      case (n, h: HostMediaCollection) =>
        MediaProviderInfo(n, h.title, h.over, h.description, pluginOf(n), builtin = true, "")
      case (n, c) =>
        MediaProviderInfo(n, safe(c.title).getOrElse(n), "places", safe(c.description).getOrElse(""), pluginOf(n),
          builtin = false, allowFor(n))
    }

  private def safe[A](a: => A): Option[A] =
    try Option(a)
    catch case NonFatal(_) => None

/** A PLUGIN's collection run by the host's own collection import ([[CollectionImport]]): the plugin
  * searches and describes; the host reads each stated licence ([[PluginMedia.rights]]) and judges
  * it against `allowed` — `media.collections` for this collection, never anything the plugin says —,
  * ties records to places by [[MediaMatch]] under the rule the collection chose, downloads, thumbnails
  * and stores. One per import (it keeps the run's items for [[complete]]). */
final class PluginCollection(val provider: String, col: MediaCollection, allowed: String) extends Collection:
  val role: String = col.role
  val conf: MediaCollectionConfig =
    MediaCollectionConfig(enabled = true, apiUrl = "", imageUrl = "", candidates = 0, perPlace = col.perPlace,
      requestIntervalMs = col.requestIntervalMs, displayPx = 0, maxYear = PluginCollection.maxYear(col.matchRule),
      licences = allowed)

  // the items as the plugin returned them, by key, for `complete` — one import run's own state
  private val items = scala.collection.concurrent.TrieMap.empty[String, MediaItem]

  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    PluginMedia.guard(col.search(PluginCollection.place(place), PluginMedia.http(fetch))).map(_.map(_.map { it =>
      items.update(it.key, it)
      PluginMedia.record(it)
    }))

  override def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve) =
    col.matchRule match
      case MediaMatchRule.Map(maxYear) => MediaMatch.map(rec, place, resolve, maxYear.value)
      case _                           => MediaMatch.museum(rec, place, resolve)

  override def usesIds: Boolean = col.matchRule.isInstanceOf[MediaMatchRule.PlaceIds]

  override def matchesWithIds(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId) =
    col.matchRule match
      case MediaMatchRule.PlaceIds(deeper) => MediaMatch.withIds(rec, place, resolveId, resolve, deeper)
      case _                               => matches(rec, place, resolve)

  override def complete(fetch: Polite, rec: MediaRecord): Either[String, MediaRecord] =
    items.get(rec.key) match
      case None     => Right(rec)
      case Some(it) => PluginMedia.guard(col.complete(it, PluginMedia.http(fetch))).map(PluginMedia.record)

object PluginCollection:

  def maxYear(rule: MediaMatchRule): Int = rule match
    case MediaMatchRule.Map(y) => y.value
    case _                     => 0

  /** The place as a plugin sees it: what the store holds, each value as its type (one that is not left out). Pure. */
  def place(p: PlaceFacts): MediaPlace =
    MediaPlace(p.id, p.label, p.qid.flatMap(SpiValues.wikidata), p.lat.flatMap(SpiValues.latitude),
      p.lon.flatMap(SpiValues.longitude), p.period.flatMap((a, b) => SpiValues.period(a, b)))

/** What a key-gated built-in collection is, before its key is known. `collection` builds it over the
  * media settings and the key; `enabled` reads its own `media.<provider>.enabled`. */
final case class KeyedMeta(plugin: String, name: String, title: String, needs: String, description: String,
    collection: (com.histmersion.config.MediaConfig, String) => Collection, settings: String,
    enabled: com.histmersion.config.MediaConfig => Boolean)

/** A key-gated built-in collection as registered: its key, resolved from its secret reference when the
  * plugin was built, or none. Without one, its import is refused with what to set — the API is not asked. */
final class KeyedEntry(val meta: KeyedMeta, private[server] val key: Option[String]) extends HostMediaCollection:
  def name: String = meta.name
  val title: String = meta.title
  def over: String = "places"
  val role: String = MediaCollection.Object
  override val description: String = meta.description
  override def toString: String = s"KeyedEntry(${meta.name}, key ${if key.isDefined then "set" else "missing"})"
  def importer(deps: Deps, http: HttpTransport, converter: ConverterClient): Either[String, MediaImporter] =
    val cfg = deps.cfg.media
    KeyedEntry.check(meta, key, cfg).flatMap(k =>
      MediaImporter.collection(meta.collection(cfg, k), meta.title, meta.settings, deps.ds, cfg, http, converter,
        DbMediaStore(deps.ds)))

object KeyedEntry:
  /** Pure: the key an import of `meta` runs with, or why it does not run — no key set, or its own
    * `media.<provider>.enabled` off. */
  def check(meta: KeyedMeta, key: Option[String], cfg: com.histmersion.config.MediaConfig): Either[String, String] =
    if !meta.enabled(cfg) then Left(s"${meta.title} is switched off (${meta.settings}.enabled = false)")
    else key.toRight(s"${meta.title} needs ${meta.needs}: set histmersion.plugins.${meta.plugin}.api-key to a secret " +
      "reference (\"env:<NAME>\" or a vault's, e.g. \"keepassxc:<entry>#password\"); it was not asked")

/** A key-gated collection's plugin settings. `api-key`: the key as a SECRET REFERENCE (`env:<NAME>`, a
  * vault's), never the key; `null` = none, and the collection is then not asked. */
final case class KeyedConfig(apiKey: Option[Secret]) derives ConfigReader

object KeyedConfig:
  private[media] def nonBlank(v: String): Option[String] = Option(v).map(_.trim).filter(_.nonEmpty)
