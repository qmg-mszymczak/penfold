package com.qmetric.penfold.app

import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.mock.Mockito
import com.qmetric.penfold.readstore.EventNotifiers

class ActorBasedEventNotifiersTest extends SpecificationWithJUnit with Mockito {
  val eventNotifiersDelegate = mock[EventNotifiers]

  val eventNotifiers = new ActorBasedEventNotifiers(eventNotifiersDelegate, noOfWorkers = 1)

  "worker delegates notification of events and waits until completed" in {
    eventNotifiers.notifyAllOfEvents()

    there was one(eventNotifiersDelegate).notifyAllOfEvents()
  }
}
