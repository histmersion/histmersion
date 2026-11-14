package com.histmersion
package chimneyiron

class IronNewtypePairsSuite extends munit.FunSuite:

  test("names in compile errors drop package objects and scala.Predef") {
    assertEquals(
      IronNewtypePairs.withoutPackageObjects("com.histmersion.domain.domain$package$.Citekey"),
      "com.histmersion.domain.Citekey"
    )
    assertEquals(
      IronNewtypePairs.withoutPackageObjects("scala.Predef.String :| com.histmersion.constraints$package.CitekeyRule"),
      "String :| com.histmersion.CitekeyRule"
    )
    assertEquals(
      IronNewtypePairs.withoutPackageObjects("io.github.iltotore.iron.constraint.collection.MinLength[3]"),
      "io.github.iltotore.iron.constraint.collection.MinLength[3]"
    )
  }
