package com.histmersion.util.ocr

import com.histmersion.upstreams.jats.Jats

import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob

/** A JATS article (`*.xml` whose root is `<article>` — PMC's full text, fetched when no PDF exists, #1523) is rendered
  * to Markdown here, by [[Jats]]: it is already text, so OCR would only lose its structure. Everything else goes to the
  * sidecar unchanged.
  */
final class JatsConverter(sidecar: ConverterClient) extends ConverterClient:
  def convert(file: Blob, filename: com.histmersion.util.ocr.ConverterClient.Filename, quality: com.histmersion.util.ocr.ConverterClient.HighQuality): Either[com.histmersion.util.ocr.JatsConverter.Problem, ConvertedDoc] =
    val jats =
      if !filename.toLowerCase.endsWith(".xml") then None
      else scala.util.Try(file.text()).toOption.filter(Jats.isJats)
    jats match
      case Some(xml) =>
        val md = Jats.toMarkdown(xml)
        if md.trim.isEmpty then Left(s"$filename is a JATS article with no text")
        else Right(ConvertedDoc(md, pages = 0, images = Nil, filename = Some(filename)))
      case None => sidecar.convert(file, filename, quality)

  def health: Either[com.histmersion.util.ocr.JatsConverter.Problem, com.histmersion.util.ocr.ConverterClient.HealthSummary] = sidecar.health

  override def thumbnail(bytes: Array[Byte], filename: String, maxPx: Int): Either[String, ConverterClient.Thumb] =
    sidecar.thumbnail(bytes, filename, maxPx)

object JatsConverter:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
