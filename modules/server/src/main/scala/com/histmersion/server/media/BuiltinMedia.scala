package com.histmersion.server.media

import com.histmersion.pluginhost.HttpTransport

import com.histmersion.domain.{MediaLicence, MediaMatch, MediaRecord, MediaRights as CoreRights}
import com.histmersion.api.MediaProvider
import com.histmersion.plugin.*
import com.histmersion.server.{ConverterClient, Deps}
import com.histmersion.server.acquire.fetchers.{Builtin, BuiltinWith}

import com.histmersion.plugin.PluginConfig.given

/** Today's media providers as BUILT-IN plugins: in every binary, each enabled by default and switched off by
  * `histmersion.plugins.<plugin>.enabled = false`. Their own settings stay where they were (`media.<provider>.*`, read
  * at each import, so a change applies without rebuilding a plugin); a collection whose `media.<provider>.enabled` is
  * false is still refused at the import, as before.
  */
object BuiltinMedia:

  /** One built-in provider: its plugin, its name, and what the CLI's list says about it. */
  final case class Entry(
      plugin: String,
      name: String,
      title: String,
      over: String,
      role: String,
      override val description: String
  ) extends HostMediaCollection:
    def importer(deps: Deps, http: HttpTransport, converter: ConverterClient) =
      MediaImporter.of(name, deps, http, converter)

  private val museum = " — Open Access objects only (as the record itself says), tied to a place on the map " +
    "when the object's most specific place field names it (and no other) and its dates overlap the place's; " +
    "--dry-run lists the matches with their reasons"

  /** In the order `media import`'s list names them. */
  val entries: List[Entry] = List(
    Entry(
      "media-wikidata",
      MediaImporter.Wikidata,
      "Wikidata pictures",
      "places and events",
      "image",
      "Pictures of places and events from their Wikidata items (image, locator map, plan, aerial view, route map), " +
        "licensed on Wikimedia Commons; a job asks about every place on the map not asked yet (one per Wikidata " +
        "item, most-cited sites first), batch by batch, and resumes where it stopped"
    ),
    Entry(
      "media-depicts",
      MediaImporter.Depicts,
      "Commons depictions",
      "events",
      "depiction",
      "Pictures that depict events: Wikimedia Commons files whose structured data says P180 'depicts' the event's " +
        "Wikidata item, and the images of the works its item lists as P1299 'depicted by'; licence read from Commons"
    ),
    Entry(
      "media-met",
      MediaImporter.Met,
      "Met objects",
      "places",
      MediaCollection.Object,
      "The Metropolitan Museum of Art (CC0, isPublicDomain)" + museum
    ),
    Entry(
      "media-cleveland",
      MediaImporter.Cleveland,
      "Cleveland Museum of Art objects",
      "places",
      MediaCollection.Object,
      "The Cleveland Museum of Art (CC0, share_license_status)" + museum
    ),
    Entry(
      "media-aic",
      MediaImporter.Aic,
      "Art Institute of Chicago objects",
      "places",
      MediaCollection.Object,
      "The Art Institute of Chicago (CC0, is_public_domain; IIIF images)" + museum
    ),
    Entry(
      "media-loc",
      MediaImporter.Loc,
      "Library of Congress maps",
      "places",
      MediaCollection.Map,
      "Historical maps from the Library of Congress, Geography and Map Division: public domain only (rights " +
        "'No known restrictions' and dated by media.loc.max-year), linked as role map to the places their " +
        "locations name"
    ),
    Entry(
      "media-pleiades",
      MediaImporter.Pleiades,
      "Pleiades-linked pictures",
      "places",
      MediaCollection.Image,
      "Wikimedia Commons files that Pleiades place records link, for the places on the map that carry a Pleiades " +
        "id (matched by that id, no name matching); licence read from Commons; links to Flickr (needs a key), " +
        "Commons categories and other sites are counted, not taken"
    ),
    Entry(
      "media-opencontext",
      MediaImporter.OpenContext,
      "Open Context media",
      "places",
      MediaCollection.Object,
      "Open Context's excavation media (photographs, drawings), each record under its own Creative Commons licence " +
        "(read from the search record and again from the media item); tied to a place on the map by a shared " +
        "Pleiades/GeoNames/Wikidata id, else by its site's name in its context path, and its dates; records flagged " +
        "as related to human remains are not shown"
    )
  )

  val SmithsonianPlugin = "media-smithsonian"
  val EuropeanaPlugin = "media-europeana"

  /** The built-in collections that need a key of the user's own: OFF by default, each with its key as a secret
    * reference in its plugin block (`api-key`). Registered without a key, the collection is listed and its import says
    * what is missing; it is never asked.
    */
  val keyed: List[KeyedMeta] = List(
    KeyedMeta(
      SmithsonianPlugin,
      MediaImporter.Smithsonian,
      "Smithsonian Open Access objects",
      "an api.data.gov key",
      "Smithsonian Open Access objects whose image is itself CC0 (usage.access), tied to a place on the map " +
        "when the object's most specific place field names it (and no other) and its dates overlap the place's; " +
        "natural-history units' specimens and objects made after media.smithsonian.max-year are refused; " +
        "needs an api.data.gov key (histmersion.plugins.media-smithsonian.api-key, a secret reference)",
      (conf, key) => SmithsonianCollection(conf.smithsonian, key),
      "media.smithsonian",
      _.smithsonian.enabled
    ),
    KeyedMeta(
      EuropeanaPlugin,
      MediaImporter.Europeana,
      "Europeana images",
      "a Europeana personal API key",
      "Europeana's openly licensed images (reusability=open: CC0, PDM, CC BY, CC BY-SA), each record's own " +
        "edm:rights read from the search and again from the Record API; tied to a place on the map by its " +
        "dcterms:spatial name or a gazetteer id, and its dates; natural-history specimens and objects made after " +
        "media.europeana.max-year are refused; needs a personal API key " +
        "(histmersion.plugins.media-europeana.api-key, a secret reference)",
      (conf, key) => EuropeanaCollection(conf.europeana, key),
      "media.europeana",
      _.europeana.enabled
    )
  )

  /** The plugin ids of the built-in collections. */
  val pluginIds: Set[String] = entries.map(_.plugin).toSet ++ keyed.map(_.plugin)

  /** Each a plugin of its own, registering its one collection; a keyed one resolves its key first. */
  def plugins: List[Plugin] = entries.map(e => Builtin(e.plugin, _.mediaCollections.add(e.name, e))) ++
    keyed.map(m =>
      BuiltinWith[KeyedConfig](
        m.plugin,
        (c, ctx) =>
          c.apiKey
            .fold[Either[String, Option[String]]](Right(None))(s => ctx.secret(s).map(KeyedConfig.nonBlank))
            .flatMap(key => ctx.mediaCollections.add(m.name, KeyedEntry(m, key)))
      )
    )

  /** The other names `media import` takes for a built-in provider. */
  val aliases: Map[String, String] = Map(
    "si" -> MediaImporter.Smithsonian,
    "smithsonian-open-access" -> MediaImporter.Smithsonian,
    "commons" -> MediaImporter.Wikidata,
    MediaProvider.Commons -> MediaImporter.Wikidata,
    "depictions" -> MediaImporter.Depicts,
    MediaProvider.CommonsDepicts -> MediaImporter.Depicts,
    MediaProvider.Met -> MediaImporter.Met,
    "cma" -> MediaImporter.Cleveland,
    MediaProvider.Cleveland -> MediaImporter.Cleveland,
    "artic" -> MediaImporter.Aic,
    MediaProvider.ArtInstitute -> MediaImporter.Aic,
    MediaProvider.LibraryOfCongress -> MediaImporter.Loc,
    "open-context" -> MediaImporter.OpenContext,
    "oc" -> MediaImporter.OpenContext
  )

  /** Names a plugin's collection may not take: the built-in imports, their aliases, and every provider the host stores
    * pictures under (a plugin's pictures would be taken for theirs).
    */
  val reserved: Set[String] = MediaImporter.names.toSet ++ keyed.map(_.name) ++ aliases.keySet ++ MediaProvider.all ++
    Set(MediaProvider.CommonsDepicts, MediaProvider.Pleiades)

  /** Pure: the name an import request means — an alias resolved, blank = `wikidata`, else as given (lower case): the
    * registry decides whether it exists.
    */
  def canonical(asked: String): String =
    val s = asked.trim.toLowerCase(java.util.Locale.ROOT)
    if s.isEmpty then MediaImporter.Wikidata else aliases.getOrElse(s, s)

  /** Pure: the registered names in the order lists show them — the built-ins in their order, then the plugins'
    * collections in registration order.
    */
  def ordered(registered: List[String]): List[String] =
    val own = entries.map(_.name) ++ keyed.map(_.name)
    own.filter(registered.contains) ++ registered.filterNot(own.contains)
