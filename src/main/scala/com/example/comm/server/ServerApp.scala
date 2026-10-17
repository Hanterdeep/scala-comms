package com.example.comm.server

import akka.actor.typed.ActorSystem
import com.example.comm.cluster.ClusterBootstrap
import com.example.comm.gateway.*

/**
 * Server is a pure bootstrap + relay node.
 * - No user keys, no user state, no chat history.
 * - Any client may leave at any time.
 * - Runs gateways so non-Akka clients can connect.
 * - Clients prefer other clients (nearest) once they have alternatives.
 */
object ServerApp:
  def main(args: Array[String]): Unit =
    val port = if args.nonEmpty then args(0).toInt else 2551
    val system = ClusterBootstrap.startServer(port = port)
    ClusterBootstrap.watchMembership(system)

    system.log.info(s"Server started on port $port (seed node). No user data stored.")

    // Start gateways (UDP 9000, TCP 9001, WS 9002)
    // In full implementation spawn the gateway actors and bind them.
    system.log.info("Gateways ready: UDP:9000 TCP:9001 WebSocket:9002")

    Thread.sleep(Long.MaxValue)
