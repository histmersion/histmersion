package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder, Json, JsonObject}
import sttp.tapir.Schema
import com.histmersion.values.{PlaceKind, WireEnumValues}

/** The gazetteer's EXTERNAL SOURCES (#1064): GeoNames, Wikidata and Linked Places Format datasets
  * (what the World Historical Gazetteer exports). All three arrive as LPF features — the GeoNames
  * and Wikidata extracts are LPF too, one feature per line, built by `db/gazetteer/build.py` — so
  * this is ONE reader, and the place model of [[Lpf]] underneath it.
  *
  * What it adds to [[Lpf]]: the feature's identity in other gazetteers ([[ExternalId]]: its own
  * `@id` and its `links`), time spans with an OPEN side (a name used "until 1935"), name citations,
  * and the LP-TSV dialect. Places are joined across sources only by those identifiers — Wikidata ↔
  * GeoNames ↔ Pleiades ↔ TGN, an LPF `closeMatch`/`exactMatch` — never by coordinates: a record
  * whose identifiers name a place already in the gazetteer adds its names to THAT place
  * ([[Gazetteer.canonical]]), and only a record that names none becomes a place of its own.
  *
  * Pure: nothing here reads a file or a database. */

/** Which importer a source is read by. `Tgn` is the Getty Thesaurus of Geographic Names (#1403), a
  * pinned extract like GeoNames and Wikidata. */
enum GazetteerKind:
  case Geonames, Wikidata, Lpf, Tgn

  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object GazetteerKind:
  def parse(s: String): Option[GazetteerKind] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** The kind of a stored source key: `geonames`, `wikidata`, `lpf:<dataset>`. */
  def ofKey(key: String): Option[GazetteerKind] = parse(key.takeWhile(_ != ':'))

  given WireEnumValues[GazetteerKind]:
    def all: Array[GazetteerKind] = GazetteerKind.values
    def wire(e: GazetteerKind): String = e.wire
    def fromWire(s: String): Option[GazetteerKind] = parse(s)
  given Encoder[GazetteerKind] = WireEnum.encoder
  given Decoder[GazetteerKind] = WireEnum.decoder
  given Schema[GazetteerKind]  = WireEnum.schema

/** An authority whose ids join places across sources. The wire spelling is `place_links.scheme`
  * ([[PlaceLink.Schemes]]). */
enum LinkScheme:
  case Pleiades, Wikidata, Geonames, Tgn, Whg

  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object LinkScheme:
  def parse(s: String): Option[LinkScheme] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

/** How sure a link is: LPF's `exactMatch` (the same entity) or `closeMatch` (near enough to join
  * on). The wire spelling is `place_links.relation`. */
enum LinkMatch:
  case Exact, Close

  def wire: String = this match
    case Exact => "exact_match"
    case Close => "close_match"

object LinkMatch:
  /** LPF `links[].type`; anything else (`primaryTopicOf`, `subjectOf`, `seeAlso`) is no identity. */
  def ofLpf(t: String): Option[LinkMatch] = t.trim.toLowerCase(java.util.Locale.ROOT) match
    case "exactmatch" | "exact_match" | "skos:exactmatch" => Some(Exact)
    case "closematch" | "close_match" | "skos:closematch" => Some(Close)
    case _                                                => None

/** One id in one authority: Wikidata `Q42`, GeoNames `2643743`, Pleiades `579885`, TGN `7011944`. */
final case class ExternalId(scheme: LinkScheme, id: String):
  /** `wikidata:Q42` — how a source names a record it imported. */
  def key: String = s"${scheme.wire}:$id"

  /** The id a place gets when this record is the first to bring it in: `geonames_2643743`,
    * `wikidata_q42` — the convention of the seed's own `pleiades_579885`. */
  def placeId: String = s"${scheme.wire}_${id.toLowerCase(java.util.Locale.ROOT)}"

