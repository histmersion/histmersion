package com.histmersion.api.catalogue
import io.circe.{Json, JsonObject}
import com.histmersion.api.{OpenSide, Span, Years}
import com.histmersion.api.given

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
object Lpf:
  object Feature:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Bound:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Document = Document.T
  object Document extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Histmersion uses historical reckoning (no year zero: 1 BCE = -1, 2 BCE = -2). ISO 8601 / astronomical year
    * numbering has a year zero (1 BCE = 0, 2 BCE = -1). These convert between the two for LPF interop.
    */

  type ParseDefaultKind = _root_.com.histmersion.api.Api.ArticleQuery.Kind.Wire
  val ParseDefaultKind: _root_.com.histmersion.api.Api.ArticleQuery.Kind.Wire.type = _root_.com.histmersion.api.Api.ArticleQuery.Kind.Wire

  def toIso(historicalYear: com.histmersion.api.library.Tree.Year): com.histmersion.api.catalogue.Period.IsoYear = Years.toAstronomical(historicalYear)
  def fromIso(isoYear: com.histmersion.api.catalogue.Period.IsoYear): com.histmersion.api.library.Tree.Year = Years.fromAstronomical(isoYear)

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
  def parseYear(raw: String): Option[com.histmersion.api.library.Tree.Year] =
    val t = Circa.replaceFirstIn((raw).value.trim.toLowerCase, "")
    if t.isEmpty then None
    else
      val isIso = IsoDate.findFirstIn(t).isDefined
      val era =
        if Bce.findFirstIn(t).isDefined then -1
        else if Ce.findFirstIn(t).isDefined then 1
        else 0
      Digits.findFirstIn(t).flatMap(_.toLongOption).map { n =>
        val y = if n < 0 then n else if era < 0 then -n else n
        if isIso then _root_.com.histmersion.api.library.Tree.Year((fromIso(_root_.com.histmersion.api.catalogue.Period.IsoYear(y))).value) else _root_.com.histmersion.api.library.Tree.Year(y)
      }

  /** LPF mixes `"-1400"` and `-1400`. */
  private def yearOf(j: Json): Option[com.histmersion.api.library.Tree.Year] =
    Years.fromJson(j, parseYear)

  /** LPF puts the certain value in `in` and the fuzzy bounds in `earliest`/`latest`. */
  private def bound(j: Json, wide: com.histmersion.api.catalogue.Lpf.Bound.Wire, narrow: com.histmersion.api.catalogue.Lpf.Bound.Wire): Option[com.histmersion.api.library.Tree.Year] =
    j.asObject match
      case None      => (yearOf(j)).map(refactorValue22 => _root_.com.histmersion.api.library.Tree.Year((refactorValue22).value))
      case Some(obj) =>
        (obj("in")
          .flatMap(yearOf)
          .orElse(obj((wide).value).flatMap(yearOf))
          .orElse(obj((narrow).value).flatMap(yearOf))).map(refactorValue => _root_.com.histmersion.api.library.Tree.Year((refactorValue).value))

  def parseWhen(j: Json): Option[Span] =
    def span(o: JsonObject): Option[Span] =
      val s = o("start").flatMap(bound(_, _root_.com.histmersion.api.catalogue.Lpf.Bound.Wire("earliest"), _root_.com.histmersion.api.catalogue.Lpf.Bound.Wire("latest")))
      val e = o("end").flatMap(bound(_, _root_.com.histmersion.api.catalogue.Lpf.Bound.Wire("latest"), _root_.com.histmersion.api.catalogue.Lpf.Bound.Wire("earliest")))
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
                if ps.forall(_.from.isDefined) then Some(ps.flatMap(_.from).min) else None,
                if ps.forall(_.to.isDefined) then Some(ps.flatMap(_.to).max) else None
              )
            )

  /** Years in historical reckoning, the practical convention in WHG/Pleiades data. */
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

  private def str(o: JsonObject, keys: String*): Option[String] =
    (keys.iterator.flatMap(k => o(k).flatMap(_.asString)).map(_.trim).find(_.nonEmpty)).map(refactorValue25 => (refactorValue25))

  private def slug(s: String): com.histmersion.api.research.ReviewGoal.Slug =
    _root_.com.histmersion.api.research.ReviewGoal.Slug((s).value.trim.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "").take(64))

  private def propertyPeriod(o: JsonObject): Option[Span] =
    def fromPair(a: Option[com.histmersion.api.library.Tree.Year], b: Option[com.histmersion.api.library.Tree.Year]): Option[Span] = (a, b) match
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

  private def readNames(f: JsonObject, props: JsonObject, label: com.histmersion.api.catalogue.Lpf.Wire): List[Place.Name] =
    val declared = f("names").orElse(props("names")).flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { n =>
      n.asString
        .map(s => Place.Name(_root_.com.histmersion.api.catalogue.Place.Name.Value(s.trim)))
        .orElse(n.asObject.flatMap { o =>
          str(o, "toponym", "name", "title", "label").map { t =>
            val (period, open) =
              OpenSide.split(o("when").flatMap(parseWhen).orElse(propertyPeriod(o)).getOrElse(Span.always))
            Place.Name(t, str(o, "lang", "language").getOrElse(""), period, open)
          }
        })
    }
    val alts = str(props, "alt_names", "altNames", "NAME_ALT", "nameAlt").toList
      .flatMap((_).value.split("[|;]").toList)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Place.Name(_root_.com.histmersion.api.catalogue.Place.Name.Value(_)))
    val given_ = (declared ++ alts)
      .filter(_.name.nonEmpty)
      .distinctBy(n => (n.name.toLowerCase, n.lang.toLowerCase))
    val spelled = given_.map(_.name.toLowerCase).toSet
    val all = given_ ++ Option(Place.Name(label))
      .filter(n => n.name.nonEmpty && !spelled.contains(n.name.toLowerCase))
    all.sortBy(n => (n.name.toLowerCase, n.lang.toLowerCase))

  /** Relation arrays come in two dialects: LPF's `relations: [{relationType, relationTo, when}]` and the plain
    * `part_of: [{parent_id, period_start, period_end}]` of hand-traced polygons.
    */
  private def readRelations(id: com.histmersion.api.catalogue.Lpf.Feature.Id, f: JsonObject, props: JsonObject): List[Place.Relation] =
    val arrays = List(f("relations"), props("relations"), props("part_of"), props("partOf"), props("parents")).flatten
      .flatMap(_.asArray)
      .flatMap(_.toList)
    val declared = arrays.flatMap { j =>
      j.asString
        .map(p => Place.Relation(id, _root_.com.histmersion.api.catalogue.Place.Relation.ParentId(p.trim), _root_.com.histmersion.api.catalogue.Place.Relation.Kind(Place.Relation.PartOf), None))
        .orElse(j.asObject.flatMap { o =>
          val pred = str(o, "relationType", "relation_type", "kind", "type").getOrElse("gvp:broaderPartitive")
          val kind = if PartOfPredicates.contains(pred.toLowerCase) then Place.Relation.PartOf else pred
          str(o, "relationTo", "relation_to", "parent", "parentId", "parent_id", "target")
            .map { p =>
              val (period, open) =
                OpenSide.split(o("when").flatMap(parseWhen).orElse(propertyPeriod(o)).getOrElse(Span.always))
              Place.Relation(id, p, kind, period, open)
            }
        })
    }
    val flat = str(props, "parent", "part_of", "partOf", "parentId")
      .map(p => Place.Relation(id, p, _root_.com.histmersion.api.catalogue.Place.Relation.Kind(Place.Relation.PartOf), None))
    (declared ++ flat)
      .filter(_.parentId.nonEmpty)
      .distinctBy(r => (r.parentId, r.kind))
      .sortBy(r => (r.parentId, r.kind))

  def readFeature(j: Json, defaultKind: com.histmersion.api.Api.ArticleQuery.Kind.Wire, ordinal: Int): Either[com.histmersion.api.catalogue.Lpf.Problem, Place.Input] =
    j.asObject.toRight(s"feature #$ordinal is not a JSON object").flatMap { f =>
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
      if id.isEmpty then Left(s"feature #$ordinal has no id and no title")
      else
        val kind = str(props, "kind")
          .flatMap(com.histmersion.api.catalogue.Place.Kind.parse)
          .orElse(
            f("types")
              .orElse(props("types"))
              .flatMap(_.asArray)
              .map(_.toList)
              .getOrElse(Nil)
              .flatMap(t => t.asString.orElse(t.asObject.flatMap(str(_, "label", "identifier", "sourceLabel"))))
              .flatMap(com.histmersion.api.catalogue.Place.Kind.parse)
              .headOption
          )
          .orElse(
            str(props, "featureType", "feature_type", "TYPE").flatMap(com.histmersion.api.catalogue.Place.Kind.parse)
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
              id = com.histmersion.api.catalogue.Place.Id(id),
              label = if label.nonEmpty then label else id,
              kind = kind,
              geometry = f("geometry").filterNot(_.isNull).map(_root_.com.histmersion.api.catalogue.Place.Geometry(_.noSpaces)),
              period = period,
              source = str(props, "source", "citation").orElse(str(f, "source")),
              properties = Some(leftover).filter(_.nonEmpty).map(_root_.com.histmersion.api.catalogue.Place.Properties(Json.fromJsonObject(_).noSpaces)),
              open = open
            ),
            readNames(f, props, if label.nonEmpty then label else id),
            readRelations(id, f, props)
          )
        )
    }

  def parse(
      json: String,
      defaultKind: com.histmersion.api.catalogue.Lpf.ParseDefaultKind = _root_.com.histmersion.api.catalogue.Lpf.ParseDefaultKind(com.histmersion.api.catalogue.Place.Kind.Region)
  ): Either[com.histmersion.api.catalogue.Lpf.Problem, List[Place.Input]] =
    io.circe.parser.parse((json).value).left.map(e => s"invalid JSON: ${e.getMessage}").flatMap { doc =>
      val found: Option[List[Json]] =
        doc.asArray
          .map(_.toList)
          .orElse(doc.asObject.flatMap(_("features")).flatMap(_.asArray).map(_.toList))
          .orElse(
            doc.asObject
              .filter(o => o.contains("geometry") || o.contains("properties"))
              .map(o => List(Json.fromJsonObject(o)))
          )
      val features: Either[com.histmersion.api.catalogue.Lpf.Problem, List[Json]] =
        found.toRight(_root_.com.histmersion.api.catalogue.Lpf.Problem("not a FeatureCollection: no `features` array and not a feature"))
      features.flatMap { fs =>
        val parsed = fs.zipWithIndex.map((f, i) => readFeature(f, defaultKind, (i)))
        parsed.collectFirst { case Left(e) => e } match
          case Some(e) => Left(e)
          case None    => Right(parsed.collect { case Right(p) => p })
      }
    }

  private def renderFeature(in: Place.Input): Json =
    val leftover = in.place.properties
      .flatMap(io.circe.parser.parse(_).toOption)
      .flatMap(_.asObject)
      .getOrElse(JsonObject.empty)
    val props = JsonObject.fromIterable(
      List("title" -> Json.fromString(in.place.label), "kind" -> Json.fromString(in.place.kind)) ++
        in.place.source.map(s => "source" -> Json.fromString(s)).toList ++
        leftover.toList
    )
    val fields = List.newBuilder[(String, Json)]
    fields += "@id" -> Json.fromString(in.place.id.value)
    fields += "type" -> Json.fromString("Feature")
    fields += "properties" -> Json.fromJsonObject(props)
    fields += "types" -> Json.arr(Json.obj("label" -> Json.fromString(in.place.kind)))
    in.place.period.foreach(p => fields += "when" -> renderWhen(p, in.place.open))
    if in.names.nonEmpty then
      fields += "names" -> Json.arr(in.names.map { n =>
        Json.fromFields(
          List("toponym" -> Json.fromString(n.name)) ++
            (if n.lang.isEmpty then Nil else List("lang" -> Json.fromString(n.lang))) ++
            n.period.map(p => "when" -> renderWhen(p, n.open)).toList
        )
      }*)
    if in.relations.nonEmpty then
      fields += "relations" -> Json.arr(in.relations.map { r =>
        Json.fromFields(
          List(
            "relationType" -> Json.fromString(if r.kind == _root_.com.histmersion.api.catalogue.Place.Relation.Kind(Place.Relation.PartOf) then "gvp:broaderPartitive"
            else r.kind),
            "relationTo" -> Json.fromString(r.parentId)
          ) ++
            r.period.map(p => "when" -> renderWhen(p, r.open)).toList
        )
      }*)
    fields += "geometry" -> in.place.geometry
      .flatMap(io.circe.parser.parse(_).toOption)
      .getOrElse(Json.Null)
    Json.fromFields(fields.result())

  def render(items: List[Place.Input]): com.histmersion.api.catalogue.Lpf.Document =
    _root_.com.histmersion.api.catalogue.Lpf.Document(Json
      .obj(
        "type" -> Json.fromString("FeatureCollection"),
        "@context" -> Json.fromString(Context),
        "features" -> Json.arr(items.map(renderFeature)*)
      )
      .spaces2)
