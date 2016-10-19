package ru.mobak.lm2.resource

import java.io.InputStream
import java.net.URL

object Reader {

  def read[T](host: String, path: String)(func: InputStream => T) = {
    val is: InputStream =
      if (host.startsWith("classpath"))
        Thread.currentThread().getContextClassLoader.getResourceAsStream(path)
      else
        new URL(s"http://$host/$path").openStream()

    val res = func(is)
    is.close()
    res
  }

}
