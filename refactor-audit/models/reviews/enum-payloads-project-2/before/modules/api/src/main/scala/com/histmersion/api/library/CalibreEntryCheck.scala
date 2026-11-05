package com.histmersion.api.library
import com.histmersion.api.given
enum CalibreEntryCheck:
  case Present

  case Missing

  case Unchecked(reason: String)