object ExternalId:
  private val Prefixed = """^(wd|wikidata|gn|geonames|pl|pleiades|tgn|whg)\s*:\s*(\S+)$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q\d+)/?$""".r
  private val GeonamesUri = """^https?://(?:sws\.|www\.)?geonames\.org/(\d+)(?:/.*)?$""".r
  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/(\d+)(?:/.*)?$""".r
  private val TgnUri      = """^https?://vocab\.getty\.edu/(?:page/)?tgn/(\d+)(?:[-/].*)?$""".r
  private val WhgUri      = """^https?://(?:www\.)?whgazetteer\.org/(?:api/)?(?:places?|db)/(?:\?id=)?(\d+)/?.*$""".r

  /** `wd:Q42`, `gn:123`, `pl:579885`, `tgn:7011944`, `whg:123` or the authority's URI; `None` for
    * anything else (a VIAF id, a dataset-local id). Ids are normalised: `Q` upper case, digits as is. */
  def parse(raw: String): Option[ExternalId] =
    val s = raw.trim
    def make(scheme: LinkScheme, id: String): Option[ExternalId] =
      val v = id.trim.stripSuffix("/")
      scheme match
        case LinkScheme.Wikidata => Option.when(v.matches("(?i)q\\d+"))(ExternalId(scheme, v.toUpperCase(java.util.Locale.ROOT)))
        case _                   => Option.when(v.matches("\\d+"))(ExternalId(scheme, v))
    s match
      case Prefixed(p, id) =>
        val scheme = p.toLowerCase(java.util.Locale.ROOT) match
          case "wd" | "wikidata" => LinkScheme.Wikidata
          case "gn" | "geonames" => LinkScheme.Geonames
          case "pl" | "pleiades" => LinkScheme.Pleiades
          case "tgn"             => LinkScheme.Tgn
          case _                 => LinkScheme.Whg
        make(scheme, id)
      case WikidataUri(id) => make(LinkScheme.Wikidata, id)
      case GeonamesUri(id) => make(LinkScheme.Geonames, id)
      case PleiadesUri(id) => make(LinkScheme.Pleiades, id)
      case TgnUri(id)      => make(LinkScheme.Tgn, id)
      case WhgUri(id)      => make(LinkScheme.Whg, id)
      case _               => None

/** A time span with either side possibly unknown — `from = None` is "since whenever", which is not
  * the same as a known single year. Signed years, historical reckoning (BCE negative, no year 0). */
final case class Span(from: Option[Long], to: Option[Long]):
  def isEmpty: Boolean = from.isEmpty && to.isEmpty

object Span:
  val always: Span = Span(None, None)

/** One name of an imported record: language (`""` unknown), when it was used, and the citation a
  * source gives for it (an LPF `names[].citations`). */
final case class GazName(name: String, lang: String = "", span: Span = Span.always, citation: Option[String] = None)

final case class GazLink(id: ExternalId, relation: LinkMatch)

/** What a record says the place IS (LPF `descriptions[]`: Wikidata's schema:description, a dataset's
  * own note), in one language (`""` unknown). */
final case class GazDescription(text: String, lang: String = "")

/** A record's shape: GeoJSON (an LPF feature) or WKT (an LP-TSV `geowkt`); PostGIS reads either. */
enum GazGeometry:
  case GeoJson(text: String)
  case Wkt(text: String)

/** One record of a source, ready to be linked and stored.
  *
  * `ref` is the record's id IN ITS SOURCE (`geonames:2643743`, `wikidata:Q42`, an LPF feature's
  * `@id`) — with the source key it is the provenance every row this record writes carries. `placeId`
  * is the id the record's place gets if no place already holds one of its identifiers. */
