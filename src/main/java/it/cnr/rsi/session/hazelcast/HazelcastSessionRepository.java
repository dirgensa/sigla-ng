/*
 * Copyright (C) 2019  Consiglio Nazionale delle Ricerche
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package it.cnr.rsi.session.hazelcast;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.AbstractEntryProcessor;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryEvictedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.query.Predicates;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

/**
 * A {@link org.springframework.session.SessionRepository} implementation that stores
 * sessions in Hazelcast's distributed {@link IMap}.
 *
 * <p>
 * An example of how to create a new instance can be seen below:
 *
 * <pre class="code">
 * Config config = new Config();
 *
 * // ... configure Hazelcast ...
 *
 * HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
 *
 * HazelcastSessionRepository sessionRepository =
 *         new HazelcastSessionRepository(hazelcastInstance);
 * </pre>
 *
 * In order to support finding sessions by principal name using
 * {@link #findByIndexNameAndIndexValue(String, String)} method, custom configuration of
 * {@code IMap} supplied to this implementation is required.
 *
 * The following snippet demonstrates how to define required configuration using
 * programmatic Hazelcast Configuration:
 *
 * <pre class="code">
 * MapAttributeConfig attributeConfig = new MapAttributeConfig()
 *         .setName(HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
 *         .setExtractor(PrincipalNameExtractor.class.getName());
 *
 * Config config = new Config();
 *
 * config.getMapConfig(HazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME)
 *         .addMapAttributeConfig(attributeConfig)
 *         .addMapIndexConfig(new MapIndexConfig(
 *                 HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE, false));
 *
 * Hazelcast.newHazelcastInstance(config);
 * </pre>
 *
 * This implementation listens for events on the Hazelcast-backed SessionRepository and
 * translates those events into the corresponding Spring Session events. Publish the
 * Spring Session events with the given {@link ApplicationEventPublisher}.
 *
 * <ul>
 * <li>entryAdded - {@link SessionCreatedEvent}</li>
 * <li>entryEvicted - {@link SessionExpiredEvent}</li>
 * <li>entryRemoved - {@link SessionDeletedEvent}</li>
 * </ul>
 *
 * @author Vedran Pavic
 * @author Tommy Ludwig
 * @author Mark Anderson
 * @author Aleksandar Stojsavljevic
 * @since 1.3.0
 */
