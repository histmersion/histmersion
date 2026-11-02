package com.histmersion.api

import com.histmersion.api.catalogue.EventsMarkdown

import com.histmersion.api.automation.AgentStateMarkdown
import com.histmersion.api.catalogue.AttestationsMarkdown
import com.histmersion.api.catalogue.PlacesMarkdown
import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown
import com.histmersion.api.media.MediaMarkdown

import com.histmersion.values.{LocationPrecision, SourceStatus}
import com.histmersion.domain.library.Confidence
import hearth.kindlings.optics.syntax.*
import io.circe.syntax.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import com.histmersion.api.automation.AgentStateEntry
import com.histmersion.api.backup.BackupStatus
import com.histmersion.api.catalogue.{
  Attestation,
  EventDating,
  EventSummary,
  GazetteerKind,
  GazetteerSourceInfo,
  PlaceGone,
  PlaceLink,
  PlaceRebuildResult,
  PlaceSummary,
  SkippedPeriod
}
import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.library.{
  ContradictionRow,
  ContradictionsResult,
  FindingRow,
  GeoHit,
  GeoHitPage,
  GraphLink,
  GraphNode,
  Hit,
  ResolvedSource,
  SearchHit,
  SearchResult,
  SourceGraph
}
import com.histmersion.api.maintenance.VacuumPlan
import com.histmersion.api.media.{
  AssetDetail,
  AssetLink,
  FigureLicenceFill,
  MediaCount,
  MediaFacets,
  MediaItem,
  MediaList,
  MediaTarget,
  MediaThumb
}
import com.histmersion.api.research.ResolvedArticle
import com.histmersion.api.views.{EventDetail, PlaceDetail, ResolvedId}

/** The Markdown and JSON of the wire records slice 12 moves, over synthetic fixture values: the renderers no other
  * suite asserts on (the brief search, a source's findings, the graph, the contradictions, the map feed, the body
  * slice, the gazetteers, a place removed, added and rebuilt, an attestation, the agent state, a picture, the media
  * list, facets and licence fill, an asset link, a backup's status) and the event's and place's pages with their
  * pictures; the JSON of an event's and a place's page, a resolved id and a job's detail. Recorded on the unchanged
  * code, so a move of the records or their renderers shows any change. `HISTMERSION_UPDATE_ORACLE=1` rewrites the file.
  */
