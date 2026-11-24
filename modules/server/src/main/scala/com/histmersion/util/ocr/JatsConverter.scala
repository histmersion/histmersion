package com.histmersion
package util.ocr
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.upstreams.jats.Jats

import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob
import com.histmersion.lint.{LintRule, tolerated}

/** A JATS article (`*.xml` whose root is `<article>` — PMC's full text, fetched when no PDF exists, #1523) is rendered
  * to Markdown here, by [[Jats]]: it is already text, so OCR would only lose its structure. Everything else goes to the
  * sidecar unchanged.
  */
final class JatsConverter(sidecar: ConverterClient) extends ConverterClient:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def convert(
      file: Blob,
      filename: ConverterClient.Filename,
      quality: ConverterClient.HighQuality
  ): Either[Problem, ConvertedDoc] =
    val jats =
      if !filename.value.toLowerCase.endsWith(".xml") then None
      else scala.util.Try(Jats.Xml(file.text().value)).toOption.filter(Jats.isJats)
    jats match
      case Some(xml) =>
        val md = Jats.toMarkdown(xml).value
        if md.trim.isEmpty then Left(Problem.Unclassified(Legacy.Text(s"$filename is a JATS article with no text")))
        else
          Right(
            ConvertedDoc(
              ConvertedDoc.Markdown(md),
              pages = ConvertedDoc.Pages(0),
              images = Nil,
              filename = Some(ConvertedDoc.Filename(filename.value))
            )
          )
      case None => sidecar.convert(file, filename, quality)

  def health: Either[Problem, ConverterClient.HealthSummary] = sidecar.health

  override def thumbnail(
      bytes: ConverterClient.DocumentBytes,
      filename: ConverterClient.Filename,
      maxPx: Pixels
  ): Either[Problem, ConverterClient.Thumb] =
    sidecar.thumbnail(bytes, filename, maxPx)
