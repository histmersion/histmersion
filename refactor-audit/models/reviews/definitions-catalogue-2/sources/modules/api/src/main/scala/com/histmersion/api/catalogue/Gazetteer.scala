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

  def sourceKey(kind: com.histmersion.api.catalogue.Gazetteer.Kind, dataset: Option[String] = None): String = kind match
    case com.histmersion.api.catalogue.Gazetteer.Kind.Lpf => s"lpf:${dataset.getOrElse("")}"
    case k                                                => k.wire

  def datasetSlug(fileName: String): String =
    val base = fileName
      .replaceAll("(?i)(\\.meta)?\\.(jsonld|json|geojson|ndjson|jsonl|tsv)(\\.gz)?$", "")
      .replaceAll("(?i)\\.lpf$", "")
    slug(base).take(48)

  private def slug(s: String): String =
    s.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "")

  def rank(m: Place.Match): Int = m.importSource.flatMap(com.histmersion.api.catalogue.Gazetteer.Kind.ofKey) match
    case None                                                        => if m.importSource.isEmpty then 0 else 5
    case Some(com.histmersion.api.catalogue.Gazetteer.Kind.Lpf)      => 1
    case Some(com.histmersion.api.catalogue.Gazetteer.Kind.Wikidata) => 2
    case Some(com.histmersion.api.catalogue.Gazetteer.Kind.Tgn)      => 3
    case Some(com.histmersion.api.catalogue.Gazetteer.Kind.Geonames) => 4

  val ImportedSources: List[String] =
    List(
      com.histmersion.api.catalogue.Gazetteer.Kind.Wikidata,
      com.histmersion.api.catalogue.Gazetteer.Kind.Geonames,
      com.histmersion.api.catalogue.Gazetteer.Kind.Tgn
    ).map(k => sourceKey(k))

  def canonical(byOwn: List[Place.Match], byLinks: List[Place.Match] = Nil): Option[String] =
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
    com.histmersion.api.catalogue.Gazetteer.Kind
      .ofKey(sourceKey)
      .exists(k => k != com.histmersion.api.catalogue.Gazetteer.Kind.Lpf)

  def validity(sourceKey: String, span: Span, windowYears: Int): Validity = span match
    case Span(None, Some(end)) if unknownWhenSilent(sourceKey) =>
      Validity(Some(end - math.max(0, windowYears)), Some(end), startUnknown = true)
    case Span(from, to) => Validity(from, to, startUnknown = false)

  val DefaultPointAreaKinds: List[String] = List(
    com.histmersion.api.catalogue.Place.Kind.Region,
    com.histmersion.api.catalogue.Place.Kind.Polity,
    com.histmersion.api.catalogue.Place.Kind.People
  )

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
    if r.span.isEmpty && !ds.span.isEmpty then r.modify(_.span).setTo(ds.span) else r

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
    Lpf.readFeature(j, com.histmersion.api.catalogue.Place.Kind.Site, ordinal).flatMap { in =>
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
          get("types")
            .flatMap(t => t.split(";").toList.flatMap(com.histmersion.api.catalogue.Place.Kind.parse).headOption)
            .getOrElse(com.histmersion.api.catalogue.Place.Kind.Site)
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
            placeId = com.histmersion.api.catalogue.Place.Id(placeIdFor(own, ref, dataset)),
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
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sha256 = Sha256.T
    object Sha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Features = Features.T
    object Features extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Licence = Licence.T
    object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attribution = Attribution.T
    object Attribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Url = Url.T
    object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Built = Built.T
    object Built extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `Tgn`: the Getty Thesaurus of Geographic Names. */
  enum Kind:
    case Geonames, Wikidata, Lpf, Tgn

    def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

  object Kind:
    def parse(s: String): Option[Kind] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

    def ofKey(key: String): Option[Kind] = parse(key.takeWhile(_ != ':'))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): String = e.wire
      def fromWire(s: String): Option[Kind] = parse(s)
    given Encoder[Kind] = WireEnum.encoder
    given Decoder[Kind] = WireEnum.decoder
    given Schema[Kind] = WireEnum.schema

  final case class LoadCounts(
      source: LoadCounts.Source,
      version: LoadCounts.Version,
      records: LoadCounts.Records = 0,
      created: LoadCounts.Created = 0,
      joined: LoadCounts.Joined = 0,
      placesChanged: LoadCounts.PlacesChanged = 0,
      namesAdded: LoadCounts.NamesAdded = 0,
      namesRemoved: LoadCounts.NamesRemoved = 0,
      namesChanged: LoadCounts.NamesChanged = 0,
      linksAdded: LoadCounts.LinksAdded = 0,
      linksRemoved: LoadCounts.LinksRemoved = 0,
      placesRemoved: LoadCounts.PlacesRemoved = 0,
      skipped: List[LoadCounts.Skipped] = Nil,
      descriptionsAdded: LoadCounts.DescriptionsAdded = 0,
      descriptionsRemoved: LoadCounts.DescriptionsRemoved = 0,
      descriptionsChanged: LoadCounts.DescriptionsChanged = 0,
      linksChanged: LoadCounts.LinksChanged = 0,
      pointAreasSkipped: LoadCounts.PointAreasSkipped = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def +(o: LoadCounts): LoadCounts =
      this.modify(
        _.records).setTo(records + o.records).modify(_.created).setTo(created + o.created).modify(_.joined).setTo(joined + o.joined).modify(_.placesChanged).setTo(placesChanged + o.placesChanged).modify(_.namesAdded).setTo(namesAdded + o.namesAdded).modify(_.namesRemoved).setTo(namesRemoved + o.namesRemoved).modify(_.namesChanged).setTo(namesChanged + o.namesChanged).modify(_.linksAdded).setTo(linksAdded + o.linksAdded).modify(_.linksRemoved).setTo(linksRemoved + o.linksRemoved).modify(_.placesRemoved).setTo(placesRemoved + o.placesRemoved).modify(_.skipped).setTo((skipped ++ o.skipped).take(20)).modify(_.descriptionsAdded).setTo(descriptionsAdded + o.descriptionsAdded).modify(_.descriptionsRemoved).setTo(descriptionsRemoved + o.descriptionsRemoved).modify(_.descriptionsChanged).setTo(descriptionsChanged + o.descriptionsChanged).modify(_.linksChanged).setTo(linksChanged + o.linksChanged).modify(_.pointAreasSkipped).setTo(pointAreasSkipped + o.pointAreasSkipped
      )

    def unchanged: Boolean =
      created == 0 && placesChanged == 0 && namesAdded == 0 && namesRemoved == 0 && namesChanged == 0 &&
        linksAdded == 0 && linksRemoved == 0 && linksChanged == 0 && placesRemoved == 0 &&
        descriptionsAdded == 0 && descriptionsRemoved == 0 && descriptionsChanged == 0

    def describe: String =
      s"$source $version: ${Plural.count(records, "record")}, $created new place(s), $joined joined an existing place, " +
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
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
