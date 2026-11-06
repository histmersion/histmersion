package com.histmersion.api.catalogue
import com.histmersion.api.OffsetPage
import com.histmersion.api.Markdown.*

object AttestationsMarkdown:

  type AttestationLineResult = AttestationLineResult.T
  object AttestationLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttestationListResult = AttestationListResult.T
  object AttestationListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttestationResult = AttestationResult.T
  object AttestationResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private[api] def attestationLine(a: Attestation): com.histmersion.api.catalogue.AttestationsMarkdown.AttestationLineResult =
    val loc = List(
      a.page.map(p => s"p.$p"),
      a.chunkOrd.map(o => s"chunk $o"),
      a.charStart.zip(a.charEnd).headOption.map((s, e) => s"chars $s–$e"),
      a.ctsUrn.map(u => s"CTS `$u`"),
      a.canvas.map(c => s"canvas ${a.xywh.fold(c)(r => s"$c#xywh=$r")}")
    ).flatten
    val locStr = if loc.isEmpty then "" else s" (${loc.mkString("; ")})"
    val q = a.quote.map(t => s""" "${if t.length > 80 then t.take(77) + "…" else t}"""").getOrElse("")
    val n = a.note.map(n => s" — $n").getOrElse("")
    _root_.com.histmersion.api.catalogue.AttestationsMarkdown.AttestationLineResult(s"- `#${a.id}` [[${a.citekey}]] → ${a.targetType}:`${a.targetId}`$locStr$q$n")

  def attestationList(result: Attestation.List): com.histmersion.api.catalogue.AttestationsMarkdown.AttestationListResult =
    OffsetPage.markdown(result.page, _root_.com.histmersion.api.OffsetPage.MarkdownNoun("attestation(s)"), _root_.com.histmersion.api.OffsetPage.MarkdownEmpty("_No matching attestations._"), result.attestations)(
      attestationLine
    )

  def attestation(a: Attestation): com.histmersion.api.catalogue.AttestationsMarkdown.AttestationResult =
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
    _root_.com.histmersion.api.catalogue.AttestationsMarkdown.AttestationResult(header + locBlock + q + n)
