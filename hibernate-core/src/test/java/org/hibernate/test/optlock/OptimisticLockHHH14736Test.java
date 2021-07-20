/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.optlock;

import static org.junit.Assert.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.PersistenceException;
import javax.persistence.Version;

import org.hibernate.JDBCException;
import org.hibernate.Session;
import org.hibernate.StaleObjectStateException;
import org.hibernate.StaleStateException;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.Source;
import org.hibernate.annotations.SourceType;
import org.hibernate.dialect.CockroachDB192Dialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.testing.RequiresDialect;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Test;

/**
 * Supplemental tests relating to the optimistic-lock mapping option for SQL Server rowversion/timestamp columns.
 *
 * @author Jason Pyeron
 */
@RequiresDialect(SQLServerDialect.class)
public class OptimisticLockHHH14736Test extends BaseCoreFunctionalTestCase {

	@Test
	public void testOptimisticLock() {
		Session mainSession = openSession();
		mainSession.beginTransaction();
		Person p = new Person();
		p.setName( "Bob" );
		mainSession.save( p );
		mainSession.getTransaction().commit();
		mainSession.close();

		mainSession = openSession();
		mainSession.beginTransaction();
		p = mainSession.get( Person.class, p.getId() );

		Session otherSession = sessionFactory().openSession();
		otherSession.beginTransaction();
		Person otherP = otherSession.get( Person.class, p.getId() );
		otherP.setName( "Robert" );
		otherSession.getTransaction().commit();
		otherSession.close();

		try {
			p.setName( "Bobby" );
			mainSession.flush();
			fail( "expecting opt lock failure" );
		}
		catch (PersistenceException e) {
			// expected
			checkException( mainSession, e );
		}
		mainSession.clear();
		mainSession.getTransaction().rollback();
		mainSession.close();

		mainSession = openSession();
		mainSession.beginTransaction();
		p = mainSession.load( Person.class, p.getId() );
		mainSession.delete( p );
		mainSession.getTransaction().commit();
		mainSession.close();
	}

	@Test
	public void testOptimisticLockDelete() {
		Session mainSession = openSession();
		mainSession.beginTransaction();
		Person p = new Person();
		p.setName( "Bob" );
		mainSession.save( p );
		mainSession.flush();
		p.setName( "Robert" );
		mainSession.flush();
		p.setName( "Bobby" );
		mainSession.flush();
		mainSession.getTransaction().commit();
		mainSession.close();

		mainSession = openSession();
		mainSession.beginTransaction();
		p = mainSession.get( Person.class, p.getId() );

		Session otherSession = openSession();
		otherSession.beginTransaction();
		Person otherP = otherSession.get( Person.class, p.getId() );
		otherP.setName( "Rob" );
		otherSession.flush();
		otherSession.getTransaction().commit();
		otherSession.close();

		try {
			mainSession.delete( p );
			mainSession.flush();
			fail( "expecting opt lock failure" );
		}
		catch (StaleObjectStateException e) {
			// expected
		}
		catch (PersistenceException e) {
			// expected
			checkException( mainSession, e );
		}
		mainSession.clear();
		mainSession.getTransaction().rollback();
		mainSession.close();

		mainSession = openSession();
		mainSession.beginTransaction();
		p = mainSession.load( Person.class, p.getId() );
		mainSession.delete( p );
		mainSession.getTransaction().commit();
		mainSession.close();
	}

	private void checkException(Session mainSession, PersistenceException e) {
		final Throwable cause = e.getCause();
		if ( cause instanceof JDBCException ) {
			if ( getDialect() instanceof SQLServerDialect && ( (JDBCException) cause ).getErrorCode() == 3960 ) {
				// SQLServer will report this condition via a SQLException
				// when using its SNAPSHOT transaction isolation.
				// it seems to "lose track" of the transaction as well...
				mainSession.getTransaction().rollback();
				mainSession.beginTransaction();
			}
			else if ( getDialect() instanceof CockroachDB192Dialect && ( (JDBCException) cause ).getSQLState().equals( "40001" ) ) {
				// CockroachDB always runs in SERIALIZABLE isolation, and uses SQL state 40001 to indicate
				// serialization failure.
			}
			else {
				throw e;
			}
		}
		else if ( !( cause instanceof StaleObjectStateException ) && !( cause instanceof StaleStateException ) ) {
			fail( "expected StaleObjectStateException or StaleStateException exception but is " + cause );
		}
	}

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class[]{ Person.class };
	}

	@Entity(name = "Person")
	public static class Person {

		@Id
		@GeneratedValue
		private Long id;

		@Version
		@Source(value = SourceType.DBBINARY)
		@Column(columnDefinition = "timestamp not null")
		@Generated(GenerationTime.ALWAYS)
		private byte[] version;

		private String name;

		public Person() {
		}

		public Person(Long id, String name) {
			this.id = id;
			this.name = name;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return "Person [id=" + id + ", version=" + version + ", name=" + name + "]";
		}

	}
}
