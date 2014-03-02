package org.huwtl.penfold.app.query

import com.redis.RedisClient
import org.huwtl.penfold.domain.model.Status
import org.joda.time.format.DateTimeFormat
import org.huwtl.penfold.app.support.json.ObjectSerializer
import org.joda.time.DateTime._
import org.huwtl.penfold.query._
import org.huwtl.penfold.domain.model.QueueName
import org.huwtl.penfold.domain.model.AggregateId
import org.huwtl.penfold.domain.model.Payload
import scala.Some
import org.huwtl.penfold.query.PageRequest
import org.huwtl.penfold.query.JobRecord
import org.huwtl.penfold.query.PageResult

class RedisQueryRepository(redisClient: RedisClient, objectSerializer: ObjectSerializer) extends QueryRepository {
  val dateFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  val retrieveJobScript = redisClient.scriptLoad(
    """
      | local jobKey = KEYS[1]
      |
      | if redis.call('exists', jobKey) == 0 then
      |   return {}
      | end
      |
      | local created = redis.call('hget', jobKey, 'created')
      | local type = redis.call('hget', jobKey, 'type')
      | local status = redis.call('hget', jobKey, 'status')
      | local trigger = redis.call('hget', jobKey, 'trigger')
      | local payload = redis.call('hget', jobKey, 'payload')
      |
      | return {created, type, status, trigger, payload}
    """.stripMargin
  )

  override def retrieveBy(queueName: QueueName, status: Status, pageRequest: PageRequest) = {
    val queueKey = s"${queueName.value}.${status.name}"
    val aggregateIdsWithOverflow = redisClient.zrange(queueKey, pageRequest.start, pageRequest.end).get
    val aggregateIdsWithoutOverflow = aggregateIdsWithOverflow.take(pageRequest.pageSize)

    val previousPageExists = !pageRequest.firstPage
    val nextPageExists = aggregateIdsWithOverflow.size != aggregateIdsWithoutOverflow.size

    PageResult(
      pageRequest.pageNumber,
      aggregateIdsWithoutOverflow.map {
        id => retrieveBy(AggregateId(id)).get
      },
      previousPageExists,
      nextPageExists)
  }

  override def retrieveBy(aggregateId: AggregateId) = {
    val jobKeyName = s"job:${aggregateId.value}"

    val jobAttributes = redisClient.evalMultiSHA[String](retrieveJobScript.get, List(jobKeyName), Nil).get

    if (jobAttributes.isEmpty) {
      None
    }
    else {
      val created = jobAttributes(0).get
      val queueName = jobAttributes(1).get
      val status = jobAttributes(2).get
      val triggerDate = jobAttributes(3).get
      val payload = jobAttributes(4).get
      Some(JobRecord(aggregateId, dateFormatter.parseDateTime(created), QueueName(queueName), Status.from(status).get, dateFormatter.parseDateTime(triggerDate), objectSerializer.deserialize[Payload](payload)))
    }
  }

  override def retrieveWithPendingTrigger = {
    val pageSize = 50

    def nextPageOfJobsToTrigger(offset: Int) = {
      val nextPageOfEarliestTriggeredJobs = redisClient.zrangebyscore(key = Status.Waiting.name, max = now().getMillis, limit = Some(offset, pageSize))
      nextPageOfEarliestTriggeredJobs.getOrElse(Nil).map {
        aggregateId => new JobRecordReference(AggregateId(aggregateId))
      }
    }

    def allPagesOfJobsToTrigger(offset: Int): Stream[List[JobRecordReference]] = {
      val page = nextPageOfJobsToTrigger(offset)
      if (page.isEmpty) Stream.empty else page #:: allPagesOfJobsToTrigger(offset + pageSize)
    }

    val allJobsToTrigger = for {
      pageOfJobsToTrigger <- allPagesOfJobsToTrigger(0)
      jobToTrigger <- pageOfJobsToTrigger
    } yield jobToTrigger

    allJobsToTrigger
  }
}