public class HazelcastSessionRepository implements
		FindByIndexNameSessionRepository<HazelcastSessionRepository.HazelcastSession>,
		EntryAddedListener<String, MapSession>, EntryEvictedListener<String, MapSession>,
		EntryRemovedListener<String, MapSession> {

	/**
	 * The default name of map used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_SESSION_MAP_NAME = "spring:session:sessions";

	/**
	 * The principal name custom attribute name.
	 */
	public static final String PRINCIPAL_NAME_ATTRIBUTE = "principalName";

	private static final Log logger = LogFactory.getLog(HazelcastSessionRepository.class);

	private final HazelcastInstance hazelcastInstance;

	private ApplicationEventPublisher eventPublisher = new ApplicationEventPublisher() {

		@Override
		public void publishEvent(ApplicationEvent event) {
		}

		@Override
		public void publishEvent(Object event) {
		}

	};

	/**
	 * If non-null, this value is used to override
	 * {@link MapSession#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private String sessionMapName = DEFAULT_SESSION_MAP_NAME;

	private HazelcastFlushMode hazelcastFlushMode = HazelcastFlushMode.ON_SAVE;

	private IMap<String, MapSession> sessions;

	private String sessionListenerId;

	public HazelcastSessionRepository(HazelcastInstance hazelcastInstance) {
		Assert.notNull(hazelcastInstance, "HazelcastInstance must not be null");
		this.hazelcastInstance = hazelcastInstance;
	}

	@PostConstruct
	public void init() {
		this.sessions = this.hazelcastInstance.getMap(this.sessionMapName);
		this.sessionListenerId = this.sessions.addEntryListener(this, true);
	}

	@PreDestroy
	public void close() {
		this.sessions.removeEntryListener(this.sessionListenerId);
	}

	/**
	 * Sets the {@link ApplicationEventPublisher} that is used to publish
	 * {@link AbstractSessionEvent session events}. The default is to not publish session
	 * events.
	 *
	 * @param applicationEventPublisher the {@link ApplicationEventPublisher} that is used
	 * to publish session events. Cannot be null.
	 */
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		Assert.notNull(applicationEventPublisher,
				"ApplicationEventPublisher cannot be null");
		this.eventPublisher = applicationEventPublisher;
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * timeout. The default is 1800 (30 minutes).
	 * @param defaultMaxInactiveInterval the maximum inactive interval in seconds
	 */
	public void setDefaultMaxInactiveInterval(Integer defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Set the name of map used to store sessions.
	 * @param sessionMapName the session map name
	 */
	public void setSessionMapName(String sessionMapName) {
		Assert.hasText(sessionMapName, "Map name must not be empty");
		this.sessionMapName = sessionMapName;
	}

	/**
	 * Sets the Hazelcast flush mode. Default flush mode is
	 * {@link HazelcastFlushMode#ON_SAVE}.
	 * @param hazelcastFlushMode the new Hazelcast flush mode
	 */
	public void setHazelcastFlushMode(HazelcastFlushMode hazelcastFlushMode) {
		Assert.notNull(hazelcastFlushMode, "HazelcastFlushMode cannot be null");
		this.hazelcastFlushMode = hazelcastFlushMode;
	}

	@Override
	public HazelcastSession createSession() {
		HazelcastSession result = new HazelcastSession();
		if (this.defaultMaxInactiveInterval != null) {
			result.setMaxInactiveInterval(
					Duration.ofSeconds(this.defaultMaxInactiveInterval));
		}
		return result;
	}

	@Override
	public void save(HazelcastSession session) {
		if (!session.getId().equals(session.originalId)) {
			this.sessions.remove(session.originalId);
			session.originalId = session.getId();
		}
		if (session.isNew) {
			this.sessions.set(session.getId(), session.getDelegate(),
					session.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
		}
		else if (session.changed) {
			this.sessions.executeOnKey(session.getId(),
					new SessionUpdateEntryProcessor(session.getLastAccessedTime(),
							session.getMaxInactiveInterval(), session.delta));
		}
		session.clearFlags();
	}

	@Override
	public HazelcastSession findById(String id) {
		MapSession saved = this.sessions.get(id);
		if (saved == null) {
			return null;
		}
		if (saved.isExpired()) {
			deleteById(saved.getId());
			return null;
		}
		return new HazelcastSession(saved);
	}

	@Override
	public void deleteById(String id) {
		this.sessions.remove(id);
	}

	@Override
	public Map<String, HazelcastSession> findByIndexNameAndIndexValue(String indexName,
			String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}
		Collection<MapSession> sessions = this.sessions
				.values(Predicates.equal(PRINCIPAL_NAME_ATTRIBUTE, indexValue));
		Map<String, HazelcastSession> sessionMap = new HashMap<>(sessions.size());
		for (MapSession session : sessions) {
			sessionMap.put(session.getId(), new HazelcastSession(session));
		}
		return sessionMap;
	}

	@Override
	public void entryAdded(EntryEvent<String, MapSession> event) {
		if (logger.isDebugEnabled()) {
			logger.debug("Session created with id: " + event.getValue().getId());
		}
		this.eventPublisher.publishEvent(new SessionCreatedEvent(this, event.getValue()));
	}

	@Override
	public void entryEvicted(EntryEvent<String, MapSession> event) {
		if (logger.isDebugEnabled()) {
			logger.debug("Session expired with id: " + event.getOldValue().getId());
		}
		this.eventPublisher
				.publishEvent(new SessionExpiredEvent(this, event.getOldValue()));
	}

	@Override
	public void entryRemoved(EntryEvent<String, MapSession> event) {
		if (logger.isDebugEnabled()) {
			logger.debug("Session deleted with id: " + event.getOldValue().getId());
		}
		this.eventPublisher
				.publishEvent(new SessionDeletedEvent(this, event.getOldValue()));
	}

	/**
	 * A custom implementation of {@link Session} that uses a {@link MapSession} as the
	 * basis for its mapping. It keeps track if changes have been made since last save.
	 *
	 * @author Aleksandar Stojsavljevic
	 */
	final class HazelcastSession implements Session {

		private final MapSession delegate;

		private boolean isNew;

		private boolean changed;

		private String originalId;

		private Map<String, Object> delta = new HashMap<>();

		/**
		 * Creates a new instance ensuring to mark all of the new attributes to be
		 * persisted in the next save operation.
		 */
		HazelcastSession() {
			this(new MapSession());
			this.isNew = true;
			flushImmediateIfNecessary();
		}

		/**
		 * Creates a new instance from the provided {@link MapSession}.
		 * @param cached the {@link MapSession} that represents the persisted session that
		 * was retrieved. Cannot be {@code null}.
		 */
		HazelcastSession(MapSession cached) {
			Assert.notNull(cached, "MapSession cannot be null");
			this.delegate = cached;
			this.originalId = cached.getId();
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.changed = true;
			flushImmediateIfNecessary();
		}

		@Override
		public boolean isExpired() {
			return this.delegate.isExpired();
		}

		@Override
		public Instant getCreationTime() {
			return this.delegate.getCreationTime();
		}

		@Override
		public String getId() {
			return this.delegate.getId();
		}

		@Override
		public String changeSessionId() {
			this.isNew = true;
			return this.delegate.changeSessionId();
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.delegate.setMaxInactiveInterval(interval);
			this.changed = true;
			flushImmediateIfNecessary();
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			return this.delegate.getAttribute(attributeName);
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.delegate.setAttribute(attributeName, attributeValue);
			this.delta.put(attributeName, attributeValue);
			this.changed = true;
			flushImmediateIfNecessary();
		}

		@Override
		public void removeAttribute(String attributeName) {
			this.delegate.removeAttribute(attributeName);
			this.delta.put(attributeName, null);
			this.changed = true;
			flushImmediateIfNecessary();
		}

		MapSession getDelegate() {
			return this.delegate;
		}

		void clearFlags() {
			this.isNew = false;
			this.changed = false;
			this.delta.clear();
		}

		private void flushImmediateIfNecessary() {
			if (HazelcastSessionRepository.this.hazelcastFlushMode == HazelcastFlushMode.IMMEDIATE) {
				HazelcastSessionRepository.this.save(this);
			}
		}

	}

	/**
	 * Hazelcast {@link EntryProcessor} responsible for handling updates to session.
	 *
	 * @since 2.0.0
	 * @see #save(HazelcastSession)
	 */
	private static final class SessionUpdateEntryProcessor
			extends AbstractEntryProcessor<String, MapSession> {

		private final Instant lastAccessedTime;

		private final Duration maxInactiveInterval;

		private final Map<String, Object> delta;

		SessionUpdateEntryProcessor(Instant lastAccessedTime,
				Duration maxInactiveInterval, Map<String, Object> delta) {
			this.lastAccessedTime = lastAccessedTime;
			this.maxInactiveInterval = maxInactiveInterval;
			this.delta = delta;
		}

		@Override
		public Object process(Map.Entry<String, MapSession> entry) {
			return Optional.ofNullable(entry.getValue())
                    .map(value -> {
                        value.setLastAccessedTime(this.lastAccessedTime);
                        value.setMaxInactiveInterval(this.maxInactiveInterval);
                        for (final Map.Entry<String, Object> attribute : this.delta.entrySet()) {
                            if (attribute.getValue() != null) {
                                value.setAttribute(attribute.getKey(), attribute.getValue());
                            }
                            else {
                                value.removeAttribute(attribute.getKey());
                            }
                        }
                        entry.setValue(value);
                        return value;
                    }).orElse(null);
		}

	}

}
