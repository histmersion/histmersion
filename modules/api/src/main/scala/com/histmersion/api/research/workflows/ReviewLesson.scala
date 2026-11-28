package com.histmersion
package api.research.workflows

import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec

final case class ReviewLesson(text: ReviewLesson.Text, source: LessonSource, run: ReviewLesson.Run, round: ReviewLesson.Round) derives JsonCodec

object ReviewLesson:
  export RecheckedFindings.Lessons as Text
  export Merged.Round
  export RunInfo.Id as Run
