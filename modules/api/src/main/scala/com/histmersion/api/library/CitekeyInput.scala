package com.histmersion.api.library
import com.histmersion.values.Citekey
import com.histmersion.api.{ApiError, Write}
import com.histmersion.api.given

extension (c: Citekey.type)
  def of(raw: String): Either[ApiError, Citekey] =
    Citekey.parse(raw).left.map(m => ApiError(m, Some(Write.CodeInvalid)))
