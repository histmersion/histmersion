package com.histmersion.domain.media.figures

import com.histmersion.util.data.maintenance.WriteGate

/** The licence fill's controller: a dry run always answers; writing is refused while the maintenance lock is held. */
class FiguresControllersSuite extends munit.FunSuite:

  test("a dry run answers under the lock; the fill itself does not"):
    val gate = WriteGate()
    val c = FiguresControllers(FigureService.InMemory(), gate)
    val held = gate.exclusively("restore")(Right((c.fillLicences(apply = false), c.fillLicences(apply = true))))
    val (dry, wet) = held.fold(e => fail(e.message), identity)
    assert(dry.isRight && wet.isLeft, (dry, wet))
