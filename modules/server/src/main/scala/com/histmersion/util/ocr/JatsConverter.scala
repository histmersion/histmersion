package com.histmersion.util.ocr

import com.histmersion.upstreams.jats.Jats

import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob

/** A JATS article (`*.xml` whose root is `<article>` — PMC's full text, fetched when no PDF exists, #1523) is rendered
  * to Markdown here, by [[Jats]]: it is already text, so OCR would only lose its structure. Everything else goes to the
  * sidecar unchanged.
  */
final class JatsConverter(sidecar: ConverterClient) extends ConverterClient:
  def convert(file: Blob, filename: ConverterClient.Filename, quality: ConverterClient.HighQuality): Either[ConverterClient.Problem, ConvertedDoc] =
    val jats =
      if !filename.value.toLowerCase.endsWith(".xml") then None
      else scala.util.Try(Jats.Xml(file.text().value)).toOption.filter(Jats.isJats)
    jats match
      case Some(xml) =>
        val md = Jats.toMarkdown(xml).value
        if md.trim.isEmpty then Left(ConverterClient.Problem(s"$filename is a JATS article with no text"))
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

  def health: Either[ConverterClient.Problem, ConverterClient.HealthSummary] = sidecar.health

  override def thumbnail(
      bytes: ConverterClient.DocumentBytes,
      filename: ConverterClient.Filename,
      maxPx: Pixels
  ): Either[ConverterClient.Problem, ConverterClient.Thumb] =
    sidecar.thumbnail(bytes, filename, maxPx)
