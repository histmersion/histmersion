package com.histmersion.core

import com.histmersion.values.Citekey

/** A citekey a request names, refused as an [[ApiError]] (`invalid`) — what every handler needs. */
extension (c: Citekey.type)
  def of(raw: String): Either[ApiError, Citekey] =
    Citekey.parse(raw).left.map(m => ApiError(m, Some(Write.CodeInvalid)))
