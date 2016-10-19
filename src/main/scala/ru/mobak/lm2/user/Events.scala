package ru.mobak.lm2.user

import java.util.Date

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.util.Try

/** Неполный список событий, который мы хотим отслеживать */
object Events {

  case class Commit(id: String, revision: Long, tm: Date, events: Seq[Events.Event])

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
  trait Event

  trait HeroEvent extends Event {
    val heroId: String
  }

  /** Событие увеличения уровня */
  case class LevelUpped(heroId: String, value: Int) extends HeroEvent

  /** Событие, что герой зашёл на локацию */
  case class LocationEntered(heroId: String, locationId: String, reflectionId: Option[String]) extends HeroEvent

  /** Событие говорит, что герой до сих пор находится на локации и время сессии надо продлить */
  case class LocationUpdated(heroId: String, locationId: String) extends HeroEvent

  /** Событие говорит, герой покинул локацию и сессию надо закрыть */
  case class LocationLeft(heroId: String, locationId: String) extends HeroEvent

  /** Событие о том, что началась ковка вещи в кузнице */
  case class ForgeStarted(heroId: String, thingId: String, forgeTo: Date) extends HeroEvent

  /** Событие о том, что предмет достали из кузницы */
  case class ForgeFinished(heroId: String, thingId: String) extends HeroEvent

  /** Событие о том, что время ковки предмета в кузнице завершено */
  case class ForgeTimeout(heroId: String, thingId: String) extends HeroEvent

  /** Не событие, а информация о купленном лоте. */
  case class SoldLot(regionId: String,
                     locationId: String,
                     cityId: String,
                     lotId: String,
                     openTm: Date,
                     level: Int,
                     currency: String,
                     price: Long, // Цена лота
                     /* Вознаграждение герою от продажи лота */
                     fee: Long)

  /** Событие возникает, когда у героя купили вещь на аукционе */
  case class LotWasSold(heroId: String, lot: SoldLot) extends HeroEvent

  val mapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)

  mapper.registerSubtypes(
    new NamedType(classOf[LevelUpped], "HeroEvents.LevelUpped"),
    new NamedType(classOf[LocationEntered], "HeroEvents.LocationEntered"),
    new NamedType(classOf[LocationUpdated], "HeroEvents.LocationUpdated"),
    new NamedType(classOf[LocationLeft], "HeroEvents.LocationLeft"),
    new NamedType(classOf[ForgeStarted], "HeroEvents.ForgeStarted"),
    new NamedType(classOf[ForgeFinished], "HeroEvents.ForgeFinished"),
    new NamedType(classOf[LotWasSold], "HeroEvents.LotWasSold")
  )

  def deserialize(str: String): Option[Commit] = Try {
    val commit = mapper.readValue(str, classOf[Commit])
    commit.copy(events = commit.events.filter(_ != null))
  }.toOption

}
