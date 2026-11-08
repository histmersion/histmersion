package com.histmersion
package api

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
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Search
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.library.Edge
import com.histmersion.api.media.ImageSize
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.catalogue.Event
import com.histmersion.api.catalogue.EventGeometry
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.library.Doi
import com.histmersion.api.library.AuthorName
import com.histmersion.api.research.Article
import com.histmersion.api.catalogue.Period
import com.histmersion.api.exchange.ExchangeJobKinds
import com.histmersion.api.exchange.MergePlan
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.catalogue.EventsJobKinds

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
    Citekey("zq_alpha_2001"),
    Title("Zq Alpha"),
    (Some(2001)).map(Year.apply),
    Source.Status.Contested,
    List(Source.Topics("zq-topic")),
    List(Source.Regions("zq-region")),
    Some(Year(-1450)),
    Some(Year(-1350)),
    Some(Source.Summary("A summary  with   spaces.")),
    snippet = Some(Search.Snippet("the **zq** hit")),
    matchedHeading = Some(SearchHit.MatchedHeading("Zq heading"))
  )
  private val result = SearchResult(
    RowCount(3),
    List(
      Hit(hit, Nil, List(Hit.Why("tag zq"))),
      Hit(hit.modify(_.citekey).setTo(Citekey("zq_beta_1999")).modify(_.year).setTo(None), Nil, Nil)
    ),
    offset = Offset(0),
    next = (Some(2)).map(Offset.apply)
  )
  private val finding = Finding.Row(
    Finding.Claim("Zq claim one."),
    Some(Place.Region("Zqland")),
    Some(Longitude(30.5)),
    Some(Latitude(40.25)),
    Some(Year(-1300)),
    Some(Year(-1200)),
    Some(Finding.PeriodName("Zq period")),
    List(Finding.Tags("zq-tag")),
    Some(Confidence.High),
    significance = Some(Finding.Significance("key"))
  )
  private val graph = Source.Graph(
    Source.Graph.Root("zq_alpha_2001"),
    Source.Graph.Depth(2),
    List(
      GraphNode(Citekey("zq_alpha_2001"), Some(Title("Zq Alpha")), Some(Year(2001)), Some(Source.Status.Current)),
      GraphNode(Citekey("zq_gamma"))
    ),
    List(GraphLink(Citekey("zq_alpha_2001"), Citekey("zq_gamma"), Edge.Kind.Challenges))
  )
  private val contradictions = ContradictionsResult(
    List(
      ContradictionRow(
        Citekey("zq_alpha_2001"),
        Title("Zq Alpha"),
        Some(Year(2001)),
        Source.Status.Current,
        Some(ContradictionRow.Direction.Wire("out")),
        Some(Edge.Kind.Refutes),
        Some(Citekey("zq_gamma")),
        Some(Title("Zq Gamma")),
        Some(Year(1990)),
        Some(Source.Status.Debunked)
      ),
      ContradictionRow(Citekey("zq_beta_1999"), Title("Zq Beta"), None, Source.Status.Contested, None, None, None, None)
    ),
    ContradictionsResult.Total(2)
  )
  private val geo = GeoHit(
    Citekey("zq_alpha_2001"),
    GeoHit.Title("Zq Alpha"),
    GeoHit.Claim("Zq claim."),
    Some(Place.Region("Zqland")),
    Some(Longitude(30.5)),
    Some(Latitude(40.25)),
    Some(Year(-1300)),
    None,
    Some(Confidence.Low)
  )
  private val gazetteers = List(
    Gazetteer.SourceInfo(
      Gazetteer.SourceInfo.Key("zq-gaz"),
      Gazetteer.Kind.Lpf,
      Gazetteer.SourceInfo.Title("Zq gazetteer"),
      Some(Gazetteer.SourceInfo.File("zq.lpf.json")),
      present = Gazetteer.SourceInfo.Present(true),
      Some(Gazetteer.SourceInfo.PinnedVersion("1.0")),
      importedVersion = Some(Gazetteer.SourceInfo.ImportedVersion("0.9")),
      importedAt = Some(Gazetteer.SourceInfo.ImportedAt("2026-01-02T03:04:05Z")),
      places = Some(Gazetteer.SourceInfo.Places(12)),
      names = Some(Gazetteer.SourceInfo.Names(30)),
      links = Some(Gazetteer.SourceInfo.Links(4)),
      licence = Some(Gazetteer.SourceInfo.Licence("CC-BY-4.0")),
      citation = Some(Gazetteer.SourceInfo.Citation("Zq et al.")),
      periodStart = Some(Year(-3000)),
      periodEnd = Some(Year(-500))
    ),
    Gazetteer.SourceInfo(
      Gazetteer.SourceInfo.Key("zq-missing"),
      Gazetteer.Kind.Geonames,
      Gazetteer.SourceInfo.Title("Zq missing"),
      None,
      present = Gazetteer.SourceInfo.Present(false),
      None
    )
  )
  private val summary =
    Place.Summary(
      Place.Id("zq_tell"),
      Place.Summary.Label("Zq Tell"),
      Place.Summary.Kind("settlement"),
      Some(Year(-2000)),
      Some(Year(-1200)),
      Some(Place.Source("zq-gaz")),
      Place.Summary.Names("Zq Tell; Tell Zq")
    )
  private val thumb = MediaThumb(
    MediaItem.Uid("00000000-0000-4000-8000-000000000001"),
    MediaThumb.Role("depiction"),
    MediaThumb.Url("/api/media/zq/thumb"),
    MediaItem.Credit("Zq Museum"),
    license = Some(MediaItem.License("CC0-1.0")),
    licenseUrl = Some(MediaItem.LicenseUrl("https://example.org/cc0")),
    sourceUrl = Some(MediaItem.SourceUrl("https://example.org/zq")),
    width = Some(ImageSize.Width(640)),
    height = Some(ImageSize.Height(480))
  )
  private val item = MediaItem(
    MediaItem.Uid("00000000-0000-4000-8000-000000000002"),
    Bundle.Asset.FileName("zq.jpg"),
    Bundle.Asset.MimeText("image/jpeg"),
    ByteCount(2048),
    citekey = Some(Citekey("zq_alpha_2001")),
    provider = Some(MediaItem.Provider("zq-provider")),
    providerKey = Some(MediaItem.ProviderKey("zq-1")),
    width = Some(ImageSize.Width(800)),
    height = Some(ImageSize.Height(600)),
    kind = Some(MediaItem.Kind("photo")),
    caption = Some(MediaItem.Caption("A zq vase")),
    license = Some(MediaItem.License("CC-BY-4.0")),
    author = Some(MediaItem.Author("Zq Author")),
    credit = Some(MediaItem.Credit("Zq Museum")),
    sourceUrl = Some(MediaItem.SourceUrl("https://example.org/zq")),
    thumbUrl = Some(MediaItem.ThumbUrl("/api/media/zq/thumb")),
    fileUrl = MediaItem.FileUrl("/api/media/zq"),
    targets = List(
      MediaTarget(
        MediaTarget.TargetType("place"),
        MediaTarget.TargetId("zq_tell"),
        MediaTarget.Role("depicts"),
        property = Some(MediaTarget.Property("P180"))
      )
    )
  )
  private val event = EventDetail(
    EventSummary(
      Event.Id("zq-event"),
      EventSummary.Label("Zq eruption"),
      EventSummary.Kind("eruption"),
      TimeSpan(Some(Year(-1620)), Some(Year(-1610)), Some(Year(-1600)), Some(Year(-1590))),
      EventSummary.Historicity("attested"),
      placeId = Some(Place.Id("zq_tell")),
      description = Some(EventSummary.Description("A zq eruption.")),
      citekey = Some(Citekey("zq_alpha_2001"))
    ),
    geometry = Some(EventGeometry.Geojson("POINT(30.5 40.25)")),
    children = List(
      EventSummary(
        Event.Id("zq-child"),
        EventSummary.Label("Zq ash fall"),
        EventSummary.Kind("ashfall"),
        TimeSpan(notBefore = Some(Year(-1610))),
        EventSummary.Historicity("inferred")
      )
    ),
    datings = List(
      EventDating(
        Event.Id("zq-event"),
        Citekey("zq_alpha_2001"),
        TimeSpan(Some(Year(-1620)), None, None, Some(Year(-1590))),
        Some(EventDating.Method("c14"))
      )
    ),
    wikidata = Some(EventEntry.Wikidata("Q1")),
    media = List(thumb),
    names = List(EventEntry.Names("Zq eruption"), EventEntry.Names("Zq event"))
  )
  private val place = PlaceDetail(
    summary,
    Some(Place.Geometry("POINT(30.5 40.25)")),
    List(
      Place.Summary(
        Place.Id("zq_land"),
        Place.Summary.Label("Zqland"),
        Place.Summary.Kind("region"),
        None,
        None,
        None,
        Place.Summary.Names("Zqland")
      )
    ),
    Nil,
    findings = Place.Counts.Findings(3),
    asOf = Some(Year(-1500)),
    links = List(Place.Link(Place.Id("zq_tell"), Place.Link.Scheme("wikidata"), Place.Link.ExternalId("Q2"))),
    locationPrecision = Place.Precision.Stated,
    media = List(thumb),
    siteType = Some(Place.Entry.SiteType("tell"))
  )

  private def rendered: String =
    val parts: List[(String, String)] = List(
      "briefResults" -> SearchMarkdown.briefResults(result),
      "briefResults empty" -> SearchMarkdown.briefResults(SearchResult(RowCount(0), Nil)),
      "sourceFindingsOnly" -> SourcesMarkdown.sourceFindingsOnly(
        Vector(finding, finding.modify(_.claim).setTo(Finding.Claim("Zq claim two.")))
      ),
      "sourceFindingsOnly empty" -> SourcesMarkdown.sourceFindingsOnly(Vector.empty),
      "graph" -> SourcesMarkdown.graph(graph),
      "contradictions" -> SourcesMarkdown.contradictions(contradictions, Some(Citekey("zq_alpha_2001"))),
      "contradictions all" -> SourcesMarkdown.contradictions(contradictions, None),
      "geoPage" -> SearchMarkdown.geoPage(
        GeoHitPage(RowCount(2), List(geo, geo.modify(_.lat).setTo(None).modify(_.lon).setTo(None)), next = None)
      ),
      "geoResults empty" -> SearchMarkdown.geoResults(Vector.empty),
      "bodySlice" -> Markdown.bodySlice(Source.Body("Zq body text."), UnreadPage.Offset(10), 40),
      "gazetteerSources" -> PlacesMarkdown.gazetteerSources(gazetteers),
      "placeGone" -> PlacesMarkdown.placeGone(
        Place.Gone(
          Place.Id("zq_tell"),
          Place.Gone.Label("Zq Tell"),
          Some(Place.Gone.Into("zq_land")),
          Some(Place.Gone.IntoLabel("Zqland")),
          Place.Gone.FindingsBefore(3),
          Place.Gone.FindingsAfter(2),
          Place.Gone.Unlocated(1)
        )
      ),
      "placeGone none" -> PlacesMarkdown.placeGone(
        Place.Gone(
          Place.Id("zq_tell"),
          Place.Gone.Label("Zq Tell"),
          None,
          None,
          Place.Gone.FindingsBefore(0),
          Place.Gone.FindingsAfter(0),
          Place.Gone.Unlocated(0)
        )
      ),
      "placeAdded" -> PlacesMarkdown.placeAdded(summary),
      "placeRebuild" -> PlacesMarkdown.placeRebuild(
        Place.RebuildResult(
          Place.RebuildResult.Links(10),
          Place.RebuildResult.ByGeometry(4),
          Place.RebuildResult.ByName(5),
          Place.RebuildResult.Located(9),
          Place.RebuildResult.Unlocated(1),
          Place.RebuildResult.Places(7)
        )
      ),
      "attestation" -> AttestationsMarkdown.attestation(
        Attestation(
          Attestation.Id(7),
          Citekey("zq_alpha_2001"),
          Attestation.TargetType("event"),
          Attestation.TargetId("zq-event"),
          quote = Some(Attestation.Quote("the zq quote")),
          prefix = Some(Attestation.Prefix("before")),
          suffix = Some(Attestation.Suffix("after")),
          charStart = Some(Attestation.CharStart(10)),
          charEnd = Some(Attestation.CharEnd(22)),
          page = Some(Attestation.Page("12")),
          note = Some(Attestation.Note("a zq note"))
        )
      ),
      "agentStateList" -> AgentStateMarkdown.agentStateList(
        List(
          AgentStateEntry(
            AgentStateEntry.Key("zq-key"),
            AgentStateEntry.Value("zq value"),
            Some(AgentStateEntry.UpdatedAt("2026-01-02T03:04:05Z"))
          ),
          AgentStateEntry(AgentStateEntry.Key("zq-other"), AgentStateEntry.Value("x"))
        )
      ),
      "agentStateList empty" -> AgentStateMarkdown.agentStateList(Nil),
      "agentState" -> AgentStateMarkdown.agentState(
        AgentStateEntry(AgentStateEntry.Key("zq-key"), AgentStateEntry.Value("zq value"))
      ),
      "assetDetail" -> MediaMarkdown.assetDetail(
        Citekey("zq_alpha_2001"),
        AssetDetail(
          Bundle.Asset.FileName("zq.png"),
          Bundle.Asset.MimeText("image/png"),
          ByteCount(1024),
          Sha256("ab" * 32),
          width = Some(ImageSize.Width(10)),
          height = Some(ImageSize.Height(20)),
          kind = Some(MediaItem.Kind("figure")),
          caption = Some(MediaItem.Caption("Zq figure")),
          license = Some(MediaItem.License("CC-BY-4.0")),
          lat = Some(Latitude(40.25)),
          lon = Some(Longitude(30.5)),
          periodStart = Some(Year(-1500)),
          periodEnd = Some(Year(-1400))
        )
      ),
      "mediaThumbLine" -> thumb.markdownLine.value,
      "mediaList" -> MediaMarkdown.mediaList(
        MediaList(
          RowCount(2),
          List(item, item.modify(_.license).setTo(None).modify(_.targets).setTo(Nil)),
          next = (Some(2)).map(Offset.apply)
        )
      ),
      "mediaFacets" -> MediaMarkdown.mediaFacets(
        MediaFacets(
          List(MediaCount(MediaCount.Key("zq-provider"), MediaCount.N(3))),
          List(
            MediaCount(MediaCount.Key("CC0-1.0"), MediaCount.N(2)),
            MediaCount(MediaCount.Key("zq"), MediaCount.N(1), MediaCount.Allowed(false))
          )
        )
      ),
      "figureLicenceFill" -> MediaMarkdown.figureLicenceFill(
        FigureLicenceFill(
          FigureLicenceFill.Candidates(5),
          FigureLicenceFill.Updated(3),
          applied = FigureLicenceFill.Applied(false),
          List(MediaCount(MediaCount.Key("zq_alpha_2001"), MediaCount.N(3)))
        )
      ),
      "assetLink" -> MediaMarkdown.assetLink(
        AssetLink(
          AssetLink.Id(3),
          Citekey("zq_alpha_2001"),
          Bundle.Asset.FileName("zq.png"),
          AssetLink.Relation("depicts"),
          MediaTarget.TargetType("place"),
          MediaTarget.TargetId("zq_tell"),
          Some(AssetLink.Xywh("0,0,10,10")),
          Some(AssetLink.Notes("zq notes"))
        )
      ),
      "assetLinkLine" -> MediaMarkdown.assetLinkLine(
        AssetLink(
          AssetLink.Id(4),
          Citekey("zq_alpha_2001"),
          Bundle.Asset.FileName("zq.png"),
          AssetLink.Relation("shows"),
          MediaTarget.TargetType("event"),
          MediaTarget.TargetId("zq-event")
        )
      ),
      "backupStatus" -> Markdown.backupStatus(
        Backup.Status(
          Backup.Status.File("zq.dump"),
          Backup.State.Wire("done"),
          Backup.Status.DurationMs(1500),
          Some(ByteCount(4096)),
          Some(Sha256("cd" * 32)),
          None,
          List(Backup.Result.Pruned("old.dump"))
        )
      ),
      "backupStatus failed" -> Markdown.backupStatus(
        Backup.Status(
          Backup.Status.File("zq.dump"),
          Backup.State.Wire("failed"),
          Backup.Status.DurationMs(10),
          error = Some(Backup.Status.Error("zq error"))
        )
      ),
      "event" -> event.markdown.value,
      "place" -> PlacesMarkdown.place(place),
      "event json" -> event.asJson.spaces2,
      "place json" -> place.asJson.spaces2,
      "resolved source json" -> ResolvedId(
        ResolvedId.Id("zq_alpha_2001"),
        source = Some(
          ResolvedSource(
            Citekey("zq_alpha_2001"),
            Some(Doi("10.0/zq")),
            List(AuthorName("Zq A")),
            Some(Year(2001)),
            ResolvedSource.Title("Zq Alpha"),
            Source.Status.Current
          )
        )
      ).asJson.spaces2,
      "resolved article json" -> ResolvedId(
        ResolvedId.Id("zq-article"),
        article = Some(ResolvedArticle(Article.Slug("zq-article"), Article.Title("Zq article")))
      ).asJson.spaces2,
      "resolved none json" -> ResolvedId(ResolvedId.Id("zq-nothing")).asJson.spaces2,
      "job detail json" -> InitPlan
        .Detail(Some(InitPlan(gazetteer = InitPlan.Gazetteer(true), fetch = List(InitPlan.Fetch("zq-gaz")))))
        .set(SkippedPeriod.SkippedCount, Period.ImportResult.SkippedCount(2))
        .set(ExchangeJobKinds.PlanHash, Some(MergePlan.PlanHash("zq-hash")))
        .set(
          VacuumPlan.Detail,
          Some(VacuumPlan(analyze = VacuumPlan.Analyze(true), tables = List(VacuumPlan.Tables("sources"))))
        )
        .set(ImportJobKinds.Media, Some(JobKinds.Subject("a media run")))
        .set(EventsJobKinds.Catalogue, Some(JobKinds.Subject("zq-catalogue")))
        .set(JobDetail.Purpose, Some(JobKinds.Subject("zq_alpha_2001")))
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
