package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.{GraphLink, GraphNode}
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class GraphPanelSuite extends munit.FunSuite:

  private val g = Source.Graph(
    Source.Graph.Root("a2020_x"),
    Source.Graph.Depth(2),
    List(
      GraphNode(
        Citekey("a2020_x"),
        Some(Title("A")),
        Some(Year(2020)),
        Some(Source.Status.Current),
        GraphNode.Depth(0)
      ),
      GraphNode(
        Citekey("b2021_y"),
        Some(Title("B")),
        Some(Year(2021)),
        Some(Source.Status.Contested),
        GraphNode.Depth(1)
      ),
      GraphNode(Citekey("c2019_z"), Some(Title("C")), None, None, GraphNode.Depth(1))
    ),
    List(
      GraphLink(Citekey("a2020_x"), Citekey("b2021_y"), Edge.Kind.References),
      GraphLink(Citekey("c2019_z"), Citekey("a2020_x"), Edge.Kind.SupersededBy)
    )
  )

  test("every link between the graph's nodes is drawn, as index pairs") {
    assertEquals(
      GraphPanel.indexLinks(g).map((s, t, k) => (s, t, Edge.Kind.wireOf(k).value)),
      List((0, 1, "references"), (2, 0, "superseded_by"))
    )
  }

  test("a link to a node the graph does not carry is dropped, not drawn to nowhere") {
    val broken = g
      .modify(_.links)
      .setTo(g.links :+ GraphLink(Citekey("a2020_x"), Citekey("bronze_age_collapse"), Edge.Kind.References))
    assertEquals(GraphPanel.indexLinks(broken).size, 2)
  }

  test("the legend comes from the enums: only what the graph uses, in enum order") {
    val (statuses, kinds) = GraphPanel.legend(g)
    assertEquals(statuses, List(Source.Status.Current, Source.Status.Contested, Source.Status.Unassessed))
    assertEquals(kinds, List(Edge.Kind.References, Edge.Kind.SupersededBy))
  }

  test("a graph window key names its citekey") {
    assertEquals(
      WindowKey.parse(WindowKey.Wire("graph:a2020_x")),
      Some(WindowKey.Graph(WindowKey.Graph.Citekey("a2020_x")))
    )
    assertEquals(
      WindowKey.parse(WindowKey.Wire("source:a2020_x")),
      Some(WindowKey.Source(WindowKey.Source.Citekey("a2020_x")))
    )
  }
