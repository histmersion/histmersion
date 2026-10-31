package com.histmersion.domain

import com.histmersion.values.Title

/** How a referenced record shows wherever it is referenced: the key it goes by now and its title (none once it is
  * deleted), and what became of it.
  */
final case class Described(key: ItemKey, title: Option[Title], state: RecordState)
