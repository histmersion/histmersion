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
  def title = com.histmersion.plugin.EventCatalogue.TitleResult("NOAA NCEI Natural Hazards")
  def licence = Licence("public-domain")
  def citation =
    "National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami " +
      "and Volcano Databases. NOAA National Centers for Environmental Information. https://doi.org/10.7289/V5TD9V7K"
  def home = com.histmersion.plugin.EventCatalogue.HomeResult("https://www.ngdc.noaa.gov/hazard/")
  override def requestIntervalMs: Int = cfg.requestIntervalMs
  override def everyPartEachImport: Boolean = true

  def parts(query: CatalogueQuery): List[com.histmersion.plugins.events.NoaaCatalogue.Part] = cfg.datasetList.map(ds => s"$ds:1")

  def read(part: com.histmersion.plugins.events.NoaaCatalogue.Part, query: CatalogueQuery, http: Plugin.Http): Either[com.histmersion.plugins.events.NoaaCatalogue.Problem, CataloguePage] =
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
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dataset = Dataset.T
  object Dataset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Part = Part.T
  object Part extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProviderId = ProviderId.T
  object ProviderId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = Year.T
  object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Datasets = DatasetsValue
  type DatasetsValue = DatasetsValue.T
  object DatasetsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlPage = UrlPage.T
  object UrlPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseDataset = _root_.com.histmersion.plugins.events.NoaaCatalogue.Dataset
  val ParseDataset: _root_.com.histmersion.plugins.events.NoaaCatalogue.Dataset.type = _root_.com.histmersion.plugins.events.NoaaCatalogue.Dataset

  val Earthquakes = "earthquakes"
  val Volcanoes = "volcanoes"
  val Tsunamis = "tsunamis"
  val Datasets: List[com.histmersion.plugins.events.NoaaCatalogue.Datasets] = List(Earthquakes, Volcanoes, Tsunamis)

  private val Page = "([a-z]+):(\\d+)".r

  private def path(ds: com.histmersion.plugins.events.NoaaCatalogue.Dataset): com.histmersion.plugins.events.NoaaCatalogue.Path = if ds == Tsunamis then com.histmersion.plugins.events.NoaaCatalogue.Path("tsunamis/events") else ds

  /** Pure: one page of a dataset's events up to `to` (and from `from`). */
  def url(base: com.histmersion.plugin.Plugin.Http.GetUrl, dataset: com.histmersion.plugins.events.NoaaCatalogue.Dataset, from: Option[com.histmersion.plugins.events.NoaaCatalogue.Year], to: com.histmersion.plugins.events.NoaaCatalogue.Year, page: com.histmersion.plugins.events.NoaaCatalogue.UrlPage, perPage: com.histmersion.plugins.events.NoaaSettings.PageSize): com.histmersion.plugin.Plugin.Http.GetUrl =
    s"${base.stripSuffix("/")}/${path(dataset)}?maxYear=$to${from.fold("")(f => s"&minYear=$f")}" +
      s"&itemsPerPage=$perPage&page=$page"

  /** Pure: one page's events and the number of pages. Tsunamis NOAA marks erroneous (validity −1) or a mere seiche (0)
    * are dropped; very doubtful (1) and questionable (2) ones are `contested`.
    */
  def parse(dataset: com.histmersion.plugins.events.NoaaCatalogue.ParseDataset, json: Json): Either[com.histmersion.plugins.events.NoaaCatalogue.Problem, (List[CatalogueEvent], com.histmersion.plugins.events.NoaaCatalogue.Count)] =
    val c = json.hcursor
    c.downField("items").focus.flatMap(_.asArray) match
      case None        => Left(com.histmersion.plugins.events.NoaaCatalogue.Problem("no items in the answer"))
      case Some(items) =>
        Right((items.toList.flatMap(i => record(dataset, i.hcursor)), c.get[Int]("totalPages").getOrElse(1)))

  /** Pure: the dataset a NOAA record's id names (`volcano:1351` → volcanoes). */
  def datasetOf(providerId: com.histmersion.plugins.events.NoaaCatalogue.ProviderId): Option[com.histmersion.plugins.events.NoaaCatalogue.Dataset] =
    providerId.takeWhile(_ != ':') match
      case "earthquake" => Some(Earthquakes)
      case "volcano"    => Some(Volcanoes)
      case "tsunami"    => Some(Tsunamis)
      case _            => None

  /** Pure: "SYRIA:  UGARIT" → "Syria: Ugarit" — NOAA's upper-case place names in title case. */
  def titleCase(s: com.histmersion.plugins.events.NoaaCatalogue.Text): com.histmersion.plugins.events.NoaaCatalogue.Title =
    "(^|[\\s(/,-])(\\p{L})".r.replaceAllIn(
      s.trim.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT),
      m => java.util.regex.Matcher.quoteReplacement(m.group(1) + m.group(2).toUpperCase(Locale.ROOT))
    )

  /** Pure: one item of a dataset's answer as an event. */
  def record(dataset: com.histmersion.plugins.events.NoaaCatalogue.Dataset, i: ACursor): Option[CatalogueEvent] =
    import JsonFields.*
    val kind = dataset match
      case Earthquakes => "earthquake"
      case Volcanoes   => "eruption"
      case _           => "tsunami"
    val validity = long(i, com.histmersion.plugins.events.JsonFields.Key("eventValidity"))
    for
      id <- long(i, com.histmersion.plugins.events.JsonFields.Key("id"))
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

  private def singular(ds: com.histmersion.plugins.events.NoaaCatalogue.Dataset): com.histmersion.plugins.events.NoaaCatalogue.Name = ds match
    case Earthquakes => com.histmersion.plugins.events.NoaaCatalogue.Name("earthquake")
    case Volcanoes   => com.histmersion.plugins.events.NoaaCatalogue.Name("volcano")
    case _           => com.histmersion.plugins.events.NoaaCatalogue.Name("tsunami")
