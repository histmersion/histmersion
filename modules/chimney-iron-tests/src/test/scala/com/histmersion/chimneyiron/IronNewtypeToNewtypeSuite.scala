package com.histmersion
package chimneyiron

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import com.histmersion.chimneyiron.domain.Citekey
import com.histmersion.chimneyiron.domain.Source
import com.histmersion.chimneyiron.domain.Title
import com.histmersion.chimneyiron.domain.Tag
import com.histmersion.chimneyiron.domain.Period
import com.histmersion.chimneyiron.domain.Year
import com.histmersion.chimneyiron.domain.SourceStatus
import com.histmersion.chimneyiron.api.SourceView
import com.histmersion.chimneyiron.api.PeriodView
import com.histmersion.chimneyiron.api.SourceStatusView
import com.histmersion.chimneyiron.plain.Entry
import com.histmersion.chimneyiron.plain.Score
import com.histmersion.chimneyiron.plain.EntryView
import com.histmersion.chimneyiron.plain.ScoreView
import com.histmersion.chimneyiron.domain.Excerpt
import com.histmersion.chimneyiron.domain.Note
import com.histmersion.chimneyiron.api.ExcerptView
import com.histmersion.chimneyiron.api.NoteView
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class IronNewtypeToNewtypeSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def citekey(raw: String): Citekey = Citekey.applyUnsafe(raw)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def apiCitekey(raw: String): api.Citekey = api.Citekey.applyUnsafe(raw)

  private val source = Source(
    citekey = citekey("anthony2007"),
    title = Title.applyUnsafe("The Horse, the Wheel, and Language"),
    tags = List(Tag.applyUnsafe("steppe"), Tag.applyUnsafe("pie")),
    period = Some(Period(Year(-4500), Year(-2500))),
    status = SourceStatus.Contested(by = citekey("heyd2017")),
    citedBy = Vector(citekey("reich2018"), citekey("kristiansen2017")),
    replication = Some(citekey("haak2015"))
  )

  private val expectedView = SourceView(
    citekey = apiCitekey("anthony2007"),
    title = api.Title.applyUnsafe("The Horse, the Wheel, and Language"),
    tags = List(api.Tag.applyUnsafe("steppe"), api.Tag.applyUnsafe("pie")),
    period = Some(PeriodView(api.Year(-4500), api.Year(-2500))),
    status = SourceStatusView.Contested(by = apiCitekey("heyd2017")),
    citedBy = List(apiCitekey("reich2018"), apiCitekey("kristiansen2017")),
    replication = Some(apiCitekey("haak2015"))
  )

  test("a case class of newtypes, nested in Options, Lists and an enum, derives with no per-pair givens") {
    assertEquals(source.transformInto[SourceView], expectedView)
  }

  test("the partial derivation takes the same unchecked conversion") {
    assertEquals(source.transformIntoPartial[SourceView].asOption, Some(expectedView))
  }

  test("a newtype converts on its own, at the top level") {
    assertEquals(citekey("anthony2007").transformInto[api.Citekey], apiCitekey("anthony2007"))
  }

  test("no constraint runs while converting: the counted constraint's check count does not move") {
    val checksBefore = NonBlankCounted.checksRun.get()
    val view = source.transformInto[SourceView]
    val partialView = source.transformIntoPartial[SourceView]
    assertEquals(NonBlankCounted.checksRun.get(), checksBefore)
    assertEquals(view.tags, expectedView.tags)
    assertEquals(partialView.asOption, Some(expectedView))
    api.Title.applyUnsafe("checked once")
    assertEquals(NonBlankCounted.checksRun.get(), checksBefore + 1)
  }

  test("a Transformer given for the pair wins over the extension") {
    given Transformer[Citekey, api.Citekey] = key => api.Citekey.applyUnsafe(s"${key.value}-renamed")
    assertEquals(citekey("anthony2007").transformInto[api.Citekey], apiCitekey("anthony2007-renamed"))
  }

  test("newtypes of the same name with different constraints fail to compile, naming both constraints") {
    val errors = compileErrors("citekey(\"anthony2007\").transformInto[mismatched.Citekey]")
    assert(errors.contains("CitekeyRule"), errors)
    assert(errors.contains("MinLength[3]"), errors)
    assert(errors.contains("different constraints"), errors)
    val nestedErrors = compileErrors(
      "domain.Excerpt(citekey(\"anthony2007\"), domain.Note(\"x\")).transformInto[mismatched.Reference]"
    )
    assert(nestedErrors.contains("MinLength[3]"), nestedErrors)
  }

  private val clientView = client.SourceView(
    citekey = client.Citekey("anthony2007"),
    title = client.Title("The Horse, the Wheel, and Language"),
    tags = List(client.Tag("steppe"), client.Tag("pie")),
    period = Some(client.PeriodView(client.Year(-4500), client.Year(-2500))),
    status = client.SourceStatusView.Contested(by = client.Citekey("heyd2017")),
    citedBy = List(client.Citekey("reich2018"), client.Citekey("kristiansen2017")),
    replication = Some(client.Citekey("haak2015"))
  )

  test("into a client's copy that judges nothing (Pure), every newtype converts without a check, total") {
    val checksBefore = NonBlankCounted.checksRun.get()
    assertEquals(source.transformInto[client.SourceView], clientView)
    assertEquals(NonBlankCounted.checksRun.get(), checksBefore)
  }

  test("from a client's copy, the partial derivation checks each value with the target's constraint") {
    assertEquals(clientView.transformIntoPartial[Source].asOption, Some(source))
    val bad = clientView.copy(citekey = client.Citekey("Not A Key"), tags = List(client.Tag(" ")))
    val errors = bad.transformIntoPartial[Source].asErrorPathMessageStrings.toList
    assertEquals(errors.map(_._1), List("citekey", "tags(0)"))
    assert(errors.head._2.contains("[a-z][a-z0-9_-]*"), errors.toString)
    assertEquals(errors(1)._2, "must not be blank")
  }

  test("from a client's copy, the total derivation does not compile: a value that judged nothing must be checked") {
    val errors = compileErrors("client.Citekey(\"x\").transformInto[domain.Citekey]")
    assert(errors.contains("transformIntoPartial"), errors)
  }

  test("newtypes of different names do not convert, even over the same constraint") {
    val sameConstraintErrors = compileErrors(
      "domain.Title.applyUnsafe(\"x\").transformInto[api.Heading]"
    )
    assert(sameConstraintErrors.contains("not supported in Chimney"), sameConstraintErrors)
    val pureIntErrors = compileErrors("domain.Year(-3300).transformInto[domain.PageSize]")
    assert(pureIntErrors.contains("not supported in Chimney"), pureIntErrors)
  }

  test("a newtype does not silently become its base type, nor the base type a newtype") {
    assert(compileErrors("citekey(\"anthony2007\").transformInto[String]").contains("not supported in Chimney"))
    assert(compileErrors("\"anthony2007\".transformInto[api.Citekey]").contains("not supported in Chimney"))
  }

  test("transforms without Iron types derive as before") {
    val entry = Entry("anthony", Score(7), List("da"), Some(0.5))
    assertEquals(
      entry.transformInto[EntryView],
      EntryView("anthony", ScoreView(7), Vector("da"), Some(0.5))
    )
    val excerpt = Excerpt(citekey("anthony2007"), Note("steppe"))
    assertEquals(
      excerpt.transformInto[ExcerptView],
      ExcerptView(apiCitekey("anthony2007"), NoteView("steppe"))
    )
  }
