package ru.mobak.lm2.vknotificator

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import ru.mobak.lm2.coreclientapi.Api
import ru.mobak.lm2.kafka.{KafkaConsumer, KafkaCtx}
import ru.mobak.lm2.locale.{Manager => LocaleManager}
import ru.mobak.lm2.user.{EventProcessor, Events, Forge}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object App extends LazyLogging with App {

  /** Loading config */
  val config = ConfigFactory.load()

  implicit val as = ActorSystem()
  implicit val sm = ActorMaterializer()
  implicit val executionContext = as.dispatcher

  implicit val localeManager = LocaleManager(
    config.getString("app.resource.host"),
    config.getString("app.resource.locale.prefix")
  )

  val kafkaCtx = KafkaCtx(
    servers = config.getString("app.kafka.servers"),
    topicPrefix = config.getString("app.kafka.topic-prefix"),
    compressionType = config.getString("app.kafka.compression.type"),
    acks = config.getString("app.kafka.acks")
  )

  implicit val apiCtx = Api.ApiContext(config.getString("app.db.host"))
  implicit val vkCtx = VkClientApi.VkContext(
    appId = config.getString("app.vk.id"),
    appSecret = config.getString("app.vk.secret")
  )

  val vkProcessor = as.actorOf(Props(new VkCommandProcessor()))
  val anvilProcessor = as.actorOf(Forge.props("forge"), "forge")

  val eventProcessor = as.actorOf(EventProcessor.props(anvilProcessor, vkProcessor), "events")

  val eventConsumer: Props =
    Props(
      new KafkaConsumer[Events.Commit](
        kafkaCtx,
        topic = "userevents",
        groupId = "vk-notificator",
        deserialize = { str => Events.deserialize(str).get },
        timeout = 200,
        handle = { (id, commit) =>
          commit.events.foreach { event =>
            eventProcessor ! (id, event)
          }
        }
      )
    )

  val consumerActor = as.actorOf(eventConsumer)

  val serverSource = Http().bind(interface = "0.0.0.0", port = 8000)

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/ping"), _, _, _) =>
      Future.successful(HttpResponse(entity = "pong"))

    case HttpRequest(HttpMethods.GET, Uri.Path("/status"), _, _, _) =>
      implicit val timeout = Timeout(1.second)
      for {
        _ <- consumerActor ? KafkaConsumer.Status
      } yield {
        HttpResponse(entity = "Ok")
      }

    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      Future.successful(HttpResponse(404, entity = "Unknown resource!"))
  }

  val bindingFuture: Future[Http.ServerBinding] =
    serverSource.to(Sink.foreach { connection =>
      println("Accepted new connection from " + connection.remoteAddress)
      connection handleWithAsyncHandler requestHandler
    }).run()

  def shutdown(): Unit = {
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => Await.result(as.terminate(), 2.minutes)) // close actor system
  }

  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = shutdown()
  })

}
