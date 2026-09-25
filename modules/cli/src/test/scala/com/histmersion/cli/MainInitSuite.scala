package com.histmersion.cli

/** Guards object-initialisation order in `Main`: a `val` declared after the command that reads it
  * is null at init time (merge of streams 2.3/2.6 hit this). Touching the object runs `<clinit>`. */
class MainInitSuite extends munit.FunSuite:
  test("com.histmersion.cli.Main initialises (no forward val references)"):
    assert(Main.toString.nonEmpty)
