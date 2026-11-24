package com.histmersion
package util.database

import com.histmersion.domain.Creator

object TestCreators:
  given test: Creator.New = Creator.New.Manual(Creator.Surface.Internal)
