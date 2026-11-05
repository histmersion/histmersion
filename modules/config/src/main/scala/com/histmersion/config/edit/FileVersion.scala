package com.histmersion.config.edit

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Which text of the config file a change was made against: the SHA-256 of its bytes (`none` for a file that is not
  * there yet). A write that names a version the file no longer has is refused.
  */
type FileVersionRule = Match["none|[0-9a-f]{64}"]

type FileVersion = FileVersion.T

object FileVersion extends RefinedType[String, FileVersionRule]:
  type OfText = OfText.T
  object OfText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Missing: FileVersion = applyUnsafe("none")

  def of(text: Option[com.histmersion.config.edit.FileVersion.OfText]): FileVersion = text.fold(Missing) { t =>
    val d = MessageDigest.getInstance("SHA-256").digest(t.getBytes(StandardCharsets.UTF_8))
    applyUnsafe(d.map(b => f"${b & 0xff}%02x").mkString)
  }
