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
 */

package org.nexial.core.plugins.redis

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.NAMESPACE
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.ForcefulTerminate
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.ConsoleUtils
import redis.clients.jedis.JedisPool
import java.net.URI

class RedisCommand : BaseCommand(), ForcefulTerminate {
    private val prefix: String = NAMESPACE + "redis."

    override fun getTarget() = "redis"

    override fun mustForcefullyTerminate() = MapUtils.isNotEmpty(context.getObjectByPrefix(prefix))

    override fun forcefulTerminate() {
        context.getObjectByPrefix(prefix)
            .forEach({ name, redisClient ->
                         ConsoleUtils.log("closing redis connection for ${StringUtils.substringAfter(name, prefix)}: " +
                                          "${(redisClient as JedisPool).destroy()}")
                     })
    }

    fun assertKeyExists(profile: String, key: String): StepResult {
        requiresNotBlank(key, "Invalid key", key)

        resolveConnectionPool(profile).resource.use { jedis ->
            return if (jedis.exists(key)) {
                StepResult.success("key $key found")
            } else {
                StepResult.fail("key $key NOT found")
            }
        }
    }

    fun flushDb(profile: String): StepResult {
        resolveConnectionPool(profile).resource.use { jedis ->
            return StepResult.success("current db flushed: ${jedis.flushDB()}")
        }
    }

    fun flushAll(profile: String): StepResult {
        resolveConnectionPool(profile).resource.use { jedis ->
            return StepResult.success("ALL KEYS flushed: ${jedis.flushAll()}")
        }
    }

    fun store(Var: String, profile: String, key: String): StepResult {
        requiresNotBlank(Var, "invalid variable", Var)
        requiresNotBlank(key, "Invalid key", key)
        resolveConnectionPool(profile).resource.use { jedis ->
            return if (jedis.exists(key)) {
                context.setData(Var, jedis.get(key))
                StepResult.success("value of the specified key $key stored as '$Var'")
            } else {
                StepResult.fail("specified key $key NOT found")
            }
        }
    }

    fun set(profile: String, key: String, value: String): StepResult {
        requiresNotBlank(key, "Invalid key", key)
        requiresNotBlank(value, "invalid value", value)
        resolveConnectionPool(profile).resource.use { jedis ->
            return StepResult.success("key $key is set with specified value: ${jedis.set(key, value)}")
        }
    }

    fun append(profile: String, key: String, value: String): StepResult {
        requiresNotBlank(key, "Invalid key", key)
        requiresNotBlank(value, "invalid value", value)
        resolveConnectionPool(profile).resource.use { jedis ->
            return StepResult.success("value of key $key is appended with specified value: ${jedis.append(key, value)}")
        }
    }

    fun delete(profile: String, key: String): StepResult {
        requiresNotBlank(key, "Invalid key", key)
        resolveConnectionPool(profile).resource.use { jedis ->
            return StepResult.success("key $key is deleted: ${jedis.del(key)}")
        }
    }

    fun storeKeys(Var: String, profile: String, keyPattern: String): StepResult {
        requiresNotBlank(Var, "invalid variable", Var)
        requiresNotBlank(keyPattern, "Invalid key", keyPattern)
        resolveConnectionPool(profile).resource.use { jedis ->
            val matched = jedis.keys(keyPattern)
            context.setData(Var, matched)
            return StepResult.success("${matched.size} matches found to specified key pattern $keyPattern, " +
                                      "and stored as '$Var'")
        }
    }

    fun rename(profile: String, current: String, new: String): StepResult {
        requiresNotBlank(current, "invalid current key", current)
        requiresNotBlank(new, "invalid new key", new)
        resolveConnectionPool(profile).resource.use { jedis ->
            return StepResult.success("key $current renamed to $new ${jedis.rename(current, new)}")
        }
    }

    private fun resolveConnectionPool(profile: String): JedisPool {
        requiresNotBlank(profile, "Invalid profile", profile)

        val poolObject: Any? = context.getObjectData("$prefix$profile")
        return if (poolObject != null && poolObject is JedisPool) {
            poolObject
        } else {
            // todo: add reference to doc:
            // https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details
            // https://www.iana.org/assignments/uri-schemes/prov/redis
            // https://stackoverflow.com/questions/44344628/how-to-create-a-redis-cloud-connection-url-with-an-auth-password
            val url: String = context.getStringData("$profile.url")
            requiresNotBlank(url, "No valid redis url specified in '$profile.url'", url)

            val pool = JedisPool(URI(url))
            context.setData("$prefix$profile", pool)
            pool
        }
    }
}