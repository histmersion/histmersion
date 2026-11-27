package com.histmersion
package server.media

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.ImportsControllers

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.media.{MediaImportReport, MediaImportRequest, MediaItem, MediaProvider}
import com.histmersion.api.library.RepairAcquire
import com.histmersion.pluginhost.Polite
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Event
import com.histmersion.lint.{tolerated, LintRule}

/** #1491: what a running media import job says — `n/total` asked — and which requests name ONE place or event (the only
  * real import the synchronous endpoint still runs). Pure.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MediaImportProgressSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val r =
    MediaImportReport(
      MediaItem.Provider(MediaProvider.Commons),
      dryRun = RepairAcquire.RepairRequest.DryRun(false),
      targets = MediaImportReport.Targets(100),
      stored = MediaImportReport.Stored(80),
      linked = MediaImportReport.Linked(82),
      batches = MediaImportReport.Batches(2)
    )

  test("progress: n/total asked, the batch, what was written, the requests"):
    assertEquals(
      ImportsControllers.progressLine(r, ImportsControllers.TotalCount(300), Polite.RequestCount(12)),
      "100/300 place(s)/event(s) asked · batch 2 · 80 stored · 82 linked · 12 request(s)"
    )

  test("progress: failures are counted; an unknown total is left out; a total passed is the count"):
    val failed = r.modify(_.failed).setTo(List(MediaImportReport.Failed("a"), MediaImportReport.Failed("b")))
    assert(
      ImportsControllers
        .progressLine(failed, ImportsControllers.TotalCount(300), Polite.RequestCount(1))
        .value
        .contains(" · 2 failed · ")
    )
    assert(
      ImportsControllers
        .progressLine(r, ImportsControllers.TotalCount(0), Polite.RequestCount(1))
        .value
        .startsWith("100 place(s)/event(s) asked")
    )
    assert(
      ImportsControllers
        .progressLine(r, ImportsControllers.TotalCount(40), Polite.RequestCount(1))
        .value
        .startsWith("100/100 ")
    )

  test("single: a place or an event, not blanks"):
    assert(ImportsControllers.single(MediaImportRequest(place = Some(Place.Id("pleiades_1")))))
    assert(ImportsControllers.single(MediaImportRequest(event = Some(Event.Id("kadesh")))))
    assert(!ImportsControllers.single(MediaImportRequest(place = Some(Place.Id("  ")))))
    assert(!ImportsControllers.single(MediaImportRequest()))
