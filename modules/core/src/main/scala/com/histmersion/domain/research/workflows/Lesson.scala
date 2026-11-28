package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.research.workflows.LessonSource
import com.histmersion.domain.automation.Run
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.automation
import com.histmersion.domain.research.workflows.Recheck
import com.histmersion.domain.research.workflows.Merged

final case class Lesson(text: Lesson.Text, source: LessonSource, run: Lesson.Run, round: Lesson.Round)

object Lesson:
  export Recheck.Lessons as Text
  export automation.Run.Id as Run
  export Merged.Round
