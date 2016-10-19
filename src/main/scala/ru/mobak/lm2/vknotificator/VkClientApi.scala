package ru.mobak.lm2.vknotificator

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object VkClientApi extends LazyLogging {

  case class VkContext(appId: String,
                       appSecret: String)

  val mapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  case class AccessToken(access_token: String)

  def receiveServerToken()(implicit as: ActorSystem, sm: Materializer, ctx: VkContext): Future[String] = {
    val request = HttpRequest(uri = Uri(s"https://oauth.vk.com/access_token?client_id=${ctx.appId}&client_secret=${ctx.appSecret}&v=5.45&grant_type=client_credentials"))
    for {
      response <- Http().singleRequest(request) if response.status == StatusCodes.OK
      responseAsString <- response.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
    } yield {
      mapper.readValue(responseAsString, classOf[AccessToken]).access_token
    }
  }

  def setUserLevel(userId: String, level: Int, accessToken: String)(implicit as: ActorSystem, sm: Materializer, ctx: VkContext): Future[Unit] = {
    val request = HttpRequest(uri = Uri("https://api.vk.com/method/secure.sendUserLevel").withQuery(Uri.Query(Map("user_id" -> userId, "level" -> level.toString, "access_token" -> accessToken, "client_secret" -> ctx.appSecret))))
    for {
      response <- Http().singleRequest(request) if response.status == StatusCodes.OK
      responseAsString <- response.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8")) if responseAsString == "{\"response\":1}"
    } yield {
    }
  }

  def sendNotification(userId: String, message: String, accessToken: String)(implicit as: ActorSystem, sm: Materializer, ctx: VkContext): Future[Unit] = {
    val notification = message take 254
    val request = HttpRequest(uri = Uri("https://api.vk.com/method/secure.sendNotification").withQuery(Uri.Query(Map("user_id" -> userId, "message" -> notification, "access_token" -> accessToken, "client_secret" -> ctx.appSecret))))
    for {
      response <- Http().singleRequest(request) if response.status == StatusCodes.OK
      responseAsString <- response.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8")) if responseAsString == "{\"response\":1}"
    } yield {
    }
  }

  def sendSMSNotification(userId: String, message: String, accessToken: String)(implicit as: ActorSystem, sm: Materializer, ctx: VkContext): Future[Unit] = {
    val notification = message take 160
    val request = HttpRequest(uri = Uri("https://api.vk.com/method/secure.sendSMSNotification").withQuery(Uri.Query(Map("user_id" -> userId, "message" -> notification, "access_token" -> accessToken, "client_secret" -> ctx.appSecret))))
    for {
      response <- Http().singleRequest(request) if response.status == StatusCodes.OK
      responseAsString <- response.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8")) if responseAsString == "{\"response\":1}"
    } yield {
    }
  }

}
