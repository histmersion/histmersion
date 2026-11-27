package com.histmersion
package api.media
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
object ImageSize:
  type Bytes = Bytes.T
  object Bytes extends Newtype[Array[Byte], Pure]
  type Height = Height.T
  object Height extends Newtype[Int, Pure]
  type Width = Width.T
  object Width extends Newtype[Int, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def of(bytes: Bytes): Option[(Width, Height)] =
    if png(bytes) then pngSize(bytes)
    else if gif(bytes) then gifSize(bytes)
    else if jpeg(bytes) then jpegSize(bytes)
    else None

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def u8(b: Bytes, i: Int): Int = b.value(i) & 0xff
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def be16(b: Bytes, i: Int): Int = ((u8(b, i)) << 8) | u8(b, ((i) + 1))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def le16(b: Bytes, i: Int): Int = ((u8(b, ((i) + 1))) << 8) | u8(b, i)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def be32(b: Bytes, i: Int): Int =
    ((u8(b, i)) << 24) | ((u8(b, ((i) + 1))) << 16) | (u8(b, i + 2) << 8) | u8(b, i + 3)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def png(b: Bytes): Boolean =
    (b).value.length >= 24 && u8(b, (0)) == 0x89 && b.value(1) == 'P' && b.value(2) == 'N' && b.value(3) == 'G'

  /** IHDR is always the first chunk: 8-byte signature, 4-byte length, "IHDR", width, height. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pngSize(b: Bytes): Option[(Width, Height)] = Some((Width(be32(b, 16)), Height(be32(b, 20))))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def gif(b: Bytes): Boolean =
    (b).value.length >= 10 && b.value(0) == 'G' && b.value(1) == 'I' && b.value(2) == 'F'

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def gifSize(b: Bytes): Option[(Width, Height)] = Some((Width(le16(b, 6)), Height(le16(b, 8))))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def jpeg(b: Bytes): Boolean =
    (b).value.length >= 4 && u8(b, (0)) == 0xff && u8(b, (1)) == 0xd8

  /** Walk the segment chain to the first SOFn frame header (height then width, 16-bit big-endian). SOF4/SOF8/SOF12 are
    * not frame headers (DHT / JPG / DAC) and must be skipped.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def jpegSize(b: Bytes): Option[(Width, Height)] =
    @annotation.tailrec
    def go(i: Int): Option[(Width, Height)] =
      if (i) + 9 >= (b).value.length || u8(b, i) != 0xff then None
      else
        val marker = u8(b, ((i) + 1))
        val len = be16(b, (i) + 2)
        if len < 2 then None
        else if (marker >= 0xc0 && marker <= 0xcf) && marker != 0xc4 && marker != 0xc8 && marker != 0xcc
        then Some((Width(be16(b, (i) + 7)), Height(be16(b, (i) + 5))))
        else go((i) + 2 + len)
    go((2))
