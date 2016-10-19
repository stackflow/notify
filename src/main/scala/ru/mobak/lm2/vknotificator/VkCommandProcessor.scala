package ru.mobak.lm2.vknotificator

import akka.actor._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object VkCommandProcessor {

  trait Command

  case class SetUserLevel(userId: String, level: Int) extends Command

  case class SendNotification(userId: String, message: String) extends Command

  case class SendSMSNotification(userId: String, message: String) extends Command

}

/** Отправляющий уведомления в vk */
class VkCommandProcessor(implicit as: ActorSystem, sm: akka.stream.Materializer, vkCtx: VkClientApi.VkContext) extends Actor with LazyLogging {

  var accessToken = ""
  var queue = List[AnyRef]()

  /** Сообщение вызывается, когда надо обновить токен */
  object UpdateToken

  /** Новый токен */
  case class NewToken(token: String)

  /** Сообщение посылется когда пришла очередь отправки следующей команды */
  object SendCommand

  private var processQueueTask: Option[Cancellable] = None
  private var updateTokenTask: Option[Cancellable] = None

  override def preStart(): Unit = {
    self ! UpdateToken
    updateTokenTask = Some(context.system.scheduler.schedule(1.hour, 1.hour, self, SendCommand))
    processQueueTask = Some(context.system.scheduler.schedule(1.second, 1.second, self, SendCommand))
  }

  override def postStop(): Unit = {
    processQueueTask foreach (_.cancel())
    updateTokenTask foreach (_.cancel())
  }

  def receive = {
    case UpdateToken =>
      val res = VkClientApi.receiveServerToken()

      res.onSuccess {
        case t => self ! NewToken(t)
      }

      res.onFailure {
        case t: Throwable => logger.error("Token request error: ", t)
      }

    case NewToken(t) =>
      // Пришел новый токен, сохраним.
      accessToken = t

      logger.debug(s"New VK token was received.")

    case cmd: VkCommandProcessor.Command =>
      logger.debug(s"Command ($cmd) added in the queue.")
      queue = queue :+ cmd

    case SendCommand if !accessToken.isEmpty && queue.nonEmpty => {
      val command = queue.head
      queue = queue.tail

      val res = command match {
        case cmd: VkCommandProcessor.SetUserLevel =>
          VkClientApi.setUserLevel(cmd.userId, cmd.level, accessToken)

        case cmd: VkCommandProcessor.SendNotification =>
          VkClientApi.sendNotification(cmd.userId, cmd.message, accessToken)

        case cmd: VkCommandProcessor.SendSMSNotification =>
          VkClientApi.sendSMSNotification(cmd.userId, cmd.message, accessToken)
      }

      res.onSuccess {
        case t =>
          logger.info(s"Command ($command) was successful executed.")
      }

      res.onFailure {
        case t: Throwable => logger.error("VK command execution error: ", t)
      }
    }

    case _ =>
  }

}
