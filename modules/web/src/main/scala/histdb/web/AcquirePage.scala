package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object AcquirePage:

  private val items    = Var[List[Queue.QueueItem]](Nil)
  private val loading  = Var(true)
  private val showDone = Var(false)

  def init(): Unit =
    val app = div(
      cls := "acquire-page",
      div(
        cls := "acquire-header",
        h1(
          a(href := "/", cls := "back-link", "hist-db"),
          span(cls := "muted", " · acquisition queue")
        ),
        label(
          cls := "show-done-toggle",
          input(
            typ := "checkbox",
            controlled(
              checked <-- showDone.signal,
              onClick.mapToChecked --> { v =>
                showDone.set(v)
                load()
              }
            )
          ),
          " show completed"
        )
      ),
      child <-- loading.signal.map {
        case true  => div(cls := "acquire-loading", "Loading…")
        case false => emptyNode
      },
      child <-- items.signal.combineWith(loading.signal).map { (its, busy) =>
        if busy then emptyNode
        else if its.isEmpty then div(cls := "acquire-empty", "Nothing in the acquisition queue.")
        else renderGroups(its)
      }
    )

    renderOnDomContentLoaded(dom.document.getElementById("app"), app)
    load()

  private def load(): Unit =
    loading.set(true)
    ApiClient.queueList("acquire", showDone.now()).foreach { listing =>
      items.set(listing.items.filterNot(_.status == Queue.Note))
      loading.set(false)
    }

  private def renderGroups(its: List[Queue.QueueItem]): HtmlElement =
    val groups = its
      .groupBy(_.topic.getOrElse("Uncategorized"))
      .toList
      .sortBy((_, is) => is.map(_.id).min)

    div(
      cls := "acquire-groups",
      groups.map { (topic, groupItems) =>
        val parts = topic.split(" > ")
        div(
          cls := "acquire-group",
          h2(cls := "group-topic", parts.last.replaceAll("^\\*\\*|\\*\\*$", "")),
          if parts.length > 1 then
            span(cls := "group-path", parts.init.mkString(" › "))
          else emptyNode,
          ul(
            cls := "acquire-items",
            groupItems.sortBy(_.id).map(renderItem)
          )
        )
      }
    )

  private def renderItem(item: Queue.QueueItem): HtmlElement =
    val done = item.status == Queue.Done
    li(
      cls := (if done then "acquire-item done" else "acquire-item"),
      div(
        cls := "item-check",
        input(
          typ := "checkbox",
          checked := done,
          onChange.mapToChecked --> { checked =>
            val newStatus = if checked then "done" else "open"
            ApiClient.queuePatch(item.id, Queue.QueuePatch(status = Some(newStatus))).foreach { _ =>
              load()
            }
          }
        )
      ),
      div(
        cls := "item-body",
        div(cls := "item-text", item.text),
        item.reason.filter(_.nonEmpty).map { r =>
          div(cls := "item-reason", r)
        }.getOrElse(emptyNode)
      )
    )
