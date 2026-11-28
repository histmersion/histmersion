package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Validator
import sttp.tapir.Schema.annotations.description
import sttp.tapir.Schema.annotations.validateEach

final case class ColdReaderAnswer(
    @description("PASS | MINOR | MAJOR — the worst of your findings") verdict: ColdReaderAnswer.Verdict,
    @description("Five plain sentences: the question, the answer, who disagrees with whom, what is open") explainBack: ColdReaderAnswer.ExplainBack,
    @description("The slot names you could not fill from the text; [] when all four are filled")
    @validateEach(Validator.enumeration(ColdReaderAnswer.Slots)) emptySlots: List[ColdReaderAnswer.EmptySlots] = Nil,
    findings: List[ReaderFindingAnswer] = Nil,
    termsUndefined: ColdReaderAnswer.TermsUndefined,
    opaqueSentences: ColdReaderAnswer.OpaqueSentences,
    @description("The section where you stopped following; null if you never did") lostAt: Option[ColdReaderAnswer.LostAt] = None,
    @description("One check per earlier finding of yours that the review ledger lists as open (empty when it lists none)") checks: List[CheckAnswer] = Nil
) derives JsonCodec, KindlingsSchema, Schema

object ColdReaderAnswer:
  export PanelVerdict.Wire as Verdict
  type ExplainBack = ExplainBack.T
  object ExplainBack extends Newtype[String, Pure]
  type TermsUndefined = TermsUndefined.T
  object TermsUndefined extends Newtype[Int, Pure]
  type OpaqueSentences = OpaqueSentences.T
  object OpaqueSentences extends Newtype[Int, Pure]
  type LostAt = LostAt.T
  object LostAt extends Newtype[String, Pure]
  type EmptySlots = EmptySlots.ExplainSlotsValue
  object EmptySlots:
    type ExplainSlotsValue = ExplainSlotsValue.T
    object ExplainSlotsValue extends Newtype[String, Pure]
  val Slots: List[EmptySlots] = List(
    EmptySlots.ExplainSlotsValue("question"),
    EmptySlots.ExplainSlotsValue("answer"),
    EmptySlots.ExplainSlotsValue("disagreement"),
    EmptySlots.ExplainSlotsValue("open")
  )
