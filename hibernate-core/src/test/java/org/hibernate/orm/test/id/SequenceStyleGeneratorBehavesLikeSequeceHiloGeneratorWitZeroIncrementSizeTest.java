/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.id;

import java.util.Properties;

import org.hibernate.Transaction;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.ObjectNameNormalizer;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.internal.SessionImpl;
import org.hibernate.type.StandardBasicTypes;

import org.hibernate.testing.boot.MetadataBuildingContextTestingImpl;
import org.hibernate.testing.orm.junit.BaseUnitTest;
import org.hibernate.testing.orm.junit.DialectFeatureChecks.SupportsSequences;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Andrea Boriero
 */
@RequiresDialectFeature(feature = SupportsSequences.class)
@BaseUnitTest
public class SequenceStyleGeneratorBehavesLikeSequeceHiloGeneratorWitZeroIncrementSizeTest {
	private static final String TEST_SEQUENCE = "test_sequence";

	private StandardServiceRegistry serviceRegistry;
	private SessionFactoryImplementor sessionFactory;
	private SequenceStyleGenerator generator;
	private SessionImplementor sessionImpl;
	private SequenceValueExtractor sequenceValueExtractor;

	@BeforeEach
	public void setUp() throws Exception {
		serviceRegistry = new StandardServiceRegistryBuilder()
				.enableAutoClose()
				.applySetting( AvailableSettings.HBM2DDL_AUTO, "create-drop" )
				.build();

		generator = new SequenceStyleGenerator();

		// Build the properties used to configure the id generator
		Properties properties = new Properties();
		properties.setProperty( SequenceStyleGenerator.SEQUENCE_PARAM, TEST_SEQUENCE );
		properties.setProperty( SequenceStyleGenerator.OPT_PARAM, "legacy-hilo" );
		properties.setProperty( SequenceStyleGenerator.INCREMENT_PARAM, "0" ); // JPA allocationSize of 1
		properties.put(
				PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER,
				new ObjectNameNormalizer() {
					@Override
					protected MetadataBuildingContext getBuildingContext() {
						return new MetadataBuildingContextTestingImpl( serviceRegistry );
					}
				}
		);
		generator.configure( StandardBasicTypes.LONG, properties, serviceRegistry );

		final Metadata metadata = new MetadataSources( serviceRegistry ).buildMetadata();
		generator.registerExportables( metadata.getDatabase() );

		sessionFactory = (SessionFactoryImplementor) metadata.buildSessionFactory();
		sequenceValueExtractor = new SequenceValueExtractor( sessionFactory.getDialect(), TEST_SEQUENCE );
	}

	@AfterEach
	public void tearDown() {
		if ( sessionImpl != null && !sessionImpl.isClosed() ) {
			sessionImpl.close();
		}
		if ( sessionFactory != null ) {
			sessionFactory.close();
		}
		if ( serviceRegistry != null ) {
			StandardServiceRegistryBuilder.destroy( serviceRegistry );
		}
	}

	@Test
	public void testHiLoAlgorithm() {
		sessionImpl = (SessionImpl) sessionFactory.openSession();

		assertEquals( 1L, generateValue() );

		assertEquals( 1L, extractSequenceValue() );

		assertEquals( 2L, generateValue() );
		assertEquals( 2L, extractSequenceValue() );

		assertEquals( 3L, generateValue() );
		assertEquals( 3L, extractSequenceValue() );

		assertEquals( 4L, generateValue() );
		assertEquals( 4L, extractSequenceValue() );

		assertEquals( 5L, generateValue() );
		assertEquals( 5L, extractSequenceValue() );

		sessionImpl.close();
	}

	private long extractSequenceValue() {
		return sequenceValueExtractor.extractSequenceValue( sessionImpl );
	}

	private long generateValue() {
		Long generatedValue;
		Transaction transaction = sessionImpl.beginTransaction();
		generatedValue = (Long) generator.generate( sessionImpl, null );
		transaction.commit();
		return generatedValue.longValue();
	}
}