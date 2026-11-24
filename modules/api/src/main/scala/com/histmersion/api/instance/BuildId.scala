package com.histmersion
package api.instance

import com.histmersion.api.Html
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

type BuildId = BuildId.T

object BuildId extends Newtype[String, Pure]:
  private val Length = 32

  val Header: String = "X-Histmersion-Build"

  enum Verdict derives CanEqual:
    case Same

    case Unknown

    case Updated

  private def written(text: String): Boolean =
    text.length == Length && text.forall(c => c >= '0' && c <= '9' || c >= 'a' && c <= 'f')

  def parse(text: String): Option[BuildId] = Option.when(written(text))(BuildId(text))

  extension (id: BuildId)
    def known: Boolean = written(id.value)

    def against(server: BuildId): Verdict =
      if !id.known || !server.known then Verdict.Unknown
      else if id == server then Verdict.Same
      else Verdict.Updated

    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def encoded: String = Html.Atoms.segment(id.value)
