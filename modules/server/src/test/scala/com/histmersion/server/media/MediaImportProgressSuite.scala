package com.histmersion.server.media

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.ImportsControllers

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.media.{MediaImportReport, MediaImportRequest, MediaItem, MediaProvider}

/** #1491: what a running media import job says — `n/total` asked — and which requests name ONE place or event (the only
  * real import the synchronous endpoint still runs). Pure.
  */
class MediaImportProgressSuite extends munit.FunSuite:

  private val r =
    MediaImportReport(MediaItem.Provider(MediaProvider.Commons), dryRun = com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun(false), targets = com.histmersion.api.media.MediaImportReport.Targets(100), stored = MediaImportReport.Stored(80), linked = MediaImportReport.Linked(82), batches = MediaImportReport.Batches(2))

  test("progress: n/total asked, the batch, what was written, the requests"):
    assertEquals(
      ImportsControllers.progressLine(r, com.histmersion.domain.media.imports.ImportsControllers.TotalCount(300), com.histmersion.pluginhost.Polite.RequestCount(12)),
      "100/300 place(s)/event(s) asked · batch 2 · 80 stored · 82 linked · 12 request(s)"
    )

  test("progress: failures are counted; an unknown total is left out; a total passed is the count"):
    val failed = r.modify(_.failed).setTo(List(MediaImportReport.Failed("a"), MediaImportReport.Failed("b")))
    assert(ImportsControllers.progressLine(failed, com.histmersion.domain.media.imports.ImportsControllers.TotalCount(300), com.histmersion.pluginhost.Polite.RequestCount(1)).value.contains(" · 2 failed · "))
    assert(ImportsControllers.progressLine(r, com.histmersion.domain.media.imports.ImportsControllers.TotalCount(0), com.histmersion.pluginhost.Polite.RequestCount(1)).value.startsWith("100 place(s)/event(s) asked"))
    assert(ImportsControllers.progressLine(r, com.histmersion.domain.media.imports.ImportsControllers.TotalCount(40), com.histmersion.pluginhost.Polite.RequestCount(1)).value.startsWith("100/100 "))

  test("single: a place or an event, not blanks"):
    assert(ImportsControllers.single(MediaImportRequest(place = Some(com.histmersion.api.catalogue.Place.Id("pleiades_1")))))
    assert(ImportsControllers.single(MediaImportRequest(event = Some(com.histmersion.api.catalogue.Event.Id("kadesh")))))
    assert(!ImportsControllers.single(MediaImportRequest(place = Some(com.histmersion.api.catalogue.Place.Id("  ")))))
    assert(!ImportsControllers.single(MediaImportRequest()))
