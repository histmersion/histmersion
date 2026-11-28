package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ManagerDetail(
    manager: ManagerInfo,
    current: List[ManagerItem] = Nil,
    hints: List[ManagerHint] = Nil,
    events: List[ManagerEvent] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
