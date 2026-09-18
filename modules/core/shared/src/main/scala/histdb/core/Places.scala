package histdb.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Json, JsonObject}
import sttp.tapir.Schema

/** The GAZETTEER (plan §1.6a): the domain model, the wire DTOs, and a **pure** reader/writer for the
  * Linked Places Format — the World-Historical-Gazetteer interchange JSON — which is a superset of a
  * plain GeoJSON `FeatureCollection`, so both load through the same code path.
  *
  * Nothing here touches a database. `Lpf.parse` turns bytes into [[PlaceInput]] values and
  * `Lpf.render` turns them back, so `parse(render(xs)) == Right(xs)` — the round-trip the plan asks
  * for. Geometry is carried as the **GeoJSON text** it arrived as and handed to PostGIS by
  * `ST_GeomFromGeoJSON`; this module never interprets coordinates.
  */

// ── domain ──────────────────────────────────────────────────────────────────────────────────────

/** One name a place is known by. `lang` is a BCP-47-ish tag or `""` when unspecified; `period` is
  * when the name was in use (`None` = always). */
final case class PlaceName(name: String, lang: String = "", period: Option[Period] = None)

/** A hierarchy edge with its OWN validity — "Hatti part_of Anatolia" holds only for its `period`. */
final case class PlaceRelation(
    childId: String,
    parentId: String,
    kind: String = PlaceRelation.PartOf,
    period: Option[Period] = None
)

object PlaceRelation:
  val PartOf = "part_of"

/** One (entity, shape, validity) triple. The SAME name with DIFFERENT borders over time is several
  * `Place` rows sharing a [[PlaceName]]. `geometry` is GeoJSON text (`None` = hierarchy-only place,
  * e.g. a continent whose extent is the union of its members); `properties` is the importer's
  * left-over JSON object, kept verbatim so an import round-trips. */
final case class Place(
    id: String,
    label: String,
    kind: String,
    geometry: Option[String] = None,
    period: Option[Period] = None,
    source: Option[String] = None,
    properties: Option[String] = None
)

/** One place as it arrives from an import file: the row, its names and its parent edges. */
final case class PlaceInput(place: Place, names: List[PlaceName] = Nil, relations: List[PlaceRelation] = Nil)

/** The four `kind`s the store recognises. Free text in the column (an importer may carry its own
  * vocabulary), but these are what the CLI/UI offer and what [[Lpf]] maps foreign type labels onto. */
object PlaceKind:
  val Region  = "region"
  val Polity  = "polity"
  val Site    = "site"
  val Feature = "feature"
  val all: List[String] = List(Region, Polity, Site, Feature)

  /** Map a foreign type label (LPF `types[].label`, an AAT id, a Natural Earth class) onto a kind;
    * `None` when nothing recognisable is there, so the caller's default wins. */
  def parse(s: String): Option[String] =
    val t = s.trim.toLowerCase
    if t.isEmpty then None
    else if all.contains(t) then Some(t)
    else if t.contains("settlement") || t.contains("site") || t.contains("city") || t.contains("town")
      || t.contains("village") || t.contains("fort") || t.contains("temple") || t.contains("sanctuary")
    then Some(Site)
    else if t.contains("kingdom") || t.contains("empire") || t.contains("polity") || t.contains("state")
      || t.contains("dynasty") || t.contains("country") || t.contains("nation")
    then Some(Polity)
    else if t.contains("region") || t.contains("continent") || t.contains("province")
      || t.contains("territory") || t.contains("area")
    then Some(Region)
    else if t.contains("river") || t.contains("mountain") || t.contains("island") || t.contains("lake")
      || t.contains("feature") || t.contains("pass") || t.contains("valley")
    then Some(Feature)
    else None

// ── Linked Places Format (and plain GeoJSON) ───────────────────────────────────────────────────

