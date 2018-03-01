/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.mongo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.SecretUtils;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;

import static org.nexial.core.mongo.MongoConst.*;

/**
 *
 */
public class MongoClientFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoClientFactory.class);
	private static final String ME = MongoClientFactory.class.getSimpleName();
	private static final Map<String, String> DEFAULTS = initDefaults();

	private Resource config;
	private MongoClientURI mongoClientURI;
	private MongoClient mongoClient;

	public void setConfigLocation(Resource resource) { this.config = resource; }

	public MongoClient getMongoClient() {
		if (mongoClient == null) {
			try {
				mongoClient = new MongoClient(mongoClientURI);
			} catch (Exception e) {
				ConsoleUtils.error("Unable to initialize connection to datastore: " + e.getMessage());
				e.printStackTrace();
				return null;
			}
		}
		return mongoClient;
	}

	protected void init() throws IOException {
		if (LOGGER.isDebugEnabled()) { LOGGER.debug(ME + ".init(): config=" + config); }

		Properties prop = new Properties();
		prop.load(config.getInputStream());

		String serverAddresses = SecretUtils.unscramble(prop.getProperty(HOST_LOCATIONS));
		if (LOGGER.isDebugEnabled()) { LOGGER.debug(ME + ": " + HOST_LOCATIONS + "=" + serverAddresses); }

		String database = SecretUtils.unscramble(prop.getProperty(DB_NAME));
		if (LOGGER.isDebugEnabled()) { LOGGER.debug(ME + ": " + DB_NAME + "=" + database); }

		String username = SecretUtils.unscramble(prop.getProperty(USERNAME));
		String password = SecretUtils.unscramble(prop.getProperty(PASSWORD));
		String mongoCredential = null;
		if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
			mongoCredential = username + ":" + password;
		}

		String baseURI = PROTOCOL_PREFIX +
		                 (StringUtils.isNotBlank(mongoCredential) ? mongoCredential + "@" : "") +
		                 serverAddresses + "/" + database;

		MongoClientOptions.Builder optionsBuilder = readOptions(prop);
		mongoClientURI = new MongoClientURI(baseURI, optionsBuilder);
	}

	private static Map<String, String> initDefaults() {
		Map<String, String> map = new HashMap<>();
		map.put(ACCEPTABLE_LATENCY_DIFFERENCE, "15");
		map.put(CONN_PER_HOST, "100");
		map.put(CONN_TIMEOUT, "10000");
		map.put(HB_CONN_RETRY_FREQUENCY, "10");
		map.put(HB_CONN_TIMEOUT, "20000");
		map.put(HB_FREQUENCY, "5000");
		map.put(HB_SO_TIMEOUT, "20000");
		map.put(HB_THREAD_COUNT, "3");
		//map.put("maxAutoConnectRetryTime", "15");
		map.put(MAX_CONN_IDLE_TIME, "120000"); // 2 min.
		//map.put("maxConnectionLifeTime", "");
		map.put(MAX_WAIT_TIME, "120000");
		map.put(MIN_CONN_PER_HOST, "0");
		map.put(SO_TIMEOUT, "0");
		map.put(THREADS_ALLOWED_TO_BLOCK_FOR_CONN, "5");
		map.put(WRITE_CONCERN, "ACKNOWLEDGED");
		map.put(SO_KEEP_ALIVE, "false");
		map.put(READ_PREFERENCE, "primary");

		//map.put("url", "");

		return map;
	}

	private MongoClientOptions.Builder readOptions(Properties prop) {
		MongoClientOptions.Builder builder =
			MongoClientOptions.builder()
			                  .socketKeepAlive(getBooleanProp(prop, SO_KEEP_ALIVE))
			                  .socketTimeout(getIntProp(prop, SO_TIMEOUT))
			                  .connectionsPerHost(getIntProp(prop, CONN_PER_HOST))
			                  .connectTimeout(getIntProp(prop, CONN_TIMEOUT))
			                  .maxConnectionIdleTime(getIntProp(prop, MAX_CONN_IDLE_TIME))
			                  .minConnectionsPerHost(getIntProp(prop, MIN_CONN_PER_HOST))
			                  .maxWaitTime(getIntProp(prop, MAX_WAIT_TIME))
			                  //. heartbeatRetryFrequency(getIntProp(prop, HB_CONN_RETRY_FREQUENCY)).
			                  .heartbeatConnectTimeout(getIntProp(prop, HB_CONN_TIMEOUT))
			                  .heartbeatFrequency(getIntProp(prop, HB_FREQUENCY))
			                  .heartbeatSocketTimeout(getIntProp(prop, HB_SO_TIMEOUT))
			                  //.heartbeatThreadCount(getIntProp(prop, HB_THREAD_COUNT)).
			                  //.acceptableLatencyDifference(getIntProp(prop, ACCEPTABLE_LATENCY_DIFFERENCE)).
			                  .threadsAllowedToBlockForConnectionMultiplier(getIntProp(prop,
			                                                                           THREADS_ALLOWED_TO_BLOCK_FOR_CONN))
			                  .writeConcern(getWriteConcernProp(prop, WRITE_CONCERN))
			                  .readPreference(getReadPreference(readProp(prop, READ_PREFERENCE)));

		String requiredReplicaSetName = readProp(prop, REQUIRED_REPLICA_SET_NAME);
		if (StringUtils.isNotBlank(requiredReplicaSetName)) {
			builder.requiredReplicaSetName(requiredReplicaSetName);
		}

		return builder;
	}

	private static ReadPreference getReadPreference(String readPreference) {
		return ReadPreference.valueOf(readPreference);
	}

	private static WriteConcern getWriteConcernProp(Properties prop, String key) {
		return WriteConcern.valueOf(readProp(prop, key));
	}

	private static boolean getBooleanProp(Properties prop, String key) {
		return BooleanUtils.toBoolean(readProp(prop, key));
	}

	private static int getIntProp(Properties prop, String key) { return NumberUtils.toInt(readProp(prop, key)); }

	private static String readProp(Properties prop, String key) {
		String value = prop.getProperty(key, DEFAULTS.get(key));
		if (LOGGER.isDebugEnabled()) { LOGGER.debug(ME + ": " + key + "=" + value); }
		return value;
	}
}

