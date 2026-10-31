package com.histmersion.util.jobs

import io.github.iltotore.iron.*

/** What a kind of job is called for a person ("Download the background map"). */
type JobKindName = JobKindName.T
object JobKindName extends RefinedType[String, Pure]
