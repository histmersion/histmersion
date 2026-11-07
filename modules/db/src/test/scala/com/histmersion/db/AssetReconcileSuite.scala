package com.histmersion.db

import com.histmersion.domain.media.assets.{NewFigure, Reconciled}
import com.histmersion.util.database.given

/** What a re-conversion does to the asset rows already stored (#826) — pure, no database. */
class AssetReconcileSuite extends munit.FunSuite:

  private def name(raw: String): NewFigure.Name = NewFigure.Name(raw)
  private def sha(digit: Char): com.histmersion.Sha256 = com.histmersion.Sha256.applyUnsafe(digit.toString * 64)

  test("reconcile: names first, then a moved sha256 claims a leftover row; the rest is deleted"):
    val r = Reconciled.of(
      existing = List(name("a") -> sha('1'), name("b") -> sha('2'), name("c") -> sha('3'), name("d") -> sha('2')),
      incoming = List(name("a") -> sha('9'), name("e") -> sha('2'), name("f") -> sha('2'), name("g") -> sha('7'))
    )
    assertEquals(r, Reconciled(renames = List(name("b") -> name("e"), name("d") -> name("f")), deletes = List(com.histmersion.domain.media.assets.NewFigure.Name("c"))))
    assertEquals(Reconciled.of(List(name("a") -> sha('1')), List(name("a") -> sha('1'))), Reconciled(Nil, Nil))
    // a name match wins over a sha256 match elsewhere: `b` keeps its name even though `a`'s picture moved there
    assertEquals(
      Reconciled.of(List(name("a") -> sha('1'), name("b") -> sha('2')), List(name("b") -> sha('1'))),
      Reconciled(Nil, List(com.histmersion.domain.media.assets.NewFigure.Name("a")))
    )
