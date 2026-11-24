package com.histmersion
package api.catalogue
import com.histmersion.api.OffsetPage
import com.histmersion.api.Markdown.*
import com.histmersion.lint.{LintRule, tolerated}

object AttestationsMarkdown:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[api] def attestationLine(a: Attestation): String =
    val loc = List(
      a.page.map(p => s"p.$p"),
      a.chunkOrd.map(o => s"chunk $o"),
      a.charStart.zip(a.charEnd).headOption.map((s, e) => s"chars $s–$e"),
      a.ctsUrn.map(u => s"CTS `$u`"),
      a.canvas.map(c => s"canvas ${a.xywh.fold(c)(r => s"$c#xywh=$r")}")
    ).flatten
    val locStr = if loc.isEmpty then "" else s" (${loc.mkString("; ")})"
    val q = a.quote.map(t => s""" "${if t.value.length > 80 then t.value.take(77) + "…" else t.value}"""").getOrElse("")
    val n = a.note.map(n => s" — $n").getOrElse("")
    (s"- `#${a.id}` [[${a.citekey}]] → ${a.targetType}:`${a.targetId}`$locStr$q$n")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def attestationList(result: Attestation.List): String =
    OffsetPage
      .markdown(result.page, ("attestation(s)"), ("_No matching attestations._"), result.attestations)(
        attestationLine
      )
      .value

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def attestation(a: Attestation): String =
    val header = s"# Attestation #${a.id}\n\n_[[${a.citekey}]] → ${a.targetType}:`${a.targetId}`_"
    val loc = List(
      a.page.map(p => s"**Page:** $p"),
      a.chunkOrd.map(o => s"**Chunk:** $o"),
      a.charStart.zip(a.charEnd).headOption.map((s, e) => s"**Chars:** $s–$e"),
      a.ctsUrn.map(u => s"**CTS URN:** `$u`"),
      a.canvas.map(c => s"**Canvas:** $c"),
      a.xywh.map(r => s"**Region:** $r")
    ).flatten
    val locBlock = if loc.isEmpty then "" else "\n\n" + loc.mkString("  \n")
    val q = a.quote
      .map { t =>
        val ctx = List(
          a.prefix.map(p => s"…$p"),
          Some(s"**$t**"),
          a.suffix.map(s => s"$s…")
        ).flatten.mkString("")
        s"\n\n> $ctx"
      }
      .getOrElse("")
    val n = a.note.map(n => s"\n\n$n").getOrElse("")
    (header + locBlock + q + n)
