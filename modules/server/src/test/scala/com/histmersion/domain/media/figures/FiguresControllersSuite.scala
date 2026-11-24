package com.histmersion
package domain.media.figures

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** The licence fill's controller: a dry run always answers; writing is refused while the maintenance lock is held. */
class FiguresControllersSuite extends munit.FunSuite:

  test("a dry run answers under the lock; the fill itself does not"):
    val gate = WriteGate()
    val c = FiguresControllers(FigureService.InMemory(), gate)
    val held = gate.exclusively(WriteGate.OperationName("restore"))(
      Right((c.fillLicences(apply = FigureService.Apply(false)), c.fillLicences(apply = FigureService.Apply(true))))
    )
    val (dry, wet) = held.fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(dry.isRight && wet.isLeft, (dry, wet))
