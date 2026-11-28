package com.histmersion
package domain.library.acquisition

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi

enum Action derives CanEqual:
  case NoDoi

  case Close(citekey: Citekey)

  case Fetch(doi: Doi, into: Option[Citekey])
