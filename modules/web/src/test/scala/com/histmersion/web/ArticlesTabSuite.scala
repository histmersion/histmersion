package com.histmersion
package web

import com.histmersion.api.research.ReviewState

/** The articles list's review badge: one class per state, the classes the article page's badge uses. */
class ArticlesTabSuite extends munit.FunSuite:

  test("each review state has its own badge class"):
    assertEquals(ArticlesTab.reviewClass(ReviewState.InReview), "job-state review-state state-in-review")
    assertEquals(ReviewState.values.map(ArticlesTab.reviewClass).distinct.length, ReviewState.values.length)
