/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.file.remote.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.integration.util.UpperBound;

/**
 * A {@link SessionFactory} implementation that caches Sessions for reuse without
 * requiring reconnection each time the Session is retrieved from the factory.
 * This implementation wraps and delegates to a target SessionFactory instance.
 *
 * @author Josh Long
 * @author Oleg Zhurakousky
 * @author Mark Fisher
 * @since 2.0
 */
public class CachingSessionFactory implements SessionFactory, DisposableBean{

	private static final Log logger = LogFactory.getLog(CachingSessionFactory.class);

	private volatile long sessionWaitTimeout = Integer.MAX_VALUE;

	private volatile LinkedBlockingQueue<Session> queue = new LinkedBlockingQueue<Session>();

	private final SessionFactory sessionFactory;

	private final UpperBound sessionSizeManager;
	
	public CachingSessionFactory(SessionFactory sessionFactory) {
		this(sessionFactory, 0);
	}
	
	public CachingSessionFactory(SessionFactory sessionFactory, int sessionCacheSize) {
		this.sessionFactory = sessionFactory;
		this.sessionSizeManager = new UpperBound(sessionCacheSize);
	}
	
	/**
	 * Sets the limit of how long it will wait for a session to become available after which 
	 * it will throw {@link IllegalStateException}.
	 */
	public void setSessionWaitTimeout(long sessionWaitTimeout) {
		this.sessionWaitTimeout = sessionWaitTimeout;
	}

	public Session getSession() {	
		try {
			boolean permitted = this.sessionSizeManager.tryAcquire(this.sessionWaitTimeout);
			if (!permitted){
				throw new IllegalStateException("Timed out while waiting to aquire Session");
			}		
			Session session = this.doGetSession();
			return new CachedSession(session);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Exception was received during attempt to obtain Session", e);
		}
	}

	public void destroy() {
		if (this.queue != null) {
			for (Session session : this.queue) {
				this.closeSession(session);
			}
		}
	}
	
	private Session doGetSession() throws InterruptedException {
		Session session = this.queue.poll();

		if (session != null && !session.isOpen()){
			if (logger.isDebugEnabled()) {
				logger.debug("Received stale Session, will attempt to get a new one");
			}
			return this.doGetSession();
		}
		else if (session == null){
			session = this.sessionFactory.getSession();
		}
		return session;
	}

	private void closeSession(Session session) {
		try {
			if (session != null) {
				session.close();
			}	
		}
		catch (Throwable e) {
			if (logger.isWarnEnabled()) {
				// log and ignore
				logger.warn("Exception was thrown while destroying Session. ", e);
			}
		}
	}


	private class CachedSession implements Session {

		private final Session targetSession;

		private CachedSession(Session targetSession) {
			this.targetSession = targetSession;
		}

		public void close() {
			if (logger.isDebugEnabled()){
				logger.debug("Releasing Session back into the pool");
			}
			queue.add(targetSession);
			sessionSizeManager.release();
		}

		public boolean remove(String path) throws IOException{
			return this.targetSession.remove(path);
		}

		public <F> F[] list(String path) throws IOException{
			return this.targetSession.<F>list(path);
		}

		public void read(String source, OutputStream os) throws IOException{
			this.targetSession.read(source, os);
		}

		public void write(InputStream inputStream, String destination) throws IOException{
			this.targetSession.write(inputStream, destination);
		}

		public boolean isOpen() {
			return this.targetSession.isOpen();
		}

		public void rename(String pathFrom, String pathTo) throws IOException {
			this.targetSession.rename(pathFrom, pathTo);
		}

		public void mkdir(String directory) throws IOException {
			this.targetSession.mkdir(directory);
		}
	}
}
