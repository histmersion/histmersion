package com.histmersion.plugins.events

import com.histmersion.plugin.*
import com.histmersion.upstreams.dating.CatalogDating
import io.circe.{ACursor, Json}

import java.util.Locale

/** NOAA NCEI's significant earthquakes, eruptions and tsunamis: its hazard service
  * (`/hazel/hazard-service/api/v1/{earthquakes,volcanoes,tsunamis/events}`), page by page. A part is a page of a
  * dataset, `<dataset>:<page>`, the next page named by the one before; the pages of a dataset are of its newest
  * records, so every one is read at every import.
  */
final class NoaaCatalogue(cfg: NoaaSettings) extends EventCatalogue:
  // a U.S. federal work: public domain; NCEI asks to be cited
  def title = "NOAA NCEI Natural Hazards"
  def licence = Licence("public-domain")
  def citation =
    "National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami " +
      "and Volcano Databases. NOAA National Centers for Environmental Information. https://doi.org/10.7289/V5TD9V7K"
  def home = "https://www.ngdc.noaa.gov/hazard/"
  override def requestIntervalMs: Int = cfg.requestIntervalMs
  override def everyPartEachImport: Boolean = true

  def parts(query: CatalogueQuery): List[com.histmersion.plugins.events.NoaaCatalogue.PartsResult] = cfg.datasetList.map(ds => s"$ds:1")

  def read(part: com.histmersion.plugins.events.NoaaCatalogue.ReadPart, query: CatalogueQuery, http: Plugin.Http): Either[com.histmersion.plugins.events.NoaaCatalogue.ReadResultPart1, CataloguePage] =
    part match
      case NoaaCatalogue.Page(dataset, page) =>
        val p = page.toInt
        CatalogueJson(http)(
          NoaaCatalogue.url(cfg.url, dataset, query.from.map(_.value.toLong), query.to.value.toLong, p, cfg.pageSize)
        ).flatMap(NoaaCatalogue.parse(dataset, _))
          .map((rs, pages) => CataloguePage(rs, Option.when(p < pages)(s"$dataset:${p + 1}")))
      case other => Left(s"'$other' is no page of NOAA's datasets")

  /** The item read again from its `raw`, its dataset named by its id (`volcano:1351`). */
  override def reread(stored: CatalogueEvent): Option[CatalogueEvent] =
    stored.raw
      .flatMap(t => io.circe.parser.parse(t).toOption)
      .flatMap(j => NoaaCatalogue.datasetOf(stored.id).flatMap(NoaaCatalogue.record(_, j.hcursor)))
      .map(n => n.copy(names = (stored.names ++ n.names).distinct.filterNot(_ == n.label)))

