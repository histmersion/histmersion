package com.histmersion
package domain.automation.workflows

import com.histmersion.domain.Creator
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A workflow's identifier (`enrich-source`, `review-article`, …): what a run records as its pipeline. */
type WorkflowId = WorkflowId.T

object WorkflowId extends Newtype[String, Match["[a-z][a-z0-9-]*"]]:
  extension (id: WorkflowId) def creatorName: Creator.Workflow.Name = Creator.Workflow.Name.assume(id.value)
