/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.core.dal.mongo

import java.time.Instant

import com.actionml.core.model.{CBGroup, User}
import com.actionml.core.template.CommonEngineData
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import org.joda.time.DateTime

object Codecs {

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
  import org.mongodb.scala.bson.codecs.Macros._

  lazy val codecRegistry = fromRegistries(
    CodecRegistries.fromCodecs(new InstantCodec, new DateTimeCodec),
    /*
     looks like list inside fromProviders should be in usage order (accessToken and userAccount contains permissions, so,
     permissions should be declared first.
     It also can correspond to https://jira.mongodb.org/browse/SCALA-338
     */
    fromProviders(classOf[User], classOf[CommonEngineData], classOf[CBGroup]), // todo: oh no, statically defined types? noooooooooo
    DEFAULT_CODEC_REGISTRY
  )

  class InstantCodec extends Codec[Instant] {
    override def decode(reader: BsonReader, dc: DecoderContext) = Instant.ofEpochMilli(reader.readDateTime)
    override def encode(writer: BsonWriter, value: Instant, ec: EncoderContext) = writer.writeDateTime(value.toEpochMilli)
    override def getEncoderClass = classOf[Instant]
  }

  class DateTimeCodec extends Codec[DateTime] {
    override def decode(reader: BsonReader, dc: DecoderContext) = new DateTime(reader.readDateTime)
    override def encode(writer: BsonWriter, value: DateTime, ec: EncoderContext) = writer.writeDateTime(value.getMillis)
    override def getEncoderClass = classOf[DateTime]
  }
}
