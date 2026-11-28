package com.histmersion
package domain.reviewledger

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.reviewledger.LessonSource
import com.histmersion.domain.automation.Run
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.automation
import com.histmersion.domain.reviewarticle.Recheck
import com.histmersion.domain.reviewarticle.Merged

final case class Lesson(text: Lesson.Text, source: LessonSource, run: Lesson.Run, round: Lesson.Round) derives JsonCodec

object Lesson:
  export Recheck.Lessons as Text
  export automation.Run.Id as Run
  export Merged.Round
