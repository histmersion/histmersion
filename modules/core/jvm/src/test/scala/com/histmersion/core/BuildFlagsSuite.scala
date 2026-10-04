package com.histmersion.core

import java.nio.file.{Files, Path}

/** Compiler flags the build relies on to keep warnings from creeping back. */
class BuildFlagsSuite extends munit.FunSuite:

  private val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))

  // #879: E165 (a type test on a non-Matchable value) was an error only in `*Api.scala`, and the
  // warning had crept back into the YAML readers and the JDBC array codec
  test("a pattern match on a non-Matchable type is an error in EVERY source file"):
    val build = Files.readString(root.resolve("build.sbt"))
    assert(build.contains("\"-Wconf:id=E165:e\""), "build.sbt must keep -Wconf:id=E165:e, unscoped")
    assert(!build.contains("-Wconf:id=E165&src="), "the E165 error must not be narrowed to some files")
