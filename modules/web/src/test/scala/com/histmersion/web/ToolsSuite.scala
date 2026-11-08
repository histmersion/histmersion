package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.Source

import sttp.model.Method
import com.histmersion.api.automation.{Agents, AgentScheduler, ListFilters, Managers, Queue}
import com.histmersion.api.catalogue.{EventTier, SkippedPeriod}
import com.histmersion.api.exchange.{InitPlan, MergeApplyJob}
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.library.Ingest
import com.histmersion.api.maintenance.Vacuum
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Manager
import com.histmersion.api.automation.Run
import com.histmersion.api.automation.Queue.QueueItem
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Period
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Pipeline
import com.histmersion.api.automation.Goal
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year
import com.histmersion.api.UiLabels
import com.histmersion.api.Paging

/** The pure parts of the tool windows: window keys, the write gate, the jobs poll, the upload form, the bookkeeping
  * lists' text and order.
  */
class ToolsSuite extends munit.FunSuite:

  /** The citekey shape the server accepts (the model's `Citekey`), for the suggestions' tests. */
  private val CitekeyShape = "(?!.*\\.\\.)[a-z][a-z0-9_.-]{1,120}"

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash(UiState.UrlHash(""))

  // ── window keys ────────────────────────────────────────────────────────────────────────────────

  test("every window key round-trips through its hash spelling; unknown entries are dropped") {
    val keys = List(
      WindowKey.Source(WindowKey.Source.Citekey("a2020_b")),
      WindowKey.Article(WindowKey.Article.Slug("sea-peoples")),
      WindowKey.Graph(WindowKey.Graph.Citekey("a2020_b")),
      WindowKey.EditSource(WindowKey.EditSource.Citekey("x2020_v1.2")),
      WindowKey.Jobs,
      WindowKey.Upload,
      WindowKey.Acquire,
      WindowKey.Tasks,
      WindowKey.Log,
      WindowKey.ApiKey,
      WindowKey.About,
      WindowKey.Runs,
      WindowKey.Run(WindowKey.Run.Id("3f2a-77")),
      WindowKey.Models,
      WindowKey.Approvals,
      WindowKey.Goals,
      WindowKey.Goal(WindowKey.Goal.Slug("bronze-age"))
    )
    keys.foreach(k => assertEquals(WindowKey.parse(k.wire), Some(k)))
    assertEquals(WindowKey.parse(WindowKey.Wire("nope")), None)
    assertEquals(WindowKey.parse(WindowKey.Wire("source:")), None)
    import FieldCodec.given
    assertEquals(
      summon[FieldCodec[List[WindowKey]]].decode(List(FieldCodec.Token("jobs"), FieldCodec.Token("bogus"), FieldCodec.Token("jobs"), FieldCodec.Token("edit:a2020_b"))),
      Some(List(WindowKey.Jobs, WindowKey.EditSource(WindowKey.EditSource.Citekey("a2020_b"))))
    )
  }

  test("a window of one item can open in a new tab, as that item's page") {
    assertEquals(WindowKey.Source(WindowKey.Source.Citekey("a b")).pageHref, Some("/source/a%20b"))
    assertEquals(WindowKey.Article(WindowKey.Article.Slug("x")).pageHref, Some("/article/x"))
    assertEquals(WindowKey.Run(WindowKey.Run.Id("3f2a")).pageHref, Some("/run/3f2a"))
    assertEquals(WindowKey.Goal(WindowKey.Goal.Slug("bronze-age")).pageHref, Some("/research/goals/bronze-age"))
    assertEquals(WindowKey.Manager(WindowKey.Manager.Id("watch:x")).pageHref, Some("/manager/watch%3Ax"))
    assertEquals(WindowKey.Jobs.pageHref, None)
    assertEquals(WindowKey.EditSource(WindowKey.EditSource.Citekey("a")).pageHref, None)
  }

  // ── the write gate ─────────────────────────────────────────────────────────────────────────────

  test("writes are offered when no key is required, or when one is held") {
    assert(Auth.canWrite(required = false, None))
    assert(!Auth.canWrite(required = true, None))
    assert(!Auth.canWrite(required = true, Some("  ")))
    assert(Auth.canWrite(required = true, Some("k")))
    assertEquals(Auth.header(Some(" k ")), Some("Authorization" -> "Bearer k"))
    assertEquals(Auth.header(None), None)
  }

  test("only writes carry the key") {
    assert(!ApiClient.isWrite(Method.GET))
    assert(!ApiClient.isWrite(Method.HEAD))
    List(Method.POST, Method.PUT, Method.PATCH, Method.DELETE).foreach(m => assert(ApiClient.isWrite(m), m))
  }

  test("a failed response is classified by its status, with the server's message when there is one") {
    assert(ApiFailure.of(ApiFailure.Status(401), None).unauthorized)
    assert(ApiFailure.of(ApiFailure.Status(403), None).unauthorized)
    assert(ApiFailure(ApiFailure.Status(412), Api.Error(Api.Error.Message("moved on"))).conflict)
    assert(ApiFailure(ApiFailure.Status(409), Api.Error(Api.Error.Message("dup"))).duplicate)
    assertEquals(ApiFailure.of(ApiFailure.Status(400), Some(Api.Error(Api.Error.Message("bad year")))).getMessage, "bad year")
    assert(ApiFailure.of(ApiFailure.Status(0), None).getMessage.contains("could not be reached"))
  }

  test("the API key is kept in the browser's storage and never in a shared link") {
    UiState.set(
      UiState.apiKey := Some("s3cret"),
      UiState.jobStatus := Some(JobState.Failed),
      UiState.open := List(WindowKey.Jobs)
    )()
    val h = UiState.hashString
    assert(!h.value.contains("s3cret") && !h.value.contains("apiKey"), h)
    assert(h.value.contains("jobs=failed"), h)
    assertEquals(UiState.store.get("histmersion.ui.apiKey"), Some("""["s3cret"]"""))
    UiState.set(UiState.apiKey := None)()
    UiState.restoreLocal()
    assertEquals(UiState.apiKey.now(), None)
  }

  // ── jobs ───────────────────────────────────────────────────────────────────────────────────────

  private def job(id: String, s: JobState, kind: String = Ingest.KindIngest) =
    JobInfo(SpatialHierarchyResult.Job.Id(id), JobKinds.Wire(kind), s.wire, (Some(s"ck_$id")).map(Citekey.apply))

  test("polling backs off while nothing moves, restarts on a change, and stops when all jobs finished") {
    val running = List(job("a", JobState.Running), job("b", JobState.Done))
    assertEquals(JobsView.Poll.next(None, running, changed = Poll.DataChanged(false)), Some(JobsView.Poll.FirstMs))
    assertEquals(JobsView.Poll.next(Some(Poll.Revision(1000)), running, changed = Poll.DataChanged(false)), Some(1600))
    assertEquals(JobsView.Poll.next(Some(Poll.Revision(14000)), running, changed = Poll.DataChanged(false)), Some(JobsView.Poll.MaxMs))
    assertEquals(JobsView.Poll.next(Some(Poll.Revision(8000)), running, changed = Poll.DataChanged(true)), Some(JobsView.Poll.FirstMs))
    assertEquals(JobsView.Poll.next(Some(Poll.Revision(1000)), List(job("q", JobState.Queued)), changed = Poll.DataChanged(false)), Some(1600))
    assertEquals(JobsView.Poll.next(Some(Poll.Revision(1000)), List(job("b", JobState.Done), job("c", JobState.Failed)), Poll.DataChanged(true)), None)
    assertEquals(JobsView.Poll.next(None, Nil, changed = Poll.DataChanged(true)), None)
  }

  test("the state filter and its counts") {
    val js = List(job("a", JobState.Running), job("b", JobState.Failed), job("c", JobState.Failed))
    assertEquals(JobsView.visible(js, None), js)
    assertEquals(JobsView.visible(js, Some(JobState.Failed)).map(_.id), List("b", "c").map(SpatialHierarchyResult.Job.Id.apply))
    assertEquals(JobsView.counts(js), Map(JobState.Running -> 1, JobState.Failed -> 2))
  }

  test("a job links its source only once the source exists") {
    assert(!JobsView.subjectStored(job("a", JobState.Failed)))
    assert(!JobsView.subjectStored(job("a", JobState.Running)))
    assert(JobsView.subjectStored(job("a", JobState.Done)))
    assert(JobsView.subjectStored(job("a", JobState.Running, Ingest.KindReocr)))
    assertEquals(JobsView.kindLabel("some_kind"), "Some kind")
    assertEquals(JobsView.kindLabel("basemap-fetch"), "Download the background map")
    assertEquals(JobsView.kindLabel(Ingest.KindIngest), "Bring in a source")
    assertEquals(RunsView.originLabel(Agents.Origin.Wire("ingest")), "by an ingest (before the enrich manager)")
    assert(Approval.Kind.values.forall(k => ApprovalsView.kindMeaning(k.wire).nonEmpty))
    assert(ApprovalsView.kindMeaning(Approval.Kind.Wire("article-publish")).exists(_.contains("unblocks its review")))
    assert(Pipeline.values.forall(p => RunsView.pipelineMeaning(Agents.RunInfo.Pipeline(p.wire.value)).nonEmpty))
  }

  test("every value a filter button, chip or menu offers has a meaning on hover") {
    import com.histmersion.api.automation.{ItemStatus, ProviderKind}
    assert(JobState.values.forall(s => JobsView.stateMeaning(s).nonEmpty))
    val jobKinds = List(
      Ingest.KindIngest,
      Ingest.KindReocr,
      InitPlan.JobKind,
      MergeApplyJob.JobKind,
      Vacuum.JobKind,
      Agents.JobKind,
      "basemap-fetch",
      "places-fetch",
      "places-hierarchy",
      "events-import",
      "events-candidates",
      "events-rederive",
      "site-types",
      "media-import",
      "media-covers"
    )
    assertEquals(jobKinds.filter(JobsView.kindMeaning(_).isEmpty), Nil)
    assertEquals(JobsView.kindMeaning("something-new"), None)
    assert(Run.State.values.forall(s => RunsView.stateMeaning(s).value.nonEmpty))
    assert(Agents.Origin.values.forall(o => RunsView.originMeaning(o.wire).nonEmpty))
    assertEquals(RunsView.originLabel(Agents.Origin.Wire("scheduler")), "by a goal's schedule")
    val providers = List(
      ProviderKind.Anthropic,
      ProviderKind.OpenAI,
      ProviderKind.Local,
      ProviderKind.ClaudeCode,
      ProviderKind.Codex,
      ProviderKind.Opencode
    )
    assert(providers.forall(p => RunsView.providerMeaning(Agents.ProviderDoctor.Provider(p.wire.value)).nonEmpty))
    assertEquals(RunsView.providerMeaning(Agents.ProviderDoctor.Provider("some-plugin")), None)
    assert(ApprovalsView.States.forall(s => ApprovalsView.stateMeaning(s).nonEmpty))
    assert(Approval.State.values.forall(s => ApprovalsView.States.contains(s.wire)))
    assert(ListFilters.DecidedBy.values.forall(d => ApprovalsView.decidedByMeaning(d.wire).nonEmpty))
    assert(ItemStatus.values.forall(s => ManagersView.itemStatusMeaning(s).value.nonEmpty))
    assert(Manager.Kind.values.forall(k => ManagersView.managerMeaning(Managers.ManagerInfo.Id(k.wire.value)).nonEmpty))
    assert(ManagersView.managerMeaning(Managers.seedId(Goal.Ref("sea-peoples"))).exists(_.value.contains("sea-peoples")))
    assertEquals(ManagersView.managerMeaning(Managers.ManagerInfo.Id("nobody")), None)
    val jobTypes = List("goal-plan", "propose", "sweep", "currency", "enrich", "compile", "extend", "filter", "review").map(JobKinds.Wire(_))
    assertEquals(jobTypes.filter(ManagersView.jobTypeMeaning(_).isEmpty), Nil)
    val codes = List(
      "capacity",
      "pool",
      "manager-cap",
      "blocked",
      "busy",
      "budget",
      "drain",
      AgentScheduler.Code.Person,
      AgentScheduler.Code.Held,
      AgentScheduler.Code.NotDue,
      AgentScheduler.Code.Running,
      AgentScheduler.Code.Settled,
      AgentScheduler.Code.Paused
    ).map(AgentScheduler.Waiting.Code(_))
    assertEquals(codes.filter(ManagersView.waitMeaning(_).isEmpty), Nil)
    assert(EventTier.all.forall(t => LayerFilters.tierMeaning(t).nonEmpty))
    assert(LayerFilters.tiers.forall(_.hint.nonEmpty))
  }

  // #770: an init job showed as "Init" with no subject
  test("an init job says what it imports; one that skipped periods says which") {
    val plan = InitPlan(gazetteer = InitPlan.Gazetteer(true), periodPresets = InitPlan.PeriodPresets(true), fetch = List(InitPlan.Fetch("periodo")))
    val init = JobInfo(SpatialHierarchyResult.Job.Id("i"), JobKinds.Wire(InitPlan.JobKind), JobState.Running.wire, detail = Some(InitPlan.Detail(Some(plan))))
    assertEquals(JobsView.kindLabel(InitPlan.JobKind), "Import reference data")
    assertEquals(JobsView.what(init), Some("bundled gazetteer, bundled period presets, fetch periodo"))
    assertEquals(JobsView.what(job("a", JobState.Running)), None) // an OCR job's subject is its citekey
    assertEquals(JobsView.skippedLine(init), None)
    val skipped = init.modify(_.detail).setTo(init.detail.map(
        _.set(SkippedPeriod.SkippedCount, Period.ImportResult.SkippedCount(3))
          .set(
            SkippedPeriod.Skipped,
            List(SkippedPeriod(SkippedPeriod.Id("p0x1"), SkippedPeriod.Label("Old Kingdom"), Period.Skip.OutOfRange, SkippedPeriod.Detail("-2701 → 2150")))
          )
      )
    )
    assertEquals(JobsView.skippedLine(skipped), Some("skipped 3 period definitions: p0x1 and 2 more"))
  }

  test("timestamps read as how long ago") {
    assertEquals(JobsView.ago(0, 42000), "42 s ago")
    assertEquals(JobsView.ago(0, 5 * 60000), "5 min ago")
    assertEquals(JobsView.ago(0, 3 * 3600000.0), "3 h ago")
    assertEquals(JobsView.ago(0, 2 * 86400000.0), "2 d ago")
    assertEquals(JobsView.ago(1000, 0), "0 s ago")
  }

  // ── upload ─────────────────────────────────────────────────────────────────────────────────────

  test("a citekey is suggested from the file name, in the shape the server accepts") {
    assertEquals(UploadView.suggestCitekey(Api.FileName("Anthony 2007 - The Horse.pdf")), Some("anthony_2007_the_horse"))
    assertEquals(UploadView.suggestCitekey(Api.FileName("2019_Olalde.PDF")), Some("olalde"))
    assertEquals(UploadView.suggestCitekey(Api.FileName("Çatalhöyük.pdf")), Some("atalh_y_k"))
    assertEquals(UploadView.suggestCitekey(Api.FileName("1234.pdf")), None)
    UploadView.suggestCitekey(Api.FileName("x" * 300 + ".pdf")).foreach(ck => assert(ck.value.matches(CitekeyShape), ck))
  }

  test("the upload form is validated field by field before anything is sent; the citekey only for being given") {
    val bad = UploadView.validate(UploadView.Draft(citekey = UploadView.Draft.Citekey("Bad Key"), year = UploadView.Draft.Year("twenty"), doi = UploadView.Draft.Doi("nope")))
    assertEquals(
      bad.left.toOption.get.map(_._1),
      List(UploadView.Field.File, UploadView.Field.Year, UploadView.Field.Doi)
    )
    assertEquals(
      UploadView.validate(UploadView.Draft(citekey = UploadView.Draft.Citekey(" "))).left.toOption.get.map(_._1).take(2),
      List(UploadView.Field.File, UploadView.Field.Citekey)
    )
    val ok = UploadView.validate(
      UploadView.Draft(
        fileName = Some(Api.FileName("a.pdf")),
        citekey = UploadView.Draft.Citekey(" anthony2007_horse "),
        title = UploadView.Draft.Title("  "),
        authors = List(UploadView.Draft.Authors("David W. Anthony")),
        year = UploadView.Draft.Year("2007"),
        doi = UploadView.Draft.Doi("https://doi.org/10.1515/9781400831104"),
        quality = UploadView.Draft.Quality(true)
      )
    )
    assertEquals(
      ok,
      Right(
        Ingest.Params(
          citekey = Citekey("anthony2007_horse"),
          filename = Some(Bundle.Asset.FileName("a.pdf")),
          quality = Ingest.Params.Quality(true),
          title = None,
          authors = List(AuthorName("David W. Anthony")),
          year = Some(Year(2007)),
          doi = Some(com.histmersion.api.library.Doi("10.1515/9781400831104"))
        )
      )
    )
  }

  test("an upload refusal lands next to the field it is about") {
    assertEquals(
      UploadView.fieldOf(ApiFailure(ApiFailure.Status(409), Api.Error(Api.Error.Message("already stored as x"), Some(Api.Error.Code(Ingest.CodeConflict))))),
      UploadView.Field.File
    )
    assertEquals(UploadView.fieldOf(ApiFailure(ApiFailure.Status(400), Api.Error(Api.Error.Message("invalid citekey 'X'")))), UploadView.Field.Citekey)
    assertEquals(UploadView.fieldOf(ApiFailure(ApiFailure.Status(400), Api.Error(Api.Error.Message("upload too large")))), UploadView.Field.Form)
  }

  // ── the bookkeeping lists (acquire · tasks · log) ───────────────────────────────────────────────────────────────────────────

  test("an item's text is split into bold, italic, DOI, URL and citekey runs") {
    import QueueView.Seg.*
    assertEquals(
      QueueView.segments(
        UiLabels.Text("**Anthony 2007**, *The Horse* (doi 10.1515/9781400831104). See [[anthony2007_horse]] or https://x.org/a.")
      ),
      List(
        Bold(Bold.S("Anthony 2007")),
        Text(Text.S(", ")),
        Italic(Italic.S("The Horse")),
        Text(Text.S(" (doi ")),
        Doi(Doi.Value("10.1515/9781400831104")),
        Text(Text.S("). See ")),
        Cite(Cite.Citekey("anthony2007_horse")),
        Text(Text.S(" or ")),
        Url(Url.Value("https://x.org/a")),
        Text(Text.S("."))
      )
    )
    assertEquals(QueueView.segments(UiLabels.Text("plain text")), List(Text(Text.S("plain text"))))
    assertEquals(QueueView.segments(UiLabels.Text("a * b * c")), List(Text(Text.S("a * b * c"))))
  }

  test("a topic path reads without underscores or bold markers") {
    assertEquals(
      QueueView.heading(Some(QueueItem.Topic("**Highest value — get first** > greek_god_origins"))),
      ("Greek god origins", List("Highest value — get first"))
    )
    assertEquals(QueueView.heading(None), ("Uncategorised", Nil))
  }

  test("items are grouped by topic, groups in the order they were first added") {
    def item(id: Queue.QueueItem.Id, t: Option[Queue.QueueItem.Topic]) = Queue.QueueItem(id, QueueItem.List("acquire"), Queue.QueueItem.Text(s"i$id"), t, Queue.QueueItem.Status(Queue.Open))
    val gs = QueueView.groups(
      Queue.Kind.Acquire,
      List(
        item(Queue.QueueItem.Id(5), Some(Queue.QueueItem.Topic("b"))),
        item(Queue.QueueItem.Id(2), Some(Queue.QueueItem.Topic("a"))),
        item(Queue.QueueItem.Id(9), Some(Queue.QueueItem.Topic("b"))),
        item(Queue.QueueItem.Id(1), Some(Queue.QueueItem.Topic("b")))
      )
    )
    assertEquals(gs.map((t, is) => t -> is.map(_.id)), List(Some("b") -> List(1, 5, 9), Some("a") -> List(2)))
  }

  private def qi(
      id: Queue.QueueItem.Id,
      list: Queue.QueueItem.List,
      topic: Option[Queue.QueueItem.Topic],
      status: Queue.QueueItem.Status = Queue.QueueItem.Status(Queue.Open),
      createdAt: Option[Queue.QueueItem.CreatedAt] = None
  ) = Queue.QueueItem(id, list, Queue.QueueItem.Text(s"i$id"), topic, status, createdAt = createdAt)

  test("the new windows round-trip their wire names and titles read plainly") {
    assertEquals(WindowKey.Tasks.wire, "tasks")
    assertEquals(WindowKey.Log.wire, "log")
    assertEquals(WindowKey.parse(WindowKey.Wire("tasks")), Some(WindowKey.Tasks))
    assertEquals(WindowKey.parse(WindowKey.Wire(" log ")), Some(WindowKey.Log))
    assertEquals(WindowKey.parse(WindowKey.Wire("logs")), None)
    assertEquals(WindowKey.Tasks.initialTitle, "Backlog")
    assertEquals(WindowKey.Log.initialTitle, "Worklog")
    List(WindowKey.Acquire, WindowKey.Tasks, WindowKey.Log).foreach(k => assert(!k.initialTitle.value.contains("_"), k))
    assertEquals(WindowKey.Tasks.pageHref, None)
    import FieldCodec.given
    assertEquals(
      summon[FieldCodec[List[WindowKey]]].decode(List(FieldCodec.Token("acquire"), FieldCodec.Token("tasks"), FieldCodec.Token("log"))),
      Some(List(WindowKey.Acquire, WindowKey.Tasks, WindowKey.Log))
    )
  }

  test("every queue kind has its window, and back; other windows show no queue") {
    Queue.Kind.values.foreach { k =>
      assertEquals(WindowKey.queueOf(WindowKey.ofQueue(k)), Some(k))
      assertEquals(WindowKey.ofQueue(k).wire.value, k.label.value)
    }
    assertEquals(WindowKey.queueOf(WindowKey.Jobs), None)
    assertEquals(WindowKey.queueOf(WindowKey.Source(WindowKey.Source.Citekey("a2020_b"))), None)
  }

  test("the backlog groups by topic like the acquisition queue: groups in first-added order, items by id") {
    val its = List(
      qi(QueueItem.Id(40), QueueItem.List("tasks"), Some(QueueItem.Topic("Histmersion"))),
      qi(QueueItem.Id(12), QueueItem.List("tasks"), Some(QueueItem.Topic("Corpus hygiene"))),
      qi(QueueItem.Id(7), QueueItem.List("tasks"), Some(QueueItem.Topic("Histmersion")), QueueItem.Status(Queue.Done)),
      qi(QueueItem.Id(30), QueueItem.List("tasks"), None)
    )
    assertEquals(
      QueueView.groups(Queue.Kind.Tasks, its).map((t, is) => t -> is.map(_.id)),
      List[(Option[String], List[Int])](Some("Histmersion") -> List(7, 40), Some("Corpus hygiene") -> List(12), None -> List(30))
    )
  }

  test("the worklog groups by date, newest date first, entries of a day in file order") {
    val its = List(
      qi(QueueItem.Id(217), QueueItem.List("log"), Some(QueueItem.Topic("2026-08-28")), createdAt = Some(QueueItem.CreatedAt("2026-08-28T08:39:24Z"))),
      qi(QueueItem.Id(597), QueueItem.List("log"), Some(QueueItem.Topic("2026-09-27")), createdAt = Some(QueueItem.CreatedAt("2026-09-27T08:09:36Z"))),
      qi(QueueItem.Id(218), QueueItem.List("log"), Some(QueueItem.Topic("2026-08-28")), createdAt = Some(QueueItem.CreatedAt("2026-08-28T11:26:04Z"))),
      qi(QueueItem.Id(426), QueueItem.List("log"), Some(QueueItem.Topic("2026-09-26")), createdAt = Some(QueueItem.CreatedAt("2026-09-26T22:58:35Z"))),
      qi(QueueItem.Id(90), QueueItem.List("log"), Some(QueueItem.Topic("2026-06-24/25 — a range")), createdAt = Some(QueueItem.CreatedAt("2026-06-24T00:00:00Z"))),
      // added later but dated earlier: its date decides, not its id
      qi(QueueItem.Id(700), QueueItem.List("log"), Some(QueueItem.Topic("2026-01-02")), createdAt = Some(QueueItem.CreatedAt("2026-09-27T12:00:00Z"))),
      qi(QueueItem.Id(605), QueueItem.List("log"), Some(QueueItem.Topic("2026-09-27")), createdAt = Some(QueueItem.CreatedAt("2026-09-27T08:13:11Z")))
    )
    assertEquals(
      QueueView.groups(Queue.Kind.Log, its).map((t, is) => t -> is.map(_.id)),
      List(
        Some("2026-09-27") -> List(597, 605),
        Some("2026-09-26") -> List(426),
        Some("2026-08-28") -> List(217, 218),
        Some("2026-06-24/25 — a range") -> List(90),
        Some("2026-01-02") -> List(700)
      )
    )
  }

  test("a worklog heading without a date falls back to when its entries were written; undated sorts last") {
    assertEquals(QueueView.logDate(Some(QueueItem.Topic("2026-06-24/25 — x")), Nil), "2026-06-24")
    assertEquals(
      QueueView.logDate(
        Some(QueueItem.Topic("Session notes")),
        List(
          qi(QueueItem.Id(1), QueueItem.List("log"), None, createdAt = Some(QueueItem.CreatedAt("2026-05-01T10:00:00Z"))),
          qi(QueueItem.Id(2), QueueItem.List("log"), None, createdAt = Some(QueueItem.CreatedAt("2026-05-03T10:00:00Z")))
        )
      ),
      "2026-05-03"
    )
    assertEquals(QueueView.logDate(None, List(qi(QueueItem.Id(3), QueueItem.List("log"), None))), "")
    val its = List(
      qi(QueueItem.Id(1), QueueItem.List("log"), None),
      qi(QueueItem.Id(2), QueueItem.List("log"), Some(QueueItem.Topic("Notes")), createdAt = Some(QueueItem.CreatedAt("2026-07-01T00:00:00Z"))),
      qi(QueueItem.Id(3), QueueItem.List("log"), Some(QueueItem.Topic("2026-07-02")))
    )
    assertEquals(QueueView.groups(Queue.Kind.Log, its).map(_._1), List(Some("2026-07-02"), Some("Notes"), None))
    assertEquals(QueueView.heading(None, UiLabels.Text(QueueView.spec(Queue.Kind.Log).untitled.value)), ("Undated", Nil))
  }

  test("each list's window: checkboxes and a show-done switch for acquire and tasks, neither for the log") {
    val (acq, tasks, log) =
      (QueueView.spec(Queue.Kind.Acquire), QueueView.spec(Queue.Kind.Tasks), QueueView.spec(Queue.Kind.Log))
    assert(acq.checkable && tasks.checkable && !log.checkable)
    assertEquals(acq.showDone, Some(UiState.acquireDone))
    assertEquals(tasks.showDone, Some(UiState.tasksDone))
    assertEquals(log.showDone, None)
    List(acq, tasks, log).foreach(s => assert(!s.empty.value.contains("_") && !s.untitled.value.contains("_"), s.kind))
  }

  test("the count line reads per list") {
    val acq = QueueView.spec(Queue.Kind.Acquire); val tasks = QueueView.spec(Queue.Kind.Tasks)
    val log = QueueView.spec(Queue.Kind.Log)
    val mixed = List(
      qi(QueueItem.Id(1), QueueItem.List("x"), None),
      qi(QueueItem.Id(2), QueueItem.List("x"), None, QueueItem.Status(Queue.Done)),
      qi(QueueItem.Id(3), QueueItem.List("x"), None)
    )
    assertEquals(QueueView.summary(acq, mixed), "2 to get · 1 done")
    assertEquals(QueueView.summary(tasks, mixed), "2 open · 1 done")
    assertEquals(QueueView.summary(tasks, mixed.take(1)), "1 open")
    assertEquals(
      QueueView.summary(
        log,
        List(
          qi(QueueItem.Id(1), QueueItem.List("log"), Some(QueueItem.Topic("2026-09-27"))),
          qi(QueueItem.Id(2), QueueItem.List("log"), Some(QueueItem.Topic("2026-09-27"))),
          qi(QueueItem.Id(3), QueueItem.List("log"), Some(QueueItem.Topic("2026-09-26")))
        )
      ),
      "3 entries · 2 days"
    )
    assertEquals(QueueView.summary(log, List(qi(QueueItem.Id(1), QueueItem.List("log"), Some(QueueItem.Topic("2026-09-27"))))), "1 entry · 1 day")
  }

  test("the backlog's show-done switch lives in the URL, separately from the acquisition queue's") {
    UiState.set(UiState.tasksDone := true)()
    val h = UiState.hashString
    assert(h.value.contains("tasksDone=true"), h)
    assert(!h.value.contains("acqDone"), h)
    UiState.loadHash(UiState.UrlHash(""))
    assertEquals(UiState.tasksDone.now(), false)
    UiState.loadHash(h)
    assertEquals(UiState.tasksDone.now(), true)
    assertEquals(UiState.acquireDone.now(), false)
  }

  // ── adding and editing items ───────────────────────────────────────────────────────────────

  test("an added item needs text; a blank topic or reason is left out; the worklog takes no reason") {
    val tasks = QueueView.spec(Queue.Kind.Tasks); val log = QueueView.spec(Queue.Kind.Log)
    assertEquals(QueueView.validateAdd(tasks, QueueView.AddDraft(text = QueueView.AddDraft.Text("  \n "))), Left("Write the item's text first."))
    assertEquals(
      QueueView.validateAdd(tasks, QueueView.AddDraft(QueueView.AddDraft.Text(" re-run the lint \n"), QueueView.AddDraft.Topic("  "), QueueView.AddDraft.Reason(" "))),
      Right(Queue.QueueAdd(QueueItem.Text("re-run the lint"), None, reason = None))
    )
    assertEquals(
      QueueView.validateAdd(tasks, QueueView.AddDraft(QueueView.AddDraft.Text("x"), QueueView.AddDraft.Topic(" Corpus hygiene "), QueueView.AddDraft.Reason(" because "))),
      Right(Queue.QueueAdd(QueueItem.Text("x"), Some(QueueItem.Topic("Corpus hygiene")), reason = Some(QueueItem.Reason("because"))))
    )
    assertEquals(
      QueueView.validateAdd(log, QueueView.AddDraft(QueueView.AddDraft.Text("ran the lint"), QueueView.AddDraft.Topic("2026-09-27"), QueueView.AddDraft.Reason("ignored"))),
      Right(Queue.QueueAdd(QueueItem.Text("ran the lint"), Some(QueueItem.Topic("2026-09-27")), reason = None))
    )
    // a multi-line item keeps its inner lines
    assertEquals(QueueView.validateAdd(tasks, QueueView.AddDraft(QueueView.AddDraft.Text("a\n  b"))).map(_.text), Right("a\n  b"))
  }

  test("an edit sends only what changed; a blank topic clears it; blank text is refused") {
    val item = qi(QueueItem.Id(3), QueueItem.List("tasks"), Some(QueueItem.Topic("Chores"))).modify(_.text).setTo(QueueItem.Text("tidy up"))
    assertEquals(QueueView.editPatch(item, QueueItem.Text("tidy up"), QueueItem.Topic("Chores")), Right(None))
    assertEquals(QueueView.editPatch(item, QueueItem.Text(" tidy up \n"), QueueItem.Topic(" Chores ")), Right(None))
    assertEquals(
      QueueView.editPatch(item, QueueItem.Text("tidy up well"), QueueItem.Topic("Chores")),
      Right(Some(Queue.QueuePatch(text = Some(QueueItem.Text("tidy up well")))))
    )
    assertEquals(QueueView.editPatch(item, QueueItem.Text("tidy up"), QueueItem.Topic("")), Right(Some(Queue.QueuePatch(topic = Some(QueueItem.Topic(""))))))
    assertEquals(QueueView.editPatch(item.modify(_.topic).setTo(None), QueueItem.Text("tidy up"), QueueItem.Topic("  ")), Right(None))
    assertEquals(QueueView.editPatch(item, QueueItem.Text("tidy up"), QueueItem.Topic("Later")), Right(Some(Queue.QueuePatch(topic = Some(QueueItem.Topic("Later"))))))
    assert(QueueView.editPatch(item, QueueItem.Text("   "), QueueItem.Topic("Chores")).isLeft)
  }

  test("topic suggestions: the list's own topics, first-used first, matching what is typed") {
    val its = List(
      qi(QueueItem.Id(9), QueueItem.List("acquire"), Some(QueueItem.Topic("linear_b"))),
      qi(QueueItem.Id(2), QueueItem.List("acquire"), Some(QueueItem.Topic("**Highest value** > greek_god_origins"))),
      qi(QueueItem.Id(5), QueueItem.List("acquire"), Some(QueueItem.Topic("bronze_age_collapse"))),
      qi(QueueItem.Id(7), QueueItem.List("acquire"), Some(QueueItem.Topic("linear_b"))),
      qi(QueueItem.Id(8), QueueItem.List("acquire"), None)
    )
    assertEquals(
      QueueView.topicSuggestions(its, Api.Search.Text("")),
      List("**Highest value** > greek_god_origins", "bronze_age_collapse", "linear_b")
    )
    // typed as it reads (spaces for underscores, any case)
    assertEquals(QueueView.topicSuggestions(its, Api.Search.Text("Bronze Age")), List("bronze_age_collapse"))
    assertEquals(QueueView.topicSuggestions(its, Api.Search.Text("god orig")), List("**Highest value** > greek_god_origins"))
    // the exact topic typed is not offered back
    assertEquals(QueueView.topicSuggestions(its, Api.Search.Text("linear_b")), Nil)
    assertEquals(QueueView.topicSuggestions(its, Api.Search.Text(""), limit = Paging.Limit(1)).size, 1)
  }

  // ── fulfilling an acquisition item ─────────────────────────────────────────────────────────────

  test("Link existing source… ticks the item off with the core rule, addressed to the item") {
    // the rule itself (reason kept, no duplicate tag) is tested in core's QueueSuite
    val item = qi(QueueItem.Id(567), QueueItem.List("acquire"), Some(QueueItem.Topic("mesopotamian_religion")))
    assertEquals(
      QueueView.fulfil(item, Preview.Target.Source.Citekey("lambert2013_babylonian")),
      QueueItem.Id(567) -> Queue.QueuePatch(status = Some(QueueItem.Status(Queue.Done)), reason = Some(QueueItem.Reason("ingested as [[lambert2013_babylonian]]")))
    )
    // the reason renders the citekey as a source link
    assert(QueueView.segments(UiLabels.Text(Queue.fulfilledReason(None, Citekey("a2020_b")).value)).contains(QueueView.Seg.Cite(QueueView.Seg.Cite.Citekey("a2020_b"))))
  }

  test("an upload started from an item names it as acquireItem; a plain upload names none") {
    val item = qi(QueueItem.Id(515), QueueItem.List("acquire"), Some(QueueItem.Topic("linear_b")))
    val d = UploadView.Draft(fileName = Some(Api.FileName("a.pdf")), citekey = UploadView.Draft.Citekey("killen2024_new"), year = UploadView.Draft.Year("2024"))
    assertEquals(UploadView.validate(d.modify(_.forItem).setTo(Some(item))).map(_.acquireItem), Right(Some(515)))
    assertEquals(UploadView.validate(d).map(_.acquireItem), Right(None))
    // otherwise the link changes nothing the form sends
    assertEquals(
      UploadView.validate(d.modify(_.forItem).setTo(Some(item))).map(_.modify(_.acquireItem).setTo(None)),
      UploadView.validate(d)
    )
  }

  test("a finished job that ticks an item off is noticed once; other jobs are not") {
    def job(id: String, st: JobState, item: Option[QueueItem.Id]) =
      JobInfo(SpatialHierarchyResult.Job.Id(id), JobKinds.Wire("ingest"), st.wire, (Some("ck")).map(Citekey.apply), acquireItem = item)
    val running = List(job("a", JobState.Running, Some(QueueItem.Id(7))), job("b", JobState.Running, None))
    assert(JobsView.ticksOff(running, List(job("a", JobState.Done, Some(QueueItem.Id(7))), running(1))))
    assert(!JobsView.ticksOff(running, List(running(0), job("b", JobState.Done, None))), "no item, nothing to reload")
    assert(
      !JobsView.ticksOff(running, List(job("a", JobState.Failed, Some(QueueItem.Id(7))), running(1))),
      "a failed job ticks nothing"
    )
    val done = List(job("a", JobState.Done, Some(QueueItem.Id(7))))
    assert(!JobsView.ticksOff(done, done), "already seen done")
  }

  // the texts are real items of the acquisition queue (their ids in `histmersion queue acquire`)
  private val Killen = QueueItem.Text("John T. Killen (ed.), The New Documents in Mycenaean Greek (Cambridge UP, 2024; " +
    "10.1017/9781139029049, chapters on writing system, language, society, economy, Mycenaean religion, Mycenaean " +
    "and Classical Greek religion) — the modern replacement for Ventris & Chadwick's Documents; the synthesis that " +
    "should replace ventris1953_evidence / webster1955_tablets citations flagged in #427")
  private val Lambert = QueueItem.Text("Lambert, W.G., Babylonian Creation Myths (Eisenbrauns, 2013; 10.1515/9781575068619) — the " +
    "critical edition/study of Enuma Elish and other cosmogonies (63 cites); creation_cosmogony and succession_myth " +
    "rely on Enuma Elish with no edition in the corpus")
  private val Finkelberg = QueueItem.Text("Finkelberg, M. 2000. 'The Cypria, the Iliad, and the Problem of Multiformity in Oral " +
    "and Written Tradition', Classical Philology 95: 1-11")
  private val Becks = QueueItem.Text("Ralf Becks, Troia VI und VII. Stratigraphie, Architektur, Befunde (final publication of the " +
    "Troy VI/VII levels; known here only from Franković's Gnomon review 2025, 10.17104/0017-1417-2025-2-378 — get " +
    "full bibliographic details from the review) — addresses trojan_war open question 'Troy VIh vs VIIa'")
  private val Beckman = QueueItem.Text("Beckman, Bryce & Cline, The Ahhiyawa Texts (2025 edition; JSTOR 10.2307/jj.25577267) — new " +
    "edition of beckman2011_ahhiyawa (corpus 'foundational'); check what it updates before citing the 2011 " +
    "translations in hittite_anatolia, trojan_war, achilles_origins")
  private val Kristiansen = QueueItem.Text("Kristiansen, Kroonen & Willerslev (eds.), The Indo-European Puzzle Revisited: " +
    "Integrating Archaeology, Genetics, and Linguistics (Cambridge UP, 2023; 10.1017/9781009261753; discover found " +
    "no OA copy — check CUP open access first) — the 2023 steppe-synthesis volume (Lubotsky on wagon terms & " +
    "Indo-Iranian split, Olander on metals, Pronk on kinship/marriage, Olsen on wool)")
  private val AyaliDarshan = QueueItem.Text("Ayali-Darshan & Darshan, Philo of Byblos' Version of the Storm God's Combat Against " +
    "the Sea (Journal of Near Eastern Studies, 2025; 10.1086/736959) — cites guterbock1948_kumarbi; new 2025 work")
  private val Bailey = QueueItem.Text("Bailey, D., Prehistoric Figurines: Representation and Corporeality in the Neolithic " +
    "(Routledge, 2005; 10.4324/9780203392454) — the leading post-goddess theory of figurines (120 cites)")

  test("an acquisition item's reference is read from its text: authors, title, year, DOI") {
    import QueueItemRef.{Ref, parse}
    assertEquals(
      parse(Killen),
      Ref(
        List(Ref.Authors("John T. Killen")),
        Some(Ref.Title("The New Documents in Mycenaean Greek")),
        Some(Ref.Year(2024)),
        Some(Ref.Doi("10.1017/9781139029049"))
      )
    )
    assertEquals(
      parse(Lambert),
      Ref(List(Ref.Authors("W.G. Lambert")), Some(Ref.Title("Babylonian Creation Myths")), Some(Ref.Year(2013)), Some(Ref.Doi("10.1515/9781575068619")))
    )
    assertEquals(
      parse(Finkelberg),
      Ref(
        List(Ref.Authors("M. Finkelberg")),
        Some(Ref.Title("The Cypria, the Iliad, and the Problem of Multiformity in Oral and Written Tradition")),
        Some(Ref.Year(2000)),
        None
      )
    )
    assertEquals(
      parse(Beckman),
      Ref(List(Ref.Authors("Beckman"), Ref.Authors("Bryce"), Ref.Authors("Cline")), Some(Ref.Title("The Ahhiyawa Texts")), Some(Ref.Year(2025)), Some(Ref.Doi("10.2307/jj.25577267")))
    )
    assertEquals(
      parse(Kristiansen),
      Ref(
        List(Ref.Authors("Kristiansen"), Ref.Authors("Kroonen"), Ref.Authors("Willerslev")),
        Some(Ref.Title("The Indo-European Puzzle Revisited: Integrating Archaeology, Genetics, and Linguistics")),
        Some(Ref.Year(2023)),
        Some(Ref.Doi("10.1017/9781009261753"))
      )
    )
    assertEquals(
      parse(AyaliDarshan),
      Ref(
        List(Ref.Authors("Ayali-Darshan"), Ref.Authors("Darshan")),
        Some(Ref.Title("Philo of Byblos' Version of the Storm God's Combat Against the Sea")),
        Some(Ref.Year(2025)),
        Some(Ref.Doi("10.1086/736959"))
      )
    )
    assertEquals(
      parse(Bailey),
      Ref(
        List(Ref.Authors("D. Bailey")),
        Some(Ref.Title("Prehistoric Figurines: Representation and Corporeality in the Neolithic")),
        Some(Ref.Year(2005)),
        Some(Ref.Doi("10.4324/9780203392454"))
      )
    )
  }

  // #528 names a DOI — of the REVIEW it was found through; taking it would file the book under the review
  test("a DOI that is not the item's own (a review it was found through) is not taken; nor is its year") {
    assertEquals(
      QueueItemRef.parse(Becks),
      QueueItemRef.Ref(List(QueueItemRef.Ref.Authors("Ralf Becks")), Some(QueueItemRef.Ref.Title("Troia VI und VII. Stratigraphie, Architektur, Befunde")), None, None)
    )
  }

  test("text that is not a reference still gives a usable, editable guess") {
    assertEquals(
      QueueItemRef.parse(QueueItem.Text("Just a thing to get")),
      QueueItemRef.Ref(Nil, Some(QueueItemRef.Ref.Title("Just a thing to get")), None, None)
    )
    assertEquals(QueueItemRef.parse(QueueItem.Text("")), QueueItemRef.Ref(Nil, None, None, None))
    // only the first line is the reference
    assertEquals(QueueItemRef.parse(QueueItem.Text(Lambert.value + "\n  (2019 reprint; 10.1/x)")).doi, Some("10.1515/9781575068619"))
  }

  test("the citekey suggested for an item follows the corpus's surnameYEAR_word shape and is always valid") {
    def ck(t: QueueItem.Text) = QueueItemRef.citekey(QueueItemRef.parse(t))
    assertEquals(ck(Lambert), Some("lambert2013_babylonian"))
    assertEquals(ck(Finkelberg), Some("finkelberg2000_cypria"))
    assertEquals(ck(Killen), Some("killen2024_new"))
    assertEquals(ck(Becks), Some("becks_troia"))
    assertEquals(ck(AyaliDarshan), Some("ayali_darshan2025_philo"))
    assertEquals(ck(Bailey), Some("bailey2005_prehistoric"))
    assertEquals(ck(QueueItem.Text("")), None)
    List(Killen, Lambert, Finkelberg, Becks, Beckman, Kristiansen, AyaliDarshan, Bailey)
      .flatMap(ck)
      .foreach(k => assert(k.value.matches(CitekeyShape), k))
  }

  test("Upload… fills the form from the item and links the upload to it") {
    val item = Queue.QueueItem(QueueItem.Id(567), QueueItem.List("acquire"), Lambert, Some(QueueItem.Topic("mesopotamian_religion")), QueueItem.Status(Queue.Open))
    assertEquals(
      QueueItemRef.uploadDraft(item),
      UploadView.Draft(
        citekey = UploadView.Draft.Citekey("lambert2013_babylonian"),
        title = UploadView.Draft.Title("Babylonian Creation Myths"),
        authors = List(UploadView.Draft.Authors("W.G. Lambert")),
        year = UploadView.Draft.Year("2013"),
        doi = UploadView.Draft.Doi("10.1515/9781575068619"),
        forItem = Some(item)
      )
    )
    val becks = QueueItemRef.uploadDraft(item.modify(_.id).setTo(QueueItem.Id(528)).modify(_.text).setTo(Becks))
    assertEquals((becks.year, becks.doi, becks.citekey), ("", "", "becks_troia"))
    // a filled-in form still needs its file, and nothing else
    assertEquals(
      UploadView.validate(QueueItemRef.uploadDraft(item)).left.map(_.map(_._1)),
      Left(List(UploadView.Field.File))
    )
  }

  test("picking an item in the upload form fills only what the reader has not typed, and keeps the file") {
    val lambert = Queue.QueueItem(QueueItem.Id(567), QueueItem.List("acquire"), Lambert, Some(QueueItem.Topic("mesopotamian_religion")), QueueItem.Status(Queue.Open))
    val becks = Queue.QueueItem(QueueItem.Id(528), QueueItem.List("acquire"), Becks, None, QueueItem.Status(Queue.Open))
    // a file dropped first, with the citekey suggested from its name and a title typed
    val typed = UploadView.Draft(fileName = Some(Api.FileName("scan.pdf")), citekey = UploadView.Draft.Citekey("my_own_key"), title = UploadView.Draft.Title("My title"))
    val d1 = UploadView.withItem(typed, lambert)
    assertEquals(d1.fileName, Some("scan.pdf"))
    assertEquals((d1.citekey, d1.title), ("my_own_key", "My title"), "the reader's text stays")
    assertEquals((d1.year, d1.doi, d1.authors), ("2013", "10.1515/9781575068619", List("W.G. Lambert")))
    assertEquals(d1.forItem, Some(lambert))
    // picking ANOTHER item refills what the first pick filled, still not the reader's own text
    val d2 = UploadView.withItem(d1, becks)
    assertEquals(d2.forItem, Some(becks))
    assertEquals((d2.citekey, d2.title), ("my_own_key", "My title"))
    assertEquals((d2.year, d2.doi), ("", ""), "Lambert's year and DOI do not linger on Becks")
    assertEquals(d2.authors, QueueItemRef.uploadDraft(becks).authors)
    // a citekey only SUGGESTED from the dropped file's name gives way to the item's
    val dropped = UploadView.Draft(fileName = Some(Api.FileName("good.pdf")), citekey = UploadView.Draft.Citekey("good"))
    assertEquals(UploadView.withItem(dropped, lambert).citekey, "lambert2013_babylonian")
    // on an empty form, picking is exactly what "Upload…" on the item fills in
    assertEquals(UploadView.withItem(UploadView.Draft(), lambert), QueueItemRef.uploadDraft(lambert))
  }

  test("the picker names an item by its id, and only an item it offers") {
    val items = List(
      Queue.QueueItem(QueueItem.Id(567), QueueItem.List("acquire"), Lambert, None, QueueItem.Status(Queue.Open)),
      Queue.QueueItem(QueueItem.Id(9), QueueItem.List("acquire"), QueueItem.Text("**A**, B"), None, QueueItem.Status(Queue.Open))
    )
    val label = UploadView.itemLabel(items.head)
    assert(label.value.startsWith("#567 Lambert, W.G., Babylonian Creation Myths"), label)
    assert(label.value.length <= 115 && label.value.endsWith("…"), label)
    assertEquals(UploadView.itemLabel(items(1)), "#9 A, B")
    assertEquals(UploadView.pickedItem(label.value, items), Some(items.head))
    assertEquals(UploadView.pickedItem("#9", items), Some(items(1)))
    assertEquals(UploadView.pickedItem("#95 x", items), None)
    assertEquals(UploadView.pickedItem("Lambert", items), None)
  }

  test("Link existing source… starts the corpus search from the item's title") {
    def q(t: QueueItem.Text) = QueueItemRef.searchQuery(Queue.QueueItem(QueueItem.Id(1), QueueItem.List("acquire"), t, None, QueueItem.Status(Queue.Open)))
    assertEquals(q(Lambert), "Babylonian Creation Myths")
    assertEquals(q(Bailey), "Prehistoric Figurines")
    assertEquals(q(Becks), "Troia VI und VII")
  }

  // found in the browser: "…cited by cline2014_year… For achilles_origins" printed raw
  test("bare corpus identifiers in item text become Ident segments; DOIs and URLs keep precedence") {
    import QueueView.Seg
    val segs =
      QueueView.segments(UiLabels.Text("cited by cline2014_year, see https://x.org/a_b and 10.1000/a_b. For achilles_origins."))
    assert(segs.contains(Seg.Ident(Seg.Ident.Id("cline2014_year"))), segs.toString)
    assert(segs.contains(Seg.Ident(Seg.Ident.Id("achilles_origins"))), segs.toString)
    assert(segs.contains(Seg.Url(Seg.Url.Value("https://x.org/a_b"))), segs.toString)
    assert(segs.contains(Seg.Doi(Seg.Doi.Value("10.1000/a_b"))), segs.toString)
    assert(!segs.exists { case Seg.Ident(i) => i.value == "a_b"; case _ => false }, segs.toString)
  }

  test("a link to a list item names it in the address; the list marks and scrolls to it"):
    assertEquals(QueueView.targetItem(Api.Search.Text("?item=12")), Some(12))
    assertEquals(QueueView.targetItem(Api.Search.Text("?x=1&item=7")), Some(7))
    assertEquals(QueueView.targetItem(Api.Search.Text("")), None)
    assertEquals(QueueView.targetItem(Api.Search.Text("?item=abc")), None)
    assertEquals(QueueView.itemAnchor(QueueItem.Id(12)), "queue-item-12")
