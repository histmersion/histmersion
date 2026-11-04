package com.histmersion.inventoryfixture

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.string.Match

type CodeRule = Match["[a-z]+"]
type Code = Code.T
object Code extends RefinedType[String, CodeRule]
