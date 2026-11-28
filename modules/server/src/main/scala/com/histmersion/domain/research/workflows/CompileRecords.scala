package com.histmersion
package domain.research.workflows

import scala.compiletime.deferred
import com.histmersion.domain.automation.agents.Checkpointed

trait CompileRecords:
  given found: Checkpointed[CompileArticlePipeline.Found] = deferred
