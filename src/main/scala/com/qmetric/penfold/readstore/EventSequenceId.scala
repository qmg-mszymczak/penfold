package com.qmetric.penfold.readstore

object EventSequenceId {
  val first = EventSequenceId(1)
}

case class EventSequenceId(value: Long)
