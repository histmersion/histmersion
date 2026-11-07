package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.{GraphLink, GraphNode}
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source

class GraphPanelSuite extends munit.FunSuite:

  private val g = com.histmersion.api.library.Source.Graph(
    com.histmersion.api.library.Source.Graph.Root("a2020_x"),
    com.histmersion.api.library.Source.Graph.Depth(2),
    List(
      GraphNode(com.histmersion.api.library.Citekey("a2020_x"), Some(com.histmersion.api.library.Title("A")), Some(com.histmersion.api.catalogue.Year(2020)), Some(Source.Status.Current), com.histmersion.api.library.GraphNode.Depth(0)),
      GraphNode(com.histmersion.api.library.Citekey("b2021_y"), Some(com.histmersion.api.library.Title("B")), Some(com.histmersion.api.catalogue.Year(2021)), Some(Source.Status.Contested), com.histmersion.api.library.GraphNode.Depth(1)),
      GraphNode(com.histmersion.api.library.Citekey("c2019_z"), Some(com.histmersion.api.library.Title("C")), None, None, com.histmersion.api.library.GraphNode.Depth(1))
    ),
    List(GraphLink(com.histmersion.api.library.Citekey("a2020_x"), com.histmersion.api.library.Citekey("b2021_y"), com.histmersion.api.library.Edge.Kind("references")), GraphLink(com.histmersion.api.library.Citekey("c2019_z"), com.histmersion.api.library.Citekey("a2020_x"), com.histmersion.api.library.Edge.Kind("superseded_by")))
  )

  test("every link between the graph's nodes is drawn, as index pairs") {
    assertEquals(GraphPanel.indexLinks(g), List((0, 1, "references"), (2, 0, "superseded_by")))
  }

  test("a link to a node the graph does not carry is dropped, not drawn to nowhere") {
    val broken = g.modify(_.links).setTo(g.links :+ GraphLink("a2020_x", "bronze_age_collapse", "references"))
    assertEquals(GraphPanel.indexLinks(broken).size, 2)
  }

  test("the legend comes from the enums: only what the graph uses, in enum order") {
    val (statuses, kinds) = GraphPanel.legend(g)
    assertEquals(statuses, List(Source.Status.Current, Source.Status.Contested, Source.Status.Unassessed))
    assertEquals(kinds, List(Edge.Kind.References, Edge.Kind.SupersededBy))
  }

  test("a graph window key names its citekey") {
    assertEquals(WindowKey.parse(com.histmersion.web.WindowKey.Wire("graph:a2020_x")), Some(WindowKey.Graph(com.histmersion.web.WindowKey.Graph.Citekey("a2020_x"))))
    assertEquals(WindowKey.parse(com.histmersion.web.WindowKey.Wire("source:a2020_x")), Some(WindowKey.Source(com.histmersion.web.WindowKey.Source.Citekey("a2020_x"))))
  }
