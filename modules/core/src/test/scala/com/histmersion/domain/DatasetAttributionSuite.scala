package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.media.{DataCredit, DataCredits}
import com.histmersion.api.media.MediaItem
import com.histmersion.lint.{tolerated, LintRule}

/** The datasets' credits read as the reuse they allow: the stated licence classified, its URL read when the statement
  * names none, the credit line kept, and what the credits page shows unchanged.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class DatasetAttributionSuite extends munit.FunSuite:

  private def credit(licence: DataCredit.Licence, url: Option[DataCredit.LicenceUrl]) = DataCredit(
    DataCredit.Name("Zq"),
    DataCredit.UsedFor("zq"),
    licence,
    url,
    MediaItem.Credit("© Zq"),
    DataCredit.Url("https://zq")
  )

  test("a stated Creative Commons licence is classified; the credit line and URL are kept"):
    val a = Attribution.ofDataset(
      credit(
        DataCredit.Licence("CC BY 4.0"),
        Some(DataCredit.LicenceUrl("https://creativecommons.org/licenses/by/4.0/"))
      )
    )
    assertEquals(a.licence.family, Licence.Family.CcBy)
    assertEquals(a.credit, "© Zq")
    assertEquals(a.licenceUrl, Some("https://creativecommons.org/licenses/by/4.0/"))
    assertEquals(a.author, None)

  test("a statement naming no known licence is read from its Creative Commons URL"):
    val a = Attribution.ofDataset(
      credit(
        DataCredit.Licence("CC0 unless a feature says otherwise"),
        Some(DataCredit.LicenceUrl(Attribution.Cc0Url.value))
      )
    )
    assertEquals(a.licence.family, Licence.Family.Cc0)

  test("a licence neither the statement nor the URL names stays unknown, the statement kept"):
    val a = Attribution.ofDataset(
      credit(
        DataCredit.Licence("ODbL 1.0"),
        Some(DataCredit.LicenceUrl("https://opendatacommons.org/licenses/odbl/1-0/"))
      )
    )
    assertEquals(a.licence.family, Licence.Family.Unknown)
    assertEquals(
      Attribution.ofDataset(credit(DataCredit.Licence("public domain"), None)).licence.family,
      Licence.Family.PublicDomain
    )

  test("every credit the page shows reads as an attribution with its own credit line"):
    val all = DataCredits.all.map(c => c -> Attribution.ofDataset(c))
    assert(all.nonEmpty)
    all.foreach((c, a) =>
      assertEquals((a.credit.value, a.licenceUrl.map(_.value)), (c.credit.value, c.licenceUrl.map(_.value)), c.name)
    )
    assert(all.exists(_._2.licence.family == Licence.Family.CcBy), all.map((c, a) => c.name -> a.licence).toString)
