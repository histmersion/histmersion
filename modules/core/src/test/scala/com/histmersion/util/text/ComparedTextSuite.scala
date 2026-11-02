package com.histmersion.util.text

/** The form two texts are compared in. */
class ComparedTextSuite extends munit.FunSuite:

  test("ligatures, quotes, dashes, soft hyphens, line-break hyphenation, Markdown marks and spaces"):
    assertEquals(ComparedText.normalise("The ﬁrst  “Œuvre” — **bold**"), "the first \"oeuvre\" - bold")
    assertEquals(ComparedText.normalise("exam-\n  ple"), "example")
    assertEquals(ComparedText.normalise("co­operate"), "cooperate")
    assertEquals(ComparedText.normalise("# Heading\n> quoted"), "heading quoted")
