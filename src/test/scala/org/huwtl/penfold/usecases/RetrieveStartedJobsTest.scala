package org.huwtl.penfold.usecases

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.huwtl.penfold.domain.{Status, JobStore}

class RetrieveStartedJobsTest extends Specification with Mockito {
  val jobStore = mock[JobStore]

  val retrieveStartedJobs = new RetrieveStartedJobs(jobStore)

  "retrieve started jobs" in {
    retrieveStartedJobs.retrieve()

    there was one(jobStore).retrieve(Status.Started)
  }
}