package ru.mobak.lm2.locale

import java.io.InputStreamReader

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.common.base.Charsets
import com.google.common.io.CharStreams
import ru.mobak.lm2.resource.Reader

import scala.concurrent.stm.Ref

class Manager(val host: String, val pathPrefix: String) extends ManagerLike

object Manager {

  /** Описание файла */
  case class File(file: String, locale: String, isDefault: Boolean = false)

  /** Список файлов */
  case class Index(files: Seq[File])

  case class Data(index: Map[String, Map[String, String]], defaultLocale: String)

  def apply(host: String, pathPrefix: String) = {
    new Manager(host, pathPrefix)
  }

  val mapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(JsonParser.Feature.ALLOW_COMMENTS, true)

}

trait ManagerLike {

  val host: String

  val pathPrefix: String

  private val storage = Ref[Manager.Data](load())

  private def load(): Manager.Data = {
    val index = Reader.read(host, pathPrefix + "index.json")(Manager.mapper.readValue(_, classOf[Manager.Index]))

    // Разберем фалы на запчасти.
    // Получаем кэшь кеша (Map[String, Map[String, String]]), где первый глюч это локаль, а второй это название параметра.
    val unmergedDicts = index
      .files
      .map { file =>
        val text = Reader.read(host, pathPrefix + file.file)(is => CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8)))
        val keyValues = text
          .split("\n")
          .flatMap { line =>
            val s = line.split("=", 2)
            if (s.size == 2)
              Some(s(0) -> s(1))
            else
              None
          }
          .toMap
        file.locale -> keyValues
      }
      .toMap

    // Найдем локаль по умолчанию.
    val defaultLocale = index.files.find(_.isDefault).get.locale
    // Получим словарь по умолчанию
    val defaultDict = unmergedDicts(defaultLocale)

    // Дополним остальные словари словарем по умолчанию.
    val mergedDicts = unmergedDicts.mapValues(dict => defaultDict ++ dict)

    Manager.Data(index = mergedDicts, defaultLocale = defaultLocale)
  }

  /** Возможность перегрузить ресурсы в рантфйм */
  def reload() = storage.single.set(load())

  /** Возвращает дефолтную консоль */
  def defaultLocale() = storage.single().defaultLocale

  /** Возвращает значение по ключу и локали */
  def get(key: String, locale: String) = get(key, Some(locale))

  def get(key: String) = get(key, None)

  def get(key: String, localeOpt: Option[String]): Option[String] = dict(localeOpt.getOrElse(defaultLocale())).get(key)

  def getOrElse(key: String, default: String) = getOrElse(key, default, None)

  def getOrElse(key: String, default: String, locale: Option[String]) = get(key, locale).getOrElse(default)

  /** Возвращает словарь (ключ-значение) для указанной консоли, если консоль не найдена, то берется дефлотная.*/
  def dict(locale: String = ""): Map[String, String] = {
    val data = storage.single()
    if (locale.isEmpty)
      data.index(data.defaultLocale)
    else
      data.index.get(locale.toLowerCase) match {
        case Some(dict) => dict
        case None => data.index(data.defaultLocale)
      }
  }

  /** Возвращает словарь в виде текстового файла, если консоль не найдена, то берется дефлотная. */
  def dictAsString(locale: String = ""): String = {
    dict(locale).map {case (key, value) => s"$key=$value"}.mkString("\n")
  }

}
