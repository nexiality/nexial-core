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

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.nexial.core.excel.ext.CipherHelper;
import org.nexial.core.utils.ConsoleUtils;
import com.mongodb.*;
import com.mongodb.util.JSON;

/**
 *
 */
@Service
public class AuthManager {
	protected static final String MONGO_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.S'Z'";
	protected static final String USERNAME = "username";
	protected static final String REALM = "realm";
	protected static final String IS_ACTIVE = "isActive";
	protected static final String PASSWORD = "password";
	@Autowired
	protected MongoClientFactory mongoClientFactory;
	protected String database;
	protected String collection;
	protected String realm;
	protected CipherHelper cipher = new CipherHelper();
	private final Logger LOGGER = LoggerFactory.getLogger(getClass());

	public void setMongoClientFactory(MongoClientFactory mongoClientFactory) {
		this.mongoClientFactory = mongoClientFactory;
	}

	public void setDatabase(String database) { this.database = database; }

	public void setRealm(String realm) { this.realm = realm; }

	protected void init() {
		if (StringUtils.isBlank(database)) { throw new IllegalArgumentException("property 'database' missing"); }
		if (StringUtils.isBlank(collection)) { throw new IllegalArgumentException("property 'collection' missing"); }
		if (StringUtils.isBlank(realm)) { throw new IllegalArgumentException("property 'realm' missing"); }
	}

	protected void stop() { }

	protected int getActiveUserCount() {
		DBCollection dbc = getCollection();
		DBCursor auths = dbc.find(queryByRealm().add(IS_ACTIVE, true).get());
		return auths.count();
	}

	protected void upsertAuth(JSONObject oneAuth) {
		if (oneAuth == null) { return; }
		DBCollection dbc = getCollection();
		// todo: no actual upsert logic here yet!!
		dbc.insert((DBObject) JSON.parse(oneAuth.toString()));
		ConsoleUtils.log("inserted one record: " + oneAuth.toString());
	}

	protected void upsertAuth(JSONArray auths) {
		if (auths == null || auths.length() < 1) { return; }
		for (int i = 0; i < auths.length(); i++) {
			JSONObject auth = auths.optJSONObject(i);
			upsertAuth(auth);
		}
	}

	protected void removeAuth(String username) {
		DBCollection dbc = getCollection();
		WriteResult result = dbc.remove(queryByRealm().add(USERNAME, username).get());
		ConsoleUtils.log(result.getN() + " managed auth(s) removed from realm '" + realm + "'");
	}

	protected void removeAllAuth() {
		DBCollection dbc = getCollection();
		WriteResult result = dbc.remove(queryByRealm().get());
		ConsoleUtils.log(result.getN() + " managed auth(s) removed from realm '" + realm + "'");
	}

	protected void printCurrentAuth() {
		DBCollection dbc = getCollection();
		DBCursor auths = dbc.find(queryByRealm().get(), BasicDBObjectBuilder.start().add("_id", false).get());
		if (auths.count() < 1) {
			ConsoleUtils.log("No managed auth under realm '" + realm + "'");
			return;
		}

		ConsoleUtils.log("Found " + auths.count() + " managed auth(s) under realm '" + realm + "'");
		while (auths.hasNext()) {
			DBObject auth = auths.next();
			auth.put("password", "<masked>");
			ConsoleUtils.log(auth.toString());
		}
	}

	protected DBCollection getCollection() {
		MongoClient mongo = mongoClientFactory.getMongoClient();
		if (mongo == null) { throw new RuntimeException("Unable to obtain connection to datastore."); }
		return mongo.getDB(database).getCollection(collection);
	}

	public void setCollection(String collection) { this.collection = collection; }

	protected BasicDBObjectBuilder queryByRealm() { return BasicDBObjectBuilder.start().add(REALM, realm); }

	protected static DBObject toDBO(String json) {return (DBObject) JSON.parse(json);}

}
