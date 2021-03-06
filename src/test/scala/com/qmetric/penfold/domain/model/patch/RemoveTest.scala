package com.qmetric.penfold.domain.model.patch

import org.specs2.matcher.DataTables
import org.specs2.mutable.SpecificationWithJUnit

class RemoveTest extends SpecificationWithJUnit with DataTables {
  "apply remove operation" in {
    "existing"                       ||  "operation"              || "expected"                  |
      Map("a" -> Map("c" -> "2"))    !!  Remove("/a/c")           !! Map("a" -> Map.empty)       |
      Map("a" -> Map("c" -> "2"))    !!  Remove("/a")             !! Map.empty                   |
      Map("list" -> List("a", "b"))  !!  Remove("/list/0")        !! Map("list" -> List("b"))    |
      Map("list" -> List("a", "b"))  !!  Remove("/list/1")        !! Map("list" -> List("a"))    |> {
      (existing, operation, expected) =>
        operation.exec(existing.asInstanceOf[Map[String, Any]]) must beEqualTo(expected)
    }
  }

  "ignore when path item to delete not found" in {
    Remove("/unknown").exec(Map("a" -> "1")) must beEqualTo(Map("a" -> "1"))
    Remove("/a/1/unknown").exec(Map("a" -> List("1", "2"))) must beEqualTo(Map("a" -> List("1", "2")))
  }
}
