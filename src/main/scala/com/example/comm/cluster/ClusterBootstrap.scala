package com.example.comm.cluster

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{Cluster, JoinSeedNodes, Subscribe}
import akka.cluster.ClusterEvent.{MemberEvent, MemberUp, MemberRemoved}
import com.typesafe.config.ConfigFactory

object ClusterBootstrap {
  def startServer(systemName: String = "Communicator", port: Int = 2551): ActorSystem[Nothing] = {
    val config = ConfigFactory.parseString(s"""
      akka.actor.provider = cluster
      akka.remote.artery.canonical.hostname = 127.0.0.1
      akka.remote.artery.canonical.port = $port
      akka.cluster.seed-nodes = ["akka://$systemName@127.0.0.1:$port"]
      akka.cluster.downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
    """).withFallback(ConfigFactory.load())

    ActorSystem[Nothing](Behaviors.empty, systemName, config)
  }

  def joinClient(systemName: String, seedHost: String, seedPort: Int, myPort: Int): ActorSystem[Nothing] = {
    val config = ConfigFactory.parseString(s"""
      akka.actor.provider = cluster
      akka.remote.artery.canonical.hostname = 127.0.0.1
      akka.remote.artery.canonical.port = $myPort
      akka.cluster.seed-nodes = ["akka://$systemName@$seedHost:$seedPort"]
    """).withFallback(ConfigFactory.load())

    val system = ActorSystem[Nothing](Behaviors.empty, systemName, config)
    val cluster = Cluster(system)
    cluster.manager ! JoinSeedNodes(Vector(akka.actor.Address("akka", systemName, seedHost, seedPort)))
    system
  }

  def watchMembership(system: ActorSystem[_]): Unit = {
    val cluster = Cluster(system)
    cluster.subscriptions ! Subscribe(system.systemActorOf(membershipWatcher, "membership"), classOf[MemberEvent])
  }

  private def membershipWatcher: Behavior[MemberEvent] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case MemberUp(member) =>
          ctx.log.info(s"Node up: ${member.address}")
        case MemberRemoved(member, _) =>
          ctx.log.info(s"Node left (safe): ${member.address}")
        case _ =>
      }
      Behaviors.same
    }
}
