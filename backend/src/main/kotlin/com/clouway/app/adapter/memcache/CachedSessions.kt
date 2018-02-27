package com.clouway.app.adapter.memcache

import com.clouway.app.core.Session
import com.clouway.app.core.SessionRepository
import com.google.appengine.api.datastore.DatastoreFailureException
import com.google.appengine.api.memcache.Expiration
import com.google.appengine.api.memcache.MemcacheService
import java.time.LocalDateTime
import java.util.*

/**
 * @author Stanimir Iliev <stanimir.iliev@clouway.com>
 */

class CachedSessions constructor(val chain: SessionRepository, val cache: MemcacheService) : SessionRepository {
    override fun registerSession(session: Session): String? {
        val sessionId = chain.registerSession(session) ?: return null
        cache.put("SID$sessionId", session, Expiration.byDeltaSeconds(2 * 60 * 60))
        return sessionId
    }

    override fun getSessionAvailableAt(sessionId: String, instant: LocalDateTime): Session? {
        val session = cache.get("SID$sessionId") as? Session
        if (session != null) {
            return if (session.expiresAt.isAfter(instant)) {
                session
            } else {
                null
            }
        }
        val originSession = chain.getSessionAvailableAt(sessionId, instant) ?: return null
        cache.put("SID$sessionId", originSession, Expiration.byDeltaSeconds(2 * 60 * 60))
        return originSession
    }

    override fun getSessionsCount(instant: LocalDateTime): Int {
        return chain.getSessionsCount(instant)
    }

    @Throws(DatastoreFailureException::class, ConcurrentModificationException::class)
    override fun terminateSession(sessionId: String) {
        chain.terminateSession(sessionId)
        cache.delete("SID$sessionId")
    }

    override fun terminateInactiveSessions(instant: LocalDateTime): Int {
        return chain.terminateInactiveSessions(instant)
    }

}

