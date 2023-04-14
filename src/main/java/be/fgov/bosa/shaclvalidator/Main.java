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

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command-line validator
 * 
 * @author Bart Hanssens
 */
@Command(name = "SHACL Validator", mixinStandardHelpOptions = true, version = "1.0",
         description = "Validates an RDF file using a (Turtle) SHACL file.")
public class Main implements Runnable {
	private final static Logger LOG = LoggerFactory.getLogger(Main.class);

    @Option(names = "--data", description = "Data file location (URL or local file)", required = true)
    URL data;

    @Option(names = "--format", description = "Data file format")
    Optional<String> format;

    @Option(names = "--shacl", description = "SHACL file location (URL or local file)", required = true)
    URL shacl;

    @Option(names = "--report", description = "Write report to this file(s), format can be HTML, TTL or MD")
    Path[] reports;

    @Option(names = "--statsClasses", description = "Count number of classes")
    boolean statClasses;

    @Option(names = "--statsProperties", description = "Count number of predicates/properties")
    boolean statProperties;

    @Option(names = "--statsValues", description = "Count number of values for one or more properties")
    String[] statValues;

	/**
	 * Write errors and statistics, if any
	 * 
	 * @param errors
	 * @param stats
	 * @throws IOException 
	 */
	private void writeReports(Model errors, Statistics stats) throws IOException {
		if (reports == null) {
			Rio.write(errors, System.out, RDFFormat.TURTLE);
			return;
		}

		for(Path report: reports) {
			LOG.info("Writing report to {}", report);
			String ext = FilenameUtils.getExtension(report.toString());
	
			if (ext.equals("md") || ext.equals("html")) {
				TemplateReport tmpl = new TemplateReport(errors, shacl, data);
				tmpl.prepare();
				
				try(Writer w = Files.newBufferedWriter(report)) {
					tmpl.merge(ext, w);					
				}
			}
			if (ext.equals("ttl")) {
				try(Writer w = Files.newBufferedWriter(report)) {
					Rio.write(errors, w, RDFFormat.TURTLE);
				}
			}
		}
	}

	/**
	 * Main
	 * 
	 * @param args
	 */
    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }

	@Override
    public void run() {
		try {
			
			Validator validator = new Validator();
			Model errors = validator.validate(shacl, data, format);

			Statistics statistics = new Statistics(validator.getRepository());
			writeReports(errors, statistics);
		
		} catch (IOException e) {
			LOG.error(e.getMessage());
			System.exit(-1);
		}
		LOG.info("OK");
	}
}
