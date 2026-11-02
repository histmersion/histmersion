package com.histmersion.api.catalogue
import io.circe.{Json, JsonObject}
import com.histmersion.values.PlaceKind
import com.histmersion.api.{LpfDataset, Span, Validity}
import com.histmersion.api.library.ExternalId
import com.histmersion.api.given

object Gazetteer:

  def sourceKey(kind: GazetteerKind, dataset: Option[String] = None): String = kind match
    case GazetteerKind.Lpf => s"lpf:${dataset.getOrElse("")}"
    case k                 => k.wire

  def datasetSlug(fileName: String): String =
    val base = fileName
      .replaceAll("(?i)(\\.meta)?\\.(jsonld|json|geojson|ndjson|jsonl|tsv)(\\.gz)?$", "")
      .replaceAll("(?i)\\.lpf$", "")
    slug(base).take(48)

  private def slug(s: String): String =
    s.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "")

  def rank(m: PlaceMatch): Int = m.importSource.flatMap(GazetteerKind.ofKey) match
    case None                         => if m.importSource.isEmpty then 0 else 5
    case Some(GazetteerKind.Lpf)      => 1
    case Some(GazetteerKind.Wikidata) => 2
    case Some(GazetteerKind.Tgn)      => 3
    case Some(GazetteerKind.Geonames) => 4

  val ImportedSources: List[String] =
    List(GazetteerKind.Wikidata, GazetteerKind.Geonames, GazetteerKind.Tgn).map(k => sourceKey(k))

  def canonical(byOwn: List[PlaceMatch], byLinks: List[PlaceMatch] = Nil): Option[String] =
    (byOwn.map(_ -> 0) ++ byLinks.map(_ -> 1))
      .groupBy(_._1.placeId)
      .values
      .map(_.minBy((m, via) => (if m.native then 0 else 1, via)))
      .toList
      .sortBy((m, via) => (if m.native then 0 else 1, via, rank(m), m.placeId))
      .headOption
      .map(_._1.placeId)

  val DefaultOpenStartWindowYears: Int = 300

  /** GeoNames, Wikidata and TGN leave most items undated, and their silence means unknown: an item with no inception
    * was not there since forever.
    */
  def unknownWhenSilent(sourceKey: String): Boolean =
    GazetteerKind.ofKey(sourceKey).exists(k => k != GazetteerKind.Lpf)

  def validity(sourceKey: String, span: Span, windowYears: Int): Validity = span match
    case Span(None, Some(end)) if unknownWhenSilent(sourceKey) =>
      Validity(Some(end - math.max(0, windowYears)), Some(end), startUnknown = true)
    case Span(from, to) => Validity(from, to, startUnknown = false)

  val DefaultPointAreaKinds: List[String] = List(PlaceKind.Region, PlaceKind.Polity, PlaceKind.People)

  /** GeoNames, Wikidata and TGN give every item a coordinate and no boundary. */
  val PointAreaSources: List[String] = ImportedSources

  def pointOnly(g: Option[GazGeometry]): Boolean = g match
    case None                     => true
    case Some(GazGeometry.Wkt(t)) =>
      val u = t.trim.toUpperCase(java.util.Locale.ROOT).replaceFirst("^SRID=\\d+;", "")
      u.startsWith("POINT") || u.startsWith("MULTIPOINT")
    case Some(GazGeometry.GeoJson(t)) =>
      io.circe.parser
        .parse(t)
        .toOption
        .flatMap(_.asObject)
        .flatMap(_("type"))
        .flatMap(_.asString)
        .exists(k => k == "Point" || k == "MultiPoint")

  def dropsPointArea(source: String, r: GazRecord, kinds: Set[String]): Boolean =
    PointAreaSources.contains(source) && kinds.contains(r.kind) && pointOnly(r.geometry)

  def inDataset(r: GazRecord, ds: LpfDataset): GazRecord =
    if r.span.isEmpty && !ds.span.isEmpty then r.copy(span = ds.span) else r

  def mergeNames(ns: List[GazName]): List[GazName] =
    ns.map(n => n.copy(name = n.name.trim, lang = n.lang.trim.toLowerCase(java.util.Locale.ROOT)))
      .filter(_.name.nonEmpty)
      .groupBy(n => (n.name, n.lang))
      .toList
      .map { case ((name, lang), group) =>
        val dated = group.filterNot(_.span.isEmpty)
        val span =
          if dated.isEmpty then Span.always
          else
            Span(
              if dated.forall(_.span.from.isDefined) then Some(dated.flatMap(_.span.from).min) else None,
              if dated.forall(_.span.to.isDefined) then Some(dated.flatMap(_.span.to).max) else None
            )
        val cites =
          group.flatMap(_.citation).flatMap(_.split("; ").toList).map(_.trim).filter(_.nonEmpty).distinct.sorted
        GazName(name, lang, span, Option.when(cites.nonEmpty)(cites.mkString("; ")))
      }
      .sortBy(n => (n.name.toLowerCase(java.util.Locale.ROOT), n.name, n.lang))

  def span(j: Json): Span = Lpf.parseWhen(j).getOrElse(Span.always)

  private def str(o: JsonObject, keys: String*): Option[String] =
    keys.iterator.flatMap(k => o(k).flatMap(_.asString)).map(_.trim).find(_.nonEmpty)

  private def citationOf(n: JsonObject): Option[String] =
    val cites = n("citations").flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { c =>
      c.asString.orElse(
        c.asObject.flatMap(o =>
          str(o, "label", "@id", "id").map { l =>
            val year =
              o("year").flatMap(y => y.asString.orElse(y.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)
            year.fold(l)(y => s"$l ($y)")
          }
        )
      )
    }
    Option.when(cites.nonEmpty)(cites.distinct.mkString("; "))

  private def readNames(f: JsonObject): List[GazName] =
    f("names").flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { j =>
      j.asString
        .map(GazName(_))
        .orElse(j.asObject.flatMap { o =>
          str(o, "toponym", "name", "label").map(t =>
            GazName(t, str(o, "lang", "language").getOrElse(""), o("when").fold(Span.always)(span), citationOf(o))
          )
        })
    }

  private def readDescriptions(f: JsonObject): List[GazDescription] =
    f("descriptions")
      .flatMap(_.asArray)
      .map(_.toList)
      .getOrElse(Nil)
      .flatMap { j =>
        j.asString
          .map(GazDescription(_))
          .orElse(
            j.asObject.flatMap(o =>
              str(o, "value", "description", "text")
                .map(v => GazDescription(v, str(o, "lang", "language").getOrElse("")))
            )
          )
      }
      .map(d => d.copy(text = d.text.trim, lang = d.lang.trim.toLowerCase(java.util.Locale.ROOT)))
      .filter(_.text.nonEmpty)
      .distinctBy(d => (d.lang, d.text))

  private def readLinks(f: JsonObject): List[GazLink] =
    f("links")
      .flatMap(_.asArray)
      .map(_.toList)
      .getOrElse(Nil)
      .flatMap(_.asObject)
      .flatMap { o =>
        for
          rel <- str(o, "type", "relation").flatMap(LinkMatch.ofLpf)
          id <- str(o, "identifier", "id", "@id").flatMap(ExternalId.parse)
        yield GazLink(id, rel)
      }
      .distinctBy(_.id)

  def placeIdFor(own: Option[ExternalId], ref: String, dataset: String): String =
    own.map(_.placeId).getOrElse {
      val tail = slug(ref.split("[/#=]").filter(_.trim.nonEmpty).lastOption.getOrElse(ref))
      s"lpf_${dataset}_${if tail.nonEmpty then tail.take(64) else "x" + Integer.toHexString(ref.hashCode)}"
    }

  def fromFeature(
      j: Json,
      dataset: String,
      ordinal: Int,
      defaultSource: Option[String] = None
  ): Either[String, GazRecord] =
    Lpf.readFeature(j, PlaceKind.Site, ordinal).flatMap { in =>
      val f = j.asObject.getOrElse(JsonObject.empty)
      val raw = str(f, "@id", "id").orElse(f("properties").flatMap(_.asObject).flatMap(str(_, "@id", "id", "pid")))
      raw.toRight(s"feature #$ordinal has no @id").map { ref =>
        val own = ExternalId.parse(ref)
        val names = readNames(f)
        val label = in.place.label
        val all = if names.exists(_.name == label) then names else GazName(label) :: names
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
          itemClass = f("properties").flatMap(_.asObject).flatMap(str(_, "class"))
        )
      }
    }

  def dataset(top: JsonObject, sidecar: Option[JsonObject] = None): LpfDataset =
    def pick(keys: String*): Option[String] = sidecar.flatMap(str(_, keys*)).orElse(str(top, keys*))
    val when = sidecar.flatMap(_("when")).orElse(top("when")).fold(Span.always)(span)
    LpfDataset(
      pick("title", "name", "label"),
      pick("citation", "bibliographicCitation", "attribution"),
      pick("license", "licence", "rights"),
      when
    )

  /** The LP-TSV columns this reader understands (Linked Places TSV v0.5): `id title title_source ccodes matches
    * variants types lon lat geowkt start end`. `matches` and `variants` are `;`-separated; a variant may end in
    * `@lang`.
    */
  def fromTsvRow(
      header: List[String],
      cells: List[String],
      dataset: String,
      line: Int,
      defaultSource: Option[String] = None
  ): Either[String, GazRecord] =
    val row =
      header.map(_.trim.toLowerCase(java.util.Locale.ROOT)).zip(cells.map(_.trim) ++ List.fill(header.size)("")).toMap
    def get(k: String): Option[String] = row.get(k).filter(_.nonEmpty)
    (get("id"), get("title")) match
      case (None, _)                => Left(s"line $line: no id")
      case (_, None)                => Left(s"line $line: no title")
      case (Some(ref), Some(title)) =>
        val own = ExternalId.parse(ref)
        val variants = get("variants").toList.flatMap(_.split(";").toList).map(_.trim).filter(_.nonEmpty).map { v =>
          val at = v.lastIndexOf('@')
          if at > 0 && v.drop(at + 1).matches("[A-Za-z]{2,3}(-[A-Za-z0-9]+)?") then
            GazName(v.take(at).trim, v.drop(at + 1))
          else GazName(v)
        }
        val links = get("matches").toList
          .flatMap(_.split(";").toList)
          .flatMap(ExternalId.parse)
          .filterNot(own.contains)
          .distinct
          .map(GazLink(_, LinkMatch.Close))
        val point = for
          lon <- get("lon").flatMap(_.toDoubleOption)
          lat <- get("lat").flatMap(_.toDoubleOption)
        yield GazGeometry.GeoJson(s"""{"type":"Point","coordinates":[$lon,$lat]}""")
        val kind =
          get("types").flatMap(t => t.split(";").toList.flatMap(PlaceKind.parse).headOption).getOrElse(PlaceKind.Site)
        val sp = Span(get("start").flatMap(Lpf.parseYear), get("end").flatMap(Lpf.parseYear))
        val props = JsonObject.fromIterable(
          get("ccodes")
            .map(c => "ccodes" -> Json.arr(c.split("[;,]").toList.map(_.trim).filter(_.nonEmpty).map(Json.fromString)*))
            .toList ++
            get("types").map(t => "types" -> Json.fromString(t)).toList
        )
        Right(
          GazRecord(
            ref = own.map(_.key).getOrElse(ref),
            own = own,
            placeId = placeIdFor(own, ref, dataset),
            label = title,
            kind = kind,
            geometry = point.orElse(get("geowkt").map(GazGeometry.Wkt(_))),
            span = sp,
            names = mergeNames(GazName(title) :: variants),
            links = links,
            source = get("title_source").orElse(defaultSource),
            properties = Option.when(props.nonEmpty)(Json.fromJsonObject(props).noSpaces),
            descriptions = get("description").map(GazDescription(_)).toList
          )
        )

  /** LP-TSV has no quoting: a tab separates, nothing escapes. */
  def tsvCells(line: String): List[String] = line.split("\t", -1).toList
