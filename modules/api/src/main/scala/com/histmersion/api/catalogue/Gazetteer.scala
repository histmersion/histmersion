package com.histmersion
package api.catalogue
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def sourceKey(kind: Kind, dataset: Option[DatasetName] = None): SourceInfo.Key = kind match
    case Kind.Lpf => SourceInfo.Key(s"lpf:${dataset.getOrElse("")}")
    case k        => SourceInfo.Key(k.wire.value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def datasetSlug(fileName: SourceInfo.File): DatasetName =
    val base = fileName.value
      .replaceAll("(?i)(\\.meta)?\\.(jsonld|json|geojson|ndjson|jsonl|tsv)(\\.gz)?$", "")
      .replaceAll("(?i)\\.lpf$", "")
    DatasetName((slug(base)).take(48))

  private def slug(s: String): String =
    ((s).trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def rank(m: Place.Match): Rank = m.importSource.flatMap(s => Kind.ofKey(SourceInfo.Key(s.value))) match
    case None                => if m.importSource.isEmpty then Rank(0) else Rank(5)
    case Some(Kind.Lpf)      => Rank(1)
    case Some(Kind.Wikidata) => Rank(2)
    case Some(Kind.Tgn)      => Rank(3)
    case Some(Kind.Geonames) => Rank(4)

  val ImportedSources: List[ImportedSources] =
    List(
      Kind.Wikidata,
      Kind.Geonames,
      Kind.Tgn
    ).map(k => sourceKey(k))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def canonical(byOwn: List[Place.Match], byLinks: List[Place.Match] = Nil): Option[Place.Id] =
    (byOwn.map(_ -> 0) ++ byLinks.map(_ -> 1))
      .groupBy(_._1.placeId)
      .values
      .map(_.minBy((m, via) => (if (m.native) == Place.Match.Native(true) then 0 else 1, via)))
      .toList
      .sortBy((m, via) =>
        (if (m.native) == Place.Match.Native(true) then 0 else 1, via, rank(m).value: Int, m.placeId.value: String)
      )
      .headOption
      .map(_._1.placeId)

  val DefaultOpenStartWindowYears: DefaultOpenStartWindowYears = OpenStartWindowYears(300)

  /** GeoNames, Wikidata and TGN leave most items undated, and their silence means unknown: an item with no inception
    * was not there since forever.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def unknownWhenSilent(sourceKey: SourceInfo.Key): Boolean =
    Kind
      .ofKey(sourceKey)
      .exists(k => k != Kind.Lpf)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def validity(sourceKey: Gazetteer.ValiditySourceKey, span: Span, windowYears: ValidityWindowYears): Validity =
    span match
      case Span(None, Some(end)) if ((unknownWhenSilent(sourceKey)) == (true)) =>
        Validity(
          Some(Year((end).value - math.max(0, (windowYears).value))),
          Some(end),
          startUnknown = Validity.StartUnknown(true)
        )
      case Span(from, to) => Validity(from, to, startUnknown = Validity.StartUnknown(false))

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
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def dropsPointArea(source: SourceInfo.Key, r: GazRecord, kinds: Set[PointAreaKinds]): Boolean =
    PointAreaSources.contains(source) && kinds.map(_.value).contains(r.kind.value) && pointOnly(r.geometry)

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

  final case class Extract(
      name: Extract.Name,
      file: Extract.File,
      sha256: Extract.Sha256,
      bytes: Extract.Bytes,
      features: Extract.Features,
      version: Extract.Version,
      title: Extract.Title,
      licence: Extract.Licence,
      attribution: Option[Extract.Attribution] = None,
      url: Option[Extract.Url] = None,
      built: Option[Extract.Built] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Extract:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    export SourceInfo.File
    type Sha256 = Sha256.T
    object Sha256 extends Newtype[String, Pure]
    type Bytes = Bytes.T
    object Bytes extends Newtype[Long, Pure]
    type Features = Features.T
    object Features extends Newtype[Long, Pure]
    type Version = Version.T
    object Version extends Newtype[String, Pure]
    export SourceInfo.{Licence, Title}
    type Attribution = Attribution.T
    object Attribution extends Newtype[String, Pure]
    type Url = Url.T
    object Url extends Newtype[String, Pure]
    type Built = Built.T
    object Built extends Newtype[String, Pure]

  /** `Tgn`: the Getty Thesaurus of Geographic Names. */
  enum Kind derives CanEqual:
    case Geonames, Wikidata, Lpf, Tgn

    def wire: Kind.Wire = Kind.Wire(toString.toLowerCase(java.util.Locale.ROOT))

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def parse(s: Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def ofKey(key: SourceInfo.Key): Option[Kind] = parse(Wire((key).value.takeWhile(_ != ':')))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = parse(Wire((s).value))
    given JsonValueCodec[Kind] = WireEnum.codec
    given Schema[Kind] = WireEnum.schema

  final case class LoadCounts(
      source: LoadCounts.Source,
      version: LoadCounts.Version,
      records: LoadCounts.Records = LoadCounts.Records((0).toLong),
      created: LoadCounts.Created = LoadCounts.Created((0).toLong),
      joined: LoadCounts.Joined = LoadCounts.Joined((0).toLong),
      placesChanged: LoadCounts.PlacesChanged = LoadCounts.PlacesChanged((0).toLong),
      namesAdded: LoadCounts.NamesAdded = LoadCounts.NamesAdded((0).toLong),
      namesRemoved: LoadCounts.NamesRemoved = LoadCounts.NamesRemoved((0).toLong),
      namesChanged: LoadCounts.NamesChanged = LoadCounts.NamesChanged((0).toLong),
      linksAdded: LoadCounts.LinksAdded = LoadCounts.LinksAdded((0).toLong),
      linksRemoved: LoadCounts.LinksRemoved = LoadCounts.LinksRemoved((0).toLong),
      placesRemoved: LoadCounts.PlacesRemoved = LoadCounts.PlacesRemoved((0).toLong),
      skipped: List[LoadCounts.Skipped] = Nil,
      descriptionsAdded: LoadCounts.DescriptionsAdded = LoadCounts.DescriptionsAdded((0).toLong),
      descriptionsRemoved: LoadCounts.DescriptionsRemoved = LoadCounts.DescriptionsRemoved((0).toLong),
      descriptionsChanged: LoadCounts.DescriptionsChanged = LoadCounts.DescriptionsChanged((0).toLong),
      linksChanged: LoadCounts.LinksChanged = LoadCounts.LinksChanged((0).toLong),
      pointAreasSkipped: LoadCounts.PointAreasSkipped = LoadCounts.PointAreasSkipped((0).toLong)
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def +(o: LoadCounts): LoadCounts =
      copy(
        records = LoadCounts.Records(records.value + o.records.value),
        created = LoadCounts.Created(created.value + o.created.value),
        joined = LoadCounts.Joined(joined.value + o.joined.value),
        placesChanged = LoadCounts.PlacesChanged(placesChanged.value + o.placesChanged.value),
        namesAdded = LoadCounts.NamesAdded(namesAdded.value + o.namesAdded.value),
        namesRemoved = LoadCounts.NamesRemoved(namesRemoved.value + o.namesRemoved.value),
        namesChanged = LoadCounts.NamesChanged(namesChanged.value + o.namesChanged.value),
        linksAdded = LoadCounts.LinksAdded(linksAdded.value + o.linksAdded.value),
        linksRemoved = LoadCounts.LinksRemoved(linksRemoved.value + o.linksRemoved.value),
        placesRemoved = LoadCounts.PlacesRemoved(placesRemoved.value + o.placesRemoved.value),
        skipped = (skipped ++ o.skipped).take(20),
        descriptionsAdded = LoadCounts.DescriptionsAdded(descriptionsAdded.value + o.descriptionsAdded.value),
        descriptionsRemoved = LoadCounts.DescriptionsRemoved(descriptionsRemoved.value + o.descriptionsRemoved.value),
        descriptionsChanged = LoadCounts.DescriptionsChanged(descriptionsChanged.value + o.descriptionsChanged.value),
        linksChanged = LoadCounts.LinksChanged(linksChanged.value + o.linksChanged.value),
        pointAreasSkipped = LoadCounts.PointAreasSkipped(pointAreasSkipped.value + o.pointAreasSkipped.value)
      )

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def unchanged: Boolean =
      created.value == 0L && placesChanged.value == 0L && namesAdded.value == 0L && namesRemoved.value == 0L && namesChanged.value == 0L &&
        linksAdded.value == 0L && linksRemoved.value == 0L && linksChanged.value == 0L && placesRemoved.value == 0L &&
        descriptionsAdded.value == 0L && descriptionsRemoved.value == 0L && descriptionsChanged.value == 0L

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def describe: String =
      s"$source $version: ${Plural.count(Plural.CountN(records.value), Plural.CountSingular("record"))}, $created new place(s), $joined joined an existing place, " +
        s"$placesChanged place(s) changed, names +$namesAdded −$namesRemoved ~$namesChanged, links +$linksAdded −$linksRemoved" +
        (if linksChanged.value > 0 then s" ~$linksChanged" else "") +
        (if descriptionsAdded.value + descriptionsRemoved.value + descriptionsChanged.value > 0 then
           s", descriptions +$descriptionsAdded −$descriptionsRemoved ~$descriptionsChanged"
         else "") +
        (if placesRemoved.value > 0 then s", $placesRemoved place(s) removed" else "") +
        (if pointAreasSkipped.value > 0 then s", $pointAreasSkipped point-only region(s)/polity(ies) not kept as places"
         else "") +
        (if skipped.nonEmpty then s", ${skipped.size} skipped" else "")

  object LoadCounts:

    export SourceInfo.{ImportedVersion as Version, Key as Source}
    type Records = Records.T
    object Records extends Newtype[Long, Pure]
    type Created = Created.T
    object Created extends Newtype[Long, Pure]
    type Joined = Joined.T
    object Joined extends Newtype[Long, Pure]
    type PlacesChanged = PlacesChanged.T
    object PlacesChanged extends Newtype[Long, Pure]
    type NamesAdded = NamesAdded.T
    object NamesAdded extends Newtype[Long, Pure]
    type NamesRemoved = NamesRemoved.T
    object NamesRemoved extends Newtype[Long, Pure]
    type NamesChanged = NamesChanged.T
    object NamesChanged extends Newtype[Long, Pure]
    type LinksAdded = LinksAdded.T
    object LinksAdded extends Newtype[Long, Pure]
    type LinksRemoved = LinksRemoved.T
    object LinksRemoved extends Newtype[Long, Pure]
    type PlacesRemoved = PlacesRemoved.T
    object PlacesRemoved extends Newtype[Long, Pure]
    type Skipped = Skipped.T
    object Skipped extends Newtype[String, Pure]
    type DescriptionsAdded = DescriptionsAdded.T
    object DescriptionsAdded extends Newtype[Long, Pure]
    type DescriptionsRemoved = DescriptionsRemoved.T
    object DescriptionsRemoved extends Newtype[Long, Pure]
    type DescriptionsChanged = DescriptionsChanged.T
    object DescriptionsChanged extends Newtype[Long, Pure]
    type LinksChanged = LinksChanged.T
    object LinksChanged extends Newtype[Long, Pure]
    type PointAreasSkipped = PointAreasSkipped.T
    object PointAreasSkipped extends Newtype[Long, Pure]

  final case class Manifest(extracts: List[Extract]) derives JsonCodec, KindlingsSchema, api.json.Schema

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class SourceInfo(
      key: SourceInfo.Key,
      kind: Kind,
      title: SourceInfo.Title,
      file: Option[SourceInfo.File],
      present: SourceInfo.Present,
      pinnedVersion: Option[SourceInfo.PinnedVersion],
      importedVersion: Option[SourceInfo.ImportedVersion] = None,
      importedAt: Option[SourceInfo.ImportedAt] = None,
      places: Option[SourceInfo.Places] = None,
      names: Option[SourceInfo.Names] = None,
      links: Option[SourceInfo.Links] = None,
      licence: Option[SourceInfo.Licence] = None,
      citation: Option[SourceInfo.Citation] = None,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object SourceInfo:
    type Key = Key.T
    object Key extends Newtype[String, Pure]
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type File = File.T
    object File extends Newtype[String, Pure]
    type Present = Present.T
    object Present extends Newtype[Boolean, Pure]
    type PinnedVersion = PinnedVersion.T
    object PinnedVersion extends Newtype[String, Pure]
    type ImportedVersion = ImportedVersion.T
    object ImportedVersion extends Newtype[String, Pure]
    type ImportedAt = ImportedAt.T
    object ImportedAt extends Newtype[String, Pure]
    type Places = Places.T
    object Places extends Newtype[Int, Pure]
    type Names = Names.T
    object Names extends Newtype[Int, Pure]
    type Links = Links.T
    object Links extends Newtype[Int, Pure]
    type Licence = Licence.T
    object Licence extends Newtype[String, Pure]
    type Citation = Citation.T
    object Citation extends Newtype[String, Pure]
