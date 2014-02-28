package org.huwtl.penfold.app.web

import org.huwtl.penfold.app.support.hal.{HalQueueFormatter, HalJobFormatter}
import java.net.URI
import org.json4s.jackson.JsonMethods._
import scala.io.Source._
import org.scalatra.test.specs2.MutableScalatraSpec
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import scala.Some
import org.huwtl.penfold.domain.model.{QueueName, Status, Payload, Id}
import org.huwtl.penfold.query.{PageResult, PageRequest, JobRecord, QueryRepository}
import org.huwtl.penfold.command.CommandDispatcher
import org.huwtl.penfold.app.support.json.ObjectSerializer

class QueueResourceTest extends MutableScalatraSpec with Mockito {
  sequential

  val queueName = QueueName("abc")

  val payload = Payload(Map())

  val created = new DateTime(2014, 2, 14, 12, 0, 0, 0)

  val triggerDate = new DateTime(2014, 2, 25, 14, 0, 0, 0)

  val queryRepository = mock[QueryRepository]

  val commandDispatcher = mock[CommandDispatcher]

  addServlet(new QueueResource(queryRepository, commandDispatcher, new ObjectSerializer, new HalQueueFormatter(new URI("http://host/queues"), new HalJobFormatter(new URI("http://host/jobs"), new URI("http://host/queues")))), "/queues/*")

  "return 200 with hal+json formatted queue response" in {
    val expectedJob1 = JobRecord(Id("1"), created, queueName, Status.Triggered, triggerDate, payload)
    val expectedJob2 = JobRecord(Id("2"), created, queueName, Status.Triggered, triggerDate, payload)
    queryRepository.retrieveBy(queueName, Status.Triggered, PageRequest(0, 10)) returns PageResult(List(expectedJob2, expectedJob1), earlierExists = false, laterExists = true)

    get("/queues/abc/triggered") {
      status must beEqualTo(200)
      parse(body) must beEqualTo(jsonFromFile("fixtures/hal/halFormattedQueue.json"))
    }
  }

  "return 404 when queue status not recognised" in {
    get("/queues/abc/notExists") {
      status must beEqualTo(404)
    }
  }

  "return 200 with hal+json formatted queue entry response" in {
    val expectedJob = JobRecord(Id("1"), created, queueName, Status.Triggered, triggerDate, payload)
    queryRepository.retrieveBy(expectedJob.id) returns Some(expectedJob)

    get(s"/queues/abc/triggered/${expectedJob.id.value}") {
      status must beEqualTo(200)
      parse(body) must beEqualTo(jsonFromFile("fixtures/hal/halFormattedQueueEntry.json"))
    }
  }

  "return 404 when queue entry not found" in {
    get("/queues/abc/triggered/5") {
      queryRepository.retrieveBy(Id("5")) returns None
      status must beEqualTo(404)
    }
  }

  "return 200 when putting job into started queue" in {
    val expectedJob = JobRecord(Id("3"), created, queueName, Status.Triggered, triggerDate, payload)
    queryRepository.retrieveBy(expectedJob.id) returns Some(expectedJob)

    put("/queues/abc/started", """{"id": "3"}""") {
      status must beEqualTo(200)
    }
  }

  "return 200 when putting job into completed queue" in {
    val expectedJob = JobRecord(Id("4"), created, queueName, Status.Started, triggerDate, payload)
    queryRepository.retrieveBy(expectedJob.id) returns Some(expectedJob)

    put("/queues/abc/completed", """{"id": "4"}""") {
      status must beEqualTo(200)
    }
  }

  def jsonFromFile(filePath: String) = {
    parse(textFromFile(filePath))
  }

  def textFromFile(filePath: String) = {
    fromInputStream(getClass.getClassLoader.getResourceAsStream(filePath)).mkString
  }
}