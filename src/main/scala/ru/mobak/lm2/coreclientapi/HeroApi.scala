package ru.mobak.lm2.coreclientapi

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import com.fasterxml.jackson.databind.ObjectMapper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object HeroApi {

  case class PrimaryStateSnapshot(revision: Long,
                                  state: PrimaryState)

  case class PrimaryState(userId: String,
                          heroId: String,
                          raceId: String,
                          name: String,
                          level: Int,
                          shieldTo: Date,
                          forge: Seq[Anvil])

  case class Anvil(thing: Thing,
                   forgeFrom: Date,
                   forgeTo: Date)

  case class Thing(id: String,
                   name: String,
                   templateId: String)

}

trait HeroApi {

  implicit val mapper: ObjectMapper

  implicit val timeout: FiniteDuration

  /** Get hero state, async */
  def asyncHeroPrimaryState(userId: String, heroId: String)(implicit as: ActorSystem, sm: Materializer, ctx: Api.ApiContext): Future[HeroApi.PrimaryState] = {
    val request = HttpRequest(
      uri = Uri(s"http://${ctx.host}/users/$userId/heroes/$heroId/primary-state/v7"),
      headers = List(HostHeader(ctx.host))
    )
    for {
      response <- Http().singleRequest(request) if response.status == StatusCodes.OK
      responseAsString <- response.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
    } yield {
      mapper.readValue(responseAsString, classOf[HeroApi.PrimaryStateSnapshot]).state
    }
  }

  /** Get hero state */
  def heroPrimaryState(userId: String, heroId: String)(implicit as: ActorSystem, sm: Materializer, ctx: Api.ApiContext): HeroApi.PrimaryState = {
    Await.result(asyncHeroPrimaryState(userId, heroId), timeout)
  }

}
