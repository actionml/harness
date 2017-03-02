package com.actionml

import akka.actor.{Actor, ActorLogging}
import scaldi.akka.AkkaInjectable

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 16:28
  */
package object router {

  trait ActorInjectable extends Actor with ActorLogging with AkkaInjectable

}
