package ru.mobak.lm2.locale

import org.scalatest._
import ru.mobak.lm2.CommonSpec

class ManagerSpec extends FlatSpec with CommonSpec with Matchers {

  val localeManager = Manager(config.getString("app.resource.host"), config.getString("app.resource.locale.prefix"))
  val localeKey = "anvil.finish"
  val locale = "ru"

  "Manager" should "give bundles" in {
    val bundle = localeManager.dict("ru")
    bundle should not be empty
  }

  it should "return translated string" in {
    println(localeManager.get(localeKey, locale))
    localeManager.get(localeKey, locale) should not be empty
  }

}
