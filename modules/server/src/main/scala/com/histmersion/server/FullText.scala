package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*

/** Whether a source page shows the whole OCR'd paper. */
enum FullText:
  case Hidden
  case Missing
  case Shown(markdown: String)
