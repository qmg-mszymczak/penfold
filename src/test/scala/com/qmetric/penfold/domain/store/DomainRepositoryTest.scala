package com.qmetric.penfold.domain.store

import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.mock.Mockito
import com.qmetric.penfold.readstore.EventNotifiers
import com.qmetric.penfold.domain.model._
import com.qmetric.penfold.domain.model.AggregateId
import com.qmetric.penfold.domain.model.Payload
import com.qmetric.penfold.domain.event.{TaskTriggered, TaskCreated}
import org.joda.time.DateTime
import org.specs2.specification.Scope

class DomainRepositoryTest extends SpecificationWithJUnit with Mockito {
  class context extends Scope {
    val aggregateId = AggregateId("a1")

    val binding = QueueBinding(QueueId("q1"))

    val timestamp = DateTime.now

    val createdTask = Task.create(aggregateId, binding, Payload.empty, None)

    val eventStore = mock[EventStore]

    val notifiers = mock[EventNotifiers]

    val repo = new DomainRepository(eventStore, notifiers)
  }

  "append new aggregate root events to event store" in new context {
    val task = repo.add(createdTask)

    task.uncommittedEvents must beEmpty
    there was one(notifiers).notifyAllOfEvents()
  }

  "load aggregate by id" in new context {
    eventStore.retrieveBy(aggregateId) returns List(
      TaskCreated(aggregateId, AggregateVersion.init, timestamp, binding, timestamp, Payload.empty, timestamp.getMillis),
      TaskTriggered(aggregateId, AggregateVersion.init.next, timestamp)
    )

    val task = repo.getById[Task](aggregateId)

    task.status must beEqualTo(Status.Ready)
  }

  "throw exception when no aggregate found with id" in new context {
    val unknownAggregateId = AggregateId("unknown")
    eventStore.retrieveBy(unknownAggregateId) returns Nil

    repo.getById[Task](AggregateId("unknown")) must throwA[IllegalArgumentException]
  }
}