final case class GazRecord(
    ref: String,
    own: Option[ExternalId],
    placeId: String,
    label: String,
    kind: String,
    geometry: Option[GazGeometry],
    span: Span,
    names: List[GazName],
    links: List[GazLink],
    source: Option[String],
    properties: Option[String],
    descriptions: List[GazDescription] = Nil,
    /** The class the source gives the item (a Wikidata extract's `properties.class`, e.g. Q3024240
      * "historical country") — kept on the record's own link (V077), joined or not. */
    itemClass: Option[String] = None
):
  /** Every identifier the record answers to: its own first, then its links. */
  def identifiers: List[ExternalId] = (own.toList ++ links.map(_.id)).distinct

  /** The links it writes on its place: its own id (exact) and each link not already its own. */
  def placeLinks: List[GazLink] =
    (own.map(GazLink(_, LinkMatch.Exact)).toList ++ links.filterNot(l => own.contains(l.id))).distinctBy(_.id)

/** What an LPF dataset says about itself: from a FeatureCollection's own keys or a sidecar
  * `<file>.meta.json` (an LP-TSV file has nowhere else to say it). */
final case class LpfDataset(title: Option[String], citation: Option[String], licence: Option[String],
    span: Span = Span.always)

/** A place a record's identifiers already name: its id, who brought it in (`None` = the bundled
  * seed, or a person's own place), and whether the identifier is the place's OWN — a seed id, or one
  * the record that created the place gave it — rather than one a record that JOINED it copied there. */
final case class PlaceMatch(placeId: String, importSource: Option[String], native: Boolean = true)

/** The period a record's place is STORED with, which every time filter reads (`places.period`), and
  * whether its start is a window rather than a date (`places.start_unknown`) — see [[PlaceDating]]. */
final case class Validity(from: Option[Long], to: Option[Long], startUnknown: Boolean)

