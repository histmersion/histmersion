package com.histmersion.api.catalogue
import hearth.kindlings.optics.syntax.*

import io.circe.{Json, JsonObject}
import com.histmersion.api.{LpfDataset, Span, Validity}
import com.histmersion.api.library.ExternalId
import com.histmersion.api.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.Plural
import com.histmersion.api.WireEnum
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

object Gazetteer:
  type DatasetField = DatasetField.T
  object DatasetField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatasetName = DatasetName.T
  object DatasetName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenStartWindowYears = OpenStartWindowYears.T
  object OpenStartWindowYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceRef = PlaceRef.T
  object PlaceRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointAreaKinds = PointAreaKinds.T
  object PointAreaKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rank = Rank.T
  object Rank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type ImportedSources = ImportedSourcesValue
  type ImportedSourcesValue = com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key
  val ImportedSourcesValue: _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key.type = _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key
  type DefaultOpenStartWindowYears = DefaultOpenStartWindowYearsValue
  type DefaultOpenStartWindowYearsValue = com.histmersion.api.catalogue.Gazetteer.OpenStartWindowYears
  val DefaultOpenStartWindowYearsValue: _root_.com.histmersion.api.catalogue.Gazetteer.OpenStartWindowYears.type = _root_.com.histmersion.api.catalogue.Gazetteer.OpenStartWindowYears
  type ValiditySourceKey = _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key
  val ValiditySourceKey: _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key.type = _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key
  type ValidityWindowYears = ValidityWindowYears.T
  object ValidityWindowYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultPointAreaKinds = DefaultPointAreaKindsValue
  type DefaultPointAreaKindsValue = com.histmersion.api.catalogue.Gazetteer.PointAreaKinds
  val DefaultPointAreaKindsValue: _root_.com.histmersion.api.catalogue.Gazetteer.PointAreaKinds.type = _root_.com.histmersion.api.catalogue.Gazetteer.PointAreaKinds
  type PointAreaSources = PointAreaSourcesValue
  type PointAreaSourcesValue = com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key
  val PointAreaSourcesValue: _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key.type = _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key
  type FromFeatureDataset = _root_.com.histmersion.api.catalogue.Gazetteer.DatasetName
  val FromFeatureDataset: _root_.com.histmersion.api.catalogue.Gazetteer.DatasetName.type = _root_.com.histmersion.api.catalogue.Gazetteer.DatasetName
  type FromTsvRowDataset = _root_.com.histmersion.api.catalogue.Gazetteer.DatasetName
  val FromTsvRowDataset: _root_.com.histmersion.api.catalogue.Gazetteer.DatasetName.type = _root_.com.histmersion.api.catalogue.Gazetteer.DatasetName

  def sourceKey(kind: com.histmersion.api.catalogue.Gazetteer.Kind, dataset: Option[com.histmersion.api.catalogue.Gazetteer.DatasetName] = None): com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key = kind match
    case com.histmersion.api.catalogue.Gazetteer.Kind.Lpf => _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key(s"lpf:${dataset.getOrElse("")}")
    case k                                                => k.wire

  def datasetSlug(fileName: com.histmersion.api.catalogue.Gazetteer.SourceInfo.File): com.histmersion.api.catalogue.Gazetteer.DatasetName =
    val base = fileName
      .replaceAll("(?i)(\\.meta)?\\.(jsonld|json|geojson|ndjson|jsonl|tsv)(\\.gz)?$", "")
      .replaceAll("(?i)\\.lpf$", "")
    _root_.com.histmersion.api.catalogue.Gazetteer.DatasetName((slug(base)).value.take(48))

  private def slug(s: String): String =
    ((s).value.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""))

  def rank(m: Place.Match): com.histmersion.api.catalogue.Gazetteer.Rank = m.importSource.flatMap(com.histmersion.api.catalogue.Gazetteer.Kind.ofKey) match
    case None                                                        => if m.importSource.isEmpty then _root_.com.histmersion.api.catalogue.Gazetteer.Rank(0) else _root_.com.histmersion.api.catalogue.Gazetteer.Rank(5)
    case Some(com.histmersion.api.catalogue.Gazetteer.Kind.Lpf)      => _root_.com.histmersion.api.catalogue.Gazetteer.Rank(1)
    case Some(com.histmersion.api.catalogue.Gazetteer.Kind.Wikidata) => _root_.com.histmersion.api.catalogue.Gazetteer.Rank(2)
    case Some(com.histmersion.api.catalogue.Gazetteer.Kind.Tgn)      => _root_.com.histmersion.api.catalogue.Gazetteer.Rank(3)
    case Some(com.histmersion.api.catalogue.Gazetteer.Kind.Geonames) => _root_.com.histmersion.api.catalogue.Gazetteer.Rank(4)

  val ImportedSources: List[com.histmersion.api.catalogue.Gazetteer.ImportedSources] =
    List(
      com.histmersion.api.catalogue.Gazetteer.Kind.Wikidata,
      com.histmersion.api.catalogue.Gazetteer.Kind.Geonames,
      com.histmersion.api.catalogue.Gazetteer.Kind.Tgn
    ).map(k => _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key((sourceKey(k)).value))

  def canonical(byOwn: List[Place.Match], byLinks: List[Place.Match] = Nil): Option[com.histmersion.api.catalogue.Place.Id] =
    (byOwn.map(_ -> 0) ++ byLinks.map(_ -> 1))
      .groupBy(_._1.placeId)
      .values
      .map(_.minBy((m, via) => (if ((m.native) == _root_.com.histmersion.api.catalogue.Place.Match.Native(true)) then 0 else 1, via)))
      .toList
      .sortBy((m, via) => (if ((m.native) == _root_.com.histmersion.api.catalogue.Place.Match.Native(true)) then 0 else 1, via, rank(m), m.placeId))
      .headOption
      .map(_._1.placeId)

  val DefaultOpenStartWindowYears: com.histmersion.api.catalogue.Gazetteer.DefaultOpenStartWindowYears = _root_.com.histmersion.api.catalogue.Gazetteer.OpenStartWindowYears(300)

  /** GeoNames, Wikidata and TGN leave most items undated, and their silence means unknown: an item with no inception
    * was not there since forever.
    */
  def unknownWhenSilent(sourceKey: com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key): Boolean =
    com.histmersion.api.catalogue.Gazetteer.Kind
      .ofKey(sourceKey)
      .exists(k => k != com.histmersion.api.catalogue.Gazetteer.Kind.Lpf)

  def validity(sourceKey: com.histmersion.api.catalogue.Gazetteer.ValiditySourceKey, span: Span, windowYears: com.histmersion.api.catalogue.Gazetteer.ValidityWindowYears): Validity = span match
    case Span(None, Some(end)) if ((unknownWhenSilent(sourceKey)) == (true)) =>
      Validity(Some(_root_.com.histmersion.api.catalogue.Year((end).value - math.max(0, (windowYears).value))), Some(end), startUnknown = _root_.com.histmersion.api.Validity.StartUnknown(true))
    case Span(from, to) => Validity(from, to, startUnknown = _root_.com.histmersion.api.Validity.StartUnknown(false))

  val DefaultPointAreaKinds: List[com.histmersion.api.catalogue.Gazetteer.DefaultPointAreaKinds] = List(
    _root_.com.histmersion.api.catalogue.Gazetteer.PointAreaKinds(com.histmersion.api.catalogue.Place.Kind.Region),
    _root_.com.histmersion.api.catalogue.Gazetteer.PointAreaKinds(com.histmersion.api.catalogue.Place.Kind.Polity),
    _root_.com.histmersion.api.catalogue.Gazetteer.PointAreaKinds(com.histmersion.api.catalogue.Place.Kind.People)
  )

  /** GeoNames, Wikidata and TGN give every item a coordinate and no boundary. */
  val PointAreaSources: List[com.histmersion.api.catalogue.Gazetteer.PointAreaSources] = (ImportedSources).map(refactorValue16 => _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key((refactorValue16).value))

  def pointOnly(g: Option[GazGeometry]): Boolean = g match
    case None                     => (true)
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

  def dropsPointArea(source: com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key, r: GazRecord, kinds: Set[com.histmersion.api.catalogue.Gazetteer.Kind.Wire]): Boolean =
    PointAreaSources.contains(source) && kinds.contains(r.kind) && pointOnly(r.geometry)

  def inDataset(r: GazRecord, ds: LpfDataset): GazRecord =
    if ((r.span.isEmpty) == (true)) && !((ds.span.isEmpty) == (true)) then r.modify(_.span).setTo(ds.span) else r

  def mergeNames(ns: List[GazName]): List[GazName] =
    ns.map(n => n.modify(_.name).setTo(n.name.trim).modify(_.lang).setTo(n.lang.trim.toLowerCase(java.util.Locale.ROOT)))
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
    (keys.iterator.flatMap(k => o(k).flatMap(_.asString)).map(_.trim).find(_.nonEmpty)).map(refactorValue9 => (refactorValue9))

  private def citationOf(n: JsonObject): Option[com.histmersion.api.catalogue.Gazetteer.SourceInfo.Citation] =
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
    Option.when(cites.nonEmpty)(_root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Citation(cites.distinct.mkString("; ")))

  private def readNames(f: JsonObject): List[GazName] =
    f("names").flatMap(_.asArray).map(_.toList).getOrElse(Nil).flatMap { j =>
      j.asString
        .map(GazName(_root_.com.histmersion.api.catalogue.GazName.Name(_)))
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
          .map(GazDescription(_root_.com.histmersion.api.catalogue.GazDescription.Text(_)))
          .orElse(
            j.asObject.flatMap(o =>
              str(o, "value", "description", "text")
                .map(v => GazDescription(v, str(o, "lang", "language").getOrElse("")))
            )
          )
      }
      .map(d => d.modify(_.text).setTo(d.text.trim).modify(_.lang).setTo(d.lang.trim.toLowerCase(java.util.Locale.ROOT)))
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

  def placeIdFor(own: Option[ExternalId], ref: com.histmersion.api.catalogue.Gazetteer.PlaceRef, dataset: com.histmersion.api.catalogue.Gazetteer.DatasetName): com.histmersion.api.catalogue.Place.Id =
    own.map(_.placeId).getOrElse {
      val tail = slug((ref).value.split("[/#=]").filter(_.trim.nonEmpty).lastOption.getOrElse(ref))
      s"lpf_${dataset}_${if tail.nonEmpty then tail.take(64) else "x" + Integer.toHexString(ref.hashCode)}"
    }

  def fromFeature(
      j: Json,
      dataset: com.histmersion.api.catalogue.Gazetteer.FromFeatureDataset,
      ordinal: Int,
      defaultSource: Option[com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key] = None
  ): Either[com.histmersion.api.catalogue.Gazetteer.Problem, GazRecord] =
    Lpf.readFeature(j, _root_.com.histmersion.api.Api.ArticleQuery.Kind.Wire(com.histmersion.api.catalogue.Place.Kind.Site), ordinal).flatMap { in =>
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
          placeId = com.histmersion.api.catalogue.Place.Id(placeIdFor(own, ref, dataset)),
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
    def pick(keys: String*): Option[com.histmersion.api.catalogue.Gazetteer.DatasetField] = sidecar.flatMap(str(_, keys*)).orElse(str(top, keys*))
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
      dataset: com.histmersion.api.catalogue.Gazetteer.FromTsvRowDataset,
      line: Int,
      defaultSource: Option[com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key] = None
  ): Either[com.histmersion.api.catalogue.Gazetteer.Problem, GazRecord] =
    val row =
      header.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).zip(cells.map((_).value.trim) ++ List.fill(header.size)("")).toMap
    def get(k: String): Option[String] = (row.get((k).value).filter(_.nonEmpty)).map(refactorValue19 => (refactorValue19))
    (get(("id")), get(("title"))) match
      case (None, _)                => Left(_root_.com.histmersion.api.catalogue.Gazetteer.Problem(s"line $line: no id"))
      case (_, None)                => Left(_root_.com.histmersion.api.catalogue.Gazetteer.Problem(s"line $line: no title"))
      case (Some(ref), Some(title)) =>
        val own = ExternalId.parse(ref)
        val variants = get(("variants")).toList.flatMap((_).value.split(";").toList).map(_.trim).filter(_.nonEmpty).map { v =>
          val at = v.lastIndexOf('@')
          if at > 0 && v.drop(at + 1).matches("[A-Za-z]{2,3}(-[A-Za-z0-9]+)?") then
            GazName(_root_.com.histmersion.api.catalogue.GazName.Name(v.take(at).trim), _root_.com.histmersion.api.catalogue.GazName.Lang(v.drop(at + 1)))
          else GazName(_root_.com.histmersion.api.catalogue.GazName.Name(v))
        }
        val links = get(("matches")).toList
          .flatMap((_).value.split(";").toList)
          .flatMap({ val refactorFunction3 = ExternalId.parse; (refactorValue8) => refactorFunction3(_root_.com.histmersion.api.library.ExternalId.Reference(refactorValue8)) })
          .filterNot(own.contains)
          .distinct
          .map(GazLink(_, LinkMatch.Close))
        val point = for
          lon <- get(("lon")).flatMap((_).value.toDoubleOption)
          lat <- get(("lat")).flatMap((_).value.toDoubleOption)
        yield GazGeometry.GeoJson(_root_.com.histmersion.api.catalogue.GazGeometry.GeoJson.Text(s"""{"type":"Point","coordinates":[$lon,$lat]}"""))
        val kind =
          get(("types"))
            .flatMap(t => (t).value.split(";").toList.flatMap({ val refactorFunction4 = com.histmersion.api.catalogue.Place.Kind.parse; (refactorValue10) => refactorFunction4(_root_.com.histmersion.api.catalogue.Place.Kind.ParseS(refactorValue10)) }).headOption)
            .getOrElse(com.histmersion.api.catalogue.Place.Kind.Site)
        val sp = Span(get(("start")).flatMap(Lpf.parseYear), get(("end")).flatMap(Lpf.parseYear))
        val props = JsonObject.fromIterable(
          get(("ccodes"))
            .map(c => "ccodes" -> Json.arr((c).value.split("[;,]").toList.map(_.trim).filter(_.nonEmpty).map(Json.fromString)*))
            .toList ++
            get(("types")).map(t => "types" -> Json.fromString((t).value)).toList
        )
        Right(
          GazRecord(
            ref = own.map(_.key).getOrElse(ref),
            own = own,
            placeId = com.histmersion.api.catalogue.Place.Id(placeIdFor(own, ref, dataset)),
            label = title,
            kind = _root_.com.histmersion.api.catalogue.GazRecord.Kind(kind),
            geometry = point.orElse(get(("geowkt")).map(GazGeometry.Wkt(_))),
            span = sp,
            names = mergeNames(GazName(title) :: variants),
            links = links,
            source = get(("title_source")).orElse(defaultSource),
            properties = Option.when(props.nonEmpty)(_root_.com.histmersion.api.catalogue.GazRecord.Properties(Json.fromJsonObject(props).noSpaces)),
            descriptions = get(("description")).map(GazDescription(_)).toList
          )
        )

  /** LP-TSV has no quoting: a tab separates, nothing escapes. */
  def tsvCells(line: String): List[String] = ((line).value.split("\t", -1).toList).map(refactorValue7 => (refactorValue7))

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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Extract:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type File = com.histmersion.api.catalogue.Gazetteer.SourceInfo.File
    val File: _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.File.type = _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.File
    type Sha256 = Sha256.T
    object Sha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Features = Features.T
    object Features extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = com.histmersion.api.catalogue.Gazetteer.SourceInfo.Title
    val Title: _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Title.type = _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Title
    type Licence = com.histmersion.api.catalogue.Gazetteer.SourceInfo.Licence
    val Licence: _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Licence.type = _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Licence
    type Attribution = Attribution.T
    object Attribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Url = Url.T
    object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Built = Built.T
    object Built extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `Tgn`: the Getty Thesaurus of Geographic Names. */
  enum Kind:
    case Geonames, Wikidata, Lpf, Tgn

    def wire: com.histmersion.api.catalogue.Gazetteer.Kind.Wire = _root_.com.histmersion.api.catalogue.Gazetteer.Kind.Wire(toString.toLowerCase(java.util.Locale.ROOT))

  object Kind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.catalogue.Gazetteer.Kind.Wire): Option[Kind] = values.find(_.wire == _root_.com.histmersion.api.catalogue.Gazetteer.Kind.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

    def ofKey(key: com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key): Option[Kind] = parse(_root_.com.histmersion.api.catalogue.Gazetteer.Kind.Wire((key).value.takeWhile(_ != ':')))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((e.wire).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[Kind] = parse(_root_.com.histmersion.api.catalogue.Gazetteer.Kind.Wire((s).value))
    given Encoder[Kind] = WireEnum.encoder
    given Decoder[Kind] = WireEnum.decoder
    given Schema[Kind] = WireEnum.schema

  final case class LoadCounts(
      source: LoadCounts.Source,
      version: LoadCounts.Version,
      records: LoadCounts.Records = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.Records((0).toLong),
      created: LoadCounts.Created = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.Created((0).toLong),
      joined: LoadCounts.Joined = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.Joined((0).toLong),
      placesChanged: LoadCounts.PlacesChanged = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.PlacesChanged((0).toLong),
      namesAdded: LoadCounts.NamesAdded = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.NamesAdded((0).toLong),
      namesRemoved: LoadCounts.NamesRemoved = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.NamesRemoved((0).toLong),
      namesChanged: LoadCounts.NamesChanged = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.NamesChanged((0).toLong),
      linksAdded: LoadCounts.LinksAdded = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.LinksAdded((0).toLong),
      linksRemoved: LoadCounts.LinksRemoved = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.LinksRemoved((0).toLong),
      placesRemoved: LoadCounts.PlacesRemoved = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.PlacesRemoved((0).toLong),
      skipped: List[LoadCounts.Skipped] = Nil,
      descriptionsAdded: LoadCounts.DescriptionsAdded = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.DescriptionsAdded((0).toLong),
      descriptionsRemoved: LoadCounts.DescriptionsRemoved = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.DescriptionsRemoved((0).toLong),
      descriptionsChanged: LoadCounts.DescriptionsChanged = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.DescriptionsChanged((0).toLong),
      linksChanged: LoadCounts.LinksChanged = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.LinksChanged((0).toLong),
      pointAreasSkipped: LoadCounts.PointAreasSkipped = _root_.com.histmersion.api.catalogue.Gazetteer.LoadCounts.PointAreasSkipped((0).toLong)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def +(o: LoadCounts): LoadCounts =
      this.modify(
        _.records).setTo(((records).value + (o.records).value).value).modify(_.created).setTo((created).value + o.created).modify(_.joined).setTo(joined + o.joined).modify(_.placesChanged).setTo(placesChanged + o.placesChanged).modify(_.namesAdded).setTo(namesAdded + o.namesAdded).modify(_.namesRemoved).setTo(namesRemoved + o.namesRemoved).modify(_.namesChanged).setTo(namesChanged + o.namesChanged).modify(_.linksAdded).setTo(linksAdded + o.linksAdded).modify(_.linksRemoved).setTo(linksRemoved + o.linksRemoved).modify(_.placesRemoved).setTo(placesRemoved + o.placesRemoved).modify(_.skipped).setTo((skipped ++ o.skipped).take(20)).modify(_.descriptionsAdded).setTo(descriptionsAdded + o.descriptionsAdded).modify(_.descriptionsRemoved).setTo(descriptionsRemoved + o.descriptionsRemoved).modify(_.descriptionsChanged).setTo(descriptionsChanged + o.descriptionsChanged).modify(_.linksChanged).setTo(linksChanged + o.linksChanged).modify(_.pointAreasSkipped).setTo(pointAreasSkipped + o.pointAreasSkipped
      )

    def unchanged: Boolean =
      created == 0 && placesChanged == 0 && namesAdded == 0 && namesRemoved == 0 && namesChanged == 0 &&
        linksAdded == 0 && linksRemoved == 0 && linksChanged == 0 && placesRemoved == 0 &&
        descriptionsAdded == 0 && descriptionsRemoved == 0 && descriptionsChanged == 0

    def describe: String =
      s"$source $version: ${Plural.count(records, _root_.com.histmersion.api.Plural.CountSingular("record"))}, $created new place(s), $joined joined an existing place, " +
        s"$placesChanged place(s) changed, names +$namesAdded −$namesRemoved ~$namesChanged, links +$linksAdded −$linksRemoved" +
        (if linksChanged > 0 then s" ~$linksChanged" else "") +
        (if descriptionsAdded + descriptionsRemoved + descriptionsChanged > 0 then
           s", descriptions +$descriptionsAdded −$descriptionsRemoved ~$descriptionsChanged"
         else "") +
        (if placesRemoved > 0 then s", $placesRemoved place(s) removed" else "") +
        (if pointAreasSkipped > 0 then s", $pointAreasSkipped point-only region(s)/polity(ies) not kept as places"
         else "") +
        (if skipped.nonEmpty then s", ${skipped.size} skipped" else "")

  object LoadCounts:

    type Source = com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key
    val Source: _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key.type = _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key
    type Version = com.histmersion.api.catalogue.Gazetteer.SourceInfo.ImportedVersion
    val Version: _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.ImportedVersion.type = _root_.com.histmersion.api.catalogue.Gazetteer.SourceInfo.ImportedVersion
    type Records = Records.T
    object Records extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Created = Created.T
    object Created extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Joined = Joined.T
    object Joined extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacesChanged = PlacesChanged.T
    object PlacesChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NamesAdded = NamesAdded.T
    object NamesAdded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NamesRemoved = NamesRemoved.T
    object NamesRemoved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NamesChanged = NamesChanged.T
    object NamesChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksAdded = LinksAdded.T
    object LinksAdded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksRemoved = LinksRemoved.T
    object LinksRemoved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacesRemoved = PlacesRemoved.T
    object PlacesRemoved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Skipped = Skipped.T
    object Skipped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescriptionsAdded = DescriptionsAdded.T
    object DescriptionsAdded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescriptionsRemoved = DescriptionsRemoved.T
    object DescriptionsRemoved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescriptionsChanged = DescriptionsChanged.T
    object DescriptionsChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksChanged = LinksChanged.T
    object LinksChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointAreasSkipped = PointAreasSkipped.T
    object PointAreasSkipped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Manifest(extracts: List[Extract])
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

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
      periodStart: Option[com.histmersion.api.catalogue.Year] = None,
      periodEnd: Option[com.histmersion.api.catalogue.Year] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object SourceInfo:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Present = Present.T
    object Present extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PinnedVersion = PinnedVersion.T
    object PinnedVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ImportedVersion = ImportedVersion.T
    object ImportedVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ImportedAt = ImportedAt.T
    object ImportedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Names = Names.T
    object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Links = Links.T
    object Links extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Licence = Licence.T
    object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Citation = Citation.T
    object Citation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