/** Reader/writer for the Linked Places Format (LPF v1.1) and plain GeoJSON `FeatureCollection`s.
  *
  * LPF that WHG exports looks like
  * {{{
  * { "type": "FeatureCollection", "features": [
  *   { "@id": "hatti_lba", "type": "Feature",
  *     "properties": { "title": "Hittite Empire" },
  *     "when": { "timespans": [ { "start": { "in": "-1400" }, "end": { "in": "-1180" } } ] },
  *     "names": [ { "toponym": "Hatti", "lang": "hit" } ],
  *     "types": [ { "label": "kingdom" } ],
  *     "geometry": { "type": "MultiPolygon", "coordinates": [ … ] },
  *     "relations": [ { "relationType": "gvp:broaderPartitive", "relationTo": "anatolia",
  *                      "when": { "timespans": [ { "start": { "in": "-1650" } } ] } } ] } ] }
  * }}}
  * A plain GeoJSON feature has none of `@id`/`when`/`names`/`types`/`relations`; the reader then
  * falls back to the usual property spellings (`title`/`name`/`NAME`/`ADMIN`, `period`/`start`/`end`,
  * `parent`/`part_of`) and to the `kind` the caller passed in.
  */
object Lpf:

  /** hist-db uses historical reckoning (no year zero: 1 BCE = -1, 2 BCE = -2).
    * ISO 8601 / astronomical year numbering has a year zero (1 BCE = 0, 2 BCE = -1).
    * These convert between the two for LPF interop. */
  def toIso(historicalYear: Long): Long   = if historicalYear < 0 then historicalYear + 1 else historicalYear
  def fromIso(isoYear: Long): Long        = if isoYear <= 0 then isoYear - 1 else isoYear

  val Context = "https://raw.githubusercontent.com/LinkedPasts/linked-places/master/linkedplaces-context-v1.1.jsonld"

  /** LPF's part-of predicates, plus the plain spellings an ad-hoc GeoJSON uses. */
  private val PartOfPredicates =
    Set("gvp:broaderpartitive", "gvp:broaderpartitiveoverlaps", "gvp:broadermeronymic",
      "part_of", "partof", "within", "broader")

  /** Keys the writer emits itself; everything else in `properties` is kept verbatim in
    * [[Place.properties]] so a parse→render→parse cycle is the identity. */
  private val OwnKeys = Set("title", "kind", "source")

  // ── time ─────────────────────────────────────────────────────────────────────────────────────

  private val Circa = """^(?:c|ca|circa)\.?\s+""".r
  private val Bce   = """(?<![a-z])b\.?\s?c\.?(?:e\.?)?(?![a-z])""".r
  private val Ce    = """(?<![a-z])(?:a\.?\s?d\.?|c\.?\s?e\.?)(?![a-z])""".r
  private val Digits = """-?\d+""".r
  private val IsoDate = """^-?\d{1,6}-\d{2}""".r

  /** A signed year (BCE negative, **historical reckoning**) from the spellings LPF and hand-made
    * GeoJSON actually contain: `"-1400"`, `"1400 BCE"`, `"c. 1500 BC"`, `"1200 CE"`, `"AD 800"`,
    * and ISO-ish `"-0500-01-01"`. An explicit minus sign wins over an era word.
    *
    * Bare years (`"-1400"`, `"1400 BCE"`) are treated as historical reckoning — the practical
    * convention in WHG/Pleiades data. ISO 8601 dates with month/day (e.g. `"-0500-01-01"`) are
    * converted from astronomical year numbering via [[fromIso]] (year 0 = 1 BCE). */
  def parseYear(raw: String): Option[Long] =
    val t = Circa.replaceFirstIn(raw.trim.toLowerCase, "")
    if t.isEmpty then None
    else
      val isIso = IsoDate.findFirstIn(t).isDefined
      val era =
        if Bce.findFirstIn(t).isDefined then -1
        else if Ce.findFirstIn(t).isDefined then 1
        else 0
      Digits.findFirstIn(t).flatMap(_.toLongOption).map { n =>
        val y = if n < 0 then n else if era < 0 then -n else n
        if isIso then fromIso(y) else y
      }

  /** A year from any JSON scalar (LPF mixes `"-1400"` and `-1400`). */
  private def yearOf(j: Json): Option[Long] =
    j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(parseYear)).orElse(j.asNumber.map(_.toDouble.round))

  /** One end of a timespan: LPF puts the certain value in `in` and the fuzzy bounds in
    * `earliest`/`latest`; a bare scalar is accepted too. `wide` picks the outer bound. */
  private def bound(j: Json, wide: String, narrow: String): Option[Long] =
    j.asObject match
      case None      => yearOf(j)
      case Some(obj) =>
        obj("in").flatMap(yearOf)
          .orElse(obj(wide).flatMap(yearOf))
          .orElse(obj(narrow).flatMap(yearOf))

  /** LPF `when` → a closed [[Period]] of signed years: the widest span across every timespan.
    * Accepts the strict shape (`{"timespans":[{"start":…,"end":…}]}`), the flat shape
    * (`{"start":…,"end":…}`), and a bare string/number. `None` when nothing datable is present. */
  def parseWhen(j: Json): Option[Period] =
    def span(o: JsonObject): Option[Period] =
      val s = o("start").flatMap(bound(_, "earliest", "latest"))
      val e = o("end").flatMap(bound(_, "latest", "earliest"))
      (s, e) match
        case (Some(a), Some(b)) => Some(if a <= b then Period(a, b) else Period(b, a))
        case (Some(a), None)    => Some(Period(a, a))
        case (None, Some(b))    => Some(Period(b, b))
        case (None, None)       => None
    j.asObject match
      case None => yearOf(j).map(y => Period(y, y))
      case Some(obj) =>
        val spans = obj("timespans").flatMap(_.asArray).map(_.toList).getOrElse(Nil)
          .flatMap(_.asObject).flatMap(span)
        val direct = span(obj).toList
        (spans ++ direct) match
          case Nil => None
          case ps  => Some(Period(ps.map(_.start).min, ps.map(_.end).max))

  /** A [[Period]] as the strict LPF `when` the reader accepts back.
    * Years are written in historical reckoning (the practical convention in WHG/Pleiades data).
    * Use [[toIso]] explicitly when strict ISO 8601 output is needed. */
  def renderWhen(p: Period): Json = Json.obj(
    "timespans" -> Json.arr(Json.obj(
      "start" -> Json.obj("in" -> Json.fromString(p.start.toString)),
      "end"   -> Json.obj("in" -> Json.fromString(p.end.toString)))))

  // ── reading ──────────────────────────────────────────────────────────────────────────────────

  private def str(o: JsonObject, keys: String*): Option[String] =
    keys.iterator.flatMap(k => o(k).flatMap(_.asString)).map(_.trim).find(_.nonEmpty)

  private def slug(s: String): String =
    s.trim.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "").take(64)

  /** `[a, b]`, `{"start":a,"end":b}`, `"a,b"` or a single year — the shapes a hand-made GeoJSON uses
    * for `properties.period`. */
  private def propertyPeriod(o: JsonObject): Option[Period] =
    def fromPair(a: Option[Long], b: Option[Long]) = (a, b) match
      case (Some(x), Some(y)) => Some(if x <= y then Period(x, y) else Period(y, x))
      case (Some(x), None)    => Some(Period(x, x))
      case (None, Some(y))    => Some(Period(y, y))
      case _                  => None
    o("period").flatMap { p =>
      p.asArray match
        case Some(v) if v.sizeIs >= 2 => fromPair(yearOf(v(0)), yearOf(v(1)))
        case Some(v) if v.sizeIs == 1 => yearOf(v(0)).map(y => Period(y, y))
        case _ =>
          p.asString.filter(_.contains(",")) match
            case Some(s) =>
              val parts = s.split(",").map(_.trim).toList
              fromPair(parts.headOption.flatMap(parseYear), parts.drop(1).headOption.flatMap(parseYear))
            case None => parseWhen(p)
    }.orElse(fromPair(
      o("periodStart").flatMap(yearOf).orElse(o("period_start").flatMap(yearOf))
        .orElse(o("start").flatMap(yearOf)).orElse(o("minDate").flatMap(yearOf)),
      o("periodEnd").flatMap(yearOf).orElse(o("period_end").flatMap(yearOf))
        .orElse(o("end").flatMap(yearOf)).orElse(o("maxDate").flatMap(yearOf))))

  private def readNames(f: JsonObject, props: JsonObject, label: String): List[PlaceName] =
    val declared = f("names").orElse(props("names")).flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { n =>
      n.asString.map(s => PlaceName(s.trim)).orElse(n.asObject.flatMap { o =>
        str(o, "toponym", "name", "title", "label").map(t =>
          PlaceName(t, str(o, "lang", "language").getOrElse(""),
            o("when").flatMap(parseWhen).orElse(propertyPeriod(o))))
      })
    }
    // a plain GeoJSON keeps its alternates in a delimited property
    val alts = str(props, "alt_names", "altNames", "NAME_ALT", "nameAlt").toList
      .flatMap(_.split("[|;]").toList).map(_.trim).filter(_.nonEmpty).map(PlaceName(_))
    val given_ = (declared ++ alts).filter(_.name.nonEmpty)
      .distinctBy(n => (n.name.toLowerCase, n.lang.toLowerCase))
    // the display label is always a name — unless a declared entry already spells it (with a lang or
    // a validity of its own), in which case adding a bare duplicate would only be noise.
    val spelled = given_.map(_.name.toLowerCase).toSet
    val all = given_ ++ Option(PlaceName(label))
      .filter(n => n.name.nonEmpty && !spelled.contains(n.name.toLowerCase))
    all.sortBy(n => (n.name.toLowerCase, n.lang.toLowerCase))

  /** Relation arrays come in two dialects: LPF's `relations: [{relationType, relationTo, when}]` and
    * the plain one this repo's own hand-traced polygons use, `part_of: [{parent_id, period_start,
    * period_end}]`. A bare string element (or a bare `parent: "id"` property) is a `part_of` too. */
  private def readRelations(id: String, f: JsonObject, props: JsonObject): List[PlaceRelation] =
    val arrays = List(f("relations"), props("relations"), props("part_of"), props("partOf"),
      props("parents")).flatten.flatMap(_.asArray).flatMap(_.toList)
    val declared = arrays.flatMap { j =>
      j.asString.map(p => PlaceRelation(id, p.trim, PlaceRelation.PartOf, None)).orElse(j.asObject.flatMap { o =>
        val pred = str(o, "relationType", "relation_type", "kind", "type").getOrElse("gvp:broaderPartitive")
        val kind = if PartOfPredicates.contains(pred.toLowerCase) then PlaceRelation.PartOf else pred
        str(o, "relationTo", "relation_to", "parent", "parentId", "parent_id", "target")
          .map(p => PlaceRelation(id, p, kind, o("when").flatMap(parseWhen).orElse(propertyPeriod(o))))
      })
    }
    val flat = str(props, "parent", "part_of", "partOf", "parentId")
      .map(p => PlaceRelation(id, p, PlaceRelation.PartOf, None))
    (declared ++ flat).filter(_.parentId.nonEmpty)
      .distinctBy(r => (r.parentId, r.kind)).sortBy(r => (r.parentId, r.kind))

  /** One feature → one [[PlaceInput]]. `defaultKind` is what `histdb places import --kind` passes;
    * `ordinal` only matters for a feature with no id at all. */
  def readFeature(j: Json, defaultKind: String, ordinal: Int): Either[String, PlaceInput] =
    j.asObject.toRight(s"feature #$ordinal is not a JSON object").flatMap { f =>
      val props = f("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
      val label = str(props, "title", "name", "NAME", "label", "ADMIN", "NAME_LONG", "toponym")
        .orElse(str(f, "title", "label"))
        .orElse(f("names").flatMap(_.asArray).flatMap(_.headOption)
          .flatMap(n => n.asString.orElse(n.asObject.flatMap(str(_, "toponym", "name")))))
        .getOrElse("")
      val id = str(f, "@id", "id").orElse(str(props, "@id", "id", "pid"))
        .getOrElse(if label.nonEmpty then slug(label) else s"feature_$ordinal")
      if id.isEmpty then Left(s"feature #$ordinal has no id and no title")
      else
        val kind = str(props, "kind").flatMap(PlaceKind.parse)
          .orElse(f("types").orElse(props("types")).flatMap(_.asArray).map(_.toList).getOrElse(Nil)
            .flatMap(t => t.asString.orElse(t.asObject.flatMap(str(_, "label", "identifier", "sourceLabel"))))
            .flatMap(PlaceKind.parse).headOption)
          .orElse(str(props, "featureType", "feature_type", "TYPE").flatMap(PlaceKind.parse))
          .getOrElse(defaultKind)
        val period = f("when").flatMap(parseWhen).orElse(props("when").flatMap(parseWhen))
          .orElse(propertyPeriod(props))
        val leftover = props.filterKeys(k => !OwnKeys.contains(k))
        Right(PlaceInput(
          Place(
            id = id,
            label = if label.nonEmpty then label else id,
            kind = kind,
            geometry = f("geometry").filterNot(_.isNull).map(_.noSpaces),
            period = period,
            source = str(props, "source", "citation").orElse(str(f, "source")),
            properties = Some(leftover).filter(_.nonEmpty).map(Json.fromJsonObject(_).noSpaces)),
          readNames(f, props, if label.nonEmpty then label else id),
          readRelations(id, f, props)))
    }

  /** Parse an LPF document, a plain GeoJSON `FeatureCollection`, a bare array of features, or a
    * single feature. Returns the first structural error rather than a partial list — an import that
    * silently drops half its rows is worse than one that fails. */
  def parse(json: String, defaultKind: String = PlaceKind.Region): Either[String, List[PlaceInput]] =
    io.circe.parser.parse(json).left.map(e => s"invalid JSON: ${e.getMessage}").flatMap { doc =>
      val found: Option[List[Json]] =
        doc.asArray.map(_.toList)
          .orElse(doc.asObject.flatMap(_("features")).flatMap(_.asArray).map(_.toList))
          .orElse(doc.asObject.filter(o => o.contains("geometry") || o.contains("properties"))
            .map(o => List(Json.fromJsonObject(o))))
      val features: Either[String, List[Json]] =
        found.toRight("not a FeatureCollection: no `features` array and not a feature")
      features.flatMap { fs =>
        val parsed = fs.zipWithIndex.map((f, i) => readFeature(f, defaultKind, i))
        parsed.collectFirst { case Left(e) => e } match
          case Some(e) => Left(e)
          case None    => Right(parsed.collect { case Right(p) => p })
      }
    }

  // ── writing ──────────────────────────────────────────────────────────────────────────────────

  private def renderFeature(in: PlaceInput): Json =
    val leftover = in.place.properties
      .flatMap(io.circe.parser.parse(_).toOption)
      .flatMap(_.asObject).getOrElse(JsonObject.empty)
    val props = JsonObject.fromIterable(
      List("title" -> Json.fromString(in.place.label), "kind" -> Json.fromString(in.place.kind)) ++
        in.place.source.map(s => "source" -> Json.fromString(s)).toList ++
        leftover.toList)
    val fields = List.newBuilder[(String, Json)]
    fields += "@id"        -> Json.fromString(in.place.id)
    fields += "type"       -> Json.fromString("Feature")
    fields += "properties" -> Json.fromJsonObject(props)
    fields += "types"      -> Json.arr(Json.obj("label" -> Json.fromString(in.place.kind)))
    in.place.period.foreach(p => fields += "when" -> renderWhen(p))
    if in.names.nonEmpty then
      fields += "names" -> Json.arr(in.names.map { n =>
        Json.fromFields(
          List("toponym" -> Json.fromString(n.name)) ++
            (if n.lang.isEmpty then Nil else List("lang" -> Json.fromString(n.lang))) ++
            n.period.map(p => "when" -> renderWhen(p)).toList)
      }*)
    if in.relations.nonEmpty then
      fields += "relations" -> Json.arr(in.relations.map { r =>
        Json.fromFields(
          List(
            "relationType" -> Json.fromString(
              if r.kind == PlaceRelation.PartOf then "gvp:broaderPartitive" else r.kind),
            "relationTo" -> Json.fromString(r.parentId)) ++
            r.period.map(p => "when" -> renderWhen(p)).toList)
      }*)
    fields += "geometry" -> in.place.geometry
      .flatMap(io.circe.parser.parse(_).toOption).getOrElse(Json.Null)
    Json.fromFields(fields.result())

  /** Serialise back to LPF. `parse(render(xs)) == Right(xs)` for anything `parse` produced. */
  def render(items: List[PlaceInput]): String =
    Json.obj(
      "type"     -> Json.fromString("FeatureCollection"),
      "@context" -> Json.fromString(Context),
      "features" -> Json.arr(items.map(renderFeature)*)
    ).spaces2

// ── wire DTOs ───────────────────────────────────────────────────────────────────────────────────

/** A gazetteer row as the API returns it. `names` is the comma-joined alternates (the store's arrays
  * are flattened on the wire exactly as `SearchHit` flattens `topics`). */
final case class PlaceSummary(
    id: String,
    label: String,
    kind: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    source: Option[String],
    names: String,
    /** `properties.contested` — the shape is disputed (overlapping claims, a debated existence).
      * Surfaced everywhere a label is drawn, so nobody mistakes a hand-traced guess for a fact. */
    contested: Boolean = false
) derives KindlingsCodecAsObject
object PlaceSummary:
  given Schema[PlaceSummary] = KindlingsSchema.derived[PlaceSummary].schema

/** `GET /api/places?q=` — a search result: the row, which of its names matched, and how many
  * findings are assigned to it (through `finding_places`, so it reflects the last `places rebuild`). */
final case class PlaceHit(
    id: String,
    label: String,
    kind: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    source: Option[String],
    names: String,
    matchedName: Option[String],
    findings: Int,
    contested: Boolean = false
) derives KindlingsCodecAsObject:
  def toSummary: PlaceSummary =
    PlaceSummary(id, label, kind, periodStart, periodEnd, source, names, contested)
object PlaceHit:
  given Schema[PlaceHit] = KindlingsSchema.derived[PlaceHit].schema

/** `GET /api/places/{id}?asOf=` — the row plus its GeoJSON geometry and the hierarchy AS OF `asOf`
  * (only edges whose own `period` covers it). `geometry` is GeoJSON **text**; a place with no shape
  * of its own reports the union of its children's, which is what a continent's overlay draws. */
final case class PlaceDetail(
    place: PlaceSummary,
    geometry: Option[String],
    parents: List[PlaceSummary],
    children: List[PlaceSummary],
    findings: Int,
    asOf: Option[Long],
    /** Mirrors `place.contested`, so a client drawing the overlay does not have to reach inside. */
    contested: Boolean = false
) derives KindlingsCodecAsObject
object PlaceDetail:
  given Schema[PlaceDetail] = KindlingsSchema.derived[PlaceDetail].schema

/** One row of the hierarchy walk (`places` MCP tool / `histdb places show --tree`): `depth` is the
  * distance from a root, so the caller renders an indented list without re-walking anything. */
final case class PlaceTreeRow(id: String, label: String, kind: String, depth: Int, findings: Int,
    contested: Boolean = false) derives KindlingsCodecAsObject
object PlaceTreeRow:
  given Schema[PlaceTreeRow] = KindlingsSchema.derived[PlaceTreeRow].schema

/** `POST /api/places/import` — what the upsert wrote. */
final case class PlaceImportResult(places: Int, names: Int, relations: Int, failed: List[String] = Nil)
    derives KindlingsCodecAsObject
object PlaceImportResult:
  given Schema[PlaceImportResult] = KindlingsSchema.derived[PlaceImportResult].schema

/** `POST /api/places/rebuild` — the materialisation of `finding_places`. `byGeometry` counts links
  * made by containment, `byName` those made by the `region_name` fallback (a finding can have both);
  * `unlocated` is how many findings ended up in no place at all. */
final case class PlaceRebuildResult(
    links: Int,
    byGeometry: Int,
    byName: Int,
    located: Int,
    unlocated: Int,
    places: Int
) derives KindlingsCodecAsObject
object PlaceRebuildResult:
  given Schema[PlaceRebuildResult] = KindlingsSchema.derived[PlaceRebuildResult].schema
