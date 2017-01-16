# pio-kappa
PredictionIO-Kappa migration

This project implements a microservice based Machine learning server similar to the exisitng PredictionIO (v0.10.0 currently) but with
several fundimental changes including:

 - microservice based
 - merged inut and query servers on different endpoints of the same REST service
 - based on http-akka
 - supports SSL
 - supports authentication
 - supports Kappa style and non-Spark Algorithms in Templates
 - has an integrated example SDK in Java that implements client side SSL and Authentication
 - implements the Command Line Interface (CLI) as calls to the REST mircorservice
 
# Requirements

The new PIO-Kappa server should take identical input (events) and respond to identical queries packaged in JSON as Apache PIO using 
the Contextual Bandit as an example. We use the CB because it can operate in Lambda and Kappa style and already has a well 
defined input/query objects

 
# Architecture
 
**Libraries used**

 - akka-http, scala
 - [Asynch HTTP Client](https://github.com/AsyncHttpClient/async-http-client#async-http-client-asynchttpclient-on-twitter-) (if possible)
 - existing [PIO Java client SDK](https://github.com/apache/incubator-predictionio-sdk-java)
 
# Server REST API
