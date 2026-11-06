package com.histmersion.api.media
import com.histmersion.api.given
object ImageSize:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Height = Height.T
  object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Be16B = _root_.com.histmersion.api.media.ImageSize.Bytes
  val Be16B: _root_.com.histmersion.api.media.ImageSize.Bytes.type = _root_.com.histmersion.api.media.ImageSize.Bytes
  type Le16B = _root_.com.histmersion.api.media.ImageSize.Bytes
  val Le16B: _root_.com.histmersion.api.media.ImageSize.Bytes.type = _root_.com.histmersion.api.media.ImageSize.Bytes
  type Be32B = _root_.com.histmersion.api.media.ImageSize.Bytes
  val Be32B: _root_.com.histmersion.api.media.ImageSize.Bytes.type = _root_.com.histmersion.api.media.ImageSize.Bytes
  type PngB = _root_.com.histmersion.api.media.ImageSize.Bytes
  val PngB: _root_.com.histmersion.api.media.ImageSize.Bytes.type = _root_.com.histmersion.api.media.ImageSize.Bytes
  type PngSizeB = _root_.com.histmersion.api.media.ImageSize.Bytes
  val PngSizeB: _root_.com.histmersion.api.media.ImageSize.Bytes.type = _root_.com.histmersion.api.media.ImageSize.Bytes
  type GifSizeB = _root_.com.histmersion.api.media.ImageSize.Bytes
  val GifSizeB: _root_.com.histmersion.api.media.ImageSize.Bytes.type = _root_.com.histmersion.api.media.ImageSize.Bytes
  type JpegB = _root_.com.histmersion.api.media.ImageSize.Bytes
  val JpegB: _root_.com.histmersion.api.media.ImageSize.Bytes.type = _root_.com.histmersion.api.media.ImageSize.Bytes

  type Be16I = Int
  type Le16I = Int
  type Be32I = Int

  def of(bytes: com.histmersion.api.media.ImageSize.Bytes): Option[(com.histmersion.api.media.ImageSize.Width, com.histmersion.api.media.ImageSize.Height)] =
    if png(bytes) then pngSize(bytes)
    else if gif(bytes) then gifSize(bytes)
    else if jpeg(bytes) then jpegSize(bytes)
    else None

  private def u8(b: com.histmersion.api.media.ImageSize.Bytes, i: Int): Int = b(i) & 0xff
  private def be16(b: com.histmersion.api.media.ImageSize.Be16B, i: com.histmersion.api.media.ImageSize.Be16I): Int = ((u8(b, i)) << 8) | u8(b, ((i) + 1))
  private def le16(b: com.histmersion.api.media.ImageSize.Le16B, i: com.histmersion.api.media.ImageSize.Le16I): Int = ((u8(b, ((i) + 1))) << 8) | u8(b, i)
  private def be32(b: com.histmersion.api.media.ImageSize.Be32B, i: com.histmersion.api.media.ImageSize.Be32I): Int =
    ((u8(b, i)) << 24) | ((u8(b, ((i) + 1))) << 16) | ((u8(b, ((i).value + 2))).value << 8) | u8(b, i + 3)

  private def png(b: com.histmersion.api.media.ImageSize.PngB): Boolean =
    (b).value.length >= 24 && u8(b, (0)) == 0x89 && b(1) == 'P' && b(2) == 'N' && b(3) == 'G'

  /** IHDR is always the first chunk: 8-byte signature, 4-byte length, "IHDR", width, height. */
  private def pngSize(b: com.histmersion.api.media.ImageSize.PngSizeB): Option[(com.histmersion.api.media.ImageSize.Width, com.histmersion.api.media.ImageSize.Height)] = Some((_root_.com.histmersion.api.media.ImageSize.Width((be32(b, _root_.com.histmersion.api.media.ImageSize.Be32I(16))).value), _root_.com.histmersion.api.media.ImageSize.Height((be32(b, _root_.com.histmersion.api.media.ImageSize.Be32I(20))).value)))

  private def gif(b: com.histmersion.api.media.ImageSize.Bytes): Boolean =
    (b).value.length >= 10 && b(0) == 'G' && b(1) == 'I' && b(2) == 'F'

  private def gifSize(b: com.histmersion.api.media.ImageSize.GifSizeB): Option[(com.histmersion.api.media.ImageSize.Width, com.histmersion.api.media.ImageSize.Height)] = Some((_root_.com.histmersion.api.media.ImageSize.Width((le16(b, _root_.com.histmersion.api.media.ImageSize.Le16I(6))).value), _root_.com.histmersion.api.media.ImageSize.Height((le16(b, _root_.com.histmersion.api.media.ImageSize.Le16I(8))).value)))

  private def jpeg(b: com.histmersion.api.media.ImageSize.JpegB): Boolean =
    (b).value.length >= 4 && u8(b, (0)) == 0xff && u8(b, (1)) == 0xd8

  /** Walk the segment chain to the first SOFn frame header (height then width, 16-bit big-endian). SOF4/SOF8/SOF12 are
    * not frame headers (DHT / JPG / DAC) and must be skipped.
    */
  private def jpegSize(b: com.histmersion.api.media.ImageSize.Bytes): Option[(com.histmersion.api.media.ImageSize.Width, com.histmersion.api.media.ImageSize.Height)] =
    @annotation.tailrec
    def go(i: Int): Option[(com.histmersion.api.media.ImageSize.Width, com.histmersion.api.media.ImageSize.Height)] =
      if (i) + 9 >= (b).value.length || u8(b, i) != 0xff then None
      else
        val marker = u8(b, ((i) + 1))
        val len = be16(b, _root_.com.histmersion.api.media.ImageSize.Be16I((i) + 2))
        if len < 2 then None
        else if (marker >= 0xc0 && marker <= 0xcf) && marker != 0xc4 && marker != 0xc8 && marker != 0xcc
        then Some((be16(b, _root_.com.histmersion.api.media.ImageSize.Be16I((i) + 7)), be16(b, _root_.com.histmersion.api.media.ImageSize.Be16I((i) + 5))))
        else go((i) + 2 + len)
    go((2))
