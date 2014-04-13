package org.huwtl.penfold.app.readstore.mongodb

import org.specs2.mutable._
import com.github.athieriot.EmbedConnection
import org.huwtl.penfold.domain.model._
import org.joda.time.DateTime
import org.specs2.specification.Scope
import org.huwtl.penfold.readstore._
import org.huwtl.penfold.readstore.NavigationDirection._
import com.mongodb.casbah.Imports._
import org.huwtl.penfold.readstore.EventRecord
import org.huwtl.penfold.domain.model.QueueId
import org.huwtl.penfold.domain.event.{JobCreatedEvent, FutureJobCreated, Event}
import org.huwtl.penfold.domain.model.AggregateId
import org.huwtl.penfold.readstore.PageRequest
import org.huwtl.penfold.domain.model.BoundQueue
import org.huwtl.penfold.domain.model.Binding
import org.specs2.matcher.DataTables
import org.huwtl.penfold.app.support.json.ObjectSerializer
import scala.util.Random
import org.huwtl.penfold.domain.model.Status.Waiting
import org.specs2.mock.Mockito
import org.huwtl.penfold.app.support.DateTimeSource

class MongoReadStoreTest extends Specification with DataTables with Mockito with EmbedConnection {
  sequential

  class context extends Scope {
    val queueId = QueueId("q1")
    val payload = Payload(Map("a" -> "123", "b" -> "1"))
    val created = new DateTime(2014, 2, 22, 12, 0, 0, 0)
    val triggerDate = new DateTime(2014, 2, 22, 12, 30, 0, 0)
    val mongoClient = MongoClient("localhost", embedConnectionPort())
    val database = mongoClient("wflowtest")
    val dateTimeSource = mock[DateTimeSource]
    val readStoreUpdater = new MongoReadStoreUpdater(database, new MongoEventTracker("tracker", database), new ObjectSerializer)
    val readStore = new MongoReadStore(database, new ObjectSerializer, dateTimeSource)

    def persist(events: List[Event]) = {
      Random.shuffle(events).zipWithIndex.foreach{
        case (event, index) => readStoreUpdater.handle(EventRecord(EventSequenceId(index + 1), event))
      }
    }

    def entry(aggregateId: String, triggerDate: DateTime) = {
      FutureJobCreated(AggregateId(aggregateId), AggregateVersion.init, created, Binding(List(BoundQueue(queueId))), triggerDate, payload)
    }

    def forwardFrom(lastEvent: JobCreatedEvent) = Some(LastKnownPageDetails(lastEvent.aggregateId, lastEvent.triggerDate.getMillis, Forward))

    def backFrom(lastEvent: JobCreatedEvent) = Some(LastKnownPageDetails(lastEvent.aggregateId, lastEvent.triggerDate.getMillis, Reverse))

    def setupEntries() = {
      val entries = List(
        entry("f", triggerDate.plusDays(2)),
        entry("e", triggerDate.plusDays(1)),
        entry("d", triggerDate.minusDays(0)),
        entry("c", triggerDate.minusDays(1)),
        entry("b", triggerDate.minusDays(2)),
        entry("a", triggerDate.minusDays(3))
      )
      persist(entries)
      entries
    }
  }

  "check connectivity" in new context {
    readStore.checkConnectivity.isLeft must beTrue
    mongoClient.close()
    readStore.checkConnectivity.isRight must beTrue
  }

  "retrieve waiting jobs to trigger" in new context {
    dateTimeSource.now returns triggerDate
    setupEntries()

    readStore.retrieveJobsToTrigger.toList.map(_.id.value) must beEqualTo(List("a", "b", "c", "d"))
  }

  "retrieve job by id" in new context {
    setupEntries()

    readStore.retrieveBy(AggregateId("a")).isDefined must beTrue
    readStore.retrieveBy(AggregateId("unknown")).isDefined must beFalse
  }

  "retrieve all jobs with filter" in new context {
    setupEntries()
    val pageRequest = PageRequest(2)

    readStore.retrieveBy(Filters(List(Filter("payload.a", "123"), Filter("payload.b", "1"))), pageRequest).entries.map(_.id.value) must beEqualTo(List("f", "e"))
    readStore.retrieveBy(Filters(List(Filter("payload.a", "123"))), pageRequest).entries.map(_.id.value) must beEqualTo(List("f", "e"))
    readStore.retrieveBy(Filters(List(Filter("payload.unknown", ""))), pageRequest).entries.map(_.id.value) must beEqualTo(List("f", "e"))
    readStore.retrieveBy(Filters(List(Filter("payload.unknown", "123"))), pageRequest).entries.map(_.id.value) must beEqualTo(Nil)
    readStore.retrieveBy(Filters(List(Filter("payload.a", "mismatch"), Filter("payload.b", "1"))), pageRequest).entries.map(_.id.value) must beEmpty
    readStore.retrieveBy(Filters(List(Filter("payload.a", "123"), Filter("payload.b", "mismatch"))), pageRequest).entries.map(_.id.value) must beEmpty
  }

  "pagination" should {
    "retrieve jobs by next page" in new context {
      val entries = setupEntries()

      "page"                                  | "expected"                         | "hasPrev" | "hasNext" |
      PageRequest(10)                         ! List("f", "e", "d", "c", "b", "a") ! false     ! false     |
      PageRequest(1)                          ! List("f")                          ! false     ! true      |
      PageRequest(0)                          ! Nil                                ! false     ! true      |
      PageRequest(0, forwardFrom(entries(0))) ! Nil                                ! false     ! true      |
      PageRequest(2, forwardFrom(entries(0))) ! List("e", "d")                     ! true      ! true      |
      PageRequest(2, forwardFrom(entries(2))) ! List("c", "b")                     ! true      ! true      |
      PageRequest(2, forwardFrom(entries(1))) ! List("d", "c")                     ! true      ! true      |
      PageRequest(2, forwardFrom(entries(5))) ! Nil                                ! false     ! false     |
      PageRequest(2, forwardFrom(entries(4))) ! List("a")                          ! true      ! false     |
      PageRequest(2, forwardFrom(entries(3))) ! List("b", "a")                     ! true      ! false     |> {(page, expected, hasPrev, hasNext) =>

        val pageResult = readStore.retrieveByQueue(queueId, Waiting, page)
        pageResult.entries.map(_.id) must beEqualTo(expected.map(AggregateId))
        pageResult.previousExists must beEqualTo(hasPrev)
        pageResult.nextExists must beEqualTo(hasNext)
      }
    }

    "retrieve jobs by previous page" in new context {
      val entries = setupEntries()

      "page"                                 | "expected"                         | "hasPrev" | "hasNext" |
        PageRequest(2, backFrom(entries(5))) ! List("c", "b")                     ! true      ! true      |
        PageRequest(0, backFrom(entries(5))) ! Nil                                ! true      ! false     |
        PageRequest(2, backFrom(entries(2))) ! List("f", "e")                     ! false     ! true      |
        PageRequest(2, backFrom(entries(3))) ! List("e", "d")                     ! true      ! true      |
        PageRequest(2, backFrom(entries(0))) ! Nil                                ! false     ! false     |
        PageRequest(2, backFrom(entries(3))) ! List("e", "d")                     ! true      ! true      |> {(page, expected, hasPrev, hasNext) =>

        val pageResult = readStore.retrieveByQueue(queueId, Waiting, page)
        pageResult.entries.map(_.id) must beEqualTo(expected.map(AggregateId))
        pageResult.previousExists must beEqualTo(hasPrev)
        pageResult.nextExists must beEqualTo(hasNext)
      }
    }
  }
}
