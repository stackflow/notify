package ru.mobak.lm2.coreclientapi

import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.concurrent.duration._
import scala.util.Try

object Api extends UserApi with HeroApi {

  val timeout = 1.seconds

  val mapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  case class ApiContext(host: String)

}

final class HostHeader(host: String) extends ModeledCustomHeader[HostHeader] {

  override def renderInRequests = true

  override def renderInResponses = true

  override val companion = HostHeader

  override def value: String = host

}

object HostHeader extends ModeledCustomHeaderCompanion[HostHeader] {

  override val name = "Host"

  override def parse(value: String) = Try(new HostHeader(value))

}