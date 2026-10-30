package com.histmersion.api

import com.histmersion.values.BundleKind

/** The pure rule of a label rename (article slug, event id, goal slug): the shape every one of them already has to
  * have.
  */
object LabelRenames:
  private val Shape = "^[a-z0-9][a-z0-9_.-]*$".r

  def validate(kind: BundleKind, from: String, raw: String): Either[ApiError, String] =
    val to = raw.trim
    val what = kind match
      case BundleKind.Article => "article slug"
      case BundleKind.Event   => "event id"
      case BundleKind.Place   => "place id"
      case _                  => "goal slug"
    if !Shape.matches(to) then
      Left(ApiError(s"'$to' is not an $what (lower-case letters, digits, '_', '.' and '-')", Some(Write.CodeInvalid)))
    else if to == from.trim then Left(ApiError(s"'$to' is already that $what", Some(Write.CodeInvalid)))
    else Right(to)
