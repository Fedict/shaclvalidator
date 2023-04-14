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

import be.fgov.bosa.shaclvalidator.dao.ValidationInfo;
import be.fgov.bosa.shaclvalidator.dao.ValidationIssue;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
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
	 * Prepare the report
	 * 
	 * @throws IOException 
	 */
	public void prepare() throws IOException {
		Value na = Values.literal("n/a");
		model.setNamespace(SHACL.NS);

		Map<String,ValidationInfo> errors = new TreeMap<>();
		Map<String,ValidationInfo> warnings = new TreeMap<>();
		// IDs of shapes being violated
		Set<Value> shapeIDs = model.filter(null, SHACL.SOURCE_SHAPE, null).objects();
		LOG.info("Shapes violated: {}", shapeIDs.size());
		
		for (Value shapeID: shapeIDs) {
			Model m = model.filter((Resource) shapeID, null, null);
			StringWriter sw = new StringWriter();
			Rio.write(m, sw, RDFFormat.TURTLE);
			String shape = sw.toString();
			
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
			errors.put(shapeID.stringValue(), new ValidationInfo(shape, issues));
		}

		context.put("data", data.toString());
		context.put("shacl", shacl.toString());
		context.put("timestamp", LocalDateTime.now().toString());
		context.put("errors", errors);
		context.put("warnings", warnings);
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
	 */
	public TemplateReport(Model model, URL data, URL shacl) {
		this.model = model;
		this.data = data;
		this.shacl = shacl;

	}
}
