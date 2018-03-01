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

/**
 *
 */
final class MongoConst {
	static final String HOST_LOCATIONS = "hosts";
	static final String DB_NAME = "database";
	static final String USERNAME = "username";
	static final String PASSWORD = "password";
	static final String PROTOCOL_PREFIX = "mongodb://";
	static final String SO_KEEP_ALIVE = "socketKeepAlive";
	static final String SO_TIMEOUT = "socketTimeout";
	static final String CONN_PER_HOST = "connectionsPerHost";
	static final String CONN_TIMEOUT = "connectTimeout";
	static final String MAX_CONN_IDLE_TIME = "maxConnectionIdleTime";
	static final String MIN_CONN_PER_HOST = "minConnectionsPerHost";
	static final String MAX_WAIT_TIME = "maxWaitTime";
	static final String HB_CONN_RETRY_FREQUENCY = "heartbeatConnectRetryFrequency";
	static final String HB_CONN_TIMEOUT = "heartbeatConnectTimeout";
	static final String HB_FREQUENCY = "heartbeatFrequency";
	static final String HB_SO_TIMEOUT = "heartbeatSocketTimeout";
	static final String HB_THREAD_COUNT = "heartbeatThreadCount";
	static final String ACCEPTABLE_LATENCY_DIFFERENCE = "acceptableLatencyDifference";
	static final String THREADS_ALLOWED_TO_BLOCK_FOR_CONN = "threadsAllowedToBlockForConnectionMultiplier";
	static final String WRITE_CONCERN = "writeConcern";
	static final String READ_PREFERENCE = "readPreference";
	static final String REQUIRED_REPLICA_SET_NAME = "requiredReplicaSetName";

	private MongoConst() { }
}
