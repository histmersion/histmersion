package com.histmersion.util.text

import com.histmersion.testsupport.ComparedByValue.given

/** The form two texts are compared in. */
class ComparedTextSuite extends munit.FunSuite:

  test("ligatures, quotes, dashes, soft hyphens, line-break hyphenation, Markdown marks and spaces"):
    assertEquals(ComparedText.normalise(com.histmersion.util.text.ComparedText.NormaliseS("The ﬁrst  “Œuvre” — **bold**")), "the first \"oeuvre\" - bold")
    assertEquals(ComparedText.normalise(com.histmersion.util.text.ComparedText.NormaliseS("exam-\n  ple")), "example")
    assertEquals(ComparedText.normalise(com.histmersion.util.text.ComparedText.NormaliseS("co­operate")), "cooperate")
    assertEquals(ComparedText.normalise(com.histmersion.util.text.ComparedText.NormaliseS("# Heading\n> quoted")), "heading quoted")
