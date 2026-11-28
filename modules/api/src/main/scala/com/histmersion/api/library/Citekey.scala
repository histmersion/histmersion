package com.histmersion
package api.library

import com.histmersion.api.exchange.MergeDecision
import com.histmersion.api.personal.ItemKey
import com.histmersion.api.text.WikiLinks

type Citekey = Citekey.T
object Citekey extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]:
  given Display[T] = Display(_.value)
  extension (citekey: Citekey)
    def asLinkTarget: WikiLinks.Occurrence.Target = WikiLinks.Occurrence.Target(citekey.value)
    def asItemKey: ItemKey = ItemKey(citekey.value)
    def asArgument: MergeDecision.Argument = MergeDecision.Argument(citekey.value)
