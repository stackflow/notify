package ru.mobak.lm2.kafka

import java.util.Properties

import akka.actor._
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.{KafkaConsumer => KC}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object KafkaConsumer {

  /** Запрос статуса у обработчика кафки */
  object Status

}

/** Это актор читающий сообщения из кафки */
class KafkaConsumer[T](kafkaCtx: KafkaCtx,
                       topic: String,
                       groupId: String,
                       deserialize: String => T,
                       /** Собственно самый главный метод, обрабатывающий сообщения из очереди. Первый аргумент - ключ (id), второй - сообщение */
                       handle: (String, T) => Unit,
                       autoCommit: Boolean = true,
                       /** Период проверки очереди */
                       timeout: Int = 50) extends Actor with LazyLogging {

  import scala.collection.JavaConversions._

  case object Check

  var consumer: Option[KC[String, String]] = None

  /** Время последней обработки сообщения */
  var lastCheck: Long = System.currentTimeMillis()

  override def preStart(): Unit = {
    val props = new Properties
    props.put("bootstrap.servers", kafkaCtx.servers)
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("group.id", kafkaCtx.topicPrefix + groupId)

    val c = new KC[String, String](props)
    c.subscribe(Seq(kafkaCtx.topicPrefix + topic).toList)
    consumer = Some(c)

    logger.debug(s"Kafka consumer has been subscribed for topic $topic")
    self ! Check
  }

  override def postStop(): Unit = {
    consumer foreach (_.close())
  }

  def receive = {
    case Check =>
      lastCheck = System.currentTimeMillis()

      consumer foreach { consumer =>
        val records = consumer.poll(0).iterator()

        records.foreach { record =>
          try {
            handle(record.key(), deserialize(record.value()))
          } catch {
            case t: Throwable => logger.error("Message processing error", t)
          }
        }

        if (records.nonEmpty)
          self ! Check
        else
          context.system.scheduler.scheduleOnce(timeout.millis, self, Check)
      }

    case KafkaConsumer.Status =>
      if (System.currentTimeMillis() - lastCheck < 10000)
        sender ! Unit
  }

}
