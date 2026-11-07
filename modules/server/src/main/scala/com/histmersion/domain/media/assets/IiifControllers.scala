package com.histmersion.domain.media.assets

import com.histmersion.api.Write

import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import javax.imageio.ImageIO
import com.histmersion.api.media.{AssetLinks, Iiif}
import com.histmersion.api.Api

/** Server-side implementation of the IIIF Image API Level 1 (plan §4.3).
  *
  * Reads an asset's bytes from the database, decodes with `ImageIO`, applies the requested region and size transforms,
  * and encodes the result in the requested format. Uses only JDK classes (`java.awt.image.BufferedImage`,
  * `javax.imageio.ImageIO`) — no extra dependency.
  *
  * `baseUrl` is this server's public URL, read at each request.
  */
final class IiifControllers(assets: AssetService, baseUrl: () => IiifControllers.BaseUrlResult):

  /** Serve the IIIF info.json for an asset. Returns the JSON document or a left with an error. */
  def info(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.AssetName): Either[Api.Error, io.circe.Json] =
    val safeName = AssetLinks.safeName(name)
    assets.figure(citekey, safeName) match
      case None             => Left(Api.Error(s"No asset '$safeName' for '$citekey'", Some(Write.CodeNotFound)))
      case Some((_, bytes)) =>
        decodeImage(bytes) match
          case None      => Left(Api.Error(s"Asset '$safeName' is not a decodable image"))
          case Some(img) =>
            val id = s"${baseUrl()}/iiif/${enc(citekey)}/${enc(safeName)}"
            Right(Iiif.infoJson(id, img.getWidth, img.getHeight))

  /** Serve a IIIF image request. Parses all IIIF parameters, applies region/size, encodes to the requested format, and
    * returns `(bytes, contentType)`.
    */
  def image(
      citekey: com.histmersion.domain.library.Citekey,
      name: com.histmersion.domain.media.assets.AssetService.AssetName,
      regionStr: com.histmersion.domain.media.assets.IiifControllers.Region,
      sizeStr: com.histmersion.domain.media.assets.IiifControllers.Size,
      rotationStr: com.histmersion.domain.media.assets.IiifControllers.Rotation,
      filename: com.histmersion.domain.media.assets.IiifControllers.Filename
  ): Either[Api.Error, (com.histmersion.domain.media.assets.AssetService.Bytes, com.histmersion.domain.media.assets.AssetService.Mime)] =
    // parse the filename as `default.{format}`
    val safeName = AssetLinks.safeName(name)
    for
      format <- parseFilename(filename)
      region <- Iiif.Region.parse(regionStr).left.map(Api.Error(_))
      size <- Iiif.Size.parse(sizeStr).left.map(Api.Error(_))
      _ <- validateRotation(rotationStr)
      result <- render(citekey, safeName, region, size, format)
    yield result

  // ── internals ──────────────────────────────────────────────────────────────────────────────

  /** Parse `default.jpg` or `default.png`. */
  private def parseFilename(filename: com.histmersion.domain.media.assets.IiifControllers.Filename): Either[Api.Error, com.histmersion.domain.media.assets.AssetService.AssetName] =
    filename match
      case s"default.$ext" => Iiif.parseFormat(ext).left.map(Api.Error(_))
      case _ => Left(Api.Error(s"invalid IIIF filename: '$filename' (expected 'default.jpg' or 'default.png')"))

  /** Level 1 supports rotation `0` only. */
  private def validateRotation(s: com.histmersion.domain.media.assets.IiifControllers.Rotation): Either[Api.Error, Unit] =
    s.trim match
      case "0" => Right(())
      case _   => Left(Api.Error(s"unsupported rotation: '$s' (only '0' is supported at Level 1)"))

  /** Load the asset, decode it, apply transforms, and encode to the output format. */
  private def render(
      citekey: com.histmersion.domain.library.Citekey,
      name: com.histmersion.domain.media.assets.AssetService.AssetName,
      region: Iiif.Region,
      size: Iiif.Size,
      format: com.histmersion.domain.media.assets.IiifControllers.Format
  ): Either[Api.Error, (com.histmersion.domain.media.assets.AssetService.Bytes, com.histmersion.domain.media.assets.AssetService.Mime)] =
    assets.figure(citekey, name) match
      case None =>
        Left(Api.Error(s"No asset '$name' for '$citekey'", Some(Write.CodeNotFound)))
      case Some((_, bytes)) =>
        decodeImage(bytes) match
          case None =>
            Left(Api.Error(s"Asset '$name' is not a decodable image"))
          case Some(img) =>
            applyRegion(img, region).flatMap(applySize(_, size)).flatMap(encode(_, format))

  /** Decode raw bytes into a BufferedImage. Returns None when ImageIO cannot read them. */
  private def decodeImage(bytes: com.histmersion.domain.media.assets.AssetService.Bytes): Option[BufferedImage] =
    Option(ImageIO.read(new ByteArrayInputStream(bytes)))

  /** Crop the image to the requested region. */
  private def applyRegion(img: BufferedImage, region: Iiif.Region): Either[Api.Error, BufferedImage] =
    region match
      case Iiif.Region.Full               => Right(img)
      case Iiif.Region.Pixels(x, y, w, h) =>
        val imgW = img.getWidth
        val imgH = img.getHeight
        // clamp to image bounds
        val cx = math.max(0, math.min(x, imgW))
        val cy = math.max(0, math.min(y, imgH))
        val cw = math.max(1, math.min(w, imgW - cx))
        val ch = math.max(1, math.min(h, imgH - cy))
        if cx >= imgW || cy >= imgH then
          Left(Api.Error(s"region ($x,$y,$w,$h) is outside image bounds (${imgW}x$imgH)"))
        else Right(img.getSubimage(cx, cy, cw, ch))

  /** Scale the image to the requested size. */
  private def applySize(img: BufferedImage, size: Iiif.Size): Either[Api.Error, BufferedImage] =
    val srcW = img.getWidth
    val srcH = img.getHeight
    size match
      case Iiif.Size.Max | Iiif.Size.ScaleUp =>
        // Level 1: max returns the image at its native size (no upscaling for Max, allow for ScaleUp)
        Right(img)

      case Iiif.Size.Width(w) =>
        val scale = w.toDouble / srcW
        val h = math.max(1, (srcH * scale).round.toInt)
        Right(scaleImage(img, w, h))

      case Iiif.Size.Height(h) =>
        val scale = h.toDouble / srcH
        val w = math.max(1, (srcW * scale).round.toInt)
        Right(scaleImage(img, w, h))

      case Iiif.Size.Exact(w, h) =>
        Right(scaleImage(img, w, h))

      case Iiif.Size.BestFit(maxW, maxH) =>
        // scale to fit within the bounding box, maintaining aspect ratio
        val scaleW = maxW.toDouble / srcW
        val scaleH = maxH.toDouble / srcH
        val scale = math.min(scaleW, scaleH)
        val w = math.max(1, (srcW * scale).round.toInt)
        val h = math.max(1, (srcH * scale).round.toInt)
        Right(scaleImage(img, w, h))

  /** Scale an image to the given dimensions using bilinear interpolation. */
  private def scaleImage(src: BufferedImage, w: com.histmersion.domain.media.assets.IiifControllers.PixelWidth, h: com.histmersion.domain.media.assets.IiifControllers.PixelHeight): BufferedImage =
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
  private def effectiveType(img: BufferedImage): com.histmersion.domain.media.assets.IiifControllers.ImageType =
    if img.getColorModel.hasAlpha then BufferedImage.TYPE_INT_ARGB
    else BufferedImage.TYPE_INT_RGB

  /** Encode a BufferedImage to the requested format. */
  private def encode(img: BufferedImage, format: com.histmersion.domain.media.assets.IiifControllers.Format): Either[Api.Error, (com.histmersion.domain.media.assets.AssetService.Bytes, com.histmersion.domain.media.assets.AssetService.Mime)] =
    val out = new ByteArrayOutputStream()
    // JPEG cannot encode alpha; convert to RGB if needed
    val toEncode =
      if format == "jpg" && img.getColorModel.hasAlpha then
        val rgb = new BufferedImage(img.getWidth, img.getHeight, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.drawImage(img, 0, 0, java.awt.Color.WHITE, null)
        g.dispose()
        rgb
      else img
    val written = ImageIO.write(toEncode, if format == "jpg" then "JPEG" else "PNG", out)
    if !written then Left(Api.Error(s"cannot encode image as $format"))
    else Right((out.toByteArray, Iiif.contentType(format)))

  private def enc(s: com.histmersion.domain.media.assets.IiifControllers.Format): com.histmersion.domain.media.assets.AssetService.Mime =
    java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

object IiifControllers:
  type BaseUrl = BaseUrl.T
  object BaseUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Filename = Filename.T
  object Filename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Format = Format.T
  object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageType = ImageType.T
  object ImageType extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PixelHeight = PixelHeight.T
  object PixelHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PixelWidth = PixelWidth.T
  object PixelWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Region = Region.T
  object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rotation = Rotation.T
  object Rotation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BaseUrlResult = com.histmersion.domain.media.assets.IiifControllers.BaseUrl
  val BaseUrlResult: _root_.com.histmersion.domain.media.assets.IiifControllers.BaseUrl.type = _root_.com.histmersion.domain.media.assets.IiifControllers.BaseUrl

