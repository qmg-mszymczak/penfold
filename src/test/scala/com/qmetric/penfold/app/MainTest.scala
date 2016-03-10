package com.qmetric.penfold.app

import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.{Step, Fragments}
import com.github.athieriot.EmbedConnection

class MainTest extends SpecificationWithJUnit with EmbedConnection {
  sequential

  sys.props.put("config.file", getClass.getClassLoader.getResource("fixtures/config/full.conf").getPath)

  val server = new Main().init()

  override def map(fs: => Fragments) = fs ^ Step(server.stop())

  "server should start up" in {
    server.isStarted
  }
}
