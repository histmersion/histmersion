package com.histmersion.util.data.checks

import com.histmersion.api.Lint
import com.histmersion.api.Lint.*

/** The order of every owner's lint rules: each owner registers its rules, their reports and safe fixes, itself.
  */
object CheckRulesAdapter:

  /** Today's order: the report lists the rules as `Lint.all` does (the CLI help and the MCP tool name them so), and the
    * fixes run as `lint --fix` has always applied them.
    */
  val order: CheckOrder = CheckOrder(
    Lint.names,
    List(
      UnresolvedShortCitekeys,
      StaleChunks,
      FindingGeocode,
      FindingUnplaced,
      PlaceKindRule,
      PlaceGridPoint,
      PlaceImportedPointArea,
      ArticleCritiquePairing
    )
  )
