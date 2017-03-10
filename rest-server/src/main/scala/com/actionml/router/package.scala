package com.actionml

import akka.actor.{Actor, ActorLogging}
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import cats.syntax.either._

import org.joda.time.DateTime
import scaldi.akka.AkkaInjectable

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 16:28
  */
package object router {

  trait ActorInjectable extends Actor with ActorLogging with AkkaInjectable

  implicit val dateTimeEncoder: Encoder[DateTime] = Encoder.instance(a => a.toString().asJson)
  implicit val dateTimeDecoder: Decoder[DateTime] = Decoder.instance(a => a.as[String].map(new DateTime(_)))

}
