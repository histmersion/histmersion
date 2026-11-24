package com.histmersion
package db

import com.histmersion.domain.media.assets.{NewFigure, Reconciled}
import com.histmersion.util.database.given
import com.histmersion.lint.{LintRule, tolerated}

/** What a re-conversion does to the asset rows already stored (#826) — pure, no database. */
class AssetReconcileSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def name(raw: String): NewFigure.Name = NewFigure.Name(raw)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sha(digit: Char): Sha256 = Sha256.applyUnsafe(digit.toString * 64)

  test("reconcile: names first, then a moved sha256 claims a leftover row; the rest is deleted"):
    val r = Reconciled.of(
      existing = List(name("a") -> sha('1'), name("b") -> sha('2'), name("c") -> sha('3'), name("d") -> sha('2')),
      incoming = List(name("a") -> sha('9'), name("e") -> sha('2'), name("f") -> sha('2'), name("g") -> sha('7'))
    )
    assertEquals(
      r,
      Reconciled(renames = List(name("b") -> name("e"), name("d") -> name("f")), deletes = List(NewFigure.Name("c")))
    )
    assertEquals(Reconciled.of(List(name("a") -> sha('1')), List(name("a") -> sha('1'))), Reconciled(Nil, Nil))
    // a name match wins over a sha256 match elsewhere: `b` keeps its name even though `a`'s picture moved there
    assertEquals(
      Reconciled.of(List(name("a") -> sha('1'), name("b") -> sha('2')), List(name("b") -> sha('1'))),
      Reconciled(Nil, List(NewFigure.Name("a")))
    )
