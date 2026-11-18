package com.histmersion
package server
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.{Problem, Problems}
import io.github.iltotore.iron.constraint.any.Pure

import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.files.*
import sttp.shared.Identity
import sttp.model.{Header, MediaType, StatusCode}
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.{Api, OpsApi, WebPaths}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.server.ArticleHtml
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.catalogue.TilesApi
import com.histmersion.api.instance.{UiApi, UiConfig}
import com.histmersion.util.access.Admission
import com.histmersion.api.settings.ConfigEditApi
import com.histmersion.api.views.AutopilotApi
import com.histmersion.domain.library.acquisition.AcquisitionControllers
import com.histmersion.domain.library.collections.CollectionsControllers
import com.histmersion.api.basemap.Basemap
import com.histmersion.util.basemap.BasemapService
import com.histmersion.domain.library.LibraryRoutes
import com.histmersion.api.views.ResolvedId
import com.histmersion.domain.library.LibraryData
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.api.library.Source
import com.histmersion.server.instance.MaintenanceControllers
import com.histmersion.api.aliases.InstanceInfo
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.ImportOptions
import com.histmersion.util.data.exchange.MergeResolutionsFile
import com.histmersion.util.data.exchange.MergeRepo
import com.histmersion.domain.catalogue.places.PlacesRoutes
import com.histmersion.domain.DeepTime
import com.histmersion.server.views.PlaceCardView
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.domain.catalogue.places.GazetteerLoad
import com.histmersion.domain.catalogue.periods.PeriodsRoutes
import com.histmersion.domain.catalogue.events.EventsRoutes
import com.histmersion.domain.catalogue.evidence.AttestationsRoutes
import com.histmersion.domain.automation.agents.AgentStateRecord
import com.histmersion.domain.library.ingest.Upload
import com.histmersion.util.storage.HandedFile
import com.histmersion.api.library.Ingest
import com.histmersion.util.jobs.JobAction
import com.histmersion.util.settings.SettingsService
import com.histmersion.domain.personal.PersonalRoutes
import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.ListFilters
import com.histmersion.domain.activity.ActivityQuery
import com.histmersion.domain.activity.ActivityCursor
import com.histmersion.api.Paging
import com.histmersion.domain.media.MediaRoutes
import com.histmersion.domain.Title
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.articles.PageBuilder
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.acquisition.AcquisitionRoutes
import com.histmersion.domain.library.collections.CollectionsRoutes
import com.histmersion.domain.catalogue.events.EventImportsRoutes
import com.histmersion.domain.catalogue.evidence.EventLinksRoutes
import com.histmersion.domain.catalogue.places.SiteTypesRoutes
import com.histmersion.domain.research.articles.ArticleRoutes
import com.histmersion.domain.library.search.QueryRoutes
import com.histmersion.domain.automation.agents.AgentsRoutes
import com.histmersion.domain.automation.managers.ManagersRoutes
import com.histmersion.domain.library.workflows.AcquisitionWorkRoutes
import com.histmersion.server.agents.EngineContributions
import com.histmersion.domain.media.assets.IiifRoutes
import com.histmersion.api.Write
import com.histmersion.util.jobs.Job
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.research.Slug

/** REST (the `Api` contract) + HTML article pages + the static web UI, all interpreted on the same netty-sync server as
  * MCP.
  */
