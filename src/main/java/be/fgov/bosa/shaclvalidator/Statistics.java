/*
 * Copyright (c) 2023, FPS BOSA
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package be.fgov.bosa.shaclvalidator;

import be.fgov.bosa.shaclvalidator.helper.Util;
import be.fgov.bosa.shaclvalidator.dao.CountedThing;
import be.fgov.bosa.shaclvalidator.helper.DataGovStats;
import be.fgov.bosa.shaclvalidator.helper.QB;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.rdf4j.model.BNode;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;


/**
 * Collect statistics
 * 
 * @author Bart Hanssens
 */
public class Statistics {

	private final Repository repo;

	/**
	 * Count the occurrence of the different RDF types / classes
	 * 
	 * @return 
	 */
	public List<CountedThing> countClasses() {
		try (RepositoryConnection conn = repo.getConnection()) {
			return conn.getStatements(null, RDF.TYPE, null)
				.stream()
				.filter(s -> s.getContext() == null || !s.getContext().equals(RDF4J.SHACL_SHAPE_GRAPH))
				.map(Statement::getObject)
				.collect(Collectors.groupingBy(IRI.class::cast, Collectors.counting()))
				.entrySet()
				.stream()
				.map(e -> new CountedThing(Util.prefixedIRI(e.getKey()), e.getValue()))
				.collect(Collectors.toList());
		}
	}

	/**
	 * Count the occurrence of the different RDF predicates / properties
	 * 
	 * @return 
	 */
	public List<CountedThing> countProperties() {
		try (RepositoryConnection conn = repo.getConnection()) {
			return conn.getStatements(null, null, null)
				.stream()
				.filter(s -> s.getContext() == null || !s.getContext().equals(RDF4J.SHACL_SHAPE_GRAPH))
				.map(Statement::getPredicate)
				.collect(Collectors.groupingBy(IRI.class::cast, Collectors.counting()))
				.entrySet()
				.stream()
				.map(e -> new CountedThing(Util.prefixedIRI(e.getKey()), e.getValue()))
				.collect(Collectors.toList());
		}
	}

	/**
	 * Count the occurrence of objects/values for a given list of predicates
	 * 
	 * @param predicates list of predicates
	 * @return 
	 */
	public Map<String,List<CountedThing>> countValues(List<IRI> predicates) {
		try (RepositoryConnection conn = repo.getConnection()) {
			return predicates
				.stream()
				.collect(Collectors.toMap(Util::prefixedIRI, p ->
					conn.getStatements(null, p, null)
						.stream()
						.filter(s -> s.getContext() == null || !s.getContext().equals(RDF4J.SHACL_SHAPE_GRAPH))
						.map(Statement::getObject)
						.collect(
							Collectors.groupingBy(Value::stringValue, Collectors.counting()))
						.entrySet()
						.stream()
						.map(e -> new CountedThing(e.getKey(), e.getValue()))
						.collect(Collectors.toList())));
		}
	}
	
	/**
	 * Count the occurrence of objects/values for a given list of predicates, expanding "known" prefixes
	 * 
	 * @param predicates list of predicates
	 * @return 
	 */
	public Map<String,List<CountedThing>> countValues(String[] predicates) {
		List<IRI> iris = new ArrayList<>(predicates.length);
		
		for(String predicate: predicates) {
			if (predicate.startsWith("http://") || predicate.startsWith("https://") ) {
				iris.add(Values.iri(predicate));
			} else {
				iris.add(Values.iri(Util.NS, predicate));
			}
		}
		return countValues(iris);
	}

	/**
	 * Add (DataCube) observations.
	 * Used for reporting the number of classes and properties.
	 * 
	 * @param m RDF model
	 * @param dataset name of the DataCube dataset
	 * @param counted list of counted items
	 */
	private void addObservations(Model m, String dataset, List<CountedThing> counted) {
		BNode node = Values.bnode(dataset);
		for (CountedThing c: counted) {
			BNode observation = Values.bnode();
			m.add(observation, RDF.TYPE, QB.OBSERVATION);
			m.add(observation, QB.DATASET_PROP, node);
			m.add(observation, DataGovStats.NAME, Values.iri(c.name()));
			m.add(observation, DataGovStats.NUMBER, Values.literal(c.number()));
		}
	}
	
	/**
	 * Add (DataCube) observations.
	 * Used for reporting the number of property values.
	 * 
	 * @param m RDF model
	 * @param dataset name of the DataCube dataset
	 * @param counted map with list of counted items
	 */
	private void addObservations(Model m, String dataset, Map<String, List<CountedThing>> counted) {
		BNode node = Values.bnode(dataset);
		for (String property: counted.keySet()) {
			IRI name = Values.iri(property);
			for (CountedThing c: countClasses()) {
				BNode observation = Values.bnode();
				m.add(observation, RDF.TYPE, QB.OBSERVATION);
				m.add(observation, QB.DATASET_PROP, node);
				m.add(observation, DataGovStats.NAME, name);
				m.add(observation, DataGovStats.VALUE, Util.toValue(c.name()));
				m.add(observation, DataGovStats.NUMBER, Values.literal(c.number()));
			}
		}
	}

	/**
	 * Return statistics as RDF (DataCube)
	 * 
	 * @param countClasses
	 * @param countProperties
	 * @param countValues
	 * @return RDF model
	 * @throws IOException 
	 */
	public Model asRDF(boolean countClasses, boolean countProperties, String[] countValues) throws IOException {
		Model m ;
		try(InputStream is = getClass().getClassLoader().getResourceAsStream("qb.ttl")) {
			m = Rio.parse(is, RDFFormat.TURTLE);
		}

		if (countClasses) {
			addObservations(m, "classesDataset", countClasses());
		}
		if (countProperties) {
			addObservations(m, "propertiesDataset", countProperties());
		}
		if (countValues.length > 0) {
			addObservations(m, "valuesDataset", countValues(countValues));
		}
		return m;
	}

	/**
	 * Constructor
	 * 
	 * @param repo repository
	 */
	/**
	 * Constructor
	 * 
	 * @param repo repository
	 */
    public Statistics(Repository repo) {
		this.repo = repo;
	}
}
