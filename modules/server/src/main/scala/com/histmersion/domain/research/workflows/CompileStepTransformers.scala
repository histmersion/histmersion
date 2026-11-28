package com.histmersion
package domain.research.workflows

import com.histmersion.util.json.JsonCodec.given
import com.histmersion.domain.automation.agents.Checkpointed
import com.histmersion.domain.library.workflows.TwinTransformers
import com.histmersion.api.research.workflows as wire

object CompileStepTransformers extends CompileRecords, TwinTransformers:
  override given found: Checkpointed[CompileArticlePipeline.Found] =
    stored[CompileArticlePipeline.Found, wire.FoundSources]
