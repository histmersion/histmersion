package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.Confidence

class FmtSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  // ── yr ────────────────────────────────────────────────────────────────────────

  test("yr formats BCE years") {
    assertEquals(Fmt.yr(-3300L), "3300 BCE")
    assertEquals(Fmt.yr(-1L), "1 BCE")
  }

  test("yr formats CE years") {
    assertEquals(Fmt.yr(1L), "1 CE")
    assertEquals(Fmt.yr(2026L), "2026 CE")
  }

  test("yr formats year zero as 1 BCE (no year zero in historical reckoning)") {
    assertEquals(Fmt.yr(0L), "1 BCE")
  }

  test("yr formats Option[Long]") {
    assertEquals(Fmt.yr(Some(-3300L)), "3300 BCE")
    assertEquals(Fmt.yr(None: Option[Long]), "?")
  }

  // ── periodStr ─────────────────────────────────────────────────────────────────

  test("periodStr renders a range") {
    assertEquals(Fmt.periodStr(Some(-3300L), Some(-1200L)), "3300 BCE–1200 BCE")
  }

  test("periodStr renders a point") {
    assertEquals(Fmt.periodStr(Some(-776L), Some(-776L)), "776 BCE")
  }

  test("periodStr returns empty for missing bounds") {
    assertEquals(Fmt.periodStr(None, Some(-1200L)), "")
    assertEquals(Fmt.periodStr(None, None), "")
  }

  // ── color ─────────────────────────────────────────────────────────────────────

  test("color at 0 returns the first viridis stop") {
    assertEquals(Fmt.color(0.0), "rgb(68,1,84)")
  }

  test("color at 1 returns the last viridis stop") {
    assertEquals(Fmt.color(1.0), "rgb(253,231,37)")
  }

  test("color clamps values below 0") {
    assertEquals(Fmt.color(-5.0), Fmt.color(0.0))
  }

  test("color clamps values above 1") {
    assertEquals(Fmt.color(10.0), Fmt.color(1.0))
  }

  test("color at 0.5 is the middle viridis stop") {
    assertEquals(Fmt.color(0.5), "rgb(33,145,140)")
  }

  test("color interpolates between stops") {
    val c = Fmt.color(0.25)
    assert(c.startsWith("rgb("), s"expected rgb(...) but got $c")
    assert(c != Fmt.color(0.0) && c != Fmt.color(0.5), "should be between stops")
  }

  // ── desaturate ────────────────────────────────────────────────────────────────

  test("desaturate at high confidence returns the input color") {
    val c = "rgb(33,145,140)"
    assertEquals(Fmt.desaturate(c, Confidence.High), c)
  }

  test("desaturate at contested confidence shifts toward grey") {
    val original = Fmt.color(0.5) // rgb(33,145,140)
    val desat = Fmt.desaturate(original, Confidence.Contested)
    assert(desat != original, "contested should desaturate")
    assert(desat.startsWith("rgb("), s"expected rgb format but got $desat")
  }

  test("desaturate at medium confidence desaturates partially") {
    val c = "rgb(100,200,50)"
    val desat = Fmt.desaturate(c, Confidence.Medium)
    assert(desat != c, "medium should desaturate")
    assert(desat.startsWith("rgb("), s"expected rgb format but got $desat")
  }

  // ── worstConf ─────────────────────────────────────────────────────────────────

  test("worstConf returns the lowest confidence") {
    assertEquals(Fmt.worstConf(List(Confidence.High, Confidence.Medium, Confidence.Low)), Confidence.Low)
    assertEquals(Fmt.worstConf(List(Confidence.High, Confidence.Contested)), Confidence.Contested)
  }

  test("worstConf of all high returns high") {
    assertEquals(Fmt.worstConf(List(Confidence.High, Confidence.High)), Confidence.High)
  }

  test("worstConf of empty is high") {
    assertEquals(Fmt.worstConf(Nil: List[Confidence]), Confidence.High)
  }

  // ── parseYear ─────────────────────────────────────────────────────────────────

  test("parseYear handles BCE") {
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("3300 BCE")), Some(-3300L))
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("3300 BC")), Some(-3300L))
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("3300bce")), Some(-3300L))
  }

  test("parseYear handles CE") {
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("500 CE")), Some(500L))
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("500 AD")), Some(500L))
  }

  test("parseYear handles plain numbers") {
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("-3300")), Some(-3300L))
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("2026")), Some(2026L))
  }

  test("parseYear handles circa") {
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("circa 3300 BCE")), Some(-3300L))
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("c. 1200 BCE")), Some(-1200L))
  }

  test("parseYear rejects garbage") {
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("hello")), None)
    assertEquals(Fmt.parseYear(com.histmersion.api.Years.Text("")), None)
  }

  // ── esc ───────────────────────────────────────────────────────────────────────

  test("esc escapes HTML special characters") {
    assertEquals(Fmt.esc("""<script>alert("xss")</script>"""), "&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;")
  }

  test("esc handles ampersand and apostrophe") {
    assertEquals(Fmt.esc("A & B's"), "A &amp; B&#39;s")
  }

  // ── clip ──────────────────────────────────────────────────────────────────────

  test("clip returns short text unchanged") {
    assertEquals(Fmt.clip("hello world", com.histmersion.api.Markdown.CharLimit(100)), "hello world")
  }

  test("clip truncates at word boundary") {
    val result = Fmt.clip("one two three four five six seven eight", com.histmersion.api.Markdown.CharLimit(20))
    assert(result.endsWith("…"), s"should end with ellipsis: $result")
    assert(result.length <= 22, s"should be short: $result")
  }

  // ── display ──────────────────────────────────────────────────────────────────

  test("display replaces underscores with spaces") {
    assertEquals(Fmt.display(com.histmersion.api.UiLabels.Text("bronze_age_collapse")), "bronze age collapse")
  }

  test("display leaves human-readable strings unchanged") {
    assertEquals(Fmt.display(com.histmersion.api.UiLabels.Text("already nice")), "already nice")
  }

  test("display handles empty string") {
    assertEquals(Fmt.display(com.histmersion.api.UiLabels.Text("")), "")
  }

  test("display handles multiple underscores") {
    assertEquals(Fmt.display(com.histmersion.api.UiLabels.Text("a_b_c_d")), "a b c d")
  }

  test("displayTag delegates to display") {
    assertEquals(Fmt.displayTag(com.histmersion.web.Fmt.DisplayTagS("some_tag")), Fmt.display(com.histmersion.api.UiLabels.Text("some_tag")))
  }
