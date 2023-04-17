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

import be.fgov.bosa.shaclvalidator.dao.CountedThing;
import be.fgov.bosa.shaclvalidator.dao.ValidationInfo;
import be.fgov.bosa.shaclvalidator.dao.ValidationIssue;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validation report based upon a text template
 * 
 * @author Bart Hanssens
 */
public class TemplateReport {
	private final static Logger LOG = LoggerFactory.getLogger(TemplateReport.class);

	private final PebbleEngine engine = new PebbleEngine.Builder().build();

	private final Model model;
	private final URL shacl;
	private final URL data;

	private final Map<String, Object> context = new HashMap<>();
	private final Statistics stats;

	/**
	 * Find first value for a given ID (subject) and property (predicate)
	 * 
	 * @param m
	 * @param id
	 * @param property
	 * @return 
	 */
	private Optional<Value> findFirst(Model m, Resource id, IRI property) {
		return m.filter(id, property, null).objects().stream().findFirst();
	}
	
	/**
	 * Prepare the validation report
	 * 
	 * @throws IOException 
	 */
	public void prepareValidation() throws IOException {
		Value na = Values.literal("n/a");
		for (Namespace ns: Validator.NS) {
			model.setNamespace(ns);
		}

		List<ValidationInfo> errors = new ArrayList<>();
		List<ValidationInfo> warnings = new ArrayList<>();
		// IDs of shapes being violated
		Set<Value> shapeIDs = model.filter(null, SHACL.SOURCE_SHAPE, null).objects();
		LOG.info("Shapes violated: {}", shapeIDs.size());
		
		for (Value shapeID: shapeIDs) {
			Model shape = model.filter((Resource) shapeID, null, null);
			StringWriter sw = new StringWriter();
			Rio.write(shape, sw, RDFFormat.TURTLE);
			String str = sw.toString();
			
			Set<Resource> violationIDs = model.filter(null, SHACL.SOURCE_SHAPE, shapeID).subjects();
			List<ValidationIssue> issues = new ArrayList<>(violationIDs.size());
			
			for(Resource violationID: violationIDs) {
				Model violation = model.filter(violationID, null, null);
				
				ValidationIssue issue = new ValidationIssue(
					findFirst(violation, violationID, SHACL.FOCUS_NODE).get().stringValue(),
					((IRI)findFirst(violation, violationID, SHACL.SOURCE_CONSTRAINT_COMPONENT).get()).getLocalName(),
					findFirst(violation, violationID, SHACL.VALUE).orElse(na).stringValue()
				);
				issues.add(issue);
			}
			// all validation issues are actually on same component
			String component = issues.get(0).component();

			IRI path = (IRI) findFirst(shape, (Resource) shapeID, SHACL.PATH).get();
			String msg = (path != null) ? Util.prefixedIRI(path) + " " + component : component;

			errors.add(new ValidationInfo(shapeID.stringValue(), str, msg, issues));
		}

		context.put("data", data.toString());
		context.put("shacl", shacl.toString());
		context.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		context.put("errors", errors);
		context.put("warnings", warnings);
	}

	/**
	 * Prepare the statistics
	 * 
	 * @param classes gather statistics on classes
	 * @param properties gather statistics on properties
	 * @param values
	 */
	public void prepareStatistics(boolean classes, boolean properties, String[] values) {
		if (classes) {
			List<CountedThing> countClasses = stats.countClasses();
			LOG.info("Classes: {}", countClasses.size());
			context.put("classes", countClasses);
		}
		if (properties) {
			List<CountedThing> countProperties = stats.countProperties();
			LOG.info("Properties: {}", countProperties.size());
			context.put("properties", countProperties);
		}
		if (values.length > 0) {
			Map<IRI, Map<Value, Long>> countValues = stats.countValues(values);
			LOG.info("Values: {}", countValues.size());
			context.put("values", countValues);
		}
	}

	/**
	 * Merge the data and the template into a report
	 * 
	 * @param format report format
	 * @param writer
	 * @throws IOException 
	 */
	public void merge(String format, Writer writer) throws IOException {
		PebbleTemplate template = engine.getTemplate("report." + format);
		template.evaluate(writer, context);
	}

	/**
	 * Constructor
	 * 
	 * @param model validation report
	 * @param data location of the data
	 * @param shacl location of the SHACL file
	 * @param stats statistics
	 */
	public TemplateReport(Model model, URL data, URL shacl, Statistics stats) {
		this.model = model;
		this.data = data;
		this.shacl = shacl;
		this.stats = stats;
	}
}
