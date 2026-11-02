package com.histmersion.db

import com.histmersion.domain.media.assets.Reconciled
import com.histmersion.util.database.given

/** What a re-conversion does to the asset rows already stored (#826) — pure, no database. */
class AssetReconcileSuite extends munit.FunSuite:

  test("reconcile: names first, then a moved sha256 claims a leftover row; the rest is deleted"):
    val r = Reconciled.of(
      existing = List("a" -> "s1", "b" -> "s2", "c" -> "s3", "d" -> "s2"),
      incoming = List("a" -> "s9", "e" -> "s2", "f" -> "s2", "g" -> "s7")
    )
    assertEquals(r, Reconciled(renames = List("b" -> "e", "d" -> "f"), deletes = List("c")))
    assertEquals(Reconciled.of(List("a" -> "s1"), List("a" -> "s1")), Reconciled(Nil, Nil))
    // a name match wins over a sha256 match elsewhere: `b` keeps its name even though `a`'s picture moved there
    assertEquals(
      Reconciled.of(List("a" -> "s1", "b" -> "s2"), List("b" -> "s1")),
      Reconciled(Nil, List("a"))
    )
