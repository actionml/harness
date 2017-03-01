package com.actionml.core.template

/**
  * Forms the Engine contract. Engines parse and validate input strings, probably JSON,
  * and sent the correct case class E or a Seq[E] to input or inputCol of the extending
  * Engine. Queries work in a similar way.
  * @param d dataset to store input
  * @param p engine params, typically for the algorithm
  * @tparam E input case class type, often and Event of some type
  * @tparam P engine params case class type
  * @tparam Q engine query case class type
  * @tparam R engine query result case class type
  */
abstract class Engine[E, P, Q, R](d: Dataset[E], p: P) {

  val dataset = d
  val params = p

  def train()
  def input(datum: E): Boolean
  def inputCol(data: Seq[E]): Seq[Boolean]
  def query(query: Q): R
  def parseAndValidateInput(s: String): (E, Int)
}

trait Query
trait QueryResult
trait Params
trait Event

