package com.histmersion
package domain.catalogue.places

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.catalogue.SiteTypes
import com.histmersion.domain.DeepTime
import com.histmersion.util.database.TestPermits.given
import com.histmersion.lint.{tolerated, LintRule}

/** The contract the refinement of site types keeps: the Wikidata sites on the map not asked yet, by id, with their item
  * and class; asked ones left out, unless refreshed and asked before the run began.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
abstract class SiteTypeServiceContract extends CatalogueStoreSuite:
  stest("the sites to ask, asked, and asked again on a refresh") { s =>
    val t = s.siteTypes
    s.site("wikidata_q2", onMap = true, Some("Q123"))
    s.site("wikidata_q1", onMap = true, None)
    s.site("wikidata_q3", onMap = false, None)
    s.site("pleiades_1", onMap = true, None)
    assertEquals(
      t.sitesToType(PageSize(10), refresh = SiteTypeService.Refresh.Reuse, None),
      List(("wikidata_q1", "Q1", None), ("wikidata_q2", "Q2", Some("Q123")))
    )
    assertEquals(
      t.sitesToType(PageSize(1), refresh = SiteTypeService.Refresh.Reuse, None).map(_._1),
      List("wikidata_q1")
    )
    assertEquals(t.countSitesToType(refresh = SiteTypeService.Refresh.Reuse, None), 2)
    t.setSiteTypes(List(Place.Id("wikidata_q1") -> Some(SiteTypes.SiteType.Id("palace"))))
    assertEquals(
      t.sitesToType(PageSize(10), refresh = SiteTypeService.Refresh.Reuse, None).map(_._1),
      List("wikidata_q2")
    )
    assertEquals(t.countSitesToType(refresh = SiteTypeService.Refresh.Refresh, None), 2, "a refresh asks every one")
    assertEquals(
      t.countSitesToType(refresh = SiteTypeService.Refresh.Refresh, Some(DeepTime.SignedYear(0L))),
      1,
      "asked since the run began: not again"
    )
  }
