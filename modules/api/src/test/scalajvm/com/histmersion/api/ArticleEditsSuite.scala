package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.research.Article.Edits.*

/** Edits anchored on an article's own text: the four operations, the anchor rules (exactly once, whitespace
  * normalised), the order they apply in, and skipped edits.
  */
class ArticleEditsSuite extends munit.FunSuite:

  private val BodyText =
    """Tin reached the Aegean by sea [[knapp2016_tin]].
      |
      |The ship carried copper [[knapp2016_tin]].
      |
      |## Disagreements
      |
      |Two dates are argued [[knapp2016_tin]].
      |""".stripMargin

  private val Body = com.histmersion.api.research.Article.Body(BodyText)

  private def e(op: Op, anchor: Anchor, text: Option[NewText] = None) = EditAnswer(op, anchor, text)

  test("replace, insertAfter, insertBefore and delete each change only their anchor"):
    assertEquals(
      applyAll(Body, List(e(Op.Replace, Anchor("by sea"), Some(NewText("by sea, from the east"))))).body,
      BodyText.replace("by sea", "by sea, from the east")
    )
    assertEquals(
      applyAll(
        Body,
        List(e(Op.InsertAfter, Anchor("Two dates are argued [[knapp2016_tin]]."), Some(NewText("\n\nA third is not [[knapp2016_tin]]."))))
      ).body,
      BodyText.replace(
        "Two dates are argued [[knapp2016_tin]].",
        "Two dates are argued [[knapp2016_tin]].\n\nA third is not [[knapp2016_tin]]."
      )
    )
    assertEquals(
      applyAll(Body, List(e(Op.InsertBefore, Anchor("Two dates"), Some(NewText("Probably "))))).body,
      BodyText.replace("Two dates", "Probably Two dates")
    )
    // a deleted paragraph leaves one blank line between its neighbours, not three
    val gone = applyAll(Body, List(e(Op.Delete, Anchor("The ship carried copper [[knapp2016_tin]].")))).body
    assertEquals(gone, BodyText.replace("The ship carried copper [[knapp2016_tin]].\n\n", ""))
    // a sentence deleted mid-paragraph leaves one space
    assertEquals(applyAll(com.histmersion.api.research.Article.Body("A b. C d. E f."), List(e(Op.Delete, Anchor("C d.")))).body, "A b. E f.")

  // the Codex strict schema sends null (or "") for a field it has nothing for: a replace with no text
  // used to read as a DELETE of its anchor
  test("a replace with null or empty text is a problem, never a delete; an anchor that is empty or null too"):
    for t <- List(None, Some(NewText("")), Some(NewText("   "))) do
      val r = applyAll(Body, List(e(Op.Replace, Anchor("by sea"), t)))
      assertEquals(r.body, Body, s"text ${t.fold("null")(x => s"'${x.value}'")}: nothing changed")
      assert(
        r.problems.head.value.endsWith("a replace with no new text — give the text, or use delete to remove the passage"),
        r.problems
      )
    assert(applyAll(Body, List(EditAnswer(Op.Delete, Anchor(null), None))).problems.head.value.contains("no anchor"))
    assert(applyAll(Body, List(e(Op.InsertBefore, Anchor("by sea"), None))).problems.head.value.endsWith("no text to insert"))
    // from the wire: "text": null decodes as absent
    val js = """[{"op":"replace","anchor":"by sea","text":null}]"""
    val decoded = io.circe.parser.decode[List[EditAnswer]](js).fold(e => fail(e.getMessage), identity)
    assert(applyAll(Body, decoded).problems.head.value.contains("a replace with no new text"))

  test("an anchor is matched with whitespace normalised, and nothing else"):
    val wrapped = com.histmersion.api.research.Article.Body("Tin reached the Aegean\nby sea [[knapp2016_tin]].")
    assertEquals(
      applyAll(wrapped, List(e(Op.Replace, Anchor("the Aegean   by sea"), Some(NewText("the Aegean by ship"))))).body,
      "Tin reached the Aegean by ship [[knapp2016_tin]]."
    )
    assert(locate(wrapped, Anchor("the aegean by sea")).isLeft, "case is not normalised")

  test("a missing or ambiguous anchor is a problem naming the edit and the anchor's head; the edit is skipped"):
    val r = applyAll(
      Body,
      List(e(Op.Replace, Anchor("by land"), Some(NewText("by sea"))), e(Op.Delete, Anchor("[[knapp2016_tin]].")), e(Op.Replace, Anchor("Tin"), Some(NewText("Tin metal"))))
    )
    assertEquals(r.body, BodyText.replace("Tin reached", "Tin metal reached"), "the third edit still applies")
    val ps = r.problems.map(_.value)
    assertEquals(ps.size, 2, ps)
    assert(ps.head.startsWith("edit 1 (replace \"by land\"): the anchor is not in the article"), ps.head)
    assert(ps(1).startsWith("edit 2 (delete \"[[knapp2016_tin]].\"): the anchor occurs 3 times"), ps(1))
    assertEquals(r.results.map(_.applied), List(false, false, true))
    assert(applyAll(Body, List(e(Op.InsertAfter, Anchor("  "), Some(NewText("x"))))).problems.head.value.contains("no anchor"))
    assert(applyAll(Body, List(e(Op.InsertAfter, Anchor("by sea"), Some(NewText(" "))))).problems.head.value.endsWith("no text to insert"))

  test("edits apply in order: a later anchor is looked up in the text the earlier ones produced"):
    val es = List(e(Op.Replace, Anchor("by sea"), Some(NewText("by the sea route"))), e(Op.InsertAfter, Anchor("the sea route"), Some(NewText(" from Cyprus"))))
    assertEquals(applyAll(Body, es).body, BodyText.replace("by sea", "by the sea route from Cyprus"))
    // the other order: the second anchor does not exist yet
    assert(applyAll(Body, es.reverse).problems.exists(_.value.startsWith("edit 1 (insertAfter \"the sea route\")")))

  test("a refused edit is skipped with its reason; the rest apply"):
    val es = List(e(Op.InsertAfter, Anchor("by sea"), Some(NewText(" [[ghost2020_x]]"))), e(Op.Replace, Anchor("Two dates"), Some(NewText("Three dates"))))
    val r = applyAll(Body, es, a => Option.when(a.text.exists(_.value.contains("ghost")))(com.histmersion.api.research.Article.Edits.EditResult.Problem("its text is refused: ghost")))
    assertEquals(r.body, BodyText.replace("Two dates", "Three dates"))
    assertEquals(r.problems, List("edit 1 (insertAfter \"by sea\"): its text is refused: ghost"))

  test("a check request is edits or a body, not both and not neither"):
    assert(ArticleCheckRequest().draft.isLeft)
    assert(ArticleCheckRequest(List(e(Op.Delete, Anchor("x"))), Some(com.histmersion.api.research.Article.Body("b"))).draft.isLeft)
    assertEquals(ArticleCheckRequest(body = Some(com.histmersion.api.research.Article.Body("b"))).draft, Right(Draft.Body(com.histmersion.api.research.Article.Edits.Draft.Body.Text("b"))))
    assertEquals(head(Anchor("a  long\nanchor " * 10), com.histmersion.api.research.Article.Edits.HeadLimit(20)), "a long anchor a long…")

  test("the answer's JSON: op by its wire name, text optional"):
    val js = """[{"op":"insertAfter","anchor":"by sea","text":" x"},{"op":"delete","anchor":"y"}]"""
    assertEquals(
      io.circe.parser.decode[List[EditAnswer]](js),
      Right(List(e(Op.InsertAfter, Anchor("by sea"), Some(NewText(" x"))), e(Op.Delete, Anchor("y"))))
    )
    assert(io.circe.parser.decode[List[EditAnswer]]("""[{"op":"rewrite","anchor":"y"}]""").isLeft)