object Gazetteer:

  /** The key of a source in `gazetteer_sources` and on every row it writes. */
  def sourceKey(kind: GazetteerKind, dataset: Option[String] = None): String = kind match
    case GazetteerKind.Lpf => s"lpf:${dataset.getOrElse("")}"
    case k                 => k.wire

  /** `the World's Cities.lpf.json` → `the_world_s_cities`: an LPF dataset's key, from its file name. */
  def datasetSlug(fileName: String): String =
    val base = fileName.replaceAll("(?i)(\\.meta)?\\.(jsonld|json|geojson|ndjson|jsonl|tsv)(\\.gz)?$", "")
      .replaceAll("(?i)\\.lpf$", "")
    slug(base).take(48)

  private def slug(s: String): String =
    s.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "")

  // ── which place a record joins ───────────────────────────────────────────────────────────────

  /** Who wins when a record's identifiers name SEVERAL places: the bundled seed (Pleiades, Natural
    * Earth, the curated supplement) and a person's own places first, then a scholarly LPF dataset,
    * then Wikidata, then Getty TGN, then GeoNames; the smaller id breaks a tie. Deterministic for a
    * given store. */
  def rank(m: PlaceMatch): Int = m.importSource.flatMap(GazetteerKind.ofKey) match
    case None                         => if m.importSource.isEmpty then 0 else 5
    case Some(GazetteerKind.Lpf)      => 1
    case Some(GazetteerKind.Wikidata) => 2
    case Some(GazetteerKind.Tgn)      => 3
    case Some(GazetteerKind.Geonames) => 4

  /** The BULK sources — a point and a name list per item, most of them undated: GeoNames, Wikidata,
    * Getty TGN. Their places are imported dots (no part of the hierarchy, drawn only with a finding;
    * `PlaceRepo.importedSql`), their silence about time means unknown ([[unknownWhenSilent]]) and an
    * area known only by their point is no place ([[PointAreaSources]]). An LPF dataset is none of
    * these: a scholarly dataset's statements are its own. */
  val ImportedSources: List[String] =
    List(GazetteerKind.Wikidata, GazetteerKind.Geonames, GazetteerKind.Tgn).map(k => sourceKey(k))

  /** The place a record's names go to, or `None` — then it becomes a place of its own
    * (`record.placeId`). Never by coordinates. `byOwn` are the places the record's OWN id names (and
    * its own earlier place), `byLinks` those its links name. Best first: a place for which the id
    * is NATIVE (a seed id, or one the record that created the place gave it) before one a joining
    * record merely copied it to; then one named by the record's own id before one named by a link;
    * then [[rank]]; then the smaller id. Native first is what keeps a re-run stable: the links a
    * joining record writes never pull another record (or itself, next time) somewhere else. */
  def canonical(byOwn: List[PlaceMatch], byLinks: List[PlaceMatch] = Nil): Option[String] =
    (byOwn.map(_ -> 0) ++ byLinks.map(_ -> 1))
      .groupBy(_._1.placeId).values
      .map(_.minBy((m, via) => (if m.native then 0 else 1, via)))
      .toList
      .sortBy((m, via) => (if m.native then 0 else 1, via, rank(m), m.placeId))
      .headOption.map(_._1.placeId)

  // ── time semantics of an imported place ──────────────────────────────────────────────────────

  /** The default of `places.sources.open-start-window-years` (reference.conf), which V074 used to fix
    * the rows stored before it; `GazetteerSourcesSuite` keeps the three equal. */
  val DefaultOpenStartWindowYears: Int = 300

  /** Does this source's silence mean UNKNOWN? GeoNames, Wikidata and TGN ([[ImportedSources]]): yes —
    * an item with no inception was not there "since forever". The bundled seed, a person's places and
    * an LPF dataset state their periods on purpose ([[PlaceDating.Stated]]). */
  def unknownWhenSilent(sourceKey: String): Boolean =
    GazetteerKind.ofKey(sourceKey).exists(k => k != GazetteerKind.Lpf)

  /** The stored period of a record of `sourceKey`: for GeoNames and Wikidata a known end with no
    * start becomes `[end − windowYears, end]` with `startUnknown` (so a filter sees the place only in
    * that window before its end); everything else is stored as stated — an undated record keeps the
    * open range and is told apart by its source ([[PlaceDating.Unknown]]). */
  def validity(sourceKey: String, span: Span, windowYears: Int): Validity = span match
    case Span(None, Some(end)) if unknownWhenSilent(sourceKey) =>
      Validity(Some(end - math.max(0, windowYears)), Some(end), startUnknown = true)
    case Span(from, to) => Validity(from, to, startUnknown = false)

  // ── an area known only by a point ────────────────────────────────────────────────────────────

  /** The default of `places.sources.point-area-kinds` (reference.conf; `GazetteerSourcesSuite` keeps
    * the two equal): the kinds that stand for an AREA, which a GeoNames or Wikidata record must give
    * a shape to become a place. */
  val DefaultPointAreaKinds: List[String] = List(PlaceKind.Region, PlaceKind.Polity, PlaceKind.People)

  /** The sources the rule applies to: GeoNames, Wikidata and TGN ([[ImportedSources]]), which give
    * every item a coordinate and no boundary. A seed place, a person's place and an LPF dataset keep
    * their points (a curated point-only region is drawn as "no boundary yet" and reviewed by
    * `lint --rule place-kind`). */
  val PointAreaSources: List[String] = ImportedSources

  /** Is this shape only a point — a `Point`, a `MultiPoint`, or no geometry at all? A polygon or a
    * line is a shape; anything unreadable is treated as a shape (never dropped on a guess). */
  def pointOnly(g: Option[GazGeometry]): Boolean = g match
    case None => true
    case Some(GazGeometry.Wkt(t)) =>
      val u = t.trim.toUpperCase(java.util.Locale.ROOT).replaceFirst("^SRID=\\d+;", "")
      u.startsWith("POINT") || u.startsWith("MULTIPOINT")
    case Some(GazGeometry.GeoJson(t)) =>
      io.circe.parser.parse(t).toOption.flatMap(_.asObject).flatMap(_("type")).flatMap(_.asString)
        .exists(k => k == "Point" || k == "MultiPoint")

  /** Is `r` an area known only by a point, from a source whose such records are NOT kept as places
    * (2026-10-09)? A GeoNames or Wikidata region, polity or people (`kinds`) with a point is no
    * region on the map — it draws as a dot or a "no boundary yet" label, competes in name search with
    * the real thing, and holds findings only by the accident of a shared point. Such a record still
    * JOINS a place its identifiers name (its names, links, class and dates go there); it only never
    * becomes a place of its own. */
  def dropsPointArea(source: String, r: GazRecord, kinds: Set[String]): Boolean =
    PointAreaSources.contains(source) && kinds.contains(r.kind) && pointOnly(r.geometry)

  /** An LPF record with no `when` of its own takes its DATASET's (an LPF dataset's places follow its
    * own `when`); a dated record, or a dataset that states none, is left alone. */
  def inDataset(r: GazRecord, ds: LpfDataset): GazRecord =
    if r.span.isEmpty && !ds.span.isEmpty then r.copy(span = ds.span) else r

  // ── names ────────────────────────────────────────────────────────────────────────────────────

  /** One name per (name, lang) — the key a source's names are stored under. A dated entry wins over
    * an undated repeat of the same name; several dated ones become their widest span (an open side
    * stays open); citations are kept, joined. Sorted, so the store sees a stable order. */
  def mergeNames(ns: List[GazName]): List[GazName] =
    ns.map(n => n.copy(name = n.name.trim, lang = n.lang.trim.toLowerCase(java.util.Locale.ROOT)))
      .filter(_.name.nonEmpty)
      .groupBy(n => (n.name, n.lang)).toList
      .map { case ((name, lang), group) =>
        val dated = group.filterNot(_.span.isEmpty)
        val span =
          if dated.isEmpty then Span.always
          else Span(
            if dated.forall(_.span.from.isDefined) then Some(dated.flatMap(_.span.from).min) else None,
            if dated.forall(_.span.to.isDefined) then Some(dated.flatMap(_.span.to).max) else None)
        val cites = group.flatMap(_.citation).flatMap(_.split("; ").toList).map(_.trim).filter(_.nonEmpty).distinct.sorted
        GazName(name, lang, span, Option.when(cites.nonEmpty)(cites.mkString("; ")))
      }
      .sortBy(n => (n.name.toLowerCase(java.util.Locale.ROOT), n.name, n.lang))

  // ── time ─────────────────────────────────────────────────────────────────────────────────────

  /** LPF `when` → a [[Span]] that keeps an OPEN side open: the widest of its timespans, where a
    * timespan without `start` (or `end`) leaves that side unknown. A bare year is that one year.
    * The same reading as [[Lpf.parseWhen]], which the bundled seed and `places import` use. */
  def span(j: Json): Span = Lpf.parseWhen(j).getOrElse(Span.always)

  // ── LPF features ─────────────────────────────────────────────────────────────────────────────

  private def str(o: JsonObject, keys: String*): Option[String] =
    keys.iterator.flatMap(k => o(k).flatMap(_.asString)).map(_.trim).find(_.nonEmpty)

  private def citationOf(n: JsonObject): Option[String] =
    val cites = n("citations").flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { c =>
      c.asString.orElse(c.asObject.flatMap(o =>
        str(o, "label", "@id", "id").map { l =>
          val year = o("year").flatMap(y => y.asString.orElse(y.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)
          year.fold(l)(y => s"$l ($y)")
        }))
    }
    Option.when(cites.nonEmpty)(cites.distinct.mkString("; "))

  private def readNames(f: JsonObject): List[GazName] =
    f("names").flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { j =>
      j.asString.map(GazName(_)).orElse(j.asObject.flatMap { o =>
        str(o, "toponym", "name", "label").map(t =>
          GazName(t, str(o, "lang", "language").getOrElse(""), o("when").fold(Span.always)(span), citationOf(o)))
      })
    }

  /** LPF `descriptions[]` (`{value, lang}`, or a plain string): trimmed, one per (lang, text), in the
    * order given. */
  private def readDescriptions(f: JsonObject): List[GazDescription] =
    f("descriptions").flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { j =>
      j.asString.map(GazDescription(_)).orElse(j.asObject.flatMap(o =>
        str(o, "value", "description", "text").map(v => GazDescription(v, str(o, "lang", "language").getOrElse("")))))
    }.map(d => d.copy(text = d.text.trim, lang = d.lang.trim.toLowerCase(java.util.Locale.ROOT)))
      .filter(_.text.nonEmpty).distinctBy(d => (d.lang, d.text))

  private def readLinks(f: JsonObject): List[GazLink] =
    f("links").flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap(_.asObject).flatMap { o =>
      for
        rel <- str(o, "type", "relation").flatMap(LinkMatch.ofLpf)
        id  <- str(o, "identifier", "id", "@id").flatMap(ExternalId.parse)
      yield GazLink(id, rel)
    }.distinctBy(_.id)

  /** The place id a record of `sourceKey` gets: its own authority id where it has one
    * (`wikidata_q42`), else `lpf_<dataset>_<the @id's last segment>`. */
  def placeIdFor(own: Option[ExternalId], ref: String, dataset: String): String =
    own.map(_.placeId).getOrElse {
      val tail = slug(ref.split("[/#=]").filter(_.trim.nonEmpty).lastOption.getOrElse(ref))
      s"lpf_${dataset}_${if tail.nonEmpty then tail.take(64) else "x" + Integer.toHexString(ref.hashCode)}"
    }

  /** One LPF feature (a line of an extract, or an element of a FeatureCollection) → a record.
    * `dataset` is the LPF dataset key (for the ids of places it creates); `defaultSource` the
    * citation a feature without its own `source` gets. */
  def fromFeature(j: Json, dataset: String, ordinal: Int, defaultSource: Option[String] = None): Either[String, GazRecord] =
    Lpf.readFeature(j, PlaceKind.Site, ordinal).flatMap { in =>
      val f   = j.asObject.getOrElse(JsonObject.empty)
      val raw = str(f, "@id", "id").orElse(f("properties").flatMap(_.asObject).flatMap(str(_, "@id", "id", "pid")))
      raw.toRight(s"feature #$ordinal has no @id").map { ref =>
        val own   = ExternalId.parse(ref)
        val names = readNames(f)
        val label = in.place.label
        val all   = if names.exists(_.name == label) then names else GazName(label) :: names
        GazRecord(
          ref = own.map(_.key).getOrElse(ref),
          own = own,
          placeId = placeIdFor(own, ref, dataset),
          label = label,
          kind = in.place.kind,
          geometry = in.place.geometry.map(GazGeometry.GeoJson(_)),
          span = f("when").orElse(f("properties").flatMap(_.asObject).flatMap(_("when"))).fold(Span.always)(span),
          names = mergeNames(all),
          links = readLinks(f).filterNot(l => own.contains(l.id)),
          source = in.place.source.orElse(defaultSource),
          properties = in.place.properties,
          descriptions = readDescriptions(f),
          itemClass = f("properties").flatMap(_.asObject).flatMap(str(_, "class")))
      }
    }

  /** What a FeatureCollection says about its dataset (`title`/`name`, `citation`, `license`, `when`);
    * a sidecar's keys win over the file's own. */
  def dataset(top: JsonObject, sidecar: Option[JsonObject] = None): LpfDataset =
    def pick(keys: String*): Option[String] = sidecar.flatMap(str(_, keys*)).orElse(str(top, keys*))
    val when = sidecar.flatMap(_("when")).orElse(top("when")).fold(Span.always)(span)
    LpfDataset(pick("title", "name", "label"), pick("citation", "bibliographicCitation", "attribution"),
      pick("license", "licence", "rights"), when)

  // ── LP-TSV ───────────────────────────────────────────────────────────────────────────────────

  /** The LP-TSV columns this reader understands (Linked Places TSV v0.5): `id title title_source
    * ccodes matches variants types lon lat geowkt start end` (`attestation_year` and the rest are
    * kept nowhere). `matches` and `variants` are `;`-separated; a variant may end in `@lang`. */
  def fromTsvRow(header: List[String], cells: List[String], dataset: String, line: Int,
      defaultSource: Option[String] = None): Either[String, GazRecord] =
    val row = header.map(_.trim.toLowerCase(java.util.Locale.ROOT)).zip(cells.map(_.trim) ++ List.fill(header.size)("")).toMap
    def get(k: String): Option[String] = row.get(k).filter(_.nonEmpty)
    (get("id"), get("title")) match
      case (None, _) => Left(s"line $line: no id")
      case (_, None) => Left(s"line $line: no title")
      case (Some(ref), Some(title)) =>
        val own = ExternalId.parse(ref)
        val variants = get("variants").toList.flatMap(_.split(";").toList).map(_.trim).filter(_.nonEmpty).map { v =>
          val at = v.lastIndexOf('@')
          if at > 0 && v.drop(at + 1).matches("[A-Za-z]{2,3}(-[A-Za-z0-9]+)?") then GazName(v.take(at).trim, v.drop(at + 1))
          else GazName(v)
        }
        val links = get("matches").toList.flatMap(_.split(";").toList).flatMap(ExternalId.parse)
          .filterNot(own.contains).distinct.map(GazLink(_, LinkMatch.Close))
        val point = for
          lon <- get("lon").flatMap(_.toDoubleOption)
          lat <- get("lat").flatMap(_.toDoubleOption)
        yield GazGeometry.GeoJson(s"""{"type":"Point","coordinates":[$lon,$lat]}""")
        val kind = get("types").flatMap(t => t.split(";").toList.flatMap(PlaceKind.parse).headOption).getOrElse(PlaceKind.Site)
        val sp = Span(get("start").flatMap(Lpf.parseYear), get("end").flatMap(Lpf.parseYear))
        val props = JsonObject.fromIterable(
          get("ccodes").map(c => "ccodes" -> Json.arr(c.split("[;,]").toList.map(_.trim).filter(_.nonEmpty).map(Json.fromString)*)).toList ++
            get("types").map(t => "types" -> Json.fromString(t)).toList)
        Right(GazRecord(
          ref = own.map(_.key).getOrElse(ref), own = own, placeId = placeIdFor(own, ref, dataset),
          label = title, kind = kind,
          geometry = point.orElse(get("geowkt").map(GazGeometry.Wkt(_))),
          span = sp, names = mergeNames(GazName(title) :: variants), links = links,
          source = get("title_source").orElse(defaultSource),
          properties = Option.when(props.nonEmpty)(Json.fromJsonObject(props).noSpaces),
          descriptions = get("description").map(GazDescription(_)).toList))

  /** Split one TSV line. LP-TSV has no quoting: a tab separates, nothing escapes. */
  def tsvCells(line: String): List[String] = line.split("\t", -1).toList

// ── the pinned extracts and their state (wire) ──────────────────────────────────────────────────

/** One pinned extract (`db/gazetteer/extracts.json`): the file the import reads, its checksum and
  * size, and where a missing copy may be downloaded from (`url`, empty = never). */
final case class GazetteerExtract(
    name: String,
    file: String,
    sha256: String,
    bytes: Long,
    features: Long,
    version: String,
    title: String,
    licence: String,
    attribution: Option[String] = None,
    url: Option[String] = None,
    built: Option[String] = None
) derives KindlingsCodecAsObject
object GazetteerExtract:
  given Schema[GazetteerExtract] = KindlingsSchema.derived[GazetteerExtract].schema

/** The manifest file itself. */
final case class GazetteerManifest(extracts: List[GazetteerExtract]) derives KindlingsCodecAsObject
object GazetteerManifest:
  given Schema[GazetteerManifest] = KindlingsSchema.derived[GazetteerManifest].schema

/** One source as `GET /api/places/sources` reports it: what is pinned or found on disk, whether the
  * file is there, and what was last imported from it (`gazetteer_sources`). */
final case class GazetteerSourceInfo(
    key: String,
    kind: GazetteerKind,
    title: String,
    file: Option[String],
    present: Boolean,
    pinnedVersion: Option[String],
    importedVersion: Option[String] = None,
    importedAt: Option[String] = None,
    places: Option[Int] = None,
    names: Option[Int] = None,
    links: Option[Int] = None,
    licence: Option[String] = None,
    citation: Option[String] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
) derives KindlingsCodecAsObject
object GazetteerSourceInfo:
  given Schema[GazetteerSourceInfo] = KindlingsSchema.derived[GazetteerSourceInfo].schema

/** What one import (or its dry run) did, per source. `created` places are new; `joined` records
  * found their place by an identifier; the name and link counts are rows inserted, removed and
  * changed — a second run of the same version reports zeros. */
final case class GazetteerLoadCounts(
    source: String,
    version: String,
    records: Long = 0,
    created: Long = 0,
    joined: Long = 0,
    placesChanged: Long = 0,
    namesAdded: Long = 0,
    namesRemoved: Long = 0,
    namesChanged: Long = 0,
    linksAdded: Long = 0,
    linksRemoved: Long = 0,
    placesRemoved: Long = 0,
    skipped: List[String] = Nil,
    descriptionsAdded: Long = 0,
    descriptionsRemoved: Long = 0,
    descriptionsChanged: Long = 0,
    /** Links whose relation or item facts (class, point, dates — V077) changed. */
    linksChanged: Long = 0,
    /** Records not kept as places of their own: a GeoNames/Wikidata region, polity or people known
      * only by a point ([[Gazetteer.dropsPointArea]]). */
    pointAreasSkipped: Long = 0
) derives KindlingsCodecAsObject:
  def +(o: GazetteerLoadCounts): GazetteerLoadCounts =
    copy(records = records + o.records, created = created + o.created, joined = joined + o.joined,
      placesChanged = placesChanged + o.placesChanged, namesAdded = namesAdded + o.namesAdded,
      namesRemoved = namesRemoved + o.namesRemoved, namesChanged = namesChanged + o.namesChanged,
      linksAdded = linksAdded + o.linksAdded, linksRemoved = linksRemoved + o.linksRemoved,
      placesRemoved = placesRemoved + o.placesRemoved, skipped = (skipped ++ o.skipped).take(20),
      descriptionsAdded = descriptionsAdded + o.descriptionsAdded,
      descriptionsRemoved = descriptionsRemoved + o.descriptionsRemoved,
      descriptionsChanged = descriptionsChanged + o.descriptionsChanged,
      linksChanged = linksChanged + o.linksChanged, pointAreasSkipped = pointAreasSkipped + o.pointAreasSkipped)

  /** Nothing would change: the store already holds exactly this version. */
  def unchanged: Boolean =
    created == 0 && placesChanged == 0 && namesAdded == 0 && namesRemoved == 0 && namesChanged == 0 &&
      linksAdded == 0 && linksRemoved == 0 && linksChanged == 0 && placesRemoved == 0 &&
      descriptionsAdded == 0 && descriptionsRemoved == 0 && descriptionsChanged == 0

  /** One line for a job summary. */
  def describe: String =
    s"$source $version: ${Plural.count(records, "record")}, ${created} new place(s), ${joined} joined an existing place, " +
      s"${placesChanged} place(s) changed, names +$namesAdded −$namesRemoved ~$namesChanged, links +$linksAdded −$linksRemoved" +
      (if linksChanged > 0 then s" ~$linksChanged" else "") +
      (if descriptionsAdded + descriptionsRemoved + descriptionsChanged > 0 then
         s", descriptions +$descriptionsAdded −$descriptionsRemoved ~$descriptionsChanged" else "") +
      (if placesRemoved > 0 then s", ${placesRemoved} place(s) removed" else "") +
      (if pointAreasSkipped > 0 then s", $pointAreasSkipped point-only region(s)/polity(ies) not kept as places" else "") +
      (if skipped.nonEmpty then s", ${skipped.size} skipped" else "")
object GazetteerLoadCounts:
  given Schema[GazetteerLoadCounts] = KindlingsSchema.derived[GazetteerLoadCounts].schema