final class HttpApi(
    deps: Deps,
    h: ServerWiring,
    acquire: AcquisitionControllers,
    collections: CollectionsControllers
):
  import HttpApi.Endpoint

  private val rest: List[ServerEndpoint[Any, Identity]] = List(
    Api.version.serverLogicSuccess[Identity](_ => h.instance.version),
    Api.health.serverLogicSuccess[Identity](_ => h.instance.health),
    Api.about.serverLogicSuccess[Identity](_ => h.instance.about()),
    // the page must send the key unless the access gate let this request in as a trusted network's
    Api.config
      .in(extractFromRequest(_.header(Admission.Header)))
      .serverLogicSuccess[Identity](admitted =>
        h.instance.uiConfig(UiConfig.KeyRequired(!admitted.contains(Admission.Trusted.wire)))
      ),
    // the toolbar's counts and "something new" marks: REST only, like `config` (UiApi says why)
    UiApi.counters.serverLogicSuccess[Identity](_ => h.instance.uiCounters),
    UiApi.markSeen.serverLogic[Identity]((board, mark) => h.instance.markSeen(board, mark)),
    // the terrain base map: REST and the CLI only (BasemapApi says why); tiles before nothing else —
    // `basemap/fetch` is a POST, so the GET `basemap/{layer}/…` never shadows it
    BasemapApi.status.serverLogicSuccess[Identity](_ => h.basemap.status),
    BasemapApi.fetch.serverLogic[Identity](q => h.basemap.fetch(q)),
    BasemapApi.remove.serverLogic[Identity](q => h.basemap.remove(q)),
    BasemapApi.tile.serverLogic[Identity]((l, z, x, y, sub, keys) =>
      h.basemap.tile(
        l,
        z,
        x,
        y,
        sub.map(v => Basemap.Subset.Spec(v.value)),
        keys.map(k => BasemapService.TileKeys(k.value))
      )
    ),
    TilesApi.tile.serverLogic[Identity](q => h.tiles.tile(q).left.map(Api.Error(_)))
  ) ++ LibraryRoutes(
    h.library.search,
    h.library.labels,
    h.library.sources,
    h.library.findings,
    h.library.citations,
    Api.sourceArticles.serverLogic[Identity](ck => HttpApi.citekey(ck).flatMap(h.research.articles.sourceArticles)),
    OpsApi.lintMarkdown.serverLogicSuccess[Identity](in => h.formats.check(in)),
    Api.resolveId.serverLogic[Identity](id => h.resolveView(ResolvedId.Id(id.value))),
    Api.seed.serverLogic[Identity](req => h.seeding.seed(req)),
    // ── writes (plan §1.9a): the `export` route must precede `getSource`, which is a prefix of it ──
    Api.exportSource.serverLogic[Identity](ck =>
      h.mirror.note(LibraryData.SourceNotes, MirrorPlacement.RecordId(ck.value)).map(n => Source.Export(n.value))
    ),
    Api.reindex.serverLogic[Identity]((after, limit) =>
      h.upkeep.reindex(
        after.map(a => MaintenanceControllers.Cursor(a.value)),
        limit.flatMap(l => PageSize.option(l.value))
      )
    )
  ) ++ List[ServerEndpoint[Any, Identity]](
    // ── backup / restore (plan §1.10); the listing route precedes the per-file download ──
    Api.configReload.serverLogic[Identity](_ => Right(h.instance.configReload())),
    Api.backup.serverLogic[Identity](_ => h.upkeep.backup()),
    Api.maintenanceVacuum.serverLogic[Identity](in => h.upkeep.vacuum(in)),
    Api.backupStatus.serverLogic[Identity](_ => h.upkeep.backupStatus),
    Api.backups.serverLogic[Identity](_ => h.upkeep.backupList),
    Api.downloadBackup.serverLogic[Identity](file =>
      h.upkeep.backupFile(MaintenanceControllers.BackupFileName(file.value))
    ),
    Api.restore.serverLogic[Identity](req => h.upkeep.restore(req)),
    // ── portable bundles (M0–M2) ──────────────────────────────────────────────────────────────
    Api.instance.serverLogicSuccess[Identity](_ => h.bundles.instance),
    Api.setInstance.serverLogic[Identity](in => h.bundles.setInstance(in.label.map(l => InstanceInfo.Label(l.value)))),
    Api.exportBundle.serverLogic[Identity](since =>
      h.bundles.exportBundle(since.map(s => BundleScope.Since.At(s.value))).map((f, d) => (f, Api.FileName(d.value)))
    ),
    Api.verifyBundle.serverLogic[Identity](f => h.bundles.verify(f).left.map(Api.Error(_))),
    Api.importBundle.serverLogic[Identity]((dryRun, f) =>
      h.bundles.importBundle(ImportOptions.DryRun(dryRun.value), f)
    ),
    // ── the merge plan (M3) ──
    Api.uploadBundle.serverLogic[Identity](f => h.bundles.upload(f)),
    Api.mergePlan.serverLogic[Identity](req => h.bundles.plan(req)),
    Api.mergeImport.serverLogic[Identity](req => h.bundles.importUploaded(req)),
    Api.mergeApply.serverLogic[Identity](req => h.bundles.applyUploaded(req)),
    Api.mergeApplyStatus.serverLogic[Identity](job => HttpApi.jobId(job.value).flatMap(h.bundles.applyStatus)),
    Api.mergeSession.serverLogic[Identity](b => h.bundles.session(b).left.map(Api.Error(_))),
    Api.putMergeSession.serverLogic[Identity]((b, r) => h.bundles.putSession(b, r)),
    Api.parseResolutions.serverLogic[Identity](text =>
      h.bundles.parseResolutions(MergeResolutionsFile.Text(text.value))
    ),
    // ── merge list | show | ledger (M7): `merges/ledger` before `merges/{id}` ──
    Api.merges.serverLogicSuccess[Identity](limit => h.bundles.merges(limit)),
    Api.mergeLedger.serverLogic[Identity]((uid, merge, limit) =>
      h.bundles.mergeLedger(
        uid.map(u => MergeRepo.UidText(u.value)),
        merge.map(m => MergeRepo.MergeIdOrPrefix(m.value)),
        limit
      )
    ),
    Api.mergeDetail.serverLogic[Identity](id => h.bundles.mergeDetail(MergeRepo.MergeIdOrPrefix(id.value))),
    // ── label renames (V042, V053) ──
    Api.renameArticle.serverLogic[Identity]((slug, req) =>
      HttpApi.articleSlug(slug.value).flatMap(s => h.research.articles.rename(s, req))
    ),
    Api.renameEvent.serverLogic[Identity]((id, req) =>
      HttpApi.eventId(id.value).flatMap(e => h.events.renamed(e, req))
    ),
    Api.renameGoal.serverLogic[Identity]((slug, req) =>
      HttpApi.goalSlug(slug.value).flatMap(g => h.goals.rename(g, req))
    ),
    Api.renamePlace.serverLogic[Identity]((id, req) => HttpApi.placeId(id.value).flatMap(p => h.places.renamed(p, req)))
  ) ++ PlacesRoutes(
    h.places,
    h.gazetteer,
    Api.place.serverLogic[Identity]((id, asOf, counts) =>
      HttpApi
        .placeId(id.value)
        .flatMap(p =>
          h.placeCard(
            p,
            asOf.map(a => DeepTime.SignedYear(a.value)),
            PlaceCardView.WithCounts(counts.map(_.value).getOrElse(false))
          )
        )
    ),
    () => h.bootstrap.gazetteerSources,
    (source, apply) =>
      h.bootstrap.loadGazetteer(
        Gazetteer.Kind.Wire(source.value),
        GazetteerLoad.LoadApply(apply.value)
      )
  ) ++ PeriodsRoutes(h.periods) ++ List[ServerEndpoint[Any, Identity]](
    Api.init.serverLogic[Identity](fetch => h.bootstrap.init(fetch.value))
    // ── events (plan §1.2) and attestations (plan §1.3) ──────────────────────────────────────
  ) ++ EventsRoutes(
    h.events,
    List(
      Api.getEvent.serverLogic[Identity](id => HttpApi.eventId(id.value).flatMap(h.eventCard.get)),
      Api.putEvent.serverLogic[Identity]((id, in) =>
        HttpApi.eventId(id.value).flatMap(e => h.eventCard.put(Caller.Local, e, in))
      ),
      Api.patchEvent.serverLogic[Identity]((id, p) =>
        HttpApi.eventId(id.value).flatMap(e => h.eventCard.patch(Caller.Local, e, p))
      )
    )
  ) ++ AttestationsRoutes(h.attestations) ++ List[ServerEndpoint[Any, Identity]](
    // ── goals (plan §1.5) ─────────────────────────────────────────────────────────────────────
    Api.goals.serverLogicSuccess[Identity](q => h.goals.list(q)),
    Api.getGoal.serverLogic[Identity](slug => HttpApi.goalSlug(slug.value).flatMap(h.goals.get)),
    Api.putGoal.serverLogic[Identity]((slug, in) => HttpApi.goalSlug(slug.value).flatMap(g => h.goals.put(g, in))),
    Api.patchGoal.serverLogic[Identity]((slug, p) => HttpApi.goalSlug(slug.value).flatMap(g => h.goals.patch(g, p))),
    Api.deleteGoal.serverLogic[Identity](slug => HttpApi.goalSlug(slug.value).flatMap(h.goals.delete)),
    // ── agent state (plan §1.5) ───────────────────────────────────────────────────────────────
    Api.agentStateList.serverLogicSuccess[Identity](_ => h.agentState.list()),
    Api.agentStateGet.serverLogic[Identity](key => h.agentState.get(AgentStateRecord.Key(key.value))),
    Api.agentStateSet.serverLogic[Identity]((key, in) => h.agentState.set(AgentStateRecord.Key(key.value), in)),
    Api.agentStateDelete.serverLogic[Identity](key => h.agentState.delete(AgentStateRecord.Key(key.value))),
    // ── ingest, jobs and assets (plan §1.8): `sources/{ck}/assets` precedes `sources/{ck}` ─────
    Api.ingest.serverLogic[Identity]((p, file) =>
      h.ingest.controllers.ingest(p, Upload.handed(HandedFile.snapshot(file)))
    ),
    Api.reocr.serverLogic[Identity]((ck, quality) =>
      HttpApi.citekey(ck).flatMap(c => h.ingest.controllers.reocr(c, Ingest.Params.Quality(quality.value)))
    ),
    Api.original.serverLogic[Identity](ck =>
      // the stored original is served from where the store keeps it (the HTTP layer streams a file)
      HttpApi
        .citekey(ck)
        .flatMap(h.ingest.controllers.original)
        .map((blob, disposition) => (java.io.File(blob.where.value), Api.FileName(disposition.value)))
    ),
    Api.job.serverLogic[Identity](id => HttpApi.jobId(id.value).flatMap(h.jobs.job)),
    Api.jobControl.serverLogic[Identity]((id, action) =>
      HttpApi.jobId(id.value).flatMap(j => h.jobControl.control(j, JobAction.Wire(action.value)))
    ),
    ConfigEditApi.get.serverLogic[Identity](prefix =>
      h.settings.page(prefix.map(p => SettingsService.ConfigPath(p.value)))
    ),
    ConfigEditApi.write.serverLogic[Identity](w => h.settings.write(w)),
    ConfigEditApi.restart.serverLogic[Identity](_ => h.settings.restart())
  ) ++ PersonalRoutes(h.personal) ++ List[ServerEndpoint[Any, Identity]](
    Api.activity.serverLogic[Identity]((show, since, goal, limit, before) =>
      h.activity
        .page(
          show.map(s => Activity.Show.Wire(s.value)),
          since.map(s => ListFilters.TimeBound(s.value)),
          goal.map(g => ActivityQuery.Goal(g.value)),
          limit.map(l => ActivityQuery.Limit(l.value)),
          before.map(b => ActivityCursor.Wire(b.value))
        )
        .left
        .map(Api.Error(_))
    ),
    Api.jobs.serverLogic[Identity]((f, limit, before, upTo) =>
      h.jobList.page(
        f,
        java.time.Instant.now(deps.clock),
        limit.flatMap(l => PageSize.option(l.value)),
        before.map(b => Paging.Cursor(b.value)),
        upTo.map(u => Paging.Cursor(u.value))
      )
    )
  ) ++ MediaRoutes(
    h.media.assets,
    h.media.links,
    h.media.imports,
    h.media.covers,
    h.media.figures
  )

  // ── the HTML source page (typed page model → HtmlRender; the Markdown in core is for MCP/CLI) ──
  private val pages = h.research.pages

  /** `/source/{citekey}`. An unknown citekey answers **404** with the same readable body it always had (item 21): a
    * page that says "no such source" under a 200 lies to every cache, crawler and `curl -f` that asks. The status is an
    * OUTPUT value rather than an error variant so the body is still produced by the same expression.
    *
    * An OLD citekey (V027) answers **301** to the source's page under its current citekey, the query string kept — so
    * bookmarks and links elsewhere land on the canonical URL.
    */
  private val sourcePage: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in("source" / path[String]("citekey"))
      .in(query[Boolean]("full").default(false))
      .in(query[Boolean]("fragment").default(false)) // fragment = inner HTML only (for the modal)
      .in(query[Option[String]]("q")) // highlight these terms in the article
      .out(statusCode)
      .out(header[Option[String]](sttp.model.HeaderNames.Location))
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogicSuccess[Identity] { case (requested, full, fragment, q) =>
        def missing =
          // ESCAPED: `requested` is whatever was in the URL, and this page is served as text/html
          val body = s"<p>No source <code>${ArticleHtml.esc(HtmlRender.Text(requested)).value}</code>.</p>"
          (
            sttp.model.StatusCode.NotFound,
            None,
            if fragment then body
            else ArticleHtml.page(Title("Not found"), HtmlRender.Html(body), PageKind.Source).value
          )
        Citekey.option(requested) match
          case None     => missing
          case Some(ck) =>
            val now = h.library.sources.canonical(ck)
            if now != ck then
              val to = ArticleHtml.sourceUrl(now, full, fragment, q.map(HtmlRender.Query(_)))
              (
                sttp.model.StatusCode.MovedPermanently,
                Some(to.value),
                s"""<p>Moved to <a href="${ArticleHtml
                    .esc(HtmlRender.Text(to.value))
                    .value}">${ArticleHtml.esc(HtmlRender.Text(now.value)).value}</a>.</p>"""
              )
            else
              pages.source(
                ck,
                PageBuilder.SourceFull(full),
                q.map(Search.Query(_))
              ) match
                case Some((title, inner)) =>
                  (
                    sttp.model.StatusCode.Ok,
                    None,
                    if fragment then inner.value else ArticleHtml.page(title, inner, PageKind.Source).value
                  )
                case None => missing
      }

  // ── static web UI from resources: / → web/index.html, /web/* → web/* ───────────────────────

  /** An `/api/...` path that matched nothing above is a 404 with a JSON body, not the web UI. Registered after every
    * real route and before [[index]], which would otherwise answer it: a client parsing JSON must not be handed
    * `<!doctype html>` with a 200.
    */
  private val apiNotFound: ServerEndpoint[Any, Identity] =
    endpoint
      .in("api" / paths)
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[Api.Error])
      .serverLogic[Identity](p =>
        Left(Api.Error(Problem.NotFound.Entity(Problem.TypeName.of(Endpoint), Problem.Raw(s"/api/${p.mkString("/")}"))))
      )

  /** The web UI, at its own pages' addresses only ([[WebPaths]]). It used to have no path input at all, so every
    * unmatched GET — a typo, a probe, a missing asset — came back 200 with the whole HTML page.
    */
  private val index: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in(paths)
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(stringBody)
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogic[Identity] { segments =>
        val path = segments.filter(_.nonEmpty)
        if WebPaths.isApp(path.map(WebPaths.Segment(_))) then
          Right(Static.read(Static.ReadPath("web/index.html")).value)
        else Left(s"not found: /${segments.mkString("/")}")
      }

  private val static: ServerEndpoint[Any, Identity] =
    staticResourcesGetServerEndpoint[Identity]("web")(classOf[HttpApi].getClassLoader, "web")

  // Articles (2.7), ops (2.8) and acquisition (2.6) live in their own files; their routes are appended here.
  private val acquireRoutes =
    AcquisitionRoutes(acquire, collections, h.calibreGaps).endpoints ++
      CollectionsRoutes(collections)

  val endpoints: List[ServerEndpoint[Any, Identity]] =
    EventImportsRoutes(h.eventImports) ++
      EventLinksRoutes(h.eventLinks) ++
      SiteTypesRoutes(h.siteTypes) ++ rest ++
      ArticleRoutes(h.research.articles, h.research.pages) ++ OpsRoutes(
        h
      ) ++ QueryRoutes(
        h.library.query
      ) ++ AgentsRoutes(h.agents) ++ ManagersRoutes(
        h.agents.managing,
        List(
          AutopilotApi.autopilot.serverLogicSuccess[Identity](_ => h.autopilot.autopilot),
          AutopilotApi.setAutopilot.serverLogic[Identity](in => h.autopilot.setAutopilot(in).left.map(Api.Error(_))),
          AutopilotApi.setMode.serverLogic[Identity](in => h.autopilot.setMode(in))
        ),
        AcquisitionWorkRoutes(
          EngineContributions.work(h.agents)
        ),
        List(AutopilotApi.drain.serverLogic[Identity](in => h.autopilot.drain(in).left.map(Api.Error(_))))
      ) ++ acquireRoutes ++
      IiifRoutes(h.media.iiif) ++
      List(sourcePage, static, apiNotFound, index)

