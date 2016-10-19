package ru.mobak.lm2

package object kafka {

  case class KafkaCtx(servers: String,
                      topicPrefix: String,
                      compressionType: String,
                      acks: String)

}
