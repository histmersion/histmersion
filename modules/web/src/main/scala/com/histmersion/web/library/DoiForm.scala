package com.histmersion
package web.library
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.web.{ApiClient, Auth, ErrorLine, SourceLink}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.library.Ingest
import com.histmersion.lint.{LintRule, tolerated}

/** "Add by DOI": a DOI or an arXiv id, looked up and brought in through the legal open-access chain only
  * ([[DoiIngest]]); the answer says what was found and links to the new source.
  */
object DoiForm:
  private enum State derives CanEqual:
    case Idle
    case Adding
    case Failed(message: Problem)
    case Added(report: Ingest.Report)

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def pane: HtmlElement =
    val typed = Var("")
    val state = Var(State.Idle)
    def submit(): Unit =
      DoiIngest.request(typed.now()) match
        case Left(m)    => state.set(State.Failed(m))
        case Right(req) =>
          state.set(State.Adding)
          ApiClient
            .ingestId(req)
            .onComplete(r =>
              state.set(
                r.fold(
                  e =>
                    State.Failed(
                      Problem.Unclassified(
                        Legacy
                          .Text(s"Could not add it: ${Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.toString)}")
                      )
                    ),
                  State.Added(_)
                )
              )
            )
    div(
      cls := "tool-pane doi-pane",
      p(
        cls := "doi-intro",
        "Histmersion looks the work up, fetches a legal open-access copy when there is one and reads its text. " +
          "Without one, the source is added from its record alone; upload the file once you have it."
      ),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("add a source")
        else
          form(
            cls := "doi-form toolbar",
            onSubmit.preventDefault --> (_ => submit()),
            label(forId := "doi-input", cls := "doi-label", "DOI or arXiv id"),
            div(
              cls := "btn-group",
              input(
                typ := "text",
                cls := "field doi-input",
                idAttr := "doi-input",
                autoComplete := "off",
                placeholder := "10.1017/S0003598X00000001",
                spellCheck := false,
                controlled(value <-- typed.signal, onInput.mapToValue --> typed.writer)
              ),
              button(
                typ := "submit",
                cls := "btn btn--primary doi-add",
                disabled <-- state.signal.map(_ == State.Adding),
                child.text <-- state.signal.map(s => if s == State.Adding then "Adding…" else "Add")
              )
            )
          )
      },
      child <-- state.signal.map {
        case State.Idle | State.Adding => emptyNode
        case State.Failed(m)           => ErrorLine.text(m)
        case State.Added(r)            => result(r)
      }
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def result(r: Ingest.Report): HtmlElement =
    div(
      cls := "doi-result",
      role := "status",
      p(
        strong(if r.created.value then "Added: " else "Already in the library: "),
        a(href := SourceLink.href(r.citekey), r.title.map(_.value).getOrElse(r.citekey.value))
      ),
      p(
        cls := "muted",
        if r.pdf.isDefined then
          "A legal open-access copy was found; its text is being read (Research › Activity › Background jobs)."
        else "No legal open-access copy was found: the source holds its record only."
      )
    )
