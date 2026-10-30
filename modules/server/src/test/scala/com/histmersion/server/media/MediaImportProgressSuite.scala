package com.histmersion.server.media

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** #1491: what a running media import job says — `n/total` asked — and which requests name ONE place or event (the only
  * real import the synchronous endpoint still runs). Pure.
  */
class MediaImportProgressSuite extends munit.FunSuite:

  private val r =
    MediaImportReport(MediaProvider.Commons, dryRun = false, targets = 100, stored = 80, linked = 82, batches = 2)

  test("progress: n/total asked, the batch, what was written, the requests"):
    assertEquals(
      MediaHandlers.progressLine(r, 300, 12),
      "100/300 place(s)/event(s) asked · batch 2 · 80 stored · 82 linked · 12 request(s)"
    )

  test("progress: failures are counted; an unknown total is left out; a total passed is the count"):
    val failed = r.copy(failed = List("a", "b"))
    assert(MediaHandlers.progressLine(failed, 300, 1).contains(" · 2 failed · "))
    assert(MediaHandlers.progressLine(r, 0, 1).startsWith("100 place(s)/event(s) asked"))
    assert(MediaHandlers.progressLine(r, 40, 1).startsWith("100/100 "))

  test("single: a place or an event, not blanks"):
    assert(MediaHandlers.single(MediaImportRequest(place = Some("pleiades_1"))))
    assert(MediaHandlers.single(MediaImportRequest(event = Some("kadesh"))))
    assert(!MediaHandlers.single(MediaImportRequest(place = Some("  "))))
    assert(!MediaHandlers.single(MediaImportRequest()))
