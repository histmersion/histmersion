package com.histmersion.web

import com.histmersion.core.*

class GraphPanelSuite extends munit.FunSuite:

  private val g = SourceGraph("a2020_x", 2,
    List(GraphNode("a2020_x", Some("A"), Some(2020), Some("current"), 0),
      GraphNode("b2021_y", Some("B"), Some(2021), Some("contested"), 1),
      GraphNode("c2019_z", Some("C"), None, None, 1)),
    List(GraphLink("a2020_x", "b2021_y", "references"), GraphLink("c2019_z", "a2020_x", "superseded_by")))

  test("every link between the graph's nodes is drawn, as index pairs") {
    assertEquals(GraphPanel.indexLinks(g), List((0, 1, "references"), (2, 0, "superseded_by")))
  }

  test("a link to a node the graph does not carry is dropped, not drawn to nowhere") {
    val broken = g.copy(links = g.links :+ GraphLink("a2020_x", "bronze_age_collapse", "references"))
    assertEquals(GraphPanel.indexLinks(broken).size, 2)
  }

  test("the legend comes from the enums: only what the graph uses, in enum order") {
    val (statuses, kinds) = GraphPanel.legend(g)
    assertEquals(statuses, List(SourceStatus.Current, SourceStatus.Contested, SourceStatus.Unassessed))
    assertEquals(kinds, List(EdgeKind.References, EdgeKind.SupersededBy))
  }

  test("a graph window key names its citekey") {
    assertEquals(WindowManager.graphCitekey("graph:a2020_x"), Some("a2020_x"))
    assertEquals(WindowManager.graphCitekey("source:a2020_x"), None)
  }
