package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Problem = Problem.T
object Problem extends RefinedType[String, Pure] // assert: GuidelineInventory.G5problem (the closed util.problem.Problem is the one)
