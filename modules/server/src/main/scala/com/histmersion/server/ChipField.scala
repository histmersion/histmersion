package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** A metadata dimension a chip filters the web UI by. `hashKey` is the web UI's URL-hash key, so a chip is a link that
  * works in the modal, on a standalone page and pasted anywhere.
  */
enum ChipField(val hashKey: String):
  case Topic extends ChipField("topic")
  case Place extends ChipField("place")
  case Tag extends ChipField("tag")
  case Author extends ChipField("author")
