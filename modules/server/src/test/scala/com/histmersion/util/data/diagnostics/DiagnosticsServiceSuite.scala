package com.histmersion.util.data.diagnostics

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.TableSize
import com.histmersion.server.Deps
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.ByteCount

/** The figures read from the registry: only registered tables are sized, in the order the database gives them. */
class DiagnosticsServiceSuite extends munit.FunSuite:

  private def bytes(n: Long) = ByteCount.applyUnsafe(n)

  private val service = DiagnosticsService.InMemory(
    Deps.data,
    Map(DataOwner("library") -> DataFigures(List(DataCount(com.histmersion.util.data.diagnostics.DataCount.Noun("sources"), com.histmersion.util.data.diagnostics.DataCount.Count(4)), DataCount(com.histmersion.util.data.diagnostics.DataCount.Noun("findings"), com.histmersion.util.data.diagnostics.DataCount.Count(5))))),
    (com.histmersion.util.data.diagnostics.DiagnosticsService.DatabaseName("histmersion_test"), bytes(1000)),
    List(
      TableName("sources") -> bytes(300),
      TableName("not_registered") -> bytes(200),
      TableName("places") -> bytes(100)
    )
  )

  test("the largest tables are the registered ones, largest first, at most n"):
    assertEquals(service.largestTables(com.histmersion.util.data.diagnostics.DiagnosticsService.TableLimit(5)), List(TableSize(com.histmersion.api.TableSize.Name("sources"), com.histmersion.api.ByteCount(300)), TableSize(com.histmersion.api.TableSize.Name("places"), com.histmersion.api.ByteCount(100))))
    assertEquals(service.largestTables(com.histmersion.util.data.diagnostics.DiagnosticsService.TableLimit(1)), List(TableSize(com.histmersion.api.TableSize.Name("sources"), com.histmersion.api.ByteCount(300))))
    assertEquals(service.largestTables(com.histmersion.util.data.diagnostics.DiagnosticsService.TableLimit(0)), Nil)

  test("each owner's tables summed, every owner in the registry's order"):
    val sums = service.ownerTables.toMap
    assertEquals(service.ownerTables.map(_._1), Deps.data.owners)
    assertEquals(
      (
        sums(DataOwner("library")).value: Long,
        sums(DataOwner("catalogue")).value: Long,
        sums(DataOwner("media")).value: Long
      ),
      (300L, 100L, 0L)
    )

  test("an owner's counts, by noun; none for an owner that counts nothing"):
    assertEquals(service.figures(DataOwner("library")).count(com.histmersion.util.data.diagnostics.DataCount.Noun("findings")), Some(5L))
    assertEquals(service.figures(DataOwner("library")).count(com.histmersion.util.data.diagnostics.DataCount.Noun("articles")), None)
    assertEquals(service.figures(DataOwner("nobody")), DataFigures.empty)
