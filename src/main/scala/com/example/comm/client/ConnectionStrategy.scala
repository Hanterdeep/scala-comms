package com.example.comm.client

import akka.actor.typed.ActorSystem
import akka.cluster.typed.Cluster
import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

/**
 * Measures real RTT to cluster members and selects the nearest node with protocol priority UDP > TCP > WebSocket.
 */
object ConnectionStrategy {
  sealed trait ProtocolPriority
  case object Udp extends ProtocolPriority
  case object Tcp extends ProtocolPriority
  case object WebSocket extends ProtocolPriority

  case class Candidate(address: InetSocketAddress, protocol: ProtocolPriority, rttMs: Long)

  private val measurements = new ConcurrentHashMap[String, Long]()

  def probe(host: String, port: Int, timeoutMs: Int = 500)(implicit ec: ExecutionContext): Future[Long] = {
    val start = System.nanoTime()
    Future {
      val addr = InetAddress.getByName(host)
      val reachable = addr.isReachable(timeoutMs)
      val elapsed = (System.nanoTime() - start) / 1000000
      if (reachable) elapsed else timeoutMs.toLong
    }
  }

  def selectNearest(
      seed: InetSocketAddress,
      members: Seq[InetSocketAddress],
      protocol: ProtocolPriority
  )(implicit system: ActorSystem[_], ec: ExecutionContext): Future[Candidate] = {
    val candidates = (seed +: members).distinct
    val probes = candidates.map { addr =>
      probe(addr.getHostString, addr.getPort).map { rtt =>
        measurements.put(s"${addr.getHostString}:${addr.getPort}", rtt)
        Candidate(addr, protocol, rtt)
      }.recover { case _ => Candidate(addr, protocol, Long.MaxValue) }
    }
    Future.sequence(probes).map { list =>
      list.minBy(_.rttMs)
    }
  }

  def prefer(protocol: ProtocolPriority, candidates: Seq[Candidate]): Option[Candidate] =
    candidates.filter(_.protocol == protocol).sortBy(_.rttMs).headOption

  def selectWithPriority(candidates: Seq[Candidate]): Option[Candidate] =
    prefer(Udp, candidates).orElse(prefer(Tcp, candidates)).orElse(prefer(WebSocket, candidates))

  def membersFromCluster()(implicit system: ActorSystem[_]): Seq[InetSocketAddress] = {
    val cluster = Cluster(system)
    cluster.state.members.toSeq.flatMap { m =>
      val addr = m.address
      for {
        host <- addr.host
        port <- addr.port
      } yield new InetSocketAddress(host, port)
    }
  }
}