object HttpApi:
  /** The path of an endpoint under `/api`, as a request named it. */
  type Endpoint = Endpoint.T
  object Endpoint extends Newtype[String, Pure]

  /** A request value the domain refuses is the caller's mistake: a 400 that says which value and why. */
  private def refused[A](parsed: Either[Problems, A]): Either[Api.Error, A] =
    parsed.left.map(problems => Api.Error(problems, Some(Api.Error.Code(Write.CodeInvalid))))

  private def citekey(ck: com.histmersion.api.library.Citekey): Either[Api.Error, Citekey] =
    refused(Citekey.parse(ck.value))

  private def jobId(id: String): Either[Api.Error, Job.Id] =
    Job.Id
      .either(id)
      .left
      .map(_ =>
        Api.Error(
          Problem.NotFound.Entity(Problem.TypeName.of(Job.Id), Problem.Raw(s"$id")),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )

  private def goalSlug(slug: String): Either[Api.Error, Goal.Slug] =
    refused(Goal.Slug.parse(slug))

  private def placeId(id: String): Either[Api.Error, Place.Id] =
    refused(Place.Id.parse(id))

  private def eventId(id: String): Either[Api.Error, Event.Id] =
    refused(Event.Id.parse(id))

  private def articleSlug(slug: String): Either[Api.Error, Slug] =
    refused(Slug.parse(slug))
