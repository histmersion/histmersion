package com.histmersion
package api.catalogue
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import hearth.kindlings.jsoniterjson.{Json, JsonObject}

import com.histmersion.util.problem.{External, Problem}

import com.histmersion.api.{OpenSide, Span, Years}
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

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
  * A plain GeoJSON feature has none of `@id`/`when`/`names`/`types`/`relations`; the reader then falls back to the
  * usual property spellings (`title`/`name`/`NAME`/`ADMIN`, `period`/`start`/`end`, `parent`/`part_of`) and to the
  * `kind` the caller passed in.
  */
@atBoundary(BoundaryKind.Json, "reads Linked Places Format JSON into place records")
object Lpf:
  object Bound:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
  type Document = Document.T
  object Document extends Newtype[String, Pure]

  /** Histmersion uses historical reckoning (no year zero: 1 BCE = -1, 2 BCE = -2). ISO 8601 / astronomical year
    * numbering has a year zero (1 BCE = 0, 2 BCE = -1). These convert between the two for LPF interop.
    */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def toIso(historicalYear: Year): Years.Astronomical = Years.toAstronomical(historicalYear)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def fromIso(isoYear: Years.Astronomical): Year = Years.fromAstronomical(isoYear)

  val Context = "https://raw.githubusercontent.com/LinkedPasts/linked-places/master/linkedplaces-context-v1.1.jsonld"

  private val PartOfPredicates =
    Set(
      "gvp:broaderpartitive",
      "gvp:broaderpartitiveoverlaps",
      "gvp:broadermeronymic",
      "part_of",
      "partof",
      "within",
      "broader"
    )

  private val OwnKeys = Set("title", "kind", "source")

  private val Circa = """^(?:c|ca|circa)\.?\s+""".r
  // An era word stands alone: not preceded by a letter (`(?:^|[^a-z])`, which consumes that character, in place
  // of a look-behind, which Scala.js cannot compile) and not followed by one (a look-ahead, which it can).
  private val Bce = """(?:^|[^a-z])b\.?\s?c\.?(?:e\.?)?(?![a-z])""".r
  private val Ce = """(?:^|[^a-z])(?:a\.?\s?d\.?|c\.?\s?e\.?)(?![a-z])""".r
  private val Digits = """-?\d+""".r
  private val IsoDate = """^-?\d{1,6}-\d{2}""".r

  /** Bare years (`"-1400"`, `"1400 BCE"`) are historical reckoning, the practical convention in WHG/Pleiades data; ISO
    * 8601 dates with month and day (`"-0500-01-01"`) are astronomical and converted via [[fromIso]].
    */
  def parseYear(raw: String): Option[Year] =
    val t = Circa.replaceFirstIn((raw).trim.toLowerCase, "")
    if t.isEmpty then None
    else
      val isIso = IsoDate.findFirstIn(t).isDefined
      val era =
        if Bce.findFirstIn(t).isDefined then -1
        else if Ce.findFirstIn(t).isDefined then 1
        else 0
      Digits.findFirstIn(t).flatMap(_.toLongOption).map { n =>
        val y = if n < 0 then n else if era < 0 then -n else n
        if isIso then fromIso(Years.Astronomical(y)) else Year(y)
      }

  /** LPF mixes `"-1400"` and `-1400`. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def yearOf(j: Json): Option[Year] =
    Years.fromJson(j, t => parseYear(t.value))

  /** LPF puts the certain value in `in` and the fuzzy bounds in `earliest`/`latest`. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def bound(j: Json, wide: Bound.Wire, narrow: Bound.Wire): Option[Year] =
    j.asObject match
      case None      => (yearOf(j)).map(refactorValue22 => Year((refactorValue22).value))
      case Some(obj) =>
        (obj("in")
          .flatMap(yearOf)
          .orElse(obj((wide).value).flatMap(yearOf))
          .orElse(obj((narrow).value).flatMap(yearOf)))
          .map(refactorValue => Year((refactorValue).value))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def parseWhen(j: Json): Option[Span] =
    def span(o: JsonObject): Option[Span] =
      val s = o("start").flatMap(bound(_, Bound.Wire("earliest"), Bound.Wire("latest")))
      val e = o("end").flatMap(bound(_, Bound.Wire("latest"), Bound.Wire("earliest")))
      (s, e) match
        case (Some(a), Some(b)) => Some(if a <= b then Span(s, e) else Span(e, s))
        case (None, None)       => None
        case _                  => Some(Span(s, e))
    j.asObject match
      case None      => yearOf(j).map(y => Span(Some(y), Some(y)))
      case Some(obj) =>
        val spans = obj("timespans")
          .flatMap(_.asArray)
          .map(_.toList)
          .getOrElse(Nil)
          .flatMap(_.asObject)
          .flatMap(span)
        (spans ++ span(obj).toList) match
          case Nil => None
          case ps  =>
            Some(
              Span(
                if ps.forall(_.from.isDefined) then Some(ps.flatMap(_.from).minBy[Long](_.value)) else None,
                if ps.forall(_.to.isDefined) then Some(ps.flatMap(_.to).maxBy[Long](_.value)) else None
              )
            )

  /** Years in historical reckoning, the practical convention in WHG/Pleiades data. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def renderWhen(p: Period, open: Option[OpenSide] = None): Json =
    val (s, e) = OpenSide.bounds(Some(p), open)
    Json.obj(
      "timespans" -> Json.arr(
        Json.fromFields(
          s.map(v => "start" -> Json.obj("in" -> Json.fromString(v.toString))).toList ++
            e.map(v => "end" -> Json.obj("in" -> Json.fromString(v.toString))).toList
        )
      )
    )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def parseKind(s: String): Option[Place.KindValue] =
    Place.Kind.parse(Place.Kind.ParseS(s)).map(k => Place.KindValue(k.value))

  private def str(o: JsonObject, keys: String*): Option[String] =
    (keys.iterator
      .flatMap(k => o(k).flatMap(_.asString))
      .map(_.trim)
      .find(_.nonEmpty))
      .map(refactorValue25 => (refactorValue25))

  private def slug(s: String): String =
    s.trim.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "").take(64)

  private def propertyPeriod(o: JsonObject): Option[Span] =
    def fromPair(a: Option[Year], b: Option[Year]): Option[Span] = (a, b) match
      case (Some(x), Some(y)) => Some(if x <= y then Span(a, b) else Span(b, a))
      case (None, None)       => None
      case _                  => Some(Span(a, b))
    o("period")
      .flatMap { p =>
        p.asArray match
          case Some(v) if v.sizeIs >= 2 => fromPair(yearOf(v(0)), yearOf(v(1)))
          case Some(v) if v.sizeIs == 1 => yearOf(v(0)).map(y => Span(Some(y), Some(y)))
          case _                        =>
            p.asString.filter(_.contains(",")) match
              case Some(s) =>
                val parts = s.split(",").map(_.trim).toList
                fromPair(parts.headOption.flatMap(parseYear), parts.drop(1).headOption.flatMap(parseYear))
              case None => parseWhen(p)
      }
      .orElse(
        fromPair(
          o("periodStart")
            .flatMap(yearOf)
            .orElse(o("period_start").flatMap(yearOf))
            .orElse(o("start").flatMap(yearOf))
            .orElse(o("minDate").flatMap(yearOf)),
          o("periodEnd")
            .flatMap(yearOf)
            .orElse(o("period_end").flatMap(yearOf))
            .orElse(o("end").flatMap(yearOf))
            .orElse(o("maxDate").flatMap(yearOf))
        )
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def readNames(f: JsonObject, props: JsonObject, label: String): List[Place.Name] =
    val declared = f("names").orElse(props("names")).flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { n =>
      n.asString
        .map(s => Place.Name(Place.Name.Value(s.trim)))
        .orElse(n.asObject.flatMap { o =>
          str(o, "toponym", "name", "title", "label").map { t =>
            val (period, open) =
              OpenSide.split(o("when").flatMap(parseWhen).orElse(propertyPeriod(o)).getOrElse(Span.always))
            Place.Name(Place.Name.Value(t), Place.Name.Lang(str(o, "lang", "language").getOrElse("")), period, open)
          }
        })
    }
    val alts = str(props, "alt_names", "altNames", "NAME_ALT", "nameAlt").toList
      .flatMap((_).split("[|;]").toList)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(a => Place.Name(Place.Name.Value(a)))
    val given_ = (declared ++ alts)
      .filter(_.name.value.nonEmpty)
      .distinctBy(n => (n.name.value.toLowerCase, n.lang.value.toLowerCase))
    val spelled = given_.map(_.name.value.toLowerCase).toSet
    val all = given_ ++ Option(Place.Name(Place.Name.Value(label)))
      .filter(n => n.name.value.nonEmpty && !spelled.contains(n.name.value.toLowerCase))
    all.sortBy(n => (n.name.value.toLowerCase, n.lang.value.toLowerCase))

  /** Relation arrays come in two dialects: LPF's `relations: [{relationType, relationTo, when}]` and the plain
    * `part_of: [{parent_id, period_start, period_end}]` of hand-traced polygons.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def readRelations(id: String, f: JsonObject, props: JsonObject): List[Place.Relation] =
    val arrays = List(f("relations"), props("relations"), props("part_of"), props("partOf"), props("parents")).flatten
      .flatMap(_.asArray)
      .flatMap(_.toList)
    val declared = arrays.flatMap { j =>
      j.asString
        .map(p =>
          Place.Relation(
            Place.Relation.ChildId(id),
            Place.Relation.ParentId(p.trim),
            Place.Relation.Kind(Place.Relation.PartOf),
            None
          )
        )
        .orElse(j.asObject.flatMap { o =>
          val pred = str(o, "relationType", "relation_type", "kind", "type").getOrElse("gvp:broaderPartitive")
          val kind = if PartOfPredicates.contains(pred.toLowerCase) then Place.Relation.PartOf else pred
          str(o, "relationTo", "relation_to", "parent", "parentId", "parent_id", "target")
            .map { p =>
              val (period, open) =
                OpenSide.split(o("when").flatMap(parseWhen).orElse(propertyPeriod(o)).getOrElse(Span.always))
              Place.Relation(
                Place.Relation.ChildId(id),
                Place.Relation.ParentId(p),
                Place.Relation.Kind(kind),
                period,
                open
              )
            }
        })
    }
    val flat = str(props, "parent", "part_of", "partOf", "parentId")
      .map(p =>
        Place.Relation(
          Place.Relation.ChildId(id),
          Place.Relation.ParentId(p),
          Place.Relation.Kind(Place.Relation.PartOf),
          None
        )
      )
    (declared ++ flat)
      .filter(_.parentId.value.nonEmpty)
      .distinctBy(r => (r.parentId, r.kind))
      .sortBy(r => (r.parentId.value, r.kind.value))

  def readFeature(j: Json, defaultKind: Place.KindValue, ordinal: Int): Either[Problem, Place.Input] =
    j.asObject
      .toRight(
        Problem.Io.Corrupt(Problem.Io.Format.Json, Problem.Where(s"feature #$ordinal"), Problem.Io.Damage.Undecodable)
      )
      .flatMap { f =>
        val props = f("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
        val label = str(props, "title", "name", "NAME", "label", "ADMIN", "NAME_LONG", "toponym")
          .orElse(str(f, "title", "label"))
          .orElse(
            f("names")
              .flatMap(_.asArray)
              .flatMap(_.headOption)
              .flatMap(n => n.asString.orElse(n.asObject.flatMap(str(_, "toponym", "name"))))
          )
          .getOrElse("")
        val id = str(f, "@id", "id")
          .orElse(str(props, "@id", "id", "pid"))
          .getOrElse(if label.nonEmpty then slug(label) else s"feature_$ordinal")
        if id.isEmpty then
          Left(
            Problem.Invalid.Missing(Problem.Field("id"), Vector(Problem.Field("features"), Problem.Field(s"$ordinal")))
          )
        else
          val kind = str(props, "kind")
            .flatMap(parseKind)
            .orElse(
              f("types")
                .orElse(props("types"))
                .flatMap(_.asArray)
                .map(_.toList)
                .getOrElse(Nil)
                .flatMap(t => t.asString.orElse(t.asObject.flatMap(str(_, "label", "identifier", "sourceLabel"))))
                .flatMap(parseKind)
                .headOption
            )
            .orElse(
              str(props, "featureType", "feature_type", "TYPE").flatMap(parseKind)
            )
            .getOrElse(defaultKind)
          val (period, open) = OpenSide.split(
            f("when")
              .flatMap(parseWhen)
              .orElse(props("when").flatMap(parseWhen))
              .orElse(propertyPeriod(props))
              .getOrElse(Span.always)
          )
          val leftover = props.filterKeys(k => !OwnKeys.contains(k))
          Right(
            Place.Input(
              Place(
                id = Place.Id(id),
                label = Place.Label(if label.nonEmpty then label else id),
                kind = kind,
                geometry = f("geometry").filterNot(_.isNull).map(g => Place.Geometry(g.noSpaces)),
                period = period,
                source = str(props, "source", "citation").orElse(str(f, "source")).map(Place.Source(_)),
                properties =
                  Some(leftover).filter(_.nonEmpty).map(o => Place.Properties(Json.fromJsonObject(o).noSpaces)),
                open = open
              ),
              readNames(f, props, if label.nonEmpty then label else id),
              readRelations(id, f, props)
            )
          )
      }

  def parse(
      json: String,
      defaultKind: Place.KindValue = Place.KindValue(Place.Kind.Region)
  ): Either[Problem, List[Place.Input]] =
    RawJson
      .parse((json))
      .left
      .map(e =>
        Problem.Io.Corrupt(
          Problem.Io.Format.Json,
          Problem.Where("LPF"),
          Problem.Io.Damage.Unreadable(External.Reported.of(s"$e"))
        )
      )
      .flatMap { doc =>
        val found: Option[List[Json]] =
          doc.asArray
            .map(_.toList)
            .orElse(doc.asObject.flatMap(_("features")).flatMap(_.asArray).map(_.toList))
            .orElse(
              doc.asObject
                .filter(o => o.contains("geometry") || o.contains("properties"))
                .map(o => List(Json.fromJsonObject(o)))
            )
        val features: Either[Problem, List[Json]] =
          found.toRight(
            Problem.Invalid.Missing.here(Problem.Field("features"))
          )
        features.flatMap { fs =>
          val parsed = fs.zipWithIndex.map((f, i) => readFeature(f, defaultKind, (i)))
          parsed.collectFirst { case Left(e) => e } match
            case Some(e) => Left(e)
            case None    => Right(parsed.collect { case Right(p) => p })
        }
      }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def renderFeature(in: Place.Input): Json =
    val leftover = in.place.properties
      .flatMap(p => RawJson.parse(p.value).toOption)
      .flatMap(_.asObject)
      .getOrElse(JsonObject.empty)
    val props = JsonObject.fromIterable(
      List("title" -> Json.fromString(in.place.label.value), "kind" -> Json.fromString(in.place.kind.value)) ++
        in.place.source.map(s => "source" -> Json.fromString(s.value)).toList ++
        leftover.toList
    )
    val fields = List.newBuilder[(String, Json)]
    fields += "@id" -> Json.fromString(in.place.id.value)
    fields += "type" -> Json.fromString("Feature")
    fields += "properties" -> Json.fromJsonObject(props)
    fields += "types" -> Json.arr(Json.obj("label" -> Json.fromString(in.place.kind.value)))
    in.place.period.foreach(p => fields += "when" -> renderWhen(p, in.place.open))
    if in.names.nonEmpty then
      fields += "names" -> Json.arr(in.names.map { n =>
        Json.fromFields(
          List("toponym" -> Json.fromString(n.name.value)) ++
            (if n.lang.value.isEmpty then Nil else List("lang" -> Json.fromString(n.lang.value))) ++
            n.period.map(p => "when" -> renderWhen(p, n.open)).toList
        )
      }*)
    if in.relations.nonEmpty then
      fields += "relations" -> Json.arr(in.relations.map { r =>
        Json.fromFields(
          List(
            "relationType" -> Json.fromString(if r.kind == Place.Relation.Kind(Place.Relation.PartOf) then
              "gvp:broaderPartitive"
            else r.kind.value),
            "relationTo" -> Json.fromString(r.parentId.value)
          ) ++
            r.period.map(p => "when" -> renderWhen(p, r.open)).toList
        )
      }*)
    fields += "geometry" -> in.place.geometry
      .flatMap(g => RawJson.parse(g.value).toOption)
      .getOrElse(Json.Null)
    Json.fromFields(fields.result())

  def render(items: List[Place.Input]): Document =
    Document(
      Json
        .obj(
          "type" -> Json.fromString("FeatureCollection"),
          "@context" -> Json.fromString(Context),
          "features" -> Json.arr(items.map(renderFeature)*)
        )
        .spaces2
    )
