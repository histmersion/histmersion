package com.histmersion.api.library
import com.histmersion.domain.library.Citekey
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.Api

extension (c: Citekey.type)
  def of(raw: String): Either[Api.Error, Citekey] =
    Citekey.parse(raw).left.map(m => Api.Error(m, Some(Write.CodeInvalid)))
