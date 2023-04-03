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
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.SHACL;

/**
 * Build an HTML overview of a vaidation report
 * 
 * @author Bart Hanssens
 */
public class HtmlReport {
	private final Model model;
	private final URL shacl;
	private final URL data;

	public String generate() throws IOException {
		PebbleEngine engine = new PebbleEngine.Builder().build();
		PebbleTemplate compiledTemplate = engine.getTemplate("report.html");

		Value na = Values.literal("n/a");
		model.setNamespace(SHACL.NS);

		Set<Value> paths = model.filter(null, SHACL.RESULT_PATH, null).objects();
		TreeMap<String,Object> tree = new TreeMap<>();
		for(Value path: paths) {
			Set<Resource> subjs = model.filter(null, SHACL.RESULT_PATH, path).subjects();
			List<List<String>> lst = new ArrayList<>();	

			for(Resource subj: subjs) {
				lst.add(
					List.of(
						model.filter(subj, SHACL.FOCUS_NODE, null).objects().stream().findFirst().get().stringValue(),						
						model.filter(subj, SHACL.SOURCE_CONSTRAINT_COMPONENT, null).objects().stream().findFirst().map(IRI.class::cast).get().getLocalName(),
						model.filter(subj, SHACL.VALUE, null).objects().stream().findFirst().orElse(na).stringValue()));
				tree.put(path.stringValue(), lst);
			}
		}
		
		Map<String, Object> context = new HashMap<>();
		context.put("data", data.toString());
		context.put("shacl", shacl.toString());
		context.put("timestamp", LocalDateTime.now().toString());
		context.put("tree", tree);

		Writer writer = new StringWriter();
		compiledTemplate.evaluate(writer, context);

		return writer.toString();
	}

	/**
	 * Constructor
	 * 
	 * @param model validation report
	 * @param data location of the data
	 * @param shacl location of the SHACL file
	 */
	public HtmlReport(Model model, URL data, URL shacl) {
		this.model = model;
		this.data = data;
		this.shacl = shacl;
	}
}
