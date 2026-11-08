package com.histmersion
package cli

/** Guards object-initialisation order in `Cli`: a `val` declared after the command that reads it is null at init time
  * (merge of streams 2.3/2.6 hit this). Touching the object runs `<clinit>`.
  */
class MainInitSuite extends munit.FunSuite:
  test("com.histmersion.cli.Cli initialises (no forward val references)"):
    assert(Cli.toString.nonEmpty)