class WireSnapshotSuite extends munit.FunSuite:
  private val file = Path.of("modules/api/src/test/resources/snapshot/wire.txt")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val hit = SearchHit(
    "zq_alpha_2001",
    "Zq Alpha",
    Some(2001),
    SourceStatus.Contested,
    List("zq-topic"),
    List("zq-region"),
    Some(-1450),
    Some(-1350),
    Some("A summary  with   spaces."),
    snippet = Some("the **zq** hit"),
    matchedHeading = Some("Zq heading")
  )
  private val result = SearchResult(
    3,
    List(
      Hit(hit, Nil, List("tag zq")),
      Hit(hit.modify(_.citekey).setTo("zq_beta_1999").modify(_.year).setTo(None), Nil, Nil)
    ),
    offset = 0,
    next = Some(2)
  )
  private val finding = FindingRow(
    "Zq claim one.",
    Some("Zqland"),
    Some(30.5),
    Some(40.25),
    Some(-1300),
    Some(-1200),
    Some("Zq period"),
    List("zq-tag"),
    Some(Confidence.High),
    significance = Some("key")
  )
  private val graph = SourceGraph(
    "zq_alpha_2001",
    2,
    List(GraphNode("zq_alpha_2001", Some("Zq Alpha"), Some(2001), Some(SourceStatus.Current)), GraphNode("zq_gamma")),
    List(GraphLink("zq_alpha_2001", "zq_gamma", "challenges"))
  )
  private val contradictions = ContradictionsResult(
    List(
      ContradictionRow(
        "zq_alpha_2001",
        "Zq Alpha",
        Some(2001),
        SourceStatus.Current,
        Some("out"),
        Some("refutes"),
        Some("zq_gamma"),
        Some("Zq Gamma"),
        Some(1990),
        Some(SourceStatus.Debunked)
      ),
      ContradictionRow("zq_beta_1999", "Zq Beta", None, SourceStatus.Contested, None, None, None, None)
    ),
    2
  )
  private val geo = GeoHit(
    "zq_alpha_2001",
    "Zq Alpha",
    "Zq claim.",
    Some("Zqland"),
    Some(30.5),
    Some(40.25),
    Some(-1300),
    None,
    Some(Confidence.Low)
  )
  private val gazetteers = List(
    GazetteerSourceInfo(
      "zq-gaz",
      GazetteerKind.Lpf,
      "Zq gazetteer",
      Some("zq.lpf.json"),
      present = true,
      Some("1.0"),
      importedVersion = Some("0.9"),
      importedAt = Some("2026-01-02T03:04:05Z"),
      places = Some(12),
      names = Some(30),
      links = Some(4),
      licence = Some("CC-BY-4.0"),
      citation = Some("Zq et al."),
      periodStart = Some(-3000),
      periodEnd = Some(-500)
    ),
    GazetteerSourceInfo("zq-missing", GazetteerKind.Geonames, "Zq missing", None, present = false, None)
  )
  private val summary =
    PlaceSummary("zq_tell", "Zq Tell", "settlement", Some(-2000), Some(-1200), Some("zq-gaz"), "Zq Tell; Tell Zq")
  private val thumb = MediaThumb(
    "00000000-0000-4000-8000-000000000001",
    "depiction",
    "/api/media/zq/thumb",
    "Zq Museum",
    license = Some("CC0-1.0"),
    licenseUrl = Some("https://example.org/cc0"),
    sourceUrl = Some("https://example.org/zq"),
    width = Some(640),
    height = Some(480)
  )
  private val item = MediaItem(
    "00000000-0000-4000-8000-000000000002",
    "zq.jpg",
    "image/jpeg",
    2048,
    citekey = Some("zq_alpha_2001"),
    provider = Some("zq-provider"),
    providerKey = Some("zq-1"),
    width = Some(800),
    height = Some(600),
    kind = Some("photo"),
    caption = Some("A zq vase"),
    license = Some("CC-BY-4.0"),
    author = Some("Zq Author"),
    credit = Some("Zq Museum"),
    sourceUrl = Some("https://example.org/zq"),
    thumbUrl = Some("/api/media/zq/thumb"),
    fileUrl = "/api/media/zq",
    targets = List(MediaTarget("place", "zq_tell", "depicts", property = Some("P180")))
  )
  private val event = EventDetail(
    EventSummary(
      "zq-event",
      "Zq eruption",
      "eruption",
      TimeSpan(Some(-1620), Some(-1610), Some(-1600), Some(-1590)),
      "attested",
      placeId = Some("zq_tell"),
      description = Some("A zq eruption."),
      citekey = Some("zq_alpha_2001")
    ),
    geometry = Some("POINT(30.5 40.25)"),
    children = List(EventSummary("zq-child", "Zq ash fall", "ashfall", TimeSpan(notBefore = Some(-1610)), "inferred")),
    datings =
      List(EventDating("zq-event", "zq_alpha_2001", TimeSpan(Some(-1620), None, None, Some(-1590)), Some("c14"))),
    wikidata = Some("Q1"),
    media = List(thumb),
    names = List("Zq eruption", "Zq event")
  )
  private val place = PlaceDetail(
    summary,
    Some("POINT(30.5 40.25)"),
    List(PlaceSummary("zq_land", "Zqland", "region", None, None, None, "Zqland")),
    Nil,
    findings = 3,
    asOf = Some(-1500),
    links = List(PlaceLink("zq_tell", "wikidata", "Q2")),
    locationPrecision = LocationPrecision.Stated,
    media = List(thumb),
    siteType = Some("tell")
  )

  private def rendered: String =
    val parts: List[(String, String)] = List(
      "briefResults" -> SearchMarkdown.briefResults(result),
      "briefResults empty" -> SearchMarkdown.briefResults(SearchResult(0, Nil)),
      "sourceFindingsOnly" -> SourcesMarkdown.sourceFindingsOnly(
        Vector(finding, finding.modify(_.claim).setTo("Zq claim two."))
      ),
      "sourceFindingsOnly empty" -> SourcesMarkdown.sourceFindingsOnly(Vector.empty),
      "graph" -> SourcesMarkdown.graph(graph),
      "contradictions" -> SourcesMarkdown.contradictions(contradictions, Some("zq_alpha_2001")),
      "contradictions all" -> SourcesMarkdown.contradictions(contradictions, None),
      "geoPage" -> SearchMarkdown.geoPage(
        GeoHitPage(2, List(geo, geo.modify(_.lat).setTo(None).modify(_.lon).setTo(None)), next = None)
      ),
      "geoResults empty" -> SearchMarkdown.geoResults(Vector.empty),
      "bodySlice" -> Markdown.bodySlice("Zq body text.", 10, 40),
      "gazetteerSources" -> PlacesMarkdown.gazetteerSources(gazetteers),
      "placeGone" -> PlacesMarkdown.placeGone(
        PlaceGone("zq_tell", "Zq Tell", Some("zq_land"), Some("Zqland"), 3, 2, 1)
      ),
      "placeGone none" -> PlacesMarkdown.placeGone(PlaceGone("zq_tell", "Zq Tell", None, None, 0, 0, 0)),
      "placeAdded" -> PlacesMarkdown.placeAdded(summary),
      "placeRebuild" -> PlacesMarkdown.placeRebuild(PlaceRebuildResult(10, 4, 5, 9, 1, 7)),
      "attestation" -> AttestationsMarkdown.attestation(
        Attestation(
          7,
          "zq_alpha_2001",
          "event",
          "zq-event",
          quote = Some("the zq quote"),
          prefix = Some("before"),
          suffix = Some("after"),
          charStart = Some(10),
          charEnd = Some(22),
          page = Some("12"),
          note = Some("a zq note")
        )
      ),
      "agentStateList" -> AgentStateMarkdown.agentStateList(
        List(AgentStateEntry("zq-key", "zq value", Some("2026-01-02T03:04:05Z")), AgentStateEntry("zq-other", "x"))
      ),
      "agentStateList empty" -> AgentStateMarkdown.agentStateList(Nil),
      "agentState" -> AgentStateMarkdown.agentState(AgentStateEntry("zq-key", "zq value")),
      "assetDetail" -> MediaMarkdown.assetDetail(
        "zq_alpha_2001",
        AssetDetail(
          "zq.png",
          "image/png",
          1024,
          "ab" * 32,
          width = Some(10),
          height = Some(20),
          kind = Some("figure"),
          caption = Some("Zq figure"),
          license = Some("CC-BY-4.0"),
          lat = Some(40.25),
          lon = Some(30.5),
          periodStart = Some(-1500),
          periodEnd = Some(-1400)
        )
      ),
      "mediaThumbLine" -> thumb.markdownLine,
      "mediaList" -> MediaMarkdown.mediaList(
        MediaList(2, List(item, item.modify(_.license).setTo(None).modify(_.targets).setTo(Nil)), next = Some(2))
      ),
      "mediaFacets" -> MediaMarkdown.mediaFacets(
        MediaFacets(List(MediaCount("zq-provider", 3)), List(MediaCount("CC0-1.0", 2), MediaCount("zq", 1, false)))
      ),
      "figureLicenceFill" -> MediaMarkdown.figureLicenceFill(
        FigureLicenceFill(5, 3, applied = false, List(MediaCount("zq_alpha_2001", 3)))
      ),
      "assetLink" -> MediaMarkdown.assetLink(
        AssetLink(3, "zq_alpha_2001", "zq.png", "depicts", "place", "zq_tell", Some("0,0,10,10"), Some("zq notes"))
      ),
      "assetLinkLine" -> MediaMarkdown.assetLinkLine(
        AssetLink(4, "zq_alpha_2001", "zq.png", "shows", "event", "zq-event")
      ),
      "backupStatus" -> Markdown.backupStatus(
        BackupStatus("zq.dump", "done", 1500, Some(4096), Some("cd" * 32), None, List("old.dump"))
      ),
      "backupStatus failed" -> Markdown.backupStatus(BackupStatus("zq.dump", "failed", 10, error = Some("zq error"))),
      "event" -> event.markdown,
      "place" -> PlacesMarkdown.place(place),
      "event json" -> event.asJson.spaces2,
      "place json" -> place.asJson.spaces2,
      "resolved source json" -> ResolvedId(
        "zq_alpha_2001",
        source = Some(
          ResolvedSource("zq_alpha_2001", Some("10.0/zq"), List("Zq A"), Some(2001), "Zq Alpha", SourceStatus.Current)
        )
      ).asJson.spaces2,
      "resolved article json" -> ResolvedId(
        "zq-article",
        article = Some(ResolvedArticle("zq-article", "Zq article"))
      ).asJson.spaces2,
      "resolved none json" -> ResolvedId("zq-nothing").asJson.spaces2,
      "job detail json" -> InitPlan
        .Detail(Some(InitPlan(gazetteer = true, fetch = List("zq-gaz"))))
        .set(SkippedPeriod.SkippedCount, 2)
        .set(com.histmersion.api.exchange.ExchangeJobKinds.PlanHash, Some("zq-hash"))
        .set(VacuumPlan.Detail, Some(VacuumPlan(analyze = true, List("sources"))))
        .set(com.histmersion.api.media.ImportJobKinds.Media, Some("a media run"))
        .set(com.histmersion.api.catalogue.EventsJobKinds.Catalogue, Some("zq-catalogue"))
        .set(JobDetail.Purpose, Some("zq_alpha_2001"))
        .asJson
        .spaces2
    )
    parts.map((k, v) => s"## $k\n$v\n").mkString("\n")

  test("the moved records' Markdown and JSON are the recorded ones"):
    val text = rendered
    if update then
      Files.createDirectories(file.getParent)
      val _ = Files.writeString(file, text, UTF_8)
    else assertNoDiff(text, Files.readString(file, UTF_8))
