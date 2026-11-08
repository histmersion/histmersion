package com.histmersion
package api.jobs
import io.github.iltotore.iron.*

/** What a kind of job does, in a sentence or two (the jobs window's tooltip). */
type JobKindMeaning = JobKindMeaning.T
object JobKindMeaning extends Newtype[String, Pure]
