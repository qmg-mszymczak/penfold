package com.qmetric.penfold.app

import org.specs2.mutable.Specification

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.FicusConfig._
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit._
import com.qmetric.penfold.app.readstore.mongodb.{IndexField, Index}

class ServerConfigurationTest extends Specification {

  val publicUrl = "http://localhost:9762"

  val httpPort = 9762

  val authCredentials = AuthenticationCredentials("user", "secret")

  val jdbcUrl = "jdbc:hsqldb:mem:penfold;sql.syntax_mys=true"

  val indexes = List(
    Index(Some("index1"), List(IndexField("field1", "payload.field1"))),
    Index(Some("index2"), List(IndexField("field1", "payload.field1"), IndexField("field2", "payload.field2", multiKey = true))))

  "load minimally populated config file" in {
    val expectedConfig = ServerConfiguration(publicUrl, httpPort, None, JdbcConnectionPool(jdbcUrl, "user", "", "org.hsqldb.jdbcDriver"),
      MongoDatabaseServers("dbname", List(MongoDatabaseServer("127.0.0.1", 12345))))

    val config = loadConfig("minimal")

    config must beEqualTo(expectedConfig)
  }

  "load fully populated config file" in {
    val expectedConfig = ServerConfiguration(publicUrl, httpPort, Some(authCredentials), JdbcConnectionPool(jdbcUrl, "user", "secret", "org.hsqldb.jdbcDriver", 10),
      MongoDatabaseServers("dbname", List(MongoDatabaseServer("127.0.0.1", 12345))),
      readStoreIndexes = indexes, SortOrderingConfiguration("Desc", "Desc", "Asc", "Asc"), pageSize = 25, eventSync = FiniteDuration(2L, MINUTES), triggeredCheckFrequency = FiniteDuration(1L, MINUTES),
      Some(TaskArchiverConfiguration("payload.timeout", FiniteDuration(1L, MINUTES))))

    val config = loadConfig("full")

    config must beEqualTo(expectedConfig)
  }

  private def loadConfig(fileName: String) = ConfigFactory.load(s"fixtures/config/$fileName").as[ServerConfiguration]("penfold")
}
