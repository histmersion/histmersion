package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.customise

@customise(_.name(SName("Findings")))
final case class ProposedFindings(findings: List[ProposedFinding] = Nil) derives JsonCodec, KindlingsSchema, Schema
