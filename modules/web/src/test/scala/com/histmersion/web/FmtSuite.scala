package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.Wcag
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Confidence
import com.histmersion.api.Years
import com.histmersion.api.Markdown
import com.histmersion.api.UiLabels

class FmtSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  // ── yr ────────────────────────────────────────────────────────────────────────

  test("yr formats BCE years") {
    assertEquals(Fmt.yr(Year(-3300L)).value, "3300 BCE")
    assertEquals(Fmt.yr(Year(-1L)).value, "1 BCE")
  }

  test("yr formats CE years") {
    assertEquals(Fmt.yr(Year(1L)).value, "1 CE")
    assertEquals(Fmt.yr(Year(2026L)).value, "2026 CE")
  }

  test("yr formats year zero as 1 BCE (no year zero in historical reckoning)") {
    assertEquals(Fmt.yr(Year(0L)).value, "1 BCE")
  }

  test("yr formats Option[Long]") {
    assertEquals(Fmt.yr(Some(Year(-3300L))).value, "3300 BCE")
    assertEquals(Fmt.yr(None: Option[Year]).value, "?")
  }

  // ── periodStr ─────────────────────────────────────────────────────────────────

  test("periodStr renders a range") {
    assertEquals(Fmt.periodStr(Some(Year(-3300L)), Some(Year(-1200L))).value, "3300 BCE–1200 BCE")
  }

  test("periodStr renders a point") {
    assertEquals(Fmt.periodStr(Some(Year(-776L)), Some(Year(-776L))).value, "776 BCE")
  }

  test("periodStr returns empty for missing bounds") {
    assertEquals(Fmt.periodStr(None, Some(Year(-1200L))).value, "")
    assertEquals(Fmt.periodStr(None, None).value, "")
  }

  // ── color ─────────────────────────────────────────────────────────────────────

  test("color at 0 returns the first viridis stop") {
    assertEquals(Fmt.color(Fmt.ColorT(0.0)).value, "rgb(68,1,84)")
  }

  test("color at 1 returns the last viridis stop") {
    assertEquals(Fmt.color(Fmt.ColorT(1.0)).value, "rgb(253,231,37)")
  }

  test("color clamps values below 0") {
    assertEquals(Fmt.color(Fmt.ColorT(-5.0)), Fmt.color(Fmt.ColorT(0.0)))
  }

  test("color clamps values above 1") {
    assertEquals(Fmt.color(Fmt.ColorT(10.0)), Fmt.color(Fmt.ColorT(1.0)))
  }

  test("color at 0.5 is the middle viridis stop") {
    assertEquals(Fmt.color(Fmt.ColorT(0.5)).value, "rgb(33,145,140)")
  }

  test("color interpolates between stops") {
    val c = Fmt.color(Fmt.ColorT(0.25))
    assert(c.value.startsWith("rgb("), s"expected rgb(...) but got $c")
    assert(c != Fmt.color(Fmt.ColorT(0.0)) && c != Fmt.color(Fmt.ColorT(0.5)), "should be between stops")
  }

  // ── desaturate ────────────────────────────────────────────────────────────────

  test("desaturate at high confidence returns the input color") {
    val c = Wcag.Hex("rgb(33,145,140)")
    assertEquals(Fmt.desaturate(c, Confidence.High), c)
  }

  test("desaturate at contested confidence shifts toward grey") {
    val original = Fmt.color(Fmt.ColorT(0.5)) // rgb(33,145,140)
    val desat = Fmt.desaturate(original, Confidence.Contested)
    assert(desat != original, "contested should desaturate")
    assert(desat.value.startsWith("rgb("), s"expected rgb format but got $desat")
  }

  test("desaturate at medium confidence desaturates partially") {
    val c = Wcag.Hex("rgb(100,200,50)")
    val desat = Fmt.desaturate(c, Confidence.Medium)
    assert(desat != c, "medium should desaturate")
    assert(desat.value.startsWith("rgb("), s"expected rgb format but got $desat")
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
    assertEquals(Fmt.parseYear(Years.Text("3300 BCE")).map(_.value), Some(-3300L))
    assertEquals(Fmt.parseYear(Years.Text("3300 BC")).map(_.value), Some(-3300L))
    assertEquals(Fmt.parseYear(Years.Text("3300bce")).map(_.value), Some(-3300L))
  }

  test("parseYear handles CE") {
    assertEquals(Fmt.parseYear(Years.Text("500 CE")).map(_.value), Some(500L))
    assertEquals(Fmt.parseYear(Years.Text("500 AD")).map(_.value), Some(500L))
  }

  test("parseYear handles plain numbers") {
    assertEquals(Fmt.parseYear(Years.Text("-3300")).map(_.value), Some(-3300L))
    assertEquals(Fmt.parseYear(Years.Text("2026")).map(_.value), Some(2026L))
  }

  test("parseYear handles circa") {
    assertEquals(Fmt.parseYear(Years.Text("circa 3300 BCE")).map(_.value), Some(-3300L))
    assertEquals(Fmt.parseYear(Years.Text("c. 1200 BCE")).map(_.value), Some(-1200L))
  }

  test("parseYear rejects garbage") {
    assertEquals(Fmt.parseYear(Years.Text("hello")), None)
    assertEquals(Fmt.parseYear(Years.Text("")), None)
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
    assertEquals(Fmt.clip("hello world", Markdown.CharLimit(100)), "hello world")
  }

  test("clip truncates at word boundary") {
    val result = Fmt.clip("one two three four five six seven eight", Markdown.CharLimit(20))
    assert(result.endsWith("…"), s"should end with ellipsis: $result")
    assert(result.length <= 22, s"should be short: $result")
  }

  // ── display ──────────────────────────────────────────────────────────────────

  test("display replaces underscores with spaces") {
    assertEquals(Fmt.display(UiLabels.Text("bronze_age_collapse")).value, "bronze age collapse")
  }

  test("display leaves human-readable strings unchanged") {
    assertEquals(Fmt.display(UiLabels.Text("already nice")).value, "already nice")
  }

  test("display handles empty string") {
    assertEquals(Fmt.display(UiLabels.Text("")).value, "")
  }

  test("display handles multiple underscores") {
    assertEquals(Fmt.display(UiLabels.Text("a_b_c_d")).value, "a b c d")
  }

  test("displayTag delegates to display") {
    assertEquals(Fmt.displayTag(Fmt.DisplayTagS("some_tag")), Fmt.display(UiLabels.Text("some_tag")))
  }
