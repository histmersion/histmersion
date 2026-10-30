package com.histmersion.api

/** Headers parsed by hand rather than through ImageIO, a native-image liability. */
object ImageSize:

  def of(bytes: Array[Byte]): Option[(Int, Int)] =
    if png(bytes) then pngSize(bytes)
    else if gif(bytes) then gifSize(bytes)
    else if jpeg(bytes) then jpegSize(bytes)
    else None

  private def u8(b: Array[Byte], i: Int): Int = b(i) & 0xff
  private def be16(b: Array[Byte], i: Int): Int = (u8(b, i) << 8) | u8(b, i + 1)
  private def le16(b: Array[Byte], i: Int): Int = (u8(b, i + 1) << 8) | u8(b, i)
  private def be32(b: Array[Byte], i: Int): Int =
    (u8(b, i) << 24) | (u8(b, i + 1) << 16) | (u8(b, i + 2) << 8) | u8(b, i + 3)

  private def png(b: Array[Byte]): Boolean =
    b.length >= 24 && u8(b, 0) == 0x89 && b(1) == 'P' && b(2) == 'N' && b(3) == 'G'

  /** IHDR is always the first chunk: 8-byte signature, 4-byte length, "IHDR", width, height. */
  private def pngSize(b: Array[Byte]): Option[(Int, Int)] = Some((be32(b, 16), be32(b, 20)))

  private def gif(b: Array[Byte]): Boolean =
    b.length >= 10 && b(0) == 'G' && b(1) == 'I' && b(2) == 'F'

  private def gifSize(b: Array[Byte]): Option[(Int, Int)] = Some((le16(b, 6), le16(b, 8)))

  private def jpeg(b: Array[Byte]): Boolean =
    b.length >= 4 && u8(b, 0) == 0xff && u8(b, 1) == 0xd8

  /** Walk the segment chain to the first SOFn frame header (height then width, 16-bit big-endian). SOF4/SOF8/SOF12 are
    * not frame headers (DHT / JPG / DAC) and must be skipped.
    */
  private def jpegSize(b: Array[Byte]): Option[(Int, Int)] =
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
