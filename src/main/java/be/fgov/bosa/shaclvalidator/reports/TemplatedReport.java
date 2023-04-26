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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.WriterConfig;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validation report based upon a text template
 * 
 * @author Bart Hanssens
 */
public class TemplatedReport implements Report {
	private final static Logger LOG = LoggerFactory.getLogger(TemplatedReport.class);

	private final static PebbleEngine engine = new PebbleEngine.Builder().build();
	
	private final String format;
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
	 * Get the shape as a string, with some embedding of additional details
	 * 
	 * @param issues model with all issues
	 * @param shapeID ID of the shape
	 * @return turtle result as string
	 */
	private String getShapeString(Model issues, Resource shapeID) {
		Model m = new LinkedHashModel();
		m.setNamespace(SHACL.NS);

		Model shape = issues.filter(shapeID, null, null);
		m.addAll(shape);
		Value value = findFirst(issues, shapeID, SHACL.NODE).orElse(null);
		// add more detail
		if (value != null) {
			Model node = issues.filter((Resource) value, null, null);
			m.addAll(node);
			Set<Value> props = node.filter((Resource) value, SHACL.PROPERTY, null).objects();

			for(Value prop: props) {
				m.addAll(issues.filter((Resource) prop, null, null));
			}
		}
		StringWriter sw = new StringWriter();
		WriterConfig config = new WriterConfig();
		config.set(BasicWriterSettings.PRETTY_PRINT, true);
		config.set(BasicWriterSettings.INLINE_BLANK_NODES, true);
		Rio.write(m, sw, RDFFormat.TURTLE, config);
		return sw.toString();
	}

	@Override
	public void reportValidation(Model issues, URL data, URL[] shacls) {
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
			String str = getShapeString(issues, (Resource) shapeID);
			
			Set<Resource> violationIDs = issues.filter(null, SHACL.SOURCE_SHAPE, shapeID).subjects();
			List<ValidationIssue> violations = new ArrayList<>(violationIDs.size());
			
			for(Resource violationID: violationIDs) {
				Model violation = issues.filter(violationID, null, null);
				
				ValidationIssue issue = new ValidationIssue(
					findFirst(violation, violationID, SHACL.FOCUS_NODE).get().stringValue(),
					((IRI) findFirst(violation, violationID, SHACL.SOURCE_CONSTRAINT_COMPONENT).get()).getLocalName(),
					findFirst(violation, violationID, SHACL.VALUE).orElse(na).stringValue()
				);
				violations.add(issue);
			}
			// all validation issues are actually on same component
			String component = violations.get(0).component();

			IRI path = (IRI) findFirst(issues, (Resource) shapeID, SHACL.PATH).get();
			String msg = (path != null) ? Util.prefixedIRI(path) + " " + component : component;

			ValidationInfo result = new ValidationInfo(shapeID.stringValue(), str, msg, violations);
			// check severity, default is violation
			IRI severity = (IRI) findFirst(issues, (Resource) shapeID, SHACL.SEVERITY_PROP).orElse(SHACL.VIOLATION);

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
		context.put("shacls", Arrays.asList(shacls).stream().map(URL::toString).collect(Collectors.toList()));
		context.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		context.put("errors", errors);
		context.put("warnings", warnings);
		context.put("infos", infos);
	}


	@Override
	public void reportStatistics(Map<String,Object> stats) {
		context.putAll(stats);
	}

	@Override
	public void write(Writer writer) throws IOException {
		PebbleTemplate template = engine.getTemplate("report." + format);
		template.evaluate(writer, context);
	}

	/**
	 * Constructor
	 * @param format 
	 */
	public TemplatedReport(String format) {
		this.format = format;
	}
}
