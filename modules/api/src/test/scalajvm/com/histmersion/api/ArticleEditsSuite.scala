package com.histmersion.api

import com.histmersion.api.ArticleEdits.*

/** Edits anchored on an article's own text: the four operations, the anchor rules (exactly once, whitespace
  * normalised), the order they apply in, and skipped edits.
  */
class ArticleEditsSuite extends munit.FunSuite:

  private val Body =
    """Tin reached the Aegean by sea [[knapp2016_tin]].
      |
      |The ship carried copper [[knapp2016_tin]].
      |
      |## Disagreements
      |
      |Two dates are argued [[knapp2016_tin]].
      |""".stripMargin

  private def e(op: Op, anchor: String, text: String = null) = EditAnswer(op, anchor, Option(text))

  test("replace, insertAfter, insertBefore and delete each change only their anchor"):
    assertEquals(
      applyAll(Body, List(e(Op.Replace, "by sea", "by sea, from the east"))).body,
      Body.replace("by sea", "by sea, from the east")
    )
    assertEquals(
      applyAll(
        Body,
        List(e(Op.InsertAfter, "Two dates are argued [[knapp2016_tin]].", "\n\nA third is not [[knapp2016_tin]]."))
      ).body,
      Body.replace(
        "Two dates are argued [[knapp2016_tin]].",
        "Two dates are argued [[knapp2016_tin]].\n\nA third is not [[knapp2016_tin]]."
      )
    )
    assertEquals(
      applyAll(Body, List(e(Op.InsertBefore, "Two dates", "Probably "))).body,
      Body.replace("Two dates", "Probably Two dates")
    )
    // a deleted paragraph leaves one blank line between its neighbours, not three
    val gone = applyAll(Body, List(e(Op.Delete, "The ship carried copper [[knapp2016_tin]]."))).body
    assertEquals(gone, Body.replace("The ship carried copper [[knapp2016_tin]].\n\n", ""))
    // a sentence deleted mid-paragraph leaves one space
    assertEquals(applyAll("A b. C d. E f.", List(e(Op.Delete, "C d."))).body, "A b. E f.")

  // the Codex strict schema sends null (or "") for a field it has nothing for: a replace with no text
  // used to read as a DELETE of its anchor
  test("a replace with null or empty text is a problem, never a delete; an anchor that is empty or null too"):
    for t <- List(null, "", "   ") do
      val r = applyAll(Body, List(e(Op.Replace, "by sea", t)))
      assertEquals(r.body, Body, s"text ${Option(t).fold("null")(x => s"'$x'")}: nothing changed")
      assert(
        r.problems.head.endsWith("a replace with no new text — give the text, or use delete to remove the passage"),
        r.problems
      )
    assert(applyAll(Body, List(EditAnswer(Op.Delete, null, None))).problems.head.contains("no anchor"))
    assert(applyAll(Body, List(e(Op.InsertBefore, "by sea", null))).problems.head.endsWith("no text to insert"))
    // from the wire: "text": null decodes as absent
    val js = """[{"op":"replace","anchor":"by sea","text":null}]"""
    val decoded = io.circe.parser.decode[List[EditAnswer]](js).fold(e => fail(e.getMessage), identity)
    assert(applyAll(Body, decoded).problems.head.contains("a replace with no new text"))

  test("an anchor is matched with whitespace normalised, and nothing else"):
    val wrapped = "Tin reached the Aegean\nby sea [[knapp2016_tin]]."
    assertEquals(
      applyAll(wrapped, List(e(Op.Replace, "the Aegean   by sea", "the Aegean by ship"))).body,
      "Tin reached the Aegean by ship [[knapp2016_tin]]."
    )
    assert(locate(wrapped, "the aegean by sea").isLeft, "case is not normalised")

  test("a missing or ambiguous anchor is a problem naming the edit and the anchor's head; the edit is skipped"):
    val r = applyAll(
      Body,
      List(e(Op.Replace, "by land", "by sea"), e(Op.Delete, "[[knapp2016_tin]]."), e(Op.Replace, "Tin", "Tin metal"))
    )
    assertEquals(r.body, Body.replace("Tin reached", "Tin metal reached"), "the third edit still applies")
    val ps = r.problems
    assertEquals(ps.size, 2, ps)
    assert(ps.head.startsWith("edit 1 (replace \"by land\"): the anchor is not in the article"), ps.head)
    assert(ps(1).startsWith("edit 2 (delete \"[[knapp2016_tin]].\"): the anchor occurs 3 times"), ps(1))
    assertEquals(r.results.map(_.applied), List(false, false, true))
    assert(applyAll(Body, List(e(Op.InsertAfter, "  ", "x"))).problems.head.contains("no anchor"))
    assert(applyAll(Body, List(e(Op.InsertAfter, "by sea", " "))).problems.head.endsWith("no text to insert"))

  test("edits apply in order: a later anchor is looked up in the text the earlier ones produced"):
    val es = List(e(Op.Replace, "by sea", "by the sea route"), e(Op.InsertAfter, "the sea route", " from Cyprus"))
    assertEquals(applyAll(Body, es).body, Body.replace("by sea", "by the sea route from Cyprus"))
    // the other order: the second anchor does not exist yet
    assert(applyAll(Body, es.reverse).problems.exists(_.startsWith("edit 1 (insertAfter \"the sea route\")")))

  test("a refused edit is skipped with its reason; the rest apply"):
    val es = List(e(Op.InsertAfter, "by sea", " [[ghost2020_x]]"), e(Op.Replace, "Two dates", "Three dates"))
    val r = applyAll(Body, es, a => Option.when(a.text.exists(_.contains("ghost")))("its text is refused: ghost"))
    assertEquals(r.body, Body.replace("Two dates", "Three dates"))
    assertEquals(r.problems, List("edit 1 (insertAfter \"by sea\"): its text is refused: ghost"))

  test("a check request is edits or a body, not both and not neither"):
    assert(ArticleCheckRequest().draft.isLeft)
    assert(ArticleCheckRequest(List(e(Op.Delete, "x")), Some("b")).draft.isLeft)
    assertEquals(ArticleCheckRequest(body = Some("b")).draft, Right(Draft.Body("b")))
    assertEquals(head("a  long\nanchor " * 10, 20), "a long anchor a long…")

  test("the answer's JSON: op by its wire name, text optional"):
    val js = """[{"op":"insertAfter","anchor":"by sea","text":" x"},{"op":"delete","anchor":"y"}]"""
    assertEquals(
      io.circe.parser.decode[List[EditAnswer]](js),
      Right(List(e(Op.InsertAfter, "by sea", " x"), e(Op.Delete, "y")))
    )
    assert(io.circe.parser.decode[List[EditAnswer]]("""[{"op":"rewrite","anchor":"y"}]""").isLeft)