object NoaaCatalogue:
  type PartsResult = PartsResult.T
  object PartsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadResultPart1 = ReadResultPart1.T
  object ReadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPart = ReadPart.T
  object ReadPart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Datasets = Datasets.T
  object Datasets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PathResult = PathResult.T
  object PathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PathDs = PathDs.T
  object PathDs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlResult = UrlResult.T
  object UrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlBase = UrlBase.T
  object UrlBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlDataset = UrlDataset.T
  object UrlDataset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlFrom = UrlFrom.T
  object UrlFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlTo = UrlTo.T
  object UrlTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlPage = UrlPage.T
  object UrlPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlPerPage = UrlPerPage.T
  object UrlPerPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart2Part2 = ParseResultPart2Part2.T
  object ParseResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseDataset = ParseDataset.T
  object ParseDataset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatasetOfResult = DatasetOfResult.T
  object DatasetOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatasetOfProviderId = DatasetOfProviderId.T
  object DatasetOfProviderId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleCaseResult = TitleCaseResult.T
  object TitleCaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleCaseS = TitleCaseS.T
  object TitleCaseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordDataset = RecordDataset.T
  object RecordDataset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SingularResult = SingularResult.T
  object SingularResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SingularDs = SingularDs.T
  object SingularDs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Earthquakes = "earthquakes"
  val Volcanoes = "volcanoes"
  val Tsunamis = "tsunamis"
  val Datasets: List[com.histmersion.plugins.events.NoaaCatalogue.Datasets] = List(Earthquakes, Volcanoes, Tsunamis)

  private val Page = "([a-z]+):(\\d+)".r

  private def path(ds: com.histmersion.plugins.events.NoaaCatalogue.PathDs): com.histmersion.plugins.events.NoaaCatalogue.PathResult = if ds == Tsunamis then "tsunamis/events" else ds

  /** Pure: one page of a dataset's events up to `to` (and from `from`). */
  def url(base: com.histmersion.plugins.events.NoaaCatalogue.UrlBase, dataset: com.histmersion.plugins.events.NoaaCatalogue.UrlDataset, from: Option[com.histmersion.plugins.events.NoaaCatalogue.UrlFrom], to: com.histmersion.plugins.events.NoaaCatalogue.UrlTo, page: com.histmersion.plugins.events.NoaaCatalogue.UrlPage, perPage: com.histmersion.plugins.events.NoaaCatalogue.UrlPerPage): com.histmersion.plugins.events.NoaaCatalogue.UrlResult =
    s"${base.stripSuffix("/")}/${path(dataset)}?maxYear=$to${from.fold("")(f => s"&minYear=$f")}" +
      s"&itemsPerPage=$perPage&page=$page"

  /** Pure: one page's events and the number of pages. Tsunamis NOAA marks erroneous (validity −1) or a mere seiche (0)
    * are dropped; very doubtful (1) and questionable (2) ones are `contested`.
    */
  def parse(dataset: com.histmersion.plugins.events.NoaaCatalogue.ParseDataset, json: Json): Either[com.histmersion.plugins.events.NoaaCatalogue.ParseResultPart1, (List[CatalogueEvent], com.histmersion.plugins.events.NoaaCatalogue.ParseResultPart2Part2)] =
    val c = json.hcursor
    c.downField("items").focus.flatMap(_.asArray) match
      case None        => Left("no items in the answer")
      case Some(items) =>
        Right((items.toList.flatMap(i => record(dataset, i.hcursor)), c.get[Int]("totalPages").getOrElse(1)))

  /** Pure: the dataset a NOAA record's id names (`volcano:1351` → volcanoes). */
  def datasetOf(providerId: com.histmersion.plugins.events.NoaaCatalogue.DatasetOfProviderId): Option[com.histmersion.plugins.events.NoaaCatalogue.DatasetOfResult] =
    providerId.takeWhile(_ != ':') match
      case "earthquake" => Some(Earthquakes)
      case "volcano"    => Some(Volcanoes)
      case "tsunami"    => Some(Tsunamis)
      case _            => None

  /** Pure: "SYRIA:  UGARIT" → "Syria: Ugarit" — NOAA's upper-case place names in title case. */
  def titleCase(s: com.histmersion.plugins.events.NoaaCatalogue.TitleCaseS): com.histmersion.plugins.events.NoaaCatalogue.TitleCaseResult =
    "(^|[\\s(/,-])(\\p{L})".r.replaceAllIn(
      s.trim.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT),
      m => java.util.regex.Matcher.quoteReplacement(m.group(1) + m.group(2).toUpperCase(Locale.ROOT))
    )

  /** Pure: one item of a dataset's answer as an event. */
  def record(dataset: com.histmersion.plugins.events.NoaaCatalogue.RecordDataset, i: ACursor): Option[CatalogueEvent] =
    import JsonFields.*
    val kind = dataset match
      case Earthquakes => "earthquake"
      case Volcanoes   => "eruption"
      case _           => "tsunami"
    val validity = long(i, "eventValidity")
    for
      id <- long(i, "id")
      year <- long(i, "year")
      if dataset != Tsunamis || validity.forall(_ >= 1)
    yield
      val where = str(i, "locationName").orElse(str(i, "name")).map(titleCase).getOrElse("unknown place")
      val one = singular(dataset)
      val label = dataset match
        case Volcanoes => s"Eruption of ${str(i, "name").getOrElse(where)}"
        case _         => s"${one.capitalize}, $where"
      val deaths = long(i, "deathsTotal").orElse(long(i, "deaths"))
      val d = Dated.of(CatalogDating.span(Some(CatalogDating.year(year)), None))
      CatalogueEvent(
        id = s"$one:$id",
        url = s"https://www.ngdc.noaa.gov/hazel/view/hazards/${
            if dataset == Tsunamis then "tsunami" else one
          }/event-more-info/$id",
        label = label,
        kind = kind,
        notBefore = d.notBefore,
        notAfter = d.notAfter,
        beginBy = d.beginBy,
        endFrom = d.endFrom,
        lat = latitude(dbl(i, "latitude")),
        lon = longitude(dbl(i, "longitude")),
        historicity = if validity.exists(_ <= 2) then "contested" else "attested",
        // a tsunami's record carries the magnitude of the earthquake behind it: not the tsunami's size
        vei = long(i, "vei").map(_.toInt).filter(_ => dataset == Volcanoes),
        magnitude = dbl(i, "eqMagnitude").filter(_ => dataset == Earthquakes),
        deaths = deaths,
        names = Option.when(dataset == Volcanoes)(str(i, "name").map(n => s"$n eruption")).flatten.toList,
        description = Some(
          (List(s"$where") ++ dbl(i, "eqMagnitude").map(m =>
            if dataset == Earthquakes then s"magnitude $m" else s"caused by an earthquake of magnitude $m"
          ) ++
            long(i, "vei").map(v => s"VEI $v") ++ deaths.map(d => s"$d deaths recorded") ++
            dbl(i, "maxWaterHeight").map(h => s"max. water height $h m")).mkString("; ")
        ),
        group = if dataset == Volcanoes then long(i, "volcanoLocationNewNum").map(v => s"volcano:$v") else None,
        central = JsonFields.year(year),
        raw = i.focus.map(_.noSpaces)
      )

  private def singular(ds: com.histmersion.plugins.events.NoaaCatalogue.SingularDs): com.histmersion.plugins.events.NoaaCatalogue.SingularResult = ds match
    case Earthquakes => "earthquake"
    case Volcanoes   => "volcano"
    case _           => "tsunami"
