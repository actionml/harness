package com.actionml.core.storage

import java.net.UnknownHostException

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient

class Mongo(m: String = "localhost", p: Int = 27017, n: String = "some-resource-id") extends Store {

  val master: String = m
  val port: Int = p
  val dbName: String = n
  lazy val client = MongoClient(master, port)
  val store = this

  // should only be called from trusted source like the CLI!
  def create(): Mongo = {
    this
  }

  def destroy(): Mongo = {
    try {
      client.dropDatabase(dbName)
    } catch {
      case e: UnknownHostException =>
        logger.error(s"Unknown host for address: ${client.address}", e)
        throw e
      case e: MongoException => throw e
      case e: Throwable =>
        logger.error(s"Unknown exception destroying the db for: ${dbName}", e)
        throw e
    }
    this
  }

}

// Todo: should we support username, password connections to MongoDB?
// Automatically detect SCRAM-SHA-1 or Challenge Response protocol
// val server = new ServerAddress("localhost", 27017)
// val credentials = MongoCredential.createCredential(userName, source, password)
// val mongoClient = MongoClient(server, List(credentials))
// Todo: also may want to connect to all mongos replica sets
// val rs1 = new ServerAddress("localhost", 27017)
// val rs2 = new ServerAddress("localhost", 27018)
// val rs3 = new ServerAddress("localhost", 27019)
// val mongoClient = MongoClient(List(rs1, rs2, rs3))

