package ru.mobak.lm2.user

import java.util.Date

import akka.actor.{Actor, ActorRef, Cancellable, Props}

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Forge(val title: String) extends ForgeActor

object Forge {

  trait Event

  case class Started(userId: String, heroId: String, thingId: String, forgeTo: Date) extends Event

  case class Finished(userId: String, heroId: String, thingId: String) extends Event

  case class Timeout(userId: String, heroId: String, thingId: String, sender: ActorRef) extends Event

  def props(title: String): Props = {
    Props(new Forge(title))
  }

}

trait ForgeActor extends Actor {

  val title: String

  val schedulers: MutableMap[String, Cancellable] = MutableMap.empty

  override def postStop(): Unit = {
    schedulers foreach (_._2.cancel())
    schedulers.clear()
    super.postStop()
  }

  override def receive() = {
    case Forge.Started(userId, heroId, thingId, forgeTo) =>
      val source = sender()
      val duration = forgeTo.getTime - System.currentTimeMillis()
      val cancellable = context.system.scheduler.scheduleOnce(duration.millis, self, Forge.Timeout(userId, heroId, thingId, source))
      schedulers.put(thingId, cancellable)

    case Forge.Finished(userId, heroId, thingId) =>
      schedulers
        .remove(thingId) // удаляем из коллекции планировщик
        .map(_.cancel()) // отменяем планировщик

    case Forge.Timeout(userId, heroId, thingId, source) =>
      schedulers
        .remove(thingId) // удаляем из коллекции планировщик
        .map(_.cancel())
      source ! (userId, Events.ForgeTimeout(heroId, thingId))

    case _ =>
  }

}
