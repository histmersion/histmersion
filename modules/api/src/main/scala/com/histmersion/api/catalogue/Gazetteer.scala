package com.histmersion
package api.catalogue

import com.histmersion.api.catalogue.gazetteer.SourceInfo
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import hearth.kindlings.jsoniterjson.{Json, JsonObject}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.problem.Problem

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{LpfDataset, Span, Validity}
import com.histmersion.api.library.ExternalId
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.Plural
import com.histmersion.api.WireEnum
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@atBoundary(BoundaryKind.Json, "reads gazetteer download files into place records")
object Gazetteer:
  type DatasetField = DatasetField.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object DatasetField extends Newtype[String, Pure]
  type DatasetName = DatasetName.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object DatasetName extends Newtype[String, Pure]
  type OpenStartWindowYears = OpenStartWindowYears.T
  object OpenStartWindowYears extends Newtype[Int, Pure]
  type PlaceRef = PlaceRef.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PlaceRef extends Newtype[String, Pure]
  type PointAreaKinds = PointAreaKinds.T
  object PointAreaKinds extends Newtype[String, Pure]
  type Rank = Rank.T
  object Rank extends Newtype[Int, Pure]

  type ImportedSources = ImportedSourcesValue
  export SourceInfo.Key as ImportedSourcesValue
  type DefaultOpenStartWindowYears = DefaultOpenStartWindowYearsValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type DefaultOpenStartWindowYearsValue = OpenStartWindowYears
  val DefaultOpenStartWindowYearsValue: OpenStartWindowYears.type = OpenStartWindowYears
  export SourceInfo.Key as ValiditySourceKey
  type ValidityWindowYears = ValidityWindowYears.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ValidityWindowYears extends Newtype[Int, Pure]
  type DefaultPointAreaKinds = DefaultPointAreaKindsValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type DefaultPointAreaKindsValue = PointAreaKinds
  val DefaultPointAreaKindsValue: PointAreaKinds.type = PointAreaKinds
  type PointAreaSources = PointAreaSourcesValue
  export SourceInfo.Key as PointAreaSourcesValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type FromFeatureDataset = DatasetName
  val FromFeatureDataset: DatasetName.type = DatasetName
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type FromTsvRowDataset = DatasetName
  val FromTsvRowDataset: DatasetName.type = DatasetName

  private[catalogue] def slug(s: String): String =
    ((s).trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def rank(m: Place.Match): Rank =
    m.importSource.flatMap(s => api.catalogue.gazetteer.Kind.ofKey(SourceInfo.Key(s.value))) match
      case None                                        => if m.importSource.isEmpty then Rank(0) else Rank(5)
      case Some(api.catalogue.gazetteer.Kind.Lpf)      => Rank(1)
      case Some(api.catalogue.gazetteer.Kind.Wikidata) => Rank(2)
      case Some(api.catalogue.gazetteer.Kind.Tgn)      => Rank(3)
      case Some(api.catalogue.gazetteer.Kind.Geonames) => Rank(4)

  val ImportedSources: List[ImportedSources] =
    List(
      api.catalogue.gazetteer.Kind.Wikidata,
      api.catalogue.gazetteer.Kind.Geonames,
      api.catalogue.gazetteer.Kind.Tgn
    ).map(k => k.sourceKey())

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def canonical(byOwn: List[Place.Match], byLinks: List[Place.Match] = Nil): Option[Place.Id] =
    (byOwn.map(_ -> 0) ++ byLinks.map(_ -> 1))
      .groupBy(_._1.placeId)
      .values
      .map(_.minBy((m, via) => (if (m.native) == Place.Match.Native.Native then 0 else 1, via)))
      .toList
      .sortBy((m, via) =>
        (if (m.native) == Place.Match.Native.Native then 0 else 1, via, rank(m).value: Int, m.placeId.value: String)
      )
      .headOption
      .map(_._1.placeId)

  val DefaultOpenStartWindowYears: DefaultOpenStartWindowYears = OpenStartWindowYears(300)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def validity(sourceKey: ValiditySourceKey, span: Span, windowYears: ValidityWindowYears): Validity =
    span match
      case Span(None, Some(end)) if ((SourceInfo.unknownWhenSilent(sourceKey)) == (true)) =>
        Validity(
          Some(Year((end).value - math.max(0, (windowYears).value))),
          Some(end),
          startUnknown = Validity.StartUnknown.StartUnknown
        )
      case Span(from, to) => Validity(from, to, startUnknown = Validity.StartUnknown.StartKnown)

  val DefaultPointAreaKinds: List[DefaultPointAreaKinds] = List(
    PointAreaKinds(Place.Kind.Region),
    PointAreaKinds(Place.Kind.Polity),
    PointAreaKinds(Place.Kind.People)
  )

  /** GeoNames, Wikidata and TGN give every item a coordinate and no boundary. */
  val PointAreaSources: List[PointAreaSources] =
    (ImportedSources).map(refactorValue16 => refactorValue16)

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def pointOnly(g: Option[GazGeometry]): Boolean = g match
    case None                     => (true)
    case Some(GazGeometry.Wkt(t)) =>
      val u = t.value.trim.toUpperCase(java.util.Locale.ROOT).replaceFirst("^SRID=\\d+;", "")
      u.startsWith("POINT") || u.startsWith("MULTIPOINT")
    case Some(GazGeometry.GeoJson(t)) =>
      RawJson
        .parse(t.value)
        .toOption
        .flatMap(_.asObject)
        .flatMap(_("type"))
        .flatMap(_.asString)
        .exists(k => k == "Point" || k == "MultiPoint")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def inDataset(r: GazRecord, ds: LpfDataset): GazRecord =
    if ((r.span.isEmpty) == (true)) && !((ds.span.isEmpty) == (true)) then r.modify(_.span).setTo(ds.span) else r

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def mergeNames(ns: List[GazName]): List[GazName] =
    ns.map(n =>
      n.modify(_.name)
        .setTo(GazName.Name(n.name.value.trim))
        .modify(_.lang)
        .setTo(GazName.Lang(n.lang.value.trim.toLowerCase(java.util.Locale.ROOT)))
    ).filter(_.name.value.nonEmpty)
      .groupBy(n => (n.name.value, n.lang.value))
      .toList
      .map { case ((name, lang), group) =>
        val dated = group.filterNot(_.span.isEmpty)
        val span =
          if dated.isEmpty then Span.always
          else
            Span(
              if dated.forall(_.span.from.isDefined) then Some(dated.flatMap(_.span.from).minBy(y => y.value: Long))
              else None,
              if dated.forall(_.span.to.isDefined) then Some(dated.flatMap(_.span.to).maxBy(y => y.value: Long))
              else None
            )
        val cites =
          group.flatMap(_.citation).flatMap(_.value.split("; ").toList).map(_.trim).filter(_.nonEmpty).distinct.sorted
        GazName(
          GazName.Name(name),
          GazName.Lang(lang),
          span,
          Option.when(cites.nonEmpty)(GazName.Citation(cites.mkString("; ")))
        )
      }
      .sortBy(n => (n.name.value.toLowerCase(java.util.Locale.ROOT), n.name.value, n.lang.value))

  def span(j: Json): Span = Lpf.parseWhen(j).getOrElse(Span.always)

  private def str(o: JsonObject, keys: String*): Option[String] =
    (keys.iterator
      .flatMap(k => o(k).flatMap(_.asString))
      .map(_.trim)
      .find(_.nonEmpty))
      .map(refactorValue9 => (refactorValue9))

  private def citationOf(n: JsonObject): Option[GazName.Citation] =
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
    Option.when(cites.nonEmpty)(GazName.Citation(cites.distinct.mkString("; ")))

  private def readNames(f: JsonObject): List[GazName] =
    f("names").flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { j =>
      j.asString
        .map(s => GazName(GazName.Name(s)))
        .orElse(j.asObject.flatMap { o =>
          str(o, "toponym", "name", "label").map(t =>
            GazName(
              GazName.Name(t),
              GazName.Lang(str(o, "lang", "language").getOrElse("")),
              o("when").fold(Span.always)(span),
              citationOf(o)
            )
          )
        })
    }

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def readDescriptions(f: JsonObject): List[GazDescription] =
    f("descriptions")
      .flatMap(_.asArray)
      .map(_.toList)
      .getOrElse(Nil)
      .flatMap { j =>
        j.asString
          .map(s => GazDescription(GazDescription.Text(s)))
          .orElse(
            j.asObject.flatMap(o =>
              str(o, "value", "description", "text")
                .map(v =>
                  GazDescription(GazDescription.Text(v), GazDescription.Lang(str(o, "lang", "language").getOrElse("")))
                )
            )
          )
      }
      .map(d =>
        d.modify(_.text)
          .setTo(GazDescription.Text(d.text.value.trim))
          .modify(_.lang)
          .setTo(GazDescription.Lang(d.lang.value.trim.toLowerCase(java.util.Locale.ROOT)))
      )
      .filter(_.text.value.nonEmpty)
      .distinctBy(d => (d.lang.value, d.text.value))

  private def readLinks(f: JsonObject): List[GazLink] =
    f("links")
      .flatMap(_.asArray)
      .map(_.toList)
      .getOrElse(Nil)
      .flatMap(_.asObject)
      .flatMap { o =>
        for
          rel <- str(o, "type", "relation").flatMap(s => LinkMatch.ofLpf(LinkMatch.Wire(s)))
          id <- str(o, "identifier", "id", "@id").flatMap(s => ExternalId.parse(ExternalId.Reference(s)))
        yield GazLink(id, rel)
      }
      .distinctBy(_.id)

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def placeIdFor(own: Option[ExternalId], ref: PlaceRef, dataset: DatasetName): Place.Id =
    own.map(o => Place.Id(o.placeId.value)).getOrElse {
      val tail = slug((ref).value.split("[/#=]").filter(_.trim.nonEmpty).lastOption.getOrElse(ref.value))
      Place.Id(
        s"lpf_${dataset}_${if tail.nonEmpty then tail.take(64) else "x" + Integer.toHexString(ref.value.hashCode)}"
      )
    }

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def fromFeature(
      j: Json,
      dataset: FromFeatureDataset,
      ordinal: Int,
      defaultSource: Option[SourceInfo.Key] = None
  ): Either[Problem, GazRecord] =
    Lpf.readFeature(j, Place.KindValue(Place.Kind.Site), ordinal).flatMap { in =>
      val f = j.asObject.getOrElse(JsonObject.empty)
      val raw = str(f, "@id", "id").orElse(f("properties").flatMap(_.asObject).flatMap(str(_, "@id", "id", "pid")))
      raw
        .toRight(
          Problem.Invalid.Missing(Problem.Field("@id"), Vector(Problem.Field(s"features"), Problem.Field(s"$ordinal")))
        )
        .map { ref =>
          val own = ExternalId.parse(ExternalId.Reference(ref))
          val names = readNames(f)
          val label = in.place.label.value
          val all = if names.exists(_.name.value == label) then names else GazName(GazName.Name(label)) :: names
          GazRecord(
            ref = GazRecord.Ref(own.map(_.key.value).getOrElse(ref)),
            own = own,
            placeId = placeIdFor(own, PlaceRef(ref), dataset),
            label = GazRecord.Label(label),
            kind = GazRecord.Kind(in.place.kind.value),
            geometry = in.place.geometry.map(g => GazGeometry.GeoJson(GazGeometry.GeoJson.Text(g.value))),
            span = f("when").orElse(f("properties").flatMap(_.asObject).flatMap(_("when"))).fold(Span.always)(span),
            names = mergeNames(all),
            links = readLinks(f).filterNot(l => own.contains(l.id)),
            source = in.place.source
              .map(s => s)
              .orElse(defaultSource.map(k => GazRecord.Source(k.value))),
            properties = in.place.properties.map(p => p),
            descriptions = readDescriptions(f),
            itemClass = f("properties").flatMap(_.asObject).flatMap(str(_, "class")).map(c => GazRecord.ItemClass(c))
          )
        }
    }

  def dataset(top: JsonObject, sidecar: Option[JsonObject] = None): LpfDataset =
    def pick(keys: String*): Option[String] = sidecar.flatMap(str(_, keys*)).orElse(str(top, keys*))
    val when = sidecar.flatMap(_("when")).orElse(top("when")).fold(Span.always)(span)
    LpfDataset(
      pick("title", "name", "label").map(LpfDataset.Title(_)),
      pick("citation", "bibliographicCitation", "attribution").map(LpfDataset.Citation(_)),
      pick("license", "licence", "rights").map(LpfDataset.Licence(_)),
      when
    )

  /** The LP-TSV columns this reader understands (Linked Places TSV v0.5): `id title title_source ccodes matches
    * variants types lon lat geowkt start end`. `matches` and `variants` are `;`-separated; a variant may end in
    * `@lang`.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def fromTsvRow(
      header: List[String],
      cells: List[String],
      dataset: FromTsvRowDataset,
      line: Int,
      defaultSource: Option[SourceInfo.Key] = None
  ): Either[Problem, GazRecord] =
    val row =
      header
        .map((_).trim.toLowerCase(java.util.Locale.ROOT))
        .zip(cells.map((_).trim) ++ List.fill(header.size)(""))
        .toMap
    def get(k: String): Option[String] = (row.get((k)).filter(_.nonEmpty)).map(refactorValue19 => (refactorValue19))
    (get(("id")), get(("title"))) match
      case (None, _) => Left(Problem.Invalid.Missing(Problem.Field("id"), Vector(Problem.Field(s"line $line"))))
      case (_, None) => Left(Problem.Invalid.Missing(Problem.Field("title"), Vector(Problem.Field(s"line $line"))))
      case (Some(ref), Some(title)) =>
        val own = ExternalId.parse(ExternalId.Reference(ref))
        val variants = get(("variants")).toList.flatMap((_).split(";").toList).map(_.trim).filter(_.nonEmpty).map { v =>
          val at = v.lastIndexOf('@')
          if at > 0 && v.drop(at + 1).matches("[A-Za-z]{2,3}(-[A-Za-z0-9]+)?") then
            GazName(GazName.Name(v.take(at).trim), GazName.Lang(v.drop(at + 1)))
          else GazName(GazName.Name(v))
        }
        val links = get(("matches")).toList
          .flatMap((_).split(";").toList)
          .flatMap {
            val refactorFunction3 = ExternalId.parse;
            (refactorValue8) => refactorFunction3(ExternalId.Reference(refactorValue8))
          }
          .filterNot(own.contains)
          .distinct
          .map(GazLink(_, LinkMatch.Close))
        val point = for
          lon <- get(("lon")).flatMap((_).toDoubleOption)
          lat <- get(("lat")).flatMap((_).toDoubleOption)
        yield GazGeometry.GeoJson(GazGeometry.GeoJson.Text(s"""{"type":"Point","coordinates":[$lon,$lat]}"""))
        val kind =
          get(("types"))
            .flatMap(t =>
              (t)
                .split(";")
                .toList
                .flatMap {
                  val refactorFunction4 = Place.Kind.parse;
                  (refactorValue10) => refactorFunction4(Place.Kind.ParseS(refactorValue10))
                }
                .headOption
            )
            .map(_.value)
            .getOrElse(Place.Kind.Site)
        val sp = Span(get(("start")).flatMap(Lpf.parseYear), get(("end")).flatMap(Lpf.parseYear))
        val props = JsonObject.fromIterable(
          get(("ccodes"))
            .map(c =>
              "ccodes" -> Json.arr((c).split("[;,]").toList.map(_.trim).filter(_.nonEmpty).map(Json.fromString)*)
            )
            .toList ++
            get(("types")).map(t => "types" -> Json.fromString((t))).toList
        )
        Right(
          GazRecord(
            ref = GazRecord.Ref(own.map(_.key.value).getOrElse(ref)),
            own = own,
            placeId = placeIdFor(own, PlaceRef(ref), dataset),
            label = GazRecord.Label(title),
            kind = GazRecord.Kind(kind),
            geometry = point.orElse(get(("geowkt")).map(w => GazGeometry.Wkt(GazGeometry.Wkt.Text(w)))),
            span = sp,
            names = mergeNames(GazName(GazName.Name(title)) :: variants),
            links = links,
            source = get(("title_source"))
              .map(s => GazRecord.Source(s))
              .orElse(defaultSource.map(k => GazRecord.Source(k.value))),
            properties = Option.when(props.nonEmpty)(GazRecord.Properties(Json.fromJsonObject(props).noSpaces)),
            descriptions = get(("description")).map(d => GazDescription(GazDescription.Text(d))).toList
          )
        )

  /** LP-TSV has no quoting: a tab separates, nothing escapes. */
  def tsvCells(line: String): List[String] = ((line).split("\t", -1).toList).map(refactorValue7 => (refactorValue7))
