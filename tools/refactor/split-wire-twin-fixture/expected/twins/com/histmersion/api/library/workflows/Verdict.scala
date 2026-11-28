package com.histmersion
package api.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class Verdict(@description("a ratio") ratio: Verdict.Ratio, @description("at most 3 labels") label: Verdict.Label, @description("pick one") note: Verdict.Remark, reasons: List[VerdictReason]) derives JsonCodec, KindlingsSchema, Schema

object Verdict:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Ratio = Ratio.T
  object Ratio extends Newtype[Double, Pure]
  type Remark = Remark.Note
  object Remark:
    type Note = Note.T
    object Note extends Newtype[String, Pure]
