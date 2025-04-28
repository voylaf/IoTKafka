package com.github.voylaf
package consumer

import com.fasterxml.jackson.databind.json.JsonMapper
import org.apache.kafka.common.serialization.StringDeserializer

import scala.reflect.ClassTag

trait JsonStringDeserializer[T] {
  implicit val keyDeserializer: StringDeserializer   = new StringDeserializer()
  implicit val valueDeserializer: StringDeserializer = new StringDeserializer()

  def fromJsonString(
      str: String
  )(implicit jsonMapper: JsonMapper, classTag: ClassTag[T]): T = {
    jsonMapper.readValue(str, classTag.runtimeClass).asInstanceOf[T]
  }
}
