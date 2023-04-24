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
package be.fgov.bosa.shaclvalidator.reports;

import be.fgov.bosa.shaclvalidator.helper.Util;
import be.fgov.bosa.shaclvalidator.dao.CountedThing;
import be.fgov.bosa.shaclvalidator.helper.DataGovStats;
import be.fgov.bosa.shaclvalidator.helper.QB;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.eclipse.rdf4j.model.BNode;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validation report in RDF/Turtle
 * 
 * @author Bart Hanssens
 */
public class TurtleReport implements Report {
	private final static Logger LOG = LoggerFactory.getLogger(TurtleReport.class);

	private Model model = new LinkedHashModel();

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
			for (CountedThing c: counted.get(property)) {
				BNode observation = Values.bnode();
				m.add(observation, RDF.TYPE, QB.OBSERVATION);
				m.add(observation, QB.DATASET_PROP, node);
				m.add(observation, DataGovStats.NAME, name);
				m.add(observation, DataGovStats.VALUE, Util.toValue(c.name()));
				m.add(observation, DataGovStats.NUMBER, Values.literal(c.number()));
			}
		}
	}

	@Override
	public void reportValidation(Model issues, URL data, URL shacl) {
		model.addAll(issues);

		Resource id = model.filter(null, RDF.TYPE, SHACL.VALIDATION_REPORT).subjects().stream().findFirst().get();
		model.add(id, DCTERMS.ISSUED, Values.literal(LocalDateTime.now()));
		model.add(id, DCTERMS.SOURCE, Values.literal(data.toString()));
		model.add(id, DCTERMS.CONFORMS_TO, Values.literal(shacl.toString()));
	}

	@Override
	public void reportStatistics(Map<String, Object> stats) {
		try(InputStream is = getClass().getClassLoader().getResourceAsStream("qb.ttl")) {
			Model m = Rio.parse(is, RDFFormat.TURTLE);
			model.addAll(m);
		} catch (IOException ioe) {
			//
		}
		if (stats.containsKey("classes")) {
			addObservations(model, "classesDataset", (List<CountedThing>) stats.get("classes"));
		}
		if (stats.containsKey("properties")) {
			addObservations(model, "propertiesDataset", (List<CountedThing>) stats.get("properties"));
		}
		if (stats.containsKey("values")){
			addObservations(model, "valuesDataset", (Map<String, List<CountedThing>>) stats.get("values"));
		}	
	}

	@Override
	public void write(Writer writer) throws IOException {
		Rio.write(model, writer, RDFFormat.TURTLE);
	}

}
