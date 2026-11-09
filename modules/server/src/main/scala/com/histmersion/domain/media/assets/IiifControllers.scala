package com.histmersion
package domain.media.assets

import com.histmersion.util.problem.{Legacy, Problem}
import io.scalaland.chimney.dsl.*
import com.histmersion.api.Write

import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import javax.imageio.ImageIO
import com.histmersion.api.media.{AssetLinks, Iiif, ImageSize}
import com.histmersion.api.Api
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.ArchiveFile
import io.github.iltotore.iron.constraint.any.Pure

/** Server-side implementation of the IIIF Image API Level 1 (plan §4.3).
  *
  * Reads an asset's bytes from the database, decodes with `ImageIO`, applies the requested region and size transforms,
  * and encodes the result in the requested format. Uses only JDK classes (`java.awt.image.BufferedImage`,
  * `javax.imageio.ImageIO`) — no extra dependency.
  *
  * `baseUrl` is this server's public URL, read at each request.
  */
final class IiifControllers(assets: AssetService, baseUrl: () => IiifControllers.BaseUrl):

  /** Serve the IIIF info.json for an asset. Returns the JSON document or a left with an error. */
  def info(citekey: Citekey, name: AssetService.AssetName): Either[Api.Error, io.circe.Json] =
    val safeName = AssetService.AssetName(AssetLinks.safeName(name.value).value)
    assets.figure(citekey, safeName) match
      case None =>
        Left(
          Api.Error(
            Problem.Unclassified(Legacy.Text(s"No asset '$safeName' for '$citekey'")),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
      case Some((_, bytes)) =>
        decodeImage(bytes) match
          case None => Left(Api.Error(Problem.Unclassified(Legacy.Text(s"Asset '$safeName' is not a decodable image"))))
          case Some(img) =>
            val id = s"${baseUrl()}/iiif/${enc(citekey.value)}/${enc(safeName.value)}"
            Right(Iiif.infoJson(Iiif.ImageId(id), ImageSize.Width(img.getWidth), ImageSize.Height(img.getHeight)))

  /** Serve a IIIF image request. Parses all IIIF parameters, applies region/size, encodes to the requested format, and
    * returns `(bytes, contentType)`.
    */
  def image(
      citekey: Citekey,
      name: AssetService.AssetName,
      regionStr: Iiif.Region.Wire,
      sizeStr: Iiif.Size.Wire,
      rotationStr: Iiif.Rotation,
      filename: IiifControllers.Filename
  ): Either[Api.Error, (AssetService.Bytes, AssetService.Mime)] =
    // parse the filename as `default.{format}`
    val safeName = AssetService.AssetName(AssetLinks.safeName(name.value).value)
    for
      format <- parseFilename(filename)
      region <- Iiif.Region.parse(regionStr).left.map(p => Api.Error(p))
      size <- Iiif.Size.parse(sizeStr).left.map(p => Api.Error(p))
      _ <- validateRotation(rotationStr)
      result <- render(citekey, safeName, region, size, format)
    yield result

  // ── internals ──────────────────────────────────────────────────────────────────────────────

  /** Parse `default.jpg` or `default.png`. */
  private def parseFilename(filename: IiifControllers.Filename): Either[Api.Error, Iiif.Format] =
    filename.value match
      case s"default.$ext" =>
        Iiif.parseFormat(ArchiveFile.Format.Wire(ext)).left.map(p => Api.Error(p))
      case _ =>
        Left(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"invalid IIIF filename: '$filename' (expected 'default.jpg' or 'default.png')")
            )
          )
        )

  /** Level 1 supports rotation `0` only. */
  private def validateRotation(s: Iiif.Rotation): Either[Api.Error, Unit] =
    s.value.trim match
      case "0" => Right(())
      case _   =>
        Left(
          Api.Error(Problem.Unclassified(Legacy.Text(s"unsupported rotation: '$s' (only '0' is supported at Level 1)")))
        )

  /** Load the asset, decode it, apply transforms, and encode to the output format. */
  private def render(
      citekey: Citekey,
      name: AssetService.AssetName,
      region: Iiif.Region,
      size: Iiif.Size,
      format: Iiif.Format
  ): Either[Api.Error, (AssetService.Bytes, AssetService.Mime)] =
    assets.figure(citekey, name) match
      case None =>
        Left(
          Api.Error(
            Problem.Unclassified(Legacy.Text(s"No asset '$name' for '$citekey'")),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
      case Some((_, bytes)) =>
        decodeImage(bytes) match
          case None =>
            Left(Api.Error(Problem.Unclassified(Legacy.Text(s"Asset '$name' is not a decodable image"))))
          case Some(img) =>
            applyRegion(img, region).flatMap(applySize(_, size)).flatMap(encode(_, format))

  /** Decode raw bytes into a BufferedImage. Returns None when ImageIO cannot read them. */
  private def decodeImage(bytes: AssetService.Bytes): Option[BufferedImage] =
    Option(ImageIO.read(new ByteArrayInputStream(bytes.value)))

  /** Crop the image to the requested region. */
  private def applyRegion(img: BufferedImage, region: Iiif.Region): Either[Api.Error, BufferedImage] =
    region match
      case Iiif.Region.Full                   => Right(img)
      case Iiif.Region.Pixels(xr, yr, wr, hr) =>
        val (x, y, w, h) = (xr.value, yr.value, wr.value, hr.value)
        val imgW = img.getWidth
        val imgH = img.getHeight
        // clamp to image bounds
        val cx = math.max(0, math.min(x, imgW))
        val cy = math.max(0, math.min(y, imgH))
        val cw = math.max(1, math.min(w, imgW - cx))
        val ch = math.max(1, math.min(h, imgH - cy))
        if cx >= imgW || cy >= imgH then
          Left(
            Api.Error(
              Problem.Unclassified(Legacy.Text(s"region ($x,$y,$w,$h) is outside image bounds (${imgW}x$imgH)"))
            )
          )
        else Right(img.getSubimage(cx, cy, cw, ch))

  /** Scale the image to the requested size. */
  private def applySize(img: BufferedImage, size: Iiif.Size): Either[Api.Error, BufferedImage] =
    val srcW = img.getWidth
    val srcH = img.getHeight
    size match
      case Iiif.Size.Max | Iiif.Size.ScaleUp =>
        // Level 1: max returns the image at its native size (no upscaling for Max, allow for ScaleUp)
        Right(img)

      case Iiif.Size.Width(wr) =>
        val w = wr.value
        val scale = w.toDouble / srcW
        val h = math.max(1, (srcH * scale).round.toInt)
        Right(scaleImage(img, w, h))

      case Iiif.Size.Height(hr) =>
        val h = hr.value
        val scale = h.toDouble / srcH
        val w = math.max(1, (srcW * scale).round.toInt)
        Right(scaleImage(img, w, h))

      case Iiif.Size.Exact(w, h) =>
        Right(scaleImage(img, w.value, h.value))

      case Iiif.Size.BestFit(maxW, maxH) =>
        // scale to fit within the bounding box, maintaining aspect ratio
        val scaleW = maxW.value.toDouble / srcW
        val scaleH = maxH.value.toDouble / srcH
        val scale = math.min(scaleW, scaleH)
        val w = math.max(1, (srcW * scale).round.toInt)
        val h = math.max(1, (srcH * scale).round.toInt)
        Right(scaleImage(img, w, h))

  /** Scale an image to the given dimensions using bilinear interpolation. */
  private def scaleImage(src: BufferedImage, w: Int, h: Int): BufferedImage =
    if w == src.getWidth && h == src.getHeight then src
    else
      val dst = new BufferedImage(w, h, effectiveType(src))
      val g = dst.createGraphics()
      g.setRenderingHint(
        java.awt.RenderingHints.KEY_INTERPOLATION,
        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
      )
      g.drawImage(src, 0, 0, w, h, null)
      g.dispose()
      dst

  /** Use TYPE_INT_ARGB for images with transparency, TYPE_INT_RGB otherwise. */
  private def effectiveType(img: BufferedImage): Int =
    if img.getColorModel.hasAlpha then BufferedImage.TYPE_INT_ARGB
    else BufferedImage.TYPE_INT_RGB

  /** Encode a BufferedImage to the requested format. */
  private def encode(
      img: BufferedImage,
      format: Iiif.Format
  ): Either[Api.Error, (AssetService.Bytes, AssetService.Mime)] =
    val out = new ByteArrayOutputStream()
    // JPEG cannot encode alpha; convert to RGB if needed
    val toEncode =
      if format.value == "jpg" && img.getColorModel.hasAlpha then
        val rgb = new BufferedImage(img.getWidth, img.getHeight, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.drawImage(img, 0, 0, java.awt.Color.WHITE, null)
        g.dispose()
        rgb
      else img
    val written = ImageIO.write(toEncode, if format.value == "jpg" then "JPEG" else "PNG", out)
    if !written then Left(Api.Error(Problem.Unclassified(Legacy.Text(s"cannot encode image as $format"))))
    else Right((AssetService.Bytes(out.toByteArray), AssetService.Mime(Iiif.contentType(format).value)))

  private def enc(s: String): String =
    java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

object IiifControllers:
  type BaseUrl = BaseUrl.T
  object BaseUrl extends Newtype[String, Pure]
  type Filename = Filename.T
  object Filename extends Newtype[String, Pure]
