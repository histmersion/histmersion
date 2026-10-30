package com.histmersion.db

/** What a re-conversion does to the asset rows already stored (#826) — pure, no database. */
class AssetReconcileSuite extends munit.FunSuite:

  test("reconcile: names first, then a moved sha256 claims a leftover row; the rest is deleted"):
    val r = AssetRepo.reconcile(
      existing = List("a" -> "s1", "b" -> "s2", "c" -> "s3", "d" -> "s2"),
      incoming = List("a" -> "s9", "e" -> "s2", "f" -> "s2", "g" -> "s7")
    )
    assertEquals(r, AssetRepo.Reconciled(renames = List("b" -> "e", "d" -> "f"), deletes = List("c")))
    assertEquals(AssetRepo.reconcile(List("a" -> "s1"), List("a" -> "s1")), AssetRepo.Reconciled(Nil, Nil))
    // a name match wins over a sha256 match elsewhere: `b` keeps its name even though `a`'s picture moved there
    assertEquals(
      AssetRepo.reconcile(List("a" -> "s1", "b" -> "s2"), List("b" -> "s1")),
      AssetRepo.Reconciled(Nil, List("a"))
    )
