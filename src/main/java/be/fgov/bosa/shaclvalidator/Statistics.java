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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Statistics collector
 * 
 * @author Bart Hanssens
 */
public class Statistics {
	private final static Logger LOG = LoggerFactory.getLogger(Statistics.class);

	private final Repository repo;

	/**
	 * Count the occurrence of the different RDF types / classes
	 * 
	 * @return 
	 */
	private List<CountedThing> countClasses() {
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
	private List<CountedThing> countProperties() {
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
	private Map<String,List<CountedThing>> countValues(List<IRI> predicates) {
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
	 * Collect the statistics
	 * 
	 * @param classes collect statistics on classes
	 * @param properties collect statistics on properties
	 * @param values collect statistics on property values
	 * @return map of statistics
	 */
	public Map<String,Object> collect(boolean classes, boolean properties, String[] values) {
		Map<String,Object> stats = new HashMap<>();
	
		if (classes) {
			List<CountedThing> countClasses = countClasses();
			LOG.info("Classes: {}", countClasses.size());
			stats.put("classes", countClasses);
		}
		if (properties) {
			List<CountedThing> countProperties = countProperties();
			LOG.info("Properties: {}", countProperties.size());
			stats.put("properties", countProperties);
		}
		if (values != null && values.length > 0) {
			Map<String, List<CountedThing>> countValues = countValues(values);
			LOG.info("Value details: {}", countValues.size());
			stats.put("values", countValues);
		}
		return stats;
	}

	/**
	 * Constructor
	 * 
	 * @param repo repository
	 */
    public Statistics(Repository repo) {
		this.repo = repo;
	}
}
