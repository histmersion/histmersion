package com.histmersion.domain.catalogue.places

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.catalogue.CatalogueStoreSuite

/** The contract the refinement of site types keeps: the Wikidata sites on the map not asked yet, by id, with their item
  * and class; asked ones left out, unless refreshed and asked before the run began.
  */
abstract class SiteTypeServiceContract extends CatalogueStoreSuite:
  stest("the sites to ask, asked, and asked again on a refresh") { s =>
    val t = s.siteTypes
    s.site("wikidata_q2", onMap = true, Some("Q123"))
    s.site("wikidata_q1", onMap = true, None)
    s.site("wikidata_q3", onMap = false, None)
    s.site("pleiades_1", onMap = true, None)
    assertEquals(
      t.sitesToType(com.histmersion.PageSize(10), refresh = com.histmersion.domain.catalogue.places.SiteTypeService.Refresh(false), None),
      List(("wikidata_q1", "Q1", None), ("wikidata_q2", "Q2", Some("Q123")))
    )
    assertEquals(t.sitesToType(com.histmersion.PageSize(1), refresh = com.histmersion.domain.catalogue.places.SiteTypeService.Refresh(false), None).map(_._1), List("wikidata_q1"))
    assertEquals(t.countSitesToType(refresh = com.histmersion.domain.catalogue.places.SiteTypeService.Refresh(false), None), 2)
    t.setSiteTypes(List("wikidata_q1" -> Some("palace")))
    assertEquals(t.sitesToType(com.histmersion.PageSize(10), refresh = com.histmersion.domain.catalogue.places.SiteTypeService.Refresh(false), None).map(_._1), List("wikidata_q2"))
    assertEquals(t.countSitesToType(refresh = com.histmersion.domain.catalogue.places.SiteTypeService.Refresh(true), None), 2, "a refresh asks every one")
    assertEquals(t.countSitesToType(refresh = com.histmersion.domain.catalogue.places.SiteTypeService.Refresh(true), Some(com.histmersion.domain.DeepTime.SignedYear(0L))), 1, "asked since the run began: not again")
  }
