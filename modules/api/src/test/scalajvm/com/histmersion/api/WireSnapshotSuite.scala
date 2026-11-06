package com.histmersion.api

import com.histmersion.api.catalogue.EventsMarkdown

import com.histmersion.api.automation.AgentStateMarkdown
import com.histmersion.api.catalogue.AttestationsMarkdown
import com.histmersion.api.catalogue.PlacesMarkdown
import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown
import com.histmersion.api.media.MediaMarkdown

import com.histmersion.api.library.Confidence
import hearth.kindlings.optics.syntax.*
import io.circe.syntax.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import com.histmersion.api.automation.AgentStateEntry
import com.histmersion.api.catalogue.{Attestation, EventDating, EventSummary, SkippedPeriod}
import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.library.{
  ContradictionRow,
  ContradictionsResult,
  GeoHit,
  GeoHitPage,
  GraphLink,
  GraphNode,
  Hit,
  ResolvedSource,
  SearchHit,
  SearchResult
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
import com.histmersion.api.catalogue.Place
import com.histmersion.api.library.Source
import com.histmersion.api.backup.Backup
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.library.Finding

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
    com.histmersion.api.library.Citekey("zq_alpha_2001"),
    com.histmersion.api.library.Title("Zq Alpha"),
    (Some(2001)).map(com.histmersion.api.catalogue.Year.apply),
    Source.Status.Contested,
    List(com.histmersion.api.library.Source.Topics("zq-topic")),
    List(com.histmersion.api.library.Source.Regions("zq-region")),
    Some(com.histmersion.api.catalogue.Year(-1450)),
    Some(com.histmersion.api.catalogue.Year(-1350)),
    Some(com.histmersion.api.library.Source.Summary("A summary  with   spaces.")),
    snippet = Some(com.histmersion.api.library.Search.Snippet("the **zq** hit")),
    matchedHeading = Some(com.histmersion.api.library.SearchHit.MatchedHeading("Zq heading"))
  )
  private val result = SearchResult(
    com.histmersion.api.RowCount(3),
    List(
      Hit(hit, Nil, List(com.histmersion.api.library.Hit.Why("tag zq"))),
      Hit(hit.modify(_.citekey).setTo(com.histmersion.api.library.Citekey("zq_beta_1999")).modify(_.year).setTo(None), Nil, Nil)
    ),
    offset = com.histmersion.api.Offset(0),
    next = (Some(2)).map(com.histmersion.api.Offset.apply)
  )
  private val finding = Finding.Row(
    com.histmersion.api.library.Finding.Claim("Zq claim one."),
    Some(com.histmersion.api.catalogue.Place.Region("Zqland")),
    Some(30.5),
    Some(40.25),
    Some(com.histmersion.api.catalogue.Year(-1300)),
    Some(com.histmersion.api.catalogue.Year(-1200)),
    Some(com.histmersion.api.library.Finding.PeriodName("Zq period")),
    List(com.histmersion.api.library.Finding.Tags("zq-tag")),
    Some(Confidence.High),
    significance = Some(com.histmersion.api.library.Finding.Significance("key"))
  )
  private val graph = com.histmersion.api.library.Source.Graph(
    com.histmersion.api.library.Source.Graph.Root("zq_alpha_2001"),
    com.histmersion.api.library.Source.Graph.Depth(2),
    List(GraphNode(com.histmersion.api.library.Citekey("zq_alpha_2001"), Some(com.histmersion.api.library.Title("Zq Alpha")), Some(com.histmersion.api.catalogue.Year(2001)), Some(Source.Status.Current)), GraphNode(com.histmersion.api.library.Citekey("zq_gamma"))),
    List(GraphLink(com.histmersion.api.library.Citekey("zq_alpha_2001"), com.histmersion.api.library.Citekey("zq_gamma"), com.histmersion.api.library.Edge.Kind("challenges")))
  )
  private val contradictions = ContradictionsResult(
    List(
      ContradictionRow(
        com.histmersion.api.library.Citekey("zq_alpha_2001"),
        com.histmersion.api.library.Title("Zq Alpha"),
        Some(com.histmersion.api.catalogue.Year(2001)),
        Source.Status.Current,
        Some(com.histmersion.api.library.ContradictionRow.Direction.Wire("out")),
        Some(com.histmersion.api.library.Edge.Kind("refutes")),
        Some(com.histmersion.api.library.Citekey("zq_gamma")),
        Some(com.histmersion.api.library.Title("Zq Gamma")),
        Some(com.histmersion.api.catalogue.Year(1990)),
        Some(Source.Status.Debunked)
      ),
      ContradictionRow(com.histmersion.api.library.Citekey("zq_beta_1999"), com.histmersion.api.library.Title("Zq Beta"), None, Source.Status.Contested, None, None, None, None)
    ),
    com.histmersion.api.library.ContradictionsResult.Total(2)
  )
  private val geo = GeoHit(
    com.histmersion.api.library.Citekey("zq_alpha_2001"),
    com.histmersion.api.library.GeoHit.Title("Zq Alpha"),
    com.histmersion.api.library.GeoHit.Claim("Zq claim."),
    Some(com.histmersion.api.catalogue.Place.Region("Zqland")),
    Some(30.5),
    Some(40.25),
    Some(com.histmersion.api.catalogue.Year(-1300)),
    None,
    Some(Confidence.Low)
  )
  private val gazetteers = List(
    Gazetteer.SourceInfo(
      com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key("zq-gaz"),
      Gazetteer.Kind.Lpf,
      com.histmersion.api.catalogue.Gazetteer.SourceInfo.Title("Zq gazetteer"),
      Some(com.histmersion.api.catalogue.Gazetteer.SourceInfo.File("zq.lpf.json")),
      present = com.histmersion.api.catalogue.Gazetteer.SourceInfo.Present(true),
      Some(com.histmersion.api.catalogue.Gazetteer.SourceInfo.PinnedVersion("1.0")),
      importedVersion = Some(com.histmersion.api.catalogue.Gazetteer.SourceInfo.ImportedVersion("0.9")),
      importedAt = Some(com.histmersion.api.catalogue.Gazetteer.SourceInfo.ImportedAt("2026-01-02T03:04:05Z")),
      places = Some(com.histmersion.api.catalogue.Gazetteer.SourceInfo.Places(12)),
      names = Some(com.histmersion.api.catalogue.Gazetteer.SourceInfo.Names(30)),
      links = Some(com.histmersion.api.catalogue.Gazetteer.SourceInfo.Links(4)),
      licence = Some(com.histmersion.api.catalogue.Gazetteer.SourceInfo.Licence("CC-BY-4.0")),
      citation = Some(com.histmersion.api.catalogue.Gazetteer.SourceInfo.Citation("Zq et al.")),
      periodStart = Some(com.histmersion.api.catalogue.Year(-3000)),
      periodEnd = Some(com.histmersion.api.catalogue.Year(-500))
    ),
    Gazetteer.SourceInfo(com.histmersion.api.catalogue.Gazetteer.SourceInfo.Key("zq-missing"), Gazetteer.Kind.Geonames, com.histmersion.api.catalogue.Gazetteer.SourceInfo.Title("Zq missing"), None, present = com.histmersion.api.catalogue.Gazetteer.SourceInfo.Present(false), None)
  )
  private val summary =
    com.histmersion.api.catalogue.Place.Summary(
      com.histmersion.api.catalogue.Place.Id("zq_tell"),
      com.histmersion.api.catalogue.Place.Summary.Label("Zq Tell"),
      com.histmersion.api.catalogue.Place.Summary.Kind("settlement"),
      Some(com.histmersion.api.catalogue.Year(-2000)),
      Some(com.histmersion.api.catalogue.Year(-1200)),
      Some(com.histmersion.api.catalogue.Place.Source("zq-gaz")),
      com.histmersion.api.catalogue.Place.Summary.Names("Zq Tell; Tell Zq")
    )
  private val thumb = MediaThumb(
    com.histmersion.api.media.MediaItem.Uid("00000000-0000-4000-8000-000000000001"),
    com.histmersion.api.media.MediaThumb.Role("depiction"),
    com.histmersion.api.media.MediaThumb.Url("/api/media/zq/thumb"),
    com.histmersion.api.media.MediaItem.Credit("Zq Museum"),
    license = Some(com.histmersion.api.media.MediaItem.License("CC0-1.0")),
    licenseUrl = Some(com.histmersion.api.media.MediaItem.LicenseUrl("https://example.org/cc0")),
    sourceUrl = Some(com.histmersion.api.media.MediaItem.SourceUrl("https://example.org/zq")),
    width = Some(com.histmersion.api.media.ImageSize.Width(640)),
    height = Some(com.histmersion.api.media.ImageSize.Height(480))
  )
  private val item = MediaItem(
    com.histmersion.api.media.MediaItem.Uid("00000000-0000-4000-8000-000000000002"),
    com.histmersion.api.exchange.Bundle.Asset.FileName("zq.jpg"),
    com.histmersion.api.exchange.Bundle.Asset.MimeText("image/jpeg"),
    com.histmersion.api.ByteCount(2048),
    citekey = Some(com.histmersion.api.library.Citekey("zq_alpha_2001")),
    provider = Some(com.histmersion.api.media.MediaItem.Provider("zq-provider")),
    providerKey = Some(com.histmersion.api.media.MediaItem.ProviderKey("zq-1")),
    width = Some(com.histmersion.api.media.ImageSize.Width(800)),
    height = Some(com.histmersion.api.media.ImageSize.Height(600)),
    kind = Some(com.histmersion.api.media.MediaItem.Kind("photo")),
    caption = Some(com.histmersion.api.media.MediaItem.Caption("A zq vase")),
    license = Some(com.histmersion.api.media.MediaItem.License("CC-BY-4.0")),
    author = Some("Zq Author"),
    credit = Some("Zq Museum"),
    sourceUrl = Some("https://example.org/zq"),
    thumbUrl = Some("/api/media/zq/thumb"),
    fileUrl = "/api/media/zq",
    targets = List(MediaTarget("place", "zq_tell", "depicts", property = Some("P180")))
  )
  private val event = EventDetail(
    EventSummary(
      com.histmersion.api.catalogue.Event.Id("zq-event"),
      com.histmersion.api.catalogue.EventSummary.Label("Zq eruption"),
      com.histmersion.api.catalogue.EventSummary.Kind("eruption"),
      TimeSpan(Some(com.histmersion.api.catalogue.Year(-1620)), Some(com.histmersion.api.catalogue.Year(-1610)), Some(com.histmersion.api.catalogue.Year(-1600)), Some(com.histmersion.api.catalogue.Year(-1590))),
      com.histmersion.api.catalogue.EventSummary.Historicity("attested"),
      placeId = Some(com.histmersion.api.catalogue.Place.Id("zq_tell")),
      description = Some("A zq eruption."),
      citekey = Some("zq_alpha_2001")
    ),
    geometry = Some(com.histmersion.api.catalogue.EventGeometry.Geojson("POINT(30.5 40.25)")),
    children = List(EventSummary("zq-child", "Zq ash fall", "ashfall", TimeSpan(notBefore = Some(-1610)), "inferred")),
    datings =
      List(EventDating(com.histmersion.api.catalogue.Event.Id("zq-event"), com.histmersion.api.library.Citekey("zq_alpha_2001"), TimeSpan(Some(-1620), None, None, Some(-1590)), Some("c14"))),
    wikidata = Some("Q1"),
    media = List(thumb),
    names = List("Zq eruption", "Zq event")
  )
  private val place = PlaceDetail(
    summary,
    Some(com.histmersion.api.catalogue.Place.Geometry("POINT(30.5 40.25)")),
    List(com.histmersion.api.catalogue.Place.Summary(com.histmersion.api.catalogue.Place.Id("zq_land"), com.histmersion.api.catalogue.Place.Summary.Label("Zqland"), com.histmersion.api.catalogue.Place.Summary.Kind("region"), None, None, None, com.histmersion.api.catalogue.Place.Summary.Names("Zqland"))),
    Nil,
    findings = com.histmersion.api.catalogue.Place.Counts.Findings(3),
    asOf = Some(com.histmersion.api.catalogue.Year(-1500)),
    links = List(com.histmersion.api.catalogue.Place.Link(com.histmersion.api.catalogue.Place.Id("zq_tell"), "wikidata", "Q2")),
    locationPrecision = Place.Precision.Stated,
    media = List(thumb),
    siteType = Some("tell")
  )

  private def rendered: String =
    val parts: List[(String, String)] = List(
      "briefResults" -> SearchMarkdown.briefResults(result),
      "briefResults empty" -> SearchMarkdown.briefResults(SearchResult(com.histmersion.api.RowCount(0), Nil)),
      "sourceFindingsOnly" -> SourcesMarkdown.sourceFindingsOnly(
        Vector(finding, finding.modify(_.claim).setTo("Zq claim two."))
      ),
      "sourceFindingsOnly empty" -> SourcesMarkdown.sourceFindingsOnly(Vector.empty),
      "graph" -> SourcesMarkdown.graph(graph),
      "contradictions" -> SourcesMarkdown.contradictions(contradictions, Some(com.histmersion.api.library.Citekey("zq_alpha_2001"))),
      "contradictions all" -> SourcesMarkdown.contradictions(contradictions, None),
      "geoPage" -> SearchMarkdown.geoPage(
        GeoHitPage(com.histmersion.api.RowCount(2), List(geo, geo.modify(_.lat).setTo(None).modify(_.lon).setTo(None)), next = None)
      ),
      "geoResults empty" -> SearchMarkdown.geoResults(Vector.empty),
      "bodySlice" -> Markdown.bodySlice(com.histmersion.api.library.Source.Body("Zq body text."), com.histmersion.api.personal.UnreadPage.Offset(10), 40),
      "gazetteerSources" -> PlacesMarkdown.gazetteerSources(gazetteers),
      "placeGone" -> PlacesMarkdown.placeGone(
        com.histmersion.api.catalogue.Place.Gone(com.histmersion.api.catalogue.Place.Id("zq_tell"), com.histmersion.api.catalogue.Place.Gone.Label("Zq Tell"), Some(com.histmersion.api.catalogue.Place.Gone.Into("zq_land")), Some(com.histmersion.api.catalogue.Place.Gone.IntoLabel("Zqland")), com.histmersion.api.catalogue.Place.Gone.FindingsBefore(3), com.histmersion.api.catalogue.Place.Gone.FindingsAfter(2), com.histmersion.api.catalogue.Place.Gone.Unlocated(1))
      ),
      "placeGone none" -> PlacesMarkdown.placeGone(
        com.histmersion.api.catalogue.Place.Gone(com.histmersion.api.catalogue.Place.Id("zq_tell"), com.histmersion.api.catalogue.Place.Gone.Label("Zq Tell"), None, None, com.histmersion.api.catalogue.Place.Gone.FindingsBefore(0), com.histmersion.api.catalogue.Place.Gone.FindingsAfter(0), com.histmersion.api.catalogue.Place.Gone.Unlocated(0))
      ),
      "placeAdded" -> PlacesMarkdown.placeAdded(summary),
      "placeRebuild" -> PlacesMarkdown.placeRebuild(
        com.histmersion.api.catalogue.Place.RebuildResult(com.histmersion.api.catalogue.Place.RebuildResult.Links(10), com.histmersion.api.catalogue.Place.RebuildResult.ByGeometry(4), com.histmersion.api.catalogue.Place.RebuildResult.ByName(5), com.histmersion.api.catalogue.Place.RebuildResult.Located(9), com.histmersion.api.catalogue.Place.RebuildResult.Unlocated(1), com.histmersion.api.catalogue.Place.RebuildResult.Places(7))
      ),
      "attestation" -> AttestationsMarkdown.attestation(
        Attestation(
          com.histmersion.api.catalogue.Attestation.Id(7),
          com.histmersion.api.library.Citekey("zq_alpha_2001"),
          com.histmersion.api.catalogue.Attestation.TargetType("event"),
          com.histmersion.api.catalogue.Attestation.TargetId("zq-event"),
          quote = Some(com.histmersion.api.catalogue.Attestation.Quote("the zq quote")),
          prefix = Some(com.histmersion.api.catalogue.Attestation.Prefix("before")),
          suffix = Some(com.histmersion.api.catalogue.Attestation.Suffix("after")),
          charStart = Some(com.histmersion.api.catalogue.Attestation.CharStart(10)),
          charEnd = Some(com.histmersion.api.catalogue.Attestation.CharEnd(22)),
          page = Some("12"),
          note = Some("a zq note")
        )
      ),
      "agentStateList" -> AgentStateMarkdown.agentStateList(
        List(AgentStateEntry(com.histmersion.api.automation.AgentStateEntry.Key("zq-key"), com.histmersion.api.automation.AgentStateEntry.Value("zq value"), Some(com.histmersion.api.automation.AgentStateEntry.UpdatedAt("2026-01-02T03:04:05Z"))), AgentStateEntry(com.histmersion.api.automation.AgentStateEntry.Key("zq-other"), com.histmersion.api.automation.AgentStateEntry.Value("x")))
      ),
      "agentStateList empty" -> AgentStateMarkdown.agentStateList(Nil),
      "agentState" -> AgentStateMarkdown.agentState(AgentStateEntry(com.histmersion.api.automation.AgentStateEntry.Key("zq-key"), com.histmersion.api.automation.AgentStateEntry.Value("zq value"))),
      "assetDetail" -> MediaMarkdown.assetDetail(
        com.histmersion.api.library.Citekey("zq_alpha_2001"),
        AssetDetail(
          com.histmersion.api.exchange.Bundle.Asset.FileName("zq.png"),
          com.histmersion.api.exchange.Bundle.Asset.MimeText("image/png"),
          com.histmersion.api.ByteCount(1024),
          "ab" * 32,
          width = Some(com.histmersion.api.media.ImageSize.Width(10)),
          height = Some(com.histmersion.api.media.ImageSize.Height(20)),
          kind = Some(com.histmersion.api.media.MediaItem.Kind("figure")),
          caption = Some(com.histmersion.api.media.MediaItem.Caption("Zq figure")),
          license = Some(com.histmersion.api.media.MediaItem.License("CC-BY-4.0")),
          lat = Some(40.25),
          lon = Some(30.5),
          periodStart = Some(com.histmersion.api.catalogue.Year(-1500)),
          periodEnd = Some(com.histmersion.api.catalogue.Year(-1400))
        )
      ),
      "mediaThumbLine" -> thumb.markdownLine,
      "mediaList" -> MediaMarkdown.mediaList(
        MediaList(com.histmersion.api.RowCount(2), List(item, item.modify(_.license).setTo(None).modify(_.targets).setTo(Nil)), next = (Some(2)).map(com.histmersion.api.Offset.apply))
      ),
      "mediaFacets" -> MediaMarkdown.mediaFacets(
        MediaFacets(List(MediaCount(com.histmersion.api.media.MediaCount.Key("zq-provider"), com.histmersion.api.media.MediaCount.N(3))), List(MediaCount(com.histmersion.api.media.MediaCount.Key("CC0-1.0"), com.histmersion.api.media.MediaCount.N(2)), MediaCount(com.histmersion.api.media.MediaCount.Key("zq"), com.histmersion.api.media.MediaCount.N(1), com.histmersion.api.media.MediaCount.Allowed(false))))
      ),
      "figureLicenceFill" -> MediaMarkdown.figureLicenceFill(
        FigureLicenceFill(com.histmersion.api.media.FigureLicenceFill.Candidates(5), com.histmersion.api.media.FigureLicenceFill.Updated(3), applied = com.histmersion.api.media.FigureLicenceFill.Applied(false), List(MediaCount(com.histmersion.api.media.MediaCount.Key("zq_alpha_2001"), com.histmersion.api.media.MediaCount.N(3))))
      ),
      "assetLink" -> MediaMarkdown.assetLink(
        AssetLink(com.histmersion.api.media.AssetLink.Id(3), com.histmersion.api.library.Citekey("zq_alpha_2001"), com.histmersion.api.exchange.Bundle.Asset.FileName("zq.png"), com.histmersion.api.media.AssetLink.Relation("depicts"), com.histmersion.api.media.MediaTarget.TargetType("place"), com.histmersion.api.media.MediaTarget.TargetId("zq_tell"), Some(com.histmersion.api.media.AssetLink.Xywh("0,0,10,10")), Some(com.histmersion.api.media.AssetLink.Notes("zq notes")))
      ),
      "assetLinkLine" -> MediaMarkdown.assetLinkLine(
        AssetLink(com.histmersion.api.media.AssetLink.Id(4), com.histmersion.api.library.Citekey("zq_alpha_2001"), com.histmersion.api.exchange.Bundle.Asset.FileName("zq.png"), com.histmersion.api.media.AssetLink.Relation("shows"), com.histmersion.api.media.MediaTarget.TargetType("event"), com.histmersion.api.media.MediaTarget.TargetId("zq-event"))
      ),
      "backupStatus" -> Markdown.backupStatus(
        Backup.Status(com.histmersion.api.backup.Backup.Status.File("zq.dump"), com.histmersion.api.backup.Backup.State.Wire("done"), com.histmersion.api.backup.Backup.Status.DurationMs(1500), Some(com.histmersion.api.ByteCount(4096)), Some("cd" * 32), None, List(com.histmersion.api.backup.Backup.Result.Pruned("old.dump")))
      ),
      "backupStatus failed" -> Markdown.backupStatus(Backup.Status(com.histmersion.api.backup.Backup.Status.File("zq.dump"), com.histmersion.api.backup.Backup.State.Wire("failed"), com.histmersion.api.backup.Backup.Status.DurationMs(10), error = Some("zq error"))),
      "event" -> event.markdown,
      "place" -> PlacesMarkdown.place(place),
      "event json" -> event.asJson.spaces2,
      "place json" -> place.asJson.spaces2,
      "resolved source json" -> ResolvedId(
        com.histmersion.api.views.ResolvedId.Id("zq_alpha_2001"),
        source = Some(
          ResolvedSource(com.histmersion.api.library.Citekey("zq_alpha_2001"), Some(com.histmersion.api.library.Doi("10.0/zq")), List(com.histmersion.api.library.AuthorName("Zq A")), Some(com.histmersion.api.catalogue.Year(2001)), com.histmersion.api.library.ResolvedSource.Title("Zq Alpha"), Source.Status.Current)
        )
      ).asJson.spaces2,
      "resolved article json" -> ResolvedId(
        com.histmersion.api.views.ResolvedId.Id("zq-article"),
        article = Some(ResolvedArticle(com.histmersion.api.research.Article.Slug("zq-article"), com.histmersion.api.research.Article.Title("Zq article")))
      ).asJson.spaces2,
      "resolved none json" -> ResolvedId(com.histmersion.api.views.ResolvedId.Id("zq-nothing")).asJson.spaces2,
      "job detail json" -> InitPlan
        .Detail(Some(InitPlan(gazetteer = com.histmersion.api.exchange.InitPlan.Gazetteer(true), fetch = List("zq-gaz"))))
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
