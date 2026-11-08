package com.histmersion
package domain.automation.workflows

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A workflow's identifier (`enrich-source`, `review-article`, …): what a run records as its pipeline. */
type WorkflowId = WorkflowId.T

object WorkflowId extends Newtype[String, Match["[a-z][a-z0-9-]*"]]
