/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */
package ai.grakn.test;

import ai.grakn.GraknGraph;
import ai.grakn.GraknTxType;
import ai.grakn.engine.GraknEngineConfig;
import ai.grakn.engine.GraknEngineServer;
import ai.grakn.engine.factory.EngineGraknGraphFactory;
import ai.grakn.engine.tasks.TaskState;
import ai.grakn.factory.SystemKeyspace;
import com.jayway.restassured.RestAssured;
import info.batey.kafka.unit.KafkaUnit;
import org.slf4j.LoggerFactory;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.grakn.engine.GraknEngineConfig.REDIS_SERVER_PORT;
import static ai.grakn.graql.Graql.var;

/**
 * <p>
 * Contains utility methods and statically initialized environment variables to control
 * Grakn unit tests. 
 * </p>
 * 
 * @author borislav
 *
 */
public abstract class GraknTestEnv {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(GraknTestEnv.class);

    private static final GraknEngineConfig properties = GraknEngineConfig.getInstance();

    private static String CONFIG = System.getProperty("grakn.test-profile");
    private static AtomicBoolean CASSANDRA_RUNNING = new AtomicBoolean(false);
    private static AtomicInteger KAFKA_COUNTER = new AtomicInteger(0);
    private static AtomicInteger REDIS_COUNTER = new AtomicInteger(0);

    private static KafkaUnit kafkaUnit = new KafkaUnit(2181, 9092);
    private static RedisServer redisServer;

    public static void ensureCassandraRunning() throws Exception {
        if (CASSANDRA_RUNNING.compareAndSet(false, true) && usingTitan()) {
            LOG.info("starting cassandra...");
            startEmbeddedCassandra();
            LOG.info("cassandra started.");
        }
    }

    /**
     * To run engine we must ensure Cassandra, the Grakn HTTP endpoint, Kafka & Zookeeper are running
     */
    static GraknEngineServer startEngine(String taskManagerClass, int port) throws Exception {
        // To ensure consistency b/w test profiles and configuration files, when not using Titan
        // for a unit tests in an IDE, add the following option:
        // -Dgrakn.conf=../conf/test/tinker/grakn.properties
        //
        // When using titan, add -Dgrakn.test-profile=titan
        //
        // The reason is that the default configuration of Grakn uses the Titan factory while the default
        // test profile is tinker: so when running a unit test within an IDE without any extra parameters,
        // we end up wanting to use the TitanFactory but without starting Cassandra first.
        LOG.info("starting engine...");

        ensureCassandraRunning();

        // start engine
        RestAssured.baseURI = "http://" + properties.getProperty("server.host") + ":" + properties.getProperty("server.port");
        GraknEngineServer server = GraknEngineServer.start(taskManagerClass, port);

        LOG.info("engine started.");

        return server;
    }

    static void startRedis() throws IOException {
        if(REDIS_COUNTER.getAndIncrement() == 0) {
            LOG.info("Starting redis...");
            redisServer = new RedisServer(properties.getPropertyAsInt(REDIS_SERVER_PORT));
            redisServer.start();
            LOG.info("Redis started.");
        }
    }

    static void startKafka() throws Exception {
        // Kafka is using log4j, which is super annoying. We make sure it only logs error here
        org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.ERROR);

        // Clean-up ironically uses a lot of memory
        if (KAFKA_COUNTER.getAndIncrement() == 0) {
            LOG.info("Starting kafka...");
            kafkaUnit.setKafkaBrokerConfig("log.cleaner.enable", "false");
            kafkaUnit.startup();
            kafkaUnit.createTopic(TaskState.Priority.HIGH.queue(), properties.getAvailableThreads() * 2);
            kafkaUnit.createTopic(TaskState.Priority.LOW.queue(), properties.getAvailableThreads() * 2);
            LOG.info("Kafka started.");
        }
    }

    static void stopKafka() throws Exception {
        if (KAFKA_COUNTER.decrementAndGet() == 0) {
            LOG.info("Stopping kafka...");
            kafkaUnit.shutdown();
            LOG.info("Kafka stopped.");
        }
    }

    static void stopRedis(){
        if (REDIS_COUNTER.decrementAndGet() == 0) {
            LOG.info("Stopping Redis...");
            redisServer.stop();
            LOG.info("Redis stopped.");
        }
    }

    static void stopEngine(GraknEngineServer server) throws Exception {
        LOG.info("stopping engine...");

        server.close();
        clearGraphs();

        LOG.info("engine stopped.");

        // There is no way to stop the embedded Casssandra, no such API offered.
    }

    private static void clearGraphs() {
        // Drop all keyspaces
        EngineGraknGraphFactory engineGraknGraphFactory = EngineGraknGraphFactory.getInstance();

        try(GraknGraph systemGraph = engineGraknGraphFactory.getGraph(SystemKeyspace.SYSTEM_GRAPH_NAME, GraknTxType.WRITE)) {
            systemGraph.graql().match(var("x").isa("keyspace-name"))
                    .execute()
                    .forEach(x -> x.values().forEach(y -> {
                        String name = y.asResource().getValue().toString();
                        GraknGraph graph = engineGraknGraphFactory.getGraph(name, GraknTxType.WRITE);
                        graph.clear();
                        graph.admin().commitNoLogs();
                    }));
        }
        engineGraknGraphFactory.refreshConnections();
    }

    static void startEmbeddedCassandra() {
        try {
            // We have to use reflection here because the cassandra dependency is only included when testing the titan profile.
            Class cl = Class.forName("org.cassandraunit.utils.EmbeddedCassandraServerHelper");

            //noinspection unchecked
            cl.getMethod("startEmbeddedCassandra", String.class).invoke(null, "cassandra-embedded.yaml");

            try {
                Thread.sleep(5000);
            } 
            catch(InterruptedException ex) { 
                LOG.info("Thread sleep interrupted."); 
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String randomKeyspace(){
        // Embedded Casandra has problems dropping keyspaces that start with a number
        return "a"+ UUID.randomUUID().toString().replaceAll("-", "");
    }

    public static boolean usingTinker() {
        return "tinker".equals(CONFIG);
    }

    public static boolean usingTitan() {
        return "titan".equals(CONFIG);
    }

    public static boolean usingOrientDB() {
        return "orientdb".equals(CONFIG);
    }
}
