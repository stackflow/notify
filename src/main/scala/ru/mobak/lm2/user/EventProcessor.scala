package ru.mobak.lm2.user

import java.text.MessageFormat

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.LazyLogging
import ru.mobak.lm2.coreclientapi.HeroApi.PrimaryState
import ru.mobak.lm2.coreclientapi.{Api, UserApi}
import ru.mobak.lm2.locale.Manager
import ru.mobak.lm2.vknotificator.App._
import ru.mobak.lm2.vknotificator.VkCommandProcessor

import scala.collection.mutable.{Set => MutableSet}

object EventProcessor {

  def props(anvilProcessor: ActorRef, vkProcessor: ActorRef): Props = {
    Props(new EventProcessor(anvilProcessor, vkProcessor))
  }

}

class EventProcessor(anvilProcessor: ActorRef, vkProcessor: ActorRef)(implicit localeManager: Manager) extends Actor with LazyLogging {

  var heroes: MutableSet[(String, String)] = MutableSet.empty

  def initUserEvent(userId: String, f: (UserApi.UserState) => Unit) = {
    val res = for {
      userState <- Api.asyncUserState(userId) if userState.providers.contains("vk")
    } yield userState

    res.onSuccess {
      case userState =>
        f(userState)
    }

//    res.onFailure {
//      case t: Throwable => logger.error("Some error: ", t)
//    }
  }

  def initPrimaryHeroEvent(userId: String, heroId: String, f: (UserApi.UserState, PrimaryState) => Unit) = {
    val res = for {
      userState <- Api.asyncUserState(userId) if userState.providers.contains("vk")
      heroPrimaryState <- Api.asyncHeroPrimaryState(userId, heroId)
    } yield (userState, heroPrimaryState)

    res.onSuccess {
      case (userState, heroPrimaryState) =>
        f(userState, heroPrimaryState)
    }

//    res.onFailure {
//      case t: Throwable => logger.error("Some error: ", t)
//    }
  }

  override def receive() = {
    case (id: String, event: Events.LevelUpped) =>
      initUserEvent(id, { userState =>
        val maxHero = userState.heroes.foldLeft(userState.heroes.head) {
          case (a, b) if a.level < b.level => b
          case (a, b) => a
        }

        // Пользователь должен быть зарегистрирован в VK. И еще, герой у которого сменился уровень
        // должен иметь самый высокий уровень.
        if (maxHero.heroId == event.heroId) {
          userState.heroes.find(_.heroId == event.heroId).foreach { hero =>
            logger.debug(s"Hero ${hero.heroId} is level ${hero.level}")
            vkProcessor ! VkCommandProcessor.SetUserLevel(userState.providers("vk"), hero.level)
          }
        }
      })

    case (id: String, event: Events.LocationEntered) =>
      logger.debug(s"LocationEntered is coming (userId: $id, heroId: ${event.heroId})")
      initPrimaryHeroEvent(id, event.heroId, { (userState, heroState) =>
        heroes.add(id, event.heroId)
        logger.debug(s"Hero added to onlineList(${heroes.size}) (userId: $id, heroId: ${event.heroId})")
      })

    case (id: String, event: Events.LocationUpdated) =>
      logger.debug(s"LocationUpdated is coming (userId: $id, heroId: ${event.heroId})")
      heroes.find(hero => hero._1 == id && hero._2 == event.heroId) match {
        case None =>
          initPrimaryHeroEvent(id, event.heroId, { (userState, heroState) =>
            heroes.add((id, event.heroId))
            logger.debug(s"Hero added to onlineList(${heroes.size}) (userId: $id, heroId: ${event.heroId})")
          })

        case _ =>
          logger.debug(s"Hero already in onlineList(${heroes.size}) (userId: $id, heroId: ${event.heroId})")
      }

    case (id: String, event: Events.LocationLeft) =>
      logger.debug(s"LocationLeft is coming (userId: $id, heroId: ${event.heroId})")
      if (heroes.remove((id, event.heroId))) {
        logger.debug(s"Hero removed from onlineList(${heroes.size}) (userId: $id, heroId: ${event.heroId})")
      } else {
        logger.debug(s"No hero in onlineList(${heroes.size}) (userId: $id, heroId: ${event.heroId})")
      }

    case (id: String, event: Events.ForgeStarted) =>
      logger.debug(s"ForgeStarted is coming (userId: $id, heroId: ${event.heroId}, thing: ${event.thingId})")
      anvilProcessor ! Forge.Started(id, event.heroId, event.thingId, event.forgeTo)

    case (id: String, event: Events.ForgeFinished) =>
      logger.debug(s"ForgeFinished is coming (userId: $id, heroId: ${event.heroId}, thing: ${event.thingId})")
      anvilProcessor ! Forge.Finished(id, event.heroId, event.thingId)

    case (id: String, event: Events.ForgeTimeout) =>
      logger.debug(s"ForgeTimeout is coming (userId: $id, heroId: ${event.heroId}, thing: ${event.thingId})")
      heroes.find(hero => hero._1 == id & hero._2 == event.heroId) match {
        case None =>
          initPrimaryHeroEvent(id, event.heroId, { (userState, heroState) =>
            heroState.forge.find(_.thing.id == event.thingId) match {
              case Some(anvil) if System.currentTimeMillis() > anvil.forgeTo.getTime =>
                val locale = userState.regionId
                for {
                  thingName <- localeManager.get("stuff." + anvil.thing.templateId + ".name", locale)
                  anvil <- localeManager.get("anvil.finish", locale)
                } yield {
                  val message = MessageFormat.format(anvil, thingName)
                  logger.debug(s"ForgeTimeout Vk message: $message")
                  val notification = VkCommandProcessor.SendNotification(userState.providers("vk"), message)
                  vkProcessor ! notification
                }

              case None =>
                logger.debug(s"ForgeTimeout Thing(${event.thingId}) isn't found in forge")
            }
          })

        case _ =>
      }

    case (id: String, event: Events.LotWasSold) =>
      heroes.find(hero => hero._1 == id & hero._2 == event.heroId) match {
        case None =>
          initPrimaryHeroEvent(id, event.heroId, { (userState, heroState) =>
            for {
              message <- localeManager.get("market.lot.was.sold", userState.regionId)
            } yield {
              vkProcessor ! VkCommandProcessor.SendNotification(userState.providers("vk"), message)
            }
          })

        case _ =>
      }

    case _ =>
  }

}
