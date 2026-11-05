package com.histmersion.api.media
import com.histmersion.api.given
object ImageSize:

  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart2 = OfResultPart2.T
  object OfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type U8Result = U8Result.T
  object U8Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type U8I = U8I.T
  object U8I extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Be16Result = Be16Result.T
  object Be16Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Be16I = Be16I.T
  object Be16I extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Le16Result = Le16Result.T
  object Le16Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Le16I = Le16I.T
  object Le16I extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Be32Result = Be32Result.T
  object Be32Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Be32I = Be32I.T
  object Be32I extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PngResult = PngResult.T
  object PngResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PngSizeResultPart1 = PngSizeResultPart1.T
  object PngSizeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PngSizeResultPart2 = PngSizeResultPart2.T
  object PngSizeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GifResult = GifResult.T
  object GifResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GifSizeResultPart1 = GifSizeResultPart1.T
  object GifSizeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GifSizeResultPart2 = GifSizeResultPart2.T
  object GifSizeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JpegResult = JpegResult.T
  object JpegResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JpegSizeResultPart1 = JpegSizeResultPart1.T
  object JpegSizeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JpegSizeResultPart2 = JpegSizeResultPart2.T
  object JpegSizeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(bytes: Array[Byte]): Option[(com.histmersion.api.media.ImageSize.OfResultPart1, com.histmersion.api.media.ImageSize.OfResultPart2)] =
    if png(bytes) then pngSize(bytes)
    else if gif(bytes) then gifSize(bytes)
    else if jpeg(bytes) then jpegSize(bytes)
    else None

  private def u8(b: Array[Byte], i: com.histmersion.api.media.ImageSize.U8I): com.histmersion.api.media.ImageSize.U8Result = b(i) & 0xff
  private def be16(b: Array[Byte], i: com.histmersion.api.media.ImageSize.Be16I): com.histmersion.api.media.ImageSize.Be16Result = (u8(b, i) << 8) | u8(b, i + 1)
  private def le16(b: Array[Byte], i: com.histmersion.api.media.ImageSize.Le16I): com.histmersion.api.media.ImageSize.Le16Result = (u8(b, i + 1) << 8) | u8(b, i)
  private def be32(b: Array[Byte], i: com.histmersion.api.media.ImageSize.Be32I): com.histmersion.api.media.ImageSize.Be32Result =
    (u8(b, i) << 24) | (u8(b, i + 1) << 16) | (u8(b, i + 2) << 8) | u8(b, i + 3)

  private def png(b: Array[Byte]): com.histmersion.api.media.ImageSize.PngResult =
    b.length >= 24 && u8(b, 0) == 0x89 && b(1) == 'P' && b(2) == 'N' && b(3) == 'G'

  /** IHDR is always the first chunk: 8-byte signature, 4-byte length, "IHDR", width, height. */
  private def pngSize(b: Array[Byte]): Option[(com.histmersion.api.media.ImageSize.PngSizeResultPart1, com.histmersion.api.media.ImageSize.PngSizeResultPart2)] = Some((be32(b, 16), be32(b, 20)))

  private def gif(b: Array[Byte]): com.histmersion.api.media.ImageSize.GifResult =
    b.length >= 10 && b(0) == 'G' && b(1) == 'I' && b(2) == 'F'

  private def gifSize(b: Array[Byte]): Option[(com.histmersion.api.media.ImageSize.GifSizeResultPart1, com.histmersion.api.media.ImageSize.GifSizeResultPart2)] = Some((le16(b, 6), le16(b, 8)))

  private def jpeg(b: Array[Byte]): com.histmersion.api.media.ImageSize.JpegResult =
    b.length >= 4 && u8(b, 0) == 0xff && u8(b, 1) == 0xd8

  /** Walk the segment chain to the first SOFn frame header (height then width, 16-bit big-endian). SOF4/SOF8/SOF12 are
    * not frame headers (DHT / JPG / DAC) and must be skipped.
    */
  private def jpegSize(b: Array[Byte]): Option[(com.histmersion.api.media.ImageSize.JpegSizeResultPart1, com.histmersion.api.media.ImageSize.JpegSizeResultPart2)] =
    @annotation.tailrec
    def go(i: Int): Option[(Int, Int)] =
      if i + 9 >= b.length || u8(b, i) != 0xff then None
      else
        val marker = u8(b, i + 1)
        val len = be16(b, i + 2)
        if len < 2 then None
        else if (marker >= 0xc0 && marker <= 0xcf) && marker != 0xc4 && marker != 0xc8 && marker != 0xcc
        then Some((be16(b, i + 7), be16(b, i + 5)))
        else go(i + 2 + len)
    go(2)
