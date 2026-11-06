package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.media.{DataCredit, DataCredits}

/** The datasets' credits read as the reuse they allow: the stated licence classified, its URL read when the statement
  * names none, the credit line kept, and what the credits page shows unchanged.
  */
class DatasetAttributionSuite extends munit.FunSuite:

  private def credit(licence: String, url: Option[String]) = DataCredit(com.histmersion.api.media.DataCredit.Name("Zq"), com.histmersion.api.media.DataCredit.UsedFor("zq"), licence, url, com.histmersion.api.media.MediaItem.Credit("© Zq"), com.histmersion.api.media.DataCredit.Url("https://zq"))

  test("a stated Creative Commons licence is classified; the credit line and URL are kept"):
    val a = Attribution.ofDataset(credit("CC BY 4.0", Some("https://creativecommons.org/licenses/by/4.0/")))
    assertEquals(a.licence.family, Licence.Family.CcBy)
    assertEquals(a.credit, "© Zq")
    assertEquals(a.licenceUrl, Some("https://creativecommons.org/licenses/by/4.0/"))
    assertEquals(a.author, None)

  test("a statement naming no known licence is read from its Creative Commons URL"):
    val a = Attribution.ofDataset(credit("CC0 unless a feature says otherwise", Some(Attribution.Cc0Url)))
    assertEquals(a.licence.family, Licence.Family.Cc0)

  test("a licence neither the statement nor the URL names stays unknown, the statement kept"):
    val a = Attribution.ofDataset(credit("ODbL 1.0", Some("https://opendatacommons.org/licenses/odbl/1-0/")))
    assertEquals(a.licence.family, Licence.Family.Unknown)
    assertEquals(Attribution.ofDataset(credit("public domain", None)).licence.family, Licence.Family.PublicDomain)

  test("every credit the page shows reads as an attribution with its own credit line"):
    val all = DataCredits.all.map(c => c -> Attribution.ofDataset(c))
    assert(all.nonEmpty)
    all.foreach((c, a) => assertEquals((a.credit, a.licenceUrl), (c.credit, c.licenceUrl), c.name))
    assert(all.exists(_._2.licence.family == Licence.Family.CcBy), all.map((c, a) => c.name -> a.licence).toString)
