package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema.annotations.description

final case class FixerAnswer(
    @description("The changes to the article, in order, each anchored on text copied EXACTLY from the body \u2014 small and local, never the whole article; code applies them") edits: List[Article.Edits.EditAnswer] = Nil,
    @description("Ids of the findings you applied") applied: List[FixerAnswer.Applied] = Nil,
    @description("Findings you reject, each with the record's contradicting sentence") rejected: List[RejectionAnswer] = Nil,
    @description("Claims you moved to the unresolved section") unresolvedMoved: List[FixerAnswer.UnresolvedMoved] = Nil,
    @description("What the next review round should know") notes: FixerAnswer.Notes,
    @description("Why the article leaves out a source its filter returns, one per source (the generated Not used list shows them); an empty reason removes one. Only for sources of the filter the article does not cite") reasons: List[Coverage.Reason] = Nil,
    @description("Findings you decline, with the reason: a MINOR, or a coverage MAJOR when no filter is recorded") wontFix: List[WontFixAnswer] = Nil,
    @description("missing-critique findings you answer by setting the critique aside as not material \u2014 the reason is stored with the article and closes the finding (instead of an edit); with no id, a checklist pair your edits brought in") setAside: List[SetAsideAnswer] = Nil
) derives JsonCodec, KindlingsSchema, Schema

object FixerAnswer:
  export ReviewFinding.Id as Applied
  export Fix.{Notes, UnresolvedMoved}
