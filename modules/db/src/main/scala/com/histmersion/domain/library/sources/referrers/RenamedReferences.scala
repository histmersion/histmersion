package com.histmersion.domain.library.sources.referrers

import com.histmersion.api.library.RenameCount

/** What a referrer rewrote on a rename: the columns it changed, and the records whose text links moved (with how many
  * links moved in each).
  */
final case class RenamedReferences(columns: List[RenameCount] = Nil, texts: List[(String, Int)] = Nil)
