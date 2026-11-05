package com.histmersion.util.ocr

import com.histmersion.upstreams.jats.Jats

import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob

/** A JATS article (`*.xml` whose root is `<article>` — PMC's full text, fetched when no PDF exists, #1523) is rendered
  * to Markdown here, by [[Jats]]: it is already text, so OCR would only lose its structure. Everything else goes to the
  * sidecar unchanged.
  */
final class JatsConverter(sidecar: ConverterClient) extends ConverterClient:
  def convert(file: Blob, filename: com.histmersion.util.ocr.JatsConverter.ConvertFilename, quality: com.histmersion.util.ocr.JatsConverter.ConvertQuality): Either[com.histmersion.util.ocr.JatsConverter.ConvertResultPart1, ConvertedDoc] =
    val jats =
      if !filename.toLowerCase.endsWith(".xml") then None
      else scala.util.Try(file.text()).toOption.filter(Jats.isJats)
    jats match
      case Some(xml) =>
        val md = Jats.toMarkdown(xml)
        if md.trim.isEmpty then Left(s"$filename is a JATS article with no text")
        else Right(ConvertedDoc(md, pages = 0, images = Nil, filename = Some(filename)))
      case None => sidecar.convert(file, filename, quality)

  def health: Either[com.histmersion.util.ocr.JatsConverter.HealthResultPart1, com.histmersion.util.ocr.JatsConverter.HealthResultPart2] = sidecar.health

  override def thumbnail(bytes: Array[Byte], filename: String, maxPx: Int): Either[String, ConverterClient.Thumb] =
    sidecar.thumbnail(bytes, filename, maxPx)

object JatsConverter:
  type ConvertResultPart1 = ConvertResultPart1.T
  object ConvertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConvertFilename = ConvertFilename.T
  object ConvertFilename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConvertQuality = ConvertQuality.T
  object ConvertQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HealthResultPart1 = HealthResultPart1.T
  object HealthResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HealthResultPart2 = HealthResultPart2.T
  object HealthResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
