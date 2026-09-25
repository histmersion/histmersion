package com.histmersion.web

import com.histmersion.core.*
import sttp.model.Method

/** The pure parts of the tool windows: window keys, the write gate, the jobs poll, the upload form,
  * the acquisition list's text. */
class ToolsSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash("")

  // ── window keys ────────────────────────────────────────────────────────────────────────────────

  test("every window key round-trips through its hash spelling; unknown entries are dropped") {
    val keys = List(WindowKey.Source("a2020_b"), WindowKey.Article("sea-peoples"), WindowKey.Graph("a2020_b"),
      WindowKey.EditSource("x2020_v1.2"), WindowKey.Jobs, WindowKey.Upload, WindowKey.Acquire, WindowKey.ApiKey)
    keys.foreach(k => assertEquals(WindowKey.parse(k.wire), Some(k)))
    assertEquals(WindowKey.parse("nope"), None)
    assertEquals(WindowKey.parse("source:"), None)
    import FieldCodec.given
    assertEquals(summon[FieldCodec[List[WindowKey]]].decode(List("jobs", "bogus", "jobs", "edit:a2020_b")),
      Some(List(WindowKey.Jobs, WindowKey.EditSource("a2020_b"))))
  }

  test("only page windows can open in a new tab") {
    assertEquals(WindowKey.Source("a b").pageHref, Some("/source/a%20b"))
    assertEquals(WindowKey.Article("x").pageHref, Some("/article/x"))
    assertEquals(WindowKey.Jobs.pageHref, None)
    assertEquals(WindowKey.EditSource("a").pageHref, None)
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
    assert(ApiFailure.of(401, None).unauthorized)
    assert(ApiFailure.of(403, None).unauthorized)
    assert(ApiFailure(412, ApiError("moved on")).conflict)
    assert(ApiFailure(409, ApiError("dup")).duplicate)
    assertEquals(ApiFailure.of(400, Some(ApiError("bad year"))).getMessage, "bad year")
    assert(ApiFailure.of(0, None).getMessage.contains("could not be reached"))
  }

  test("the API key is kept in the browser's storage and never in a shared link") {
    UiState.set(UiState.apiKey := Some("s3cret"), UiState.jobStatus := Some(JobState.Failed),
      UiState.open := List(WindowKey.Jobs))()
    val h = UiState.hashString
    assert(!h.contains("s3cret") && !h.contains("apiKey"), h)
    assert(h.contains("jobs=failed"), h)
    assertEquals(UiState.store.get("histmersion.ui.apiKey"), Some("""["s3cret"]"""))
    UiState.set(UiState.apiKey := None)()
    UiState.restoreLocal()
    assertEquals(UiState.apiKey.now(), None)
  }

  // ── jobs ───────────────────────────────────────────────────────────────────────────────────────

  private def job(id: String, s: JobState, kind: String = Ingest.KindIngest) =
    JobInfo(id, kind, s.wire, Some(s"ck_$id"))

  test("polling backs off while nothing moves, restarts on a change, and stops when all jobs finished") {
    val running = List(job("a", JobState.Running), job("b", JobState.Done))
    assertEquals(JobsView.Poll.next(None, running, changed = false), Some(JobsView.Poll.FirstMs))
    assertEquals(JobsView.Poll.next(Some(1000), running, changed = false), Some(1600))
    assertEquals(JobsView.Poll.next(Some(14000), running, changed = false), Some(JobsView.Poll.MaxMs))
    assertEquals(JobsView.Poll.next(Some(8000), running, changed = true), Some(JobsView.Poll.FirstMs))
    assertEquals(JobsView.Poll.next(Some(1000), List(job("q", JobState.Queued)), changed = false), Some(1600))
    assertEquals(JobsView.Poll.next(Some(1000), List(job("b", JobState.Done), job("c", JobState.Failed)), true), None)
    assertEquals(JobsView.Poll.next(None, Nil, changed = true), None)
  }

  test("the state filter and its counts") {
    val js = List(job("a", JobState.Running), job("b", JobState.Failed), job("c", JobState.Failed))
    assertEquals(JobsView.visible(js, None), js)
    assertEquals(JobsView.visible(js, Some(JobState.Failed)).map(_.id), List("b", "c"))
    assertEquals(JobsView.counts(js), Map(JobState.Running -> 1, JobState.Failed -> 2))
  }

  test("a job links its source only once the source exists") {
    assert(!JobsView.subjectStored(job("a", JobState.Failed)))
    assert(!JobsView.subjectStored(job("a", JobState.Running)))
    assert(JobsView.subjectStored(job("a", JobState.Done)))
    assert(JobsView.subjectStored(job("a", JobState.Running, Ingest.KindReocr)))
    assertEquals(JobsView.kindLabel("some_kind"), "some kind")
  }

  test("timestamps read as how long ago") {
    assertEquals(JobsView.ago(0, 42000), "42 s ago")
    assertEquals(JobsView.ago(0, 5 * 60000), "5 min ago")
    assertEquals(JobsView.ago(0, 3 * 3600000.0), "3 h ago")
    assertEquals(JobsView.ago(0, 2 * 86400000.0), "2 d ago")
    assertEquals(JobsView.ago(1000, 0), "0 s ago")
  }

  // ── upload ─────────────────────────────────────────────────────────────────────────────────────

  test("a citekey is suggested from the file name, and is always a valid one") {
    assertEquals(UploadView.suggestCitekey("Anthony 2007 - The Horse.pdf"), Some("anthony_2007_the_horse"))
    assertEquals(UploadView.suggestCitekey("2019_Olalde.PDF"), Some("olalde"))
    assertEquals(UploadView.suggestCitekey("Çatalhöyük.pdf"), Some("atalh_y_k"))
    assertEquals(UploadView.suggestCitekey("1234.pdf"), None)
    UploadView.suggestCitekey("x" * 300 + ".pdf").foreach(ck => assert(Citekey.valid(ck), ck))
  }

  test("the upload form is validated field by field before anything is sent") {
    val bad = UploadView.validate(UploadView.Draft(citekey = "Bad Key", year = "twenty", doi = "nope"))
    assertEquals(bad.left.toOption.get.map(_._1),
      List(UploadView.Field.File, UploadView.Field.Citekey, UploadView.Field.Year, UploadView.Field.Doi))
    val ok = UploadView.validate(UploadView.Draft(fileName = Some("a.pdf"), citekey = " anthony2007_horse ",
      title = "  ", authors = List("David W. Anthony"), year = "2007", doi = "https://doi.org/10.1515/9781400831104",
      quality = true))
    assertEquals(ok, Right(IngestParams(citekey = "anthony2007_horse", filename = Some("a.pdf"), quality = true,
      title = None, authors = List("David W. Anthony"), year = Some(2007), doi = Some("10.1515/9781400831104"))))
  }

  test("an upload refusal lands next to the field it is about") {
    assertEquals(UploadView.fieldOf(ApiFailure(409, ApiError("already stored as x", Some(Ingest.CodeConflict)))),
      UploadView.Field.File)
    assertEquals(UploadView.fieldOf(ApiFailure(400, ApiError("invalid citekey 'X'"))), UploadView.Field.Citekey)
    assertEquals(UploadView.fieldOf(ApiFailure(400, ApiError("upload too large"))), UploadView.Field.Form)
  }

  // ── acquisition list ───────────────────────────────────────────────────────────────────────────

  test("an item's text is split into bold, italic, DOI, URL and citekey runs") {
    import AcquireView.Seg.*
    assertEquals(
      AcquireView.segments("**Anthony 2007**, *The Horse* (doi 10.1515/9781400831104). See [[anthony2007_horse]] or https://x.org/a."),
      List(Bold("Anthony 2007"), Text(", "), Italic("The Horse"), Text(" (doi "), Doi("10.1515/9781400831104"),
        Text("). See "), Cite("anthony2007_horse"), Text(" or "), Url("https://x.org/a"), Text("."))
    )
    assertEquals(AcquireView.segments("plain text"), List(Text("plain text")))
    assertEquals(AcquireView.segments("a * b * c"), List(Text("a * b * c")))
  }

  test("a topic path reads without underscores or bold markers") {
    assertEquals(AcquireView.heading(Some("**Highest value — get first** > greek_god_origins")),
      ("greek god origins", List("Highest value — get first")))
    assertEquals(AcquireView.heading(None), ("Uncategorised", Nil))
  }

  test("items are grouped by topic, groups in the order they were first added") {
    def item(id: Int, t: Option[String]) = Queue.QueueItem(id, "acquire", s"i$id", t, Queue.Open)
    val gs = AcquireView.groups(List(item(5, Some("b")), item(2, Some("a")), item(9, Some("b")), item(1, Some("b"))))
    assertEquals(gs.map((t, is) => t -> is.map(_.id)), List(Some("b") -> List(1, 5, 9), Some("a") -> List(2)))
  }

  // found in the browser: "…cited by cline2014_year… For achilles_origins" printed raw
  test("bare corpus identifiers in item text become Ident segments; DOIs and URLs keep precedence") {
    import AcquireView.Seg
    val segs = AcquireView.segments("cited by cline2014_year, see https://x.org/a_b and 10.1000/a_b. For achilles_origins.")
    assert(segs.contains(Seg.Ident("cline2014_year")), segs.toString)
    assert(segs.contains(Seg.Ident("achilles_origins")), segs.toString)
    assert(segs.contains(Seg.Url("https://x.org/a_b")), segs.toString)
    assert(segs.contains(Seg.Doi("10.1000/a_b")), segs.toString)
    assert(!segs.exists { case Seg.Ident(i) => i == "a_b"; case _ => false }, segs.toString)
  }

