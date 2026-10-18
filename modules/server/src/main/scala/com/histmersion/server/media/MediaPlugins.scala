package com.histmersion.server.media

import com.histmersion.core.{MediaCollectionConfig, MediaLicence, MediaMatch, MediaProvider, MediaProviderInfo,
  MediaRecord, MediaRights as CoreRights}
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.plugin.*
import com.histmersion.server.{ConverterClient, Deps}
import com.histmersion.server.acquire.fetchers.Builtin

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal

/** A media collection that is Histmersion's OWN code, registered through the plugin host like any other
  * (docs/research-plugins.md §6, D2) but run by the host's own import: the Wikidata/Commons runs and
  * the museum, map and excavation collections with their `media.<provider>.*` settings, unchanged.
  * The SPI's search is never called for it. */
trait HostMediaCollection extends MediaCollection:
  /** The name `media import` is asked for (`met`). */
  def name: String
  /** What it asks about: `places`, `events`, `places and events`. */
  def over: String
  /** The import, built over this server's config — or why it cannot run (`media.<provider>.enabled`). */
  def importer(deps: Deps, http: MediaHttp, converter: ConverterClient): Either[String, MediaImporter]
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
    def importer(deps: Deps, http: MediaHttp, converter: ConverterClient) = MediaImporter.of(name, deps, http, converter)

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

  /** The plugin ids of the built-in collections. */
  val pluginIds: Set[String] = entries.map(_.plugin).toSet

  /** Each a plugin of its own, registering its one collection. */
  def plugins: List[Plugin] = entries.map(e => Builtin(e.plugin, _.mediaCollections.add(e.name, e)))

  /** The other names `media import` takes for a built-in provider. */
  val aliases: Map[String, String] = Map(
    "commons" -> MediaImporter.Wikidata, MediaProvider.Commons -> MediaImporter.Wikidata,
    "depictions" -> MediaImporter.Depicts, MediaProvider.CommonsDepicts -> MediaImporter.Depicts,
    MediaProvider.Met -> MediaImporter.Met,
    "cma" -> MediaImporter.Cleveland, MediaProvider.Cleveland -> MediaImporter.Cleveland,
    "artic" -> MediaImporter.Aic, MediaProvider.ArtInstitute -> MediaImporter.Aic,
    MediaProvider.LibraryOfCongress -> MediaImporter.Loc,
    "open-context" -> MediaImporter.OpenContext, "oc" -> MediaImporter.OpenContext)

  /** Names a plugin's collection may not take: the built-in imports, their aliases, and every provider
    * the host stores pictures under (a plugin's pictures would be taken for theirs). */
  val reserved: Set[String] = MediaImporter.names.toSet ++ aliases.keySet ++ MediaProvider.all ++
    Set(MediaProvider.CommonsDepicts, MediaProvider.Pleiades)

  /** Pure: the name an import request means — an alias resolved, blank = `wikidata`, else as given
    * (lower case): the registry decides whether it exists. */
  def canonical(asked: String): String =
    val s = asked.trim.toLowerCase(java.util.Locale.ROOT)
    if s.isEmpty then MediaImporter.Wikidata else aliases.getOrElse(s, s)

  /** Pure: the registered names in the order lists show them — the built-ins in their order, then the
    * plugins' collections in registration order. */
  def ordered(registered: List[String]): List[String] =
    val own = entries.map(_.name)
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
        case None => s"unknown media provider '${asked.trim}' (one of: " +
            s"${BuiltinMedia.ordered(collections.keys.toList).mkString(", ")}; `histmersion media providers` lists them)")

  /** The import `asked` names, or why there is none: unknown, its plugin switched off, or (a built-in)
    * its own `media.<provider>.enabled` off. A plugin's collection is run by [[PluginCollection]] under
    * the host's allowlist for it (`media.collections`). */
  def resolve(asked: String, collections: ListMap[String, MediaCollection], deps: Deps, http: MediaHttp,
      converter: ConverterClient): Either[String, MediaImporter] =
    lookup(asked, collections).flatMap {
      case (_, h: HostMediaCollection) => h.importer(deps, http, converter)
      case (name, c) =>
        val cfg = deps.cfg.media
        PluginCollection.guard(Right(PluginCollection(name, c, cfg.collections.licencesFor(name)))).flatMap(col =>
          MediaImporter.collection(col, PluginCollection.guard(Right(c.title)).getOrElse(name), s"plugin collection $name",
            deps.ds, cfg, http, converter, DbMediaStore(deps.ds)))
    }

  /** Every registered provider, as `histmersion media providers` lists it. `pluginOf` names the plugin
    * that registered a collection. */
  def infos(collections: ListMap[String, MediaCollection], pluginOf: String => String,
      allowFor: String => String): List[MediaProviderInfo] =
    BuiltinMedia.ordered(collections.keys.toList).flatMap(n => collections.get(n).map(n -> _)).map {
      case (n, e: BuiltinMedia.Entry) =>
        MediaProviderInfo(n, e.title, e.over, e.description, e.plugin, builtin = true, "")
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
  * searches and describes; the host reads each stated licence ([[PluginCollection.rights]]) and judges
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
    PluginCollection.guard(col.search(PluginCollection.place(place), PluginCollection.http(fetch))).map(_.map(_.map { it =>
      items.update(it.key, it)
      PluginCollection.record(it)
    }))

  override def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve) =
    col.matchRule match
      case MediaMatchRule.Map(maxYear) => MediaMatch.map(rec, place, resolve, maxYear)
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
      case Some(it) => PluginCollection.guard(col.complete(it, PluginCollection.http(fetch))).map(PluginCollection.record)

