package com.histmersion
package api.research.critiquepairing

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.text.WikiLinks

enum Mention derives CanEqual:
  case Strong, Weak, Conflict

object Mention:
  export WikiLinks.Occurrence.Target as Key
