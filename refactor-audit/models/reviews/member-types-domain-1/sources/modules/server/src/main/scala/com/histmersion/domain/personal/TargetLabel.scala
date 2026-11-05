package com.histmersion.domain.personal

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** What an item was called when the person marked it, kept for an item the collection no longer (or never) holds. */
type TargetLabel = TargetLabel.T

object TargetLabel extends RefinedType[String, Pure]:
  /** The most characters a name keeps. */
  type TidyRaw = TidyRaw.T
  object TidyRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val MaxChars = 300

  /** A name tidied (spaces collapsed, bounded); none when empty. */
  def tidy(raw: com.histmersion.domain.personal.TargetLabel.TidyRaw): Option[TargetLabel] =
    Option(raw).map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty).map(t => TargetLabel(t.take(MaxChars)))

  def tidy(raw: Option[com.histmersion.domain.personal.TargetLabel.TidyRaw]): Option[TargetLabel] = raw.flatMap(tidy)
