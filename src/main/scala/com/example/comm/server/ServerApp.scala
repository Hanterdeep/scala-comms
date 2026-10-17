package com.example.comm.server

import com.example.comm.cluster.ClusterBootstrap

object ServerApp {
  def main(args: Array[String]): Unit = {
    val port = if (args.nonEmpty) args(0).toInt else 2551
    val system = ClusterBootstrap.startServer(port = port)
    ClusterBootstrap.watchMembership(system)

    system.log.info(s"Server started on port $port (seed node). No user data stored.")
    system.log.info("Gateways ready: UDP:9000 TCP:9001 WebSocket:9002")

    Thread.sleep(Long.MaxValue)
  }
}
