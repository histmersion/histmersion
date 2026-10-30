package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.{Files, Path}

/** A JATS article (`*.xml` whose root is `<article>` — PMC's full text, fetched when no PDF exists, #1523) is rendered
  * to Markdown here, by [[Jats]]: it is already text, so OCR would only lose its structure. Everything else goes to the
  * sidecar unchanged.
  */
final class JatsConverter(sidecar: ConverterClient) extends ConverterClient:
  def convert(file: Path, filename: String, quality: Boolean): Either[String, ConvertedDoc] =
    val jats =
      if !filename.toLowerCase.endsWith(".xml") then None
      else scala.util.Try(Files.readString(file)).toOption.filter(Jats.isJats)
    jats match
      case Some(xml) =>
        val md = Jats.toMarkdown(xml)
        if md.trim.isEmpty then Left(s"$filename is a JATS article with no text")
        else Right(ConvertedDoc(md, pages = 0, images = Nil, filename = Some(filename)))
      case None => sidecar.convert(file, filename, quality)

  def health: Either[String, String] = sidecar.health

  override def thumbnail(bytes: Array[Byte], filename: String, maxPx: Int): Either[String, ConverterClient.Thumb] =
    sidecar.thumbnail(bytes, filename, maxPx)
