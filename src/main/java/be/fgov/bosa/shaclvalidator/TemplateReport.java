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
	private final Map<String, Object> context = new HashMap<>();

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
	 * @param issues validation issues model
	 * @param data location of the data
	 * @param shacl location of the SHACL rules
	 * @throws IOException 
	 */
	public void prepareValidation(Model issues, URL data, URL shacl) throws IOException {
		Value na = Values.literal("n/a");
		for (Namespace ns: Util.NS) {
			issues.setNamespace(ns);
		}

		List<ValidationInfo> errors = new ArrayList<>();
		List<ValidationInfo> warnings = new ArrayList<>();
		List<ValidationInfo> infos = new ArrayList<>();

		// IDs of shapes being violated
		Set<Value> shapeIDs = issues.filter(null, SHACL.SOURCE_SHAPE, null).objects();
		
		for (Value shapeID: shapeIDs) {
			Model shape = issues.filter((Resource) shapeID, null, null);
			StringWriter sw = new StringWriter();
			Rio.write(shape, sw, RDFFormat.TURTLE);
			String str = sw.toString();
			
			Set<Resource> violationIDs = issues.filter(null, SHACL.SOURCE_SHAPE, shapeID).subjects();
			List<ValidationIssue> violations = new ArrayList<>(violationIDs.size());
			
			for(Resource violationID: violationIDs) {
				Model violation = issues.filter(violationID, null, null);
				
				ValidationIssue issue = new ValidationIssue(
					findFirst(violation, violationID, SHACL.FOCUS_NODE).get().stringValue(),
					((IRI)findFirst(violation, violationID, SHACL.SOURCE_CONSTRAINT_COMPONENT).get()).getLocalName(),
					findFirst(violation, violationID, SHACL.VALUE).orElse(na).stringValue()
				);
				violations.add(issue);
			}
			// all validation issues are actually on same component
			String component = violations.get(0).component();

			IRI path = (IRI) findFirst(shape, (Resource) shapeID, SHACL.PATH).get();
			String msg = (path != null) ? Util.prefixedIRI(path) + " " + component : component;

			ValidationInfo result = new ValidationInfo(shapeID.stringValue(), str, msg, violations);
			// check severity, default is violation
			IRI severity = (IRI) findFirst(shape, (Resource) shapeID, SHACL.SEVERITY_PROP).orElse(SHACL.VIOLATION);

			if (severity.equals(SHACL.VIOLATION)) {
				errors.add(result);
			} else if (severity.equals(SHACL.WARNING)) {
				warnings.add(result);
			} else if (severity.equals(SHACL.INFO)) {
				infos.add(result);
			}	
		}
		
		LOG.info("Shapes with errors: {}", errors.size());
		LOG.info("Shapes with warnings: {}", warnings.size());
		LOG.info("Shapes with recommendations: {}", infos.size());

		context.put("data", data.toString());
		context.put("shacl", shacl.toString());
		context.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		context.put("errors", errors);
		context.put("warnings", warnings);
		context.put("infos", infos);
	}

	/**
	 * Prepare the statistics
	 * 
	 * @param stats
	 * @param classes gather statistics on classes
	 * @param properties gather statistics on properties
	 * @param values gather statistics on property values
	 */
	public void prepareStatistics(Statistics stats, boolean classes, boolean properties, String[] values) {
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
			Map<String, List<CountedThing>> countValues = stats.countValues(values);
			LOG.info("Value details: {}", countValues.size());
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

}
