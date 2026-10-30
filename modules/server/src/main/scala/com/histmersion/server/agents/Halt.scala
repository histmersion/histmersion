package com.histmersion.server.agents

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*

/** Why a run stopped before its pipeline finished. A value, threaded through the pipeline's for-comprehension: a step
  * either produces its output or halts the run.
  */
enum Halt:
  /** `resumeAt`: when the run may go on by itself (an allowance window freeing); None = a person resumes it. */
  case Pause(reason: PauseReason, detail: String, resumeAt: Option[java.time.Instant] = None)
  case Cancel
  case Fail(message: String)

  /** The run waits for a person to decide approval `id` (a blocking step); it holds no worker, and goes back to the
    * queue when the approval is decided.
    */
  case Wait(id: Long, what: String)
