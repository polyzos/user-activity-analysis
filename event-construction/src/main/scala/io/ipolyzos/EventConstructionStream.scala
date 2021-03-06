package io.ipolyzos

import java.util.Properties

import io.ipolyzos.config.KafkaConfig
import io.ipolyzos.rpc.RPCServer
import io.ipolyzos.topology.EventConstructionTopology
import io.ipolyzos.wrappers.AsyncWrapper
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}

import scala.util.Try

object EventConstructionStream extends AsyncWrapper {
  private val applicationID = "user-activity-stream"

  private val stateStoreLocation = "tmp/state-store"

  def main(args: Array[String]): Unit = {
    val appID = Try(System.getenv("APP_ID")).getOrElse("0")
    val queryServerHost = sys.env.getOrElse("QUERY_SERVER_HOST", "localhost")
    val queryServerPort = sys.env.get("QUERY_SERVER_PORT").map(_.toInt).getOrElse(7010)
    val bootstrapServers = sys.env.getOrElse("BOOTSTRAP_SERVERS", KafkaConfig.bootstrapServers)

    val rpcEndpoint = s"$queryServerHost:$queryServerPort"

    val topology = EventConstructionTopology.build()

    val props = new Properties()
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationID)
    props.put(StreamsConfig.CLIENT_ID_CONFIG, "cgroup")
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props.put(StreamsConfig.STATE_DIR_CONFIG, stateStoreLocation + s"$appID")
    props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "0")
    props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0")
    props.put(StreamsConfig.APPLICATION_SERVER_CONFIG, rpcEndpoint)

    val streams = new KafkaStreams(topology, props)
    streams.cleanUp()
    val rpcServer = RPCServer(streams, new HostInfo(queryServerHost, queryServerPort))
    streams.setStateListener { (currentState, previousState) =>
      val isActive = currentState == KafkaStreams.State.RUNNING && previousState == KafkaStreams.State.REBALANCING
      rpcServer.setActive(isActive)
    }

    streams.start()
    rpcServer.start()
  }
}
