package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.AssetRepo

import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import javax.imageio.ImageIO
import javax.sql.DataSource

/** Server-side implementation of the IIIF Image API Level 1 (plan §4.3).
  *
  * Reads an asset's bytes from the database, decodes with `ImageIO`, applies the requested region and size transforms,
  * and encodes the result in the requested format. Uses only JDK classes (`java.awt.image.BufferedImage`,
  * `javax.imageio.ImageIO`) — no extra dependency.
  *
  * Stateless: the `DataSource` is a parameter.
  */
object IiifHandler:

  /** Serve the IIIF info.json for an asset. Returns the JSON document or a left with an error. */
  def info(ds: DataSource, citekey: String, name: String, baseUrl: String): Either[ApiError, io.circe.Json] =
    val safeName = AssetLinks.safeName(name)
    AssetRepo.read(ds, citekey, safeName) match
      case None             => Left(ApiError(s"No asset '$safeName' for '$citekey'", Some(Write.CodeNotFound)))
      case Some((_, bytes)) =>
        decodeImage(bytes) match
          case None      => Left(ApiError(s"Asset '$safeName' is not a decodable image"))
          case Some(img) =>
            val id = s"$baseUrl/iiif/${enc(citekey)}/${enc(safeName)}"
            Right(Iiif.infoJson(id, img.getWidth, img.getHeight))

  /** Serve a IIIF image request. Parses all IIIF parameters, applies region/size, encodes to the requested format, and
    * returns `(bytes, contentType)`.
    */
  def image(
      ds: DataSource,
      citekey: String,
      name: String,
      regionStr: String,
      sizeStr: String,
      rotationStr: String,
      filename: String
  ): Either[ApiError, (Array[Byte], String)] =
    // parse the filename as `default.{format}`
    val safeName = AssetLinks.safeName(name)
    for
      format <- parseFilename(filename)
      region <- Iiif.Region.parse(regionStr).left.map(ApiError(_))
      size <- Iiif.Size.parse(sizeStr).left.map(ApiError(_))
      _ <- validateRotation(rotationStr)
      result <- render(ds, citekey, safeName, region, size, format)
    yield result

  // ── internals ──────────────────────────────────────────────────────────────────────────────

  /** Parse `default.jpg` or `default.png`. */
  private def parseFilename(filename: String): Either[ApiError, String] =
    filename match
      case s"default.$ext" => Iiif.parseFormat(ext).left.map(ApiError(_))
      case _ => Left(ApiError(s"invalid IIIF filename: '$filename' (expected 'default.jpg' or 'default.png')"))

  /** Level 1 supports rotation `0` only. */
  private def validateRotation(s: String): Either[ApiError, Unit] =
    s.trim match
      case "0" => Right(())
      case _   => Left(ApiError(s"unsupported rotation: '$s' (only '0' is supported at Level 1)"))

  /** Load the asset, decode it, apply transforms, and encode to the output format. */
  private def render(
      ds: DataSource,
      citekey: String,
      name: String,
      region: Iiif.Region,
      size: Iiif.Size,
      format: String
  ): Either[ApiError, (Array[Byte], String)] =
    AssetRepo.read(ds, citekey, name) match
      case None =>
        Left(ApiError(s"No asset '$name' for '$citekey'", Some(Write.CodeNotFound)))
      case Some((_, bytes)) =>
        decodeImage(bytes) match
          case None =>
            Left(ApiError(s"Asset '$name' is not a decodable image"))
          case Some(img) =>
            applyRegion(img, region).flatMap(applySize(_, size)).flatMap(encode(_, format))

  /** Decode raw bytes into a BufferedImage. Returns None when ImageIO cannot read them. */
  private def decodeImage(bytes: Array[Byte]): Option[BufferedImage] =
    Option(ImageIO.read(new ByteArrayInputStream(bytes)))

  /** Crop the image to the requested region. */
  private def applyRegion(img: BufferedImage, region: Iiif.Region): Either[ApiError, BufferedImage] =
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
        if cx >= imgW || cy >= imgH then Left(ApiError(s"region ($x,$y,$w,$h) is outside image bounds (${imgW}x$imgH)"))
        else Right(img.getSubimage(cx, cy, cw, ch))

  /** Scale the image to the requested size. */
  private def applySize(img: BufferedImage, size: Iiif.Size): Either[ApiError, BufferedImage] =
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
  private def encode(img: BufferedImage, format: String): Either[ApiError, (Array[Byte], String)] =
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
    if !written then Left(ApiError(s"cannot encode image as $format"))
    else Right((out.toByteArray, Iiif.contentType(format)))

  private def enc(s: String): String =
    java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