object PluginCollection:

  def maxYear(rule: MediaMatchRule): Int = rule match
    case MediaMatchRule.Map(y) => y
    case _                     => 0

  /** The place as a plugin sees it. Pure. */
  def place(p: PlaceFacts): MediaPlace = MediaPlace(p.id, p.label, p.qid, p.lat, p.lon, p.period)

  /** The run's polite client as the plugin's HTTP: its pace, its User-Agent, Retry-After honoured, a
    * challenge the end of the request. */
  def http(fetch: Polite): PluginHttp = new PluginHttp:
    def get(url: String, headers: Map[String, String]) = fetch.get(url, headers).map(_.body)

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[String, A]): Either[String, A] =
    try Option(a).getOrElse(Left("the plugin answered nothing"))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

  /** Pure: the record a plugin's item becomes; its rights read by the HOST ([[rights]]). */
  def record(it: MediaItem): MediaRecord =
    MediaRecord(it.key.trim, it.title.trim, it.pageUrl.trim, it.imageUrl.map(_.trim).filter(_.nonEmpty),
      it.thumbUrl.map(_.trim).filter(_.nonEmpty), it.originalUrl.map(_.trim).filter(_.nonEmpty),
      Option(it.rights).getOrElse(Left("no rights statement")).flatMap(rights), it.geography,
      it.period.map((a, b) => (a.min(b), a.max(b))), it.ids, it.withheld)

  /** Pure: what a provider's licence statement means, read by the host as every built-in collection's
    * is ([[MediaLicence]]): the statement as an id or short name, or as a Creative Commons URL; the
    * licence URL when the statement says nothing known. A statement it cannot read is `unknown` — never
    * stored, whatever an allowlist says. A record must carry a credit line. The family decided here is
    * what the allowlist is checked against: the plugin states, the host judges. */
  def rights(r: com.histmersion.plugin.MediaRights): Either[String, CoreRights] =
    val stated = Option(r.licence).getOrElse("").trim
    val byStatement =
      if stated.toLowerCase(java.util.Locale.ROOT).matches("^https?://.*") then MediaLicence.ofUrl(stated)
      else MediaLicence.classify(stated)
    val licence =
      if byStatement.family != MediaLicence.Unknown then byStatement
      else r.licenceUrl.map(MediaLicence.ofUrl).filter(_.family != MediaLicence.Unknown).getOrElse(byStatement)
    val credit = Option(r.credit).getOrElse("").trim
    if credit.isEmpty then Left("no credit line")
    else Right(CoreRights(licence, r.licenceUrl.map(_.trim).filter(_.nonEmpty), r.author.map(_.trim).filter(_.nonEmpty), credit))
