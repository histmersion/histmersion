package com.histmersion.api

import com.histmersion.values.Citekey

extension (c: Citekey.type)
  def of(raw: String): Either[ApiError, Citekey] =
    Citekey.parse(raw).left.map(m => ApiError(m, Some(Write.CodeInvalid)))
