package ru.mobak.lm2.coreclientapi

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.stream.Materializer
import com.fasterxml.jackson.databind.ObjectMapper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object UserApi {

  case class UserStateSnapshot(revision: Long, state: UserState)

  case class UserState(userId: String,
                       regionId: String,
                       heroes: Seq[HeroState],
                       providers: Map[String, String])

  case class HeroState(heroId: String,
                       creationTm: Date,
                       name: String,
                       raceId: String,
                       level: Int,
                       lastActivityTm: Date)

}

trait UserApi {

  implicit val mapper: ObjectMapper

  implicit val timeout: FiniteDuration

  /** Get user state, async */
  def asyncUserState(userId: String)(implicit as: ActorSystem, sm: Materializer, ctx: Api.ApiContext): Future[UserApi.UserState] = {
    val request = HttpRequest(
      uri = Uri(s"http://${ctx.host}/users/$userId/state-v2"),
      headers = List(HostHeader(ctx.host))
    )
    for {
      response <- Http().singleRequest(request) if response.status == StatusCodes.OK
      responseAsString <- response.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
    } yield {
      mapper.readValue(responseAsString, classOf[UserApi.UserStateSnapshot]).state
    }
  }

  /** Get user state */
  def userState(userId: String)(implicit as: ActorSystem, sm: Materializer, ctx: Api.ApiContext): UserApi.UserState = {
    Await.result(asyncUserState(userId), timeout)
  }

}
