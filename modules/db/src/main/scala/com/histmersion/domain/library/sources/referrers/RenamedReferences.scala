package com.histmersion.domain.library.sources.referrers

/** What a referrer rewrote on a rename: the columns it changed, and the records whose text links moved (with how many
  * links moved in each).
  */
import com.histmersion.api.library.Rename
final case class RenamedReferences(columns: List[Rename.Count] = Nil, texts: List[(String, Int)] = Nil)
